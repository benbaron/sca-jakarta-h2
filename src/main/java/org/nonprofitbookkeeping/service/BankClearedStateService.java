package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
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
