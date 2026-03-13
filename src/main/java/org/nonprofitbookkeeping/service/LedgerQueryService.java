package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Query service for ledger register and transaction drill-down screens.
 */
public class LedgerQueryService
{
    private final Jpa jpa;

    public LedgerQueryService(Jpa jpa)
    {
        this.jpa = jpa;
    }

    public List<LedgerRow> listRecent(int maxRows)
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

            List<LedgerRow> out = new ArrayList<>();
            for (Object[] r : rows)
            {
                out.add(new LedgerRow(
                        (Long) r[0],
                        (LocalDate) r[1],
                        (String) r[2],
                        (String) r[3],
                        (String) r[4],
                        ((Long) r[5]).intValue()));
            }
            return out;
        }
    }

    public List<JournalLine> journalForTxn(Long txnId)
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

            List<JournalLine> out = new ArrayList<>();
            for (Object[] r : rows)
            {
                BigDecimal amount = (BigDecimal) r[9];
                NormalBalance normal = (NormalBalance) r[8];

                BigDecimal debit = BigDecimal.ZERO;
                BigDecimal credit = BigDecimal.ZERO;
                if (normal == NormalBalance.DEBIT)
                {
                    if (amount.compareTo(BigDecimal.ZERO) > 0)
                    {
                        debit = amount;
                    }
                    else
                    {
                        credit = amount.abs();
                    }
                }
                else
                {
                    if (amount.compareTo(BigDecimal.ZERO) > 0)
                    {
                        credit = amount;
                    }
                    else
                    {
                        debit = amount.abs();
                    }
                }

                out.add(new JournalLine(
                        (LocalDate) r[0],
                        (Long) r[1],
                        (String) r[2],
                        (String) r[3],
                        (String) r[4],
                        (String) r[5],
                        (String) r[6],
                        (String) r[7],
                        debit,
                        credit));
            }
            return out;
        }
    }

    public record LedgerRow(Long id, LocalDate date, String payee, String memo, String bank, int splitCount)
    {
    }
}
