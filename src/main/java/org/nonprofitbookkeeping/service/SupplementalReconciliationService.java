package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.model.TxnSupplementalLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only control-account coverage; never fabricates item identities for legacy facts. */
public final class SupplementalReconciliationService
{
    private static final BigDecimal ZERO = new BigDecimal("0.0000");

    private SupplementalReconciliationService()
    {
    }

    public record Control(long accountId, String code, String name, BigDecimal ledger,
                          BigDecimal explained, BigDecimal unmatched, BigDecimal difference)
    {
    }

    public record Gap(long transactionId, long splitId, String accountCode, LocalDate date,
                      BigDecimal ledger, BigDecimal explained, BigDecimal unmatched, String repair)
    {
    }

    public record Reconciliation(List<Control> controls, List<Gap> gaps)
    {
        public Reconciliation
        {
            controls = List.copyOf(controls);
            gaps = List.copyOf(gaps);
        }

        public boolean ready()
        {
            return gaps.isEmpty() && controls.stream().allMatch(c -> c.unmatched().signum() == 0 && c.difference().signum() == 0);
        }
    }

    static Reconciliation query(EntityManager em, String companyCode, SupplementalOpenItemQueryService.Kind kind, LocalDate cutoff)
    {
        List<Account> accounts = em.createQuery("""
                from Account a where upper(a.chart.company.code) = :company and a.subtype = :subtype order by a.code
                """, Account.class).setParameter("company", companyCode.trim().toUpperCase(java.util.Locale.ROOT))
                .setParameter("subtype", kind.accountSubtype()).getResultList();
        List<TxnSplit> splits = em.createQuery("""
                select s from TxnSplit s join fetch s.txn t join fetch s.account a join fetch s.fund
                where upper(t.company.code) = :company and t.status in ('ENTERED', 'REVERSED') order by t.id, s.id
                """, TxnSplit.class).setParameter("company", companyCode.trim().toUpperCase(java.util.Locale.ROOT)).getResultList();
        Map<Long, List<TxnSplit>> byTransaction = new HashMap<>();
        for (TxnSplit split : splits)
        {
            byTransaction.computeIfAbsent(split.getTxn().getId(), ignored -> new ArrayList<>()).add(split);
        }
        Map<Long, BigDecimal> direct = new HashMap<>();
        for (TxnSupplementalLine line : em.createQuery("""
                select l from TxnSupplementalLine l join fetch l.txn t join fetch l.txnSplit s join fetch s.account
                where upper(t.company.code) = :company and l.kind = :kind and l.itemId is not null and l.itemEffect is not null
                """, TxnSupplementalLine.class).setParameter("company", companyCode.trim().toUpperCase(java.util.Locale.ROOT))
                .setParameter("kind", kind.name()).getResultList())
        {
            if (SupplementalOpenItemQueryService.lifecycleMismatch(line, kind) == null)
            {
                BigDecimal signed = line.getAmount().multiply(BigDecimal.valueOf(line.getTxnSplit().getAmountSigned().signum()));
                direct.merge(line.getTxnSplit().getId(), signed, BigDecimal::add);
            }
        }
        Map<Long, BigDecimal[]> totals = new LinkedHashMap<>();
        for (Account account : accounts)
        {
            BigDecimal opening = account.getOpeningBalance() == null ? ZERO : account.getOpeningBalance();
            totals.put(account.getId(), new BigDecimal[] {opening, ZERO, opening.abs()});
        }
        List<Gap> gaps = new ArrayList<>();
        Map<Long, BigDecimal> explainedCache = new HashMap<>();
        for (TxnSplit split : splits)
        {
            BigDecimal[] total = totals.get(split.getAccount().getId());
            if (total == null || split.getTxn().getTxnDate().isAfter(cutoff))
            {
                continue;
            }
            BigDecimal explained = explained(split, byTransaction, direct, explainedCache, new HashSet<>());
            BigDecimal missing = split.getAmountSigned().subtract(explained).abs();
            total[0] = total[0].add(split.getAmountSigned());
            total[1] = total[1].add(explained);
            total[2] = total[2].add(missing);
            if (missing.signum() != 0)
            {
                gaps.add(new Gap(split.getTxn().getId(), split.getId(), split.getAccount().getCode(),
                        split.getTxn().getTxnDate(), split.getAmountSigned(), explained, missing,
                        "Open transaction in Journal; review Item ID, Effect and Ledger Line allocations. Respect closed-period and correction policy."));
            }
        }
        List<Control> controls = new ArrayList<>();
        for (Account account : accounts)
        {
            BigDecimal[] total = totals.get(account.getId());
            controls.add(new Control(account.getId(), account.getCode(), account.getName(), total[0], total[1], total[2], total[0].subtract(total[1])));
        }
        return new Reconciliation(controls, gaps);
    }

    private static BigDecimal explained(TxnSplit split, Map<Long, List<TxnSplit>> byTransaction,
            Map<Long, BigDecimal> direct, Map<Long, BigDecimal> cache, Set<Long> visited)
    {
        if (cache.containsKey(split.getId()))
        {
            return cache.get(split.getId());
        }
        if (!visited.add(split.getId()))
        {
            return ZERO;
        }
        BigDecimal amount = direct.get(split.getId());
        if (amount == null && split.getTxn().getReversalOf() != null)
        {
            var source = split.getTxn().getReversalOf();
            List<TxnSplit> originals = byTransaction.getOrDefault(source.getId(), List.of());
            List<TxnSplit> inverses = byTransaction.getOrDefault(split.getTxn().getId(), List.of());
            int index = inverses.indexOf(split);
            if (originals.size() == inverses.size() && index >= 0)
            {
                TxnSplit original = originals.get(index);
                // Canonical correction copies ordered splits. Refuse to infer a changed inverse.
                if (original.getAccount().getId().equals(split.getAccount().getId())
                        && original.getFund().getId().equals(split.getFund().getId())
                        && original.getAmountSigned().negate().compareTo(split.getAmountSigned()) == 0)
                {
                    amount = explained(original, byTransaction, direct, cache, visited).negate();
                }
            }
        }
        amount = amount == null ? ZERO : amount;
        cache.put(split.getId(), amount);
        return amount;
    }
}
