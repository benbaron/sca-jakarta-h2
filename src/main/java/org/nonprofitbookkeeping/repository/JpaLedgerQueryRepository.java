package org.nonprofitbookkeeping.repository;

import jakarta.persistence.EntityManager;
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
}
