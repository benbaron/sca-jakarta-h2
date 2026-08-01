package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.time.LocalDate;

/** Maps reviewed bank statement facts to cleared state on canonical ledger bank lines. */
public class BankClearedStateService
{
    private final Jpa jpa;

    public BankClearedStateService(Jpa jpa)
    {
        this.jpa = jpa;
    }

    public BankClearedStateResult markMatchedAndCleared(long statementLineId, long splitId)
    {
        try (EntityManager em = jpa.em())
        {
            var tx = em.getTransaction();
            tx.begin();
            try
            {
                BankStatementLine statementLine = required(em, BankStatementLine.class, statementLineId, "Bank statement line");
                TxnSplit split = required(em, TxnSplit.class, splitId, "Transaction split");
                CompanyBankAccount configuredAccount = statementLine.getBankAccount();
                if (configuredAccount == null || configuredAccount.getAccount() == null)
                {
                    throw new IllegalArgumentException("Bank statement line must reference a configured bank account.");
                }
                Account bankLedgerAccount = configuredAccount.getAccount();
                if (split.getAccount() == null || !split.getAccount().getId().equals(bankLedgerAccount.getId()))
                {
                    throw new IllegalArgumentException("Matched split must use the configured bank ledger account.");
                }
                if (statementLine.getCompany() == null || configuredAccount.getCompany() == null
                        || !statementLine.getCompany().getId().equals(configuredAccount.getCompany().getId()))
                {
                    throw new IllegalArgumentException("Bank statement line and configured bank account must belong to the same company.");
                }

                LocalDate clearedOn = statementLine.getPostedDate() == null ? statementLine.getTransactionDate() : statementLine.getPostedDate();
                split.setBankCleared(true);
                split.setBankClearedOn(clearedOn);
                split.setMatchedBankStatementLine(statementLine);
                statementLine.setMatchedTransaction(split.getTxn());
                statementLine.setStatus(BankStatementLine.Status.MATCHED);
                statementLine.touchUpdatedAt();
                tx.commit();
                return new BankClearedStateResult(statementLine.getId(), split.getTxn().getId(), split.getId(), clearedOn);
            }
            catch (RuntimeException ex)
            {
                rollback(tx);
                throw ex;
            }
        }
    }

    /** Restores a factual cleared-state relationship inside an interchange caller's transaction. */
    public void applyForImport(
            EntityManager em,
            Company company,
            TxnSplit split,
            BankStatementLine statementLine,
            LocalDate clearedOn)
    {
        if (em == null || company == null || split == null)
        {
            throw new IllegalArgumentException("Company and transaction split are required for cleared-state import");
        }
        if (!em.getTransaction().isActive())
        {
            throw new IllegalStateException("Cleared-state import requires an active caller-owned transaction");
        }
        if (split.getTxn() == null || split.getTxn().getCompany() == null
                || !company.getId().equals(split.getTxn().getCompany().getId()))
        {
            throw new IllegalArgumentException("Cleared transaction line belongs to another company");
        }
        if (statementLine != null)
        {
            CompanyBankAccount configuredAccount = statementLine.getBankAccount();
            if (statementLine.getCompany() == null
                    || !company.getId().equals(statementLine.getCompany().getId()))
            {
                throw new IllegalArgumentException("Cleared statement line belongs to another company");
            }
            if (configuredAccount == null || configuredAccount.getAccount() == null
                    || split.getAccount() == null
                    || !split.getAccount().getId().equals(configuredAccount.getAccount().getId()))
            {
                throw new IllegalArgumentException(
                        "Cleared transaction line must use the reviewed statement's configured bank account");
            }
        }
        split.setBankCleared(true);
        split.setBankClearedOn(clearedOn);
        split.setMatchedBankStatementLine(statementLine);
    }

    private static <T> T required(EntityManager em, Class<T> type, long id, String label)
    {
        T value = em.find(type, id);
        if (value == null)
        {
            throw new IllegalArgumentException(label + " does not exist: " + id + ".");
        }
        return value;
    }

    private static void rollback(jakarta.persistence.EntityTransaction tx)
    {
        if (tx.isActive())
        {
            tx.rollback();
        }
    }
}
