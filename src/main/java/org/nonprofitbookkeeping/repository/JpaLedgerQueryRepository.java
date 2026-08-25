package org.nonprofitbookkeeping.repository;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AccountFunction;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA-backed ledger query persistence.
 */
public class JpaLedgerQueryRepository implements LedgerQueryRepository
{
    private final Jpa jpa;

    public JpaLedgerQueryRepository(Jpa jpa)
    {
        this.jpa = jpa;
    }

    @Override
    public List<LedgerRecentRow> listRecent(int maxRows)
    {
        try (EntityManager em = jpa.em())
        {
            List<Object[]> rows = em.createQuery(
                            "select t.id, t.txnDate, coalesce(p.displayName, ''), coalesce(t.memo, ''), coalesce(b.code, ''), count(s.id) " +
                                    "from Txn t " +
                                    "left join t.payee p " +
                                    "left join t.bankAccount b " +
                                    "left join TxnSplit s on s.txn = t " +
                                    "group by t.id, t.txnDate, p.displayName, t.memo, b.code " +
                                    "order by t.txnDate desc, t.id desc", Object[].class)
                    .setMaxResults(maxRows)
                    .getResultList();

            List<LedgerRecentRow> out = new ArrayList<>();
            for (Object[] r : rows)
            {
                out.add(new LedgerRecentRow(
                        (Long) r[0],
                        (LocalDate) r[1],
                        (String) r[2],
                        (String) r[3],
                        (String) r[4],
                        (Long) r[5]));
            }
            return out;
        }
    }

    @Override
    public List<LedgerJournalRow> journalForTxn(Long txnId)
    {
        try (EntityManager em = jpa.em())
        {
            List<Object[]> rows = em.createQuery(
                            "select t.txnDate, t.id, t.memo, p.displayName, a.code, a.name, f.code, f.name, a.normalBalance, s.amountSigned " +
                                    "from TxnSplit s " +
                                    "join s.txn t " +
                                    "join s.account a " +
                                    "join s.fund f " +
                                    "left join t.payee p " +
                                    "where t.id = :id " +
                                    "order by a.code", Object[].class)
                    .setParameter("id", txnId)
                    .getResultList();

            List<LedgerJournalRow> out = new ArrayList<>();
            for (Object[] r : rows)
            {
                out.add(new LedgerJournalRow(
                        (LocalDate) r[0],
                        (Long) r[1],
                        (String) r[2],
                        (String) r[3],
                        (String) r[4],
                        (String) r[5],
                        (String) r[6],
                        (String) r[7],
                        (NormalBalance) r[8],
                        (BigDecimal) r[9]));
            }
            return out;
        }
    }

    @Override
    public List<BankLedgerActivityRow> listBankLedgerActivity(
            String companyCode,
            Long configuredBankAccountId,
            int maxRows)
    {
        try (EntityManager em = jpa.em())
        {
            List<Object[]> rows = em.createQuery(
                            "select s.id, t.id, t.txnDate, cba.id, cba.name, " +
                                    "a.code, a.name, f.code, f.name, coalesce(p.displayName, ''), " +
                                    "coalesce(t.memo, ''), a.normalBalance, s.amountSigned, " +
                                    "s.bankCleared, s.bankClearedOn " +
                                    "from TxnSplit s " +
                                    "join s.txn t " +
                                    "join s.account a " +
                                    "join s.fund f " +
                                    "left join t.payee p " +
                                    "join CompanyBankAccount cba on cba.company = t.company and cba.account = a " +
                                    "where t.company.code = :companyCode " +
                                    "and a.accountType = :assetType " +
                                    "and a.accountFunction = :bankFunction " +
                                    "and a.normalBalance = :debitNormal " +
                                    "and (:bankAccountId is null or cba.id = :bankAccountId) " +
                                    "order by t.txnDate desc, t.id desc, s.id desc", Object[].class)
                    .setParameter("companyCode", companyCode)
                    .setParameter("assetType", AccountType.ASSET)
                    .setParameter("bankFunction", AccountFunction.BANK)
                    .setParameter("debitNormal", NormalBalance.DEBIT)
                    .setParameter("bankAccountId", configuredBankAccountId)
                    .setMaxResults(normalizeLimit(maxRows))
                    .getResultList();

            List<BankLedgerActivityRow> out = new ArrayList<>();
            for (Object[] r : rows)
            {
                out.add(new BankLedgerActivityRow(
                        (Long) r[0],
                        (Long) r[1],
                        (LocalDate) r[2],
                        (Long) r[3],
                        (String) r[4],
                        (String) r[5],
                        (String) r[6],
                        (String) r[7],
                        (String) r[8],
                        (String) r[9],
                        (String) r[10],
                        (NormalBalance) r[11],
                        (BigDecimal) r[12],
                        (Boolean) r[13],
                        (LocalDate) r[14]));
            }
            return out;
        }
    }

    private static int normalizeLimit(int maxRows)
    {
        return maxRows <= 0 ? 1000 : maxRows;
    }
}
