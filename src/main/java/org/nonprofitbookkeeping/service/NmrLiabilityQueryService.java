package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;
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

/** Read-only NMR attribution over the existing canonical supplemental balance projection. */
public final class NmrLiabilityQueryService
{
    private static final BigDecimal ZERO = new BigDecimal("0.0000");

    private NmrLiabilityQueryService()
    {
    }

    public record Control(long accountId, String accountCode, Long fundId, String fundCode,
                          BigDecimal ledger, BigDecimal nmr, BigDecimal unclassified)
    {
    }

    public record Result(LocalDate cutoff, List<SupplementalOpenItemQueryService.Row> items,
                         List<Control> controls, List<LegacyFlag> legacyFlags,
                         List<SupplementalReconciliationService.Gap> gaps, boolean lifecycleReady)
    {
        public Result
        {
            items = List.copyOf(items);
            controls = List.copyOf(controls);
            legacyFlags = List.copyOf(legacyFlags);
            gaps = List.copyOf(gaps);
        }

        public long legacyFlaggedLines()
        {
            return legacyFlags.size();
        }

        public boolean ready()
        {
            return lifecycleReady && legacyFlags.isEmpty()
                    && controls.stream().allMatch(c -> c.unclassified().signum() == 0);
        }
    }

    public record LegacyFlag(long transactionId, long splitId, LocalDate date, String accountCode, String fundCode)
    {
    }

    private record Key(long account, Long fund)
    {
    }

    static Result query(EntityManager em, String companyCode, LocalDate cutoff)
    {
        var projection = SupplementalOpenItemQueryService.query(em, companyCode,
                SupplementalOpenItemQueryService.Kind.OTHER_LIABILITY, cutoff);
        String company = companyCode.trim().toUpperCase(java.util.Locale.ROOT);
        List<TxnSplit> splits = em.createQuery("""
                select s from TxnSplit s join fetch s.txn t join fetch s.account a join fetch s.fund
                where upper(t.company.code) = :company and t.status in ('ENTERED', 'REVERSED')
                order by t.id, s.id
                """, TxnSplit.class).setParameter("company", company).getResultList();
        Map<Key, BigDecimal> ledger = new LinkedHashMap<>();
        Map<Long, String> accounts = new HashMap<>();
        Map<Long, String> funds = new HashMap<>();
        for (Account account : em.createQuery("from Account a where upper(a.chart.company.code) = :company and a.subtype = :subtype order by a.code", Account.class)
                .setParameter("company", company).setParameter("subtype", AccountSubtype.OTHER_LIABILITY).getResultList())
        {
            accounts.put(account.getId(), account.getCode());
            if (account.getOpeningBalance() != null && account.getOpeningBalance().signum() != 0)
            {
                ledger.put(new Key(account.getId(), null), account.getOpeningBalance());
            }
        }
        Map<Long, List<TxnSplit>> byTransaction = new HashMap<>();
        for (TxnSplit split : splits)
        {
            byTransaction.computeIfAbsent(split.getTxn().getId(), ignored -> new ArrayList<>()).add(split);
            funds.put(split.getFund().getId(), split.getFund().getCode());
            if (accounts.containsKey(split.getAccount().getId()) && !split.getTxn().getTxnDate().isAfter(cutoff))
            {
                ledger.merge(new Key(split.getAccount().getId(), split.getFund().getId()), split.getAmountSigned(), BigDecimal::add);
            }
        }
        List<SupplementalOpenItemQueryService.Row> items = projection.rows().stream().filter(row -> row.nmr() != null).toList();
        Map<Key, BigDecimal> nmr = new HashMap<>();
        for (var item : items)
        {
            var dimension = item.nmr();
            Key key = new Key(dimension.accountId(), dimension.fundId());
            nmr.merge(key, item.openBalance(), BigDecimal::add);
            ledger.putIfAbsent(key, ZERO);
        }
        List<Control> controls = new ArrayList<>();
        ledger.forEach((key, balance) -> {
            BigDecimal amount = nmr.getOrDefault(key, ZERO);
            controls.add(new Control(key.account(), accounts.get(key.account()), key.fund(),
                    key.fund() == null ? "Unassigned opening" : funds.get(key.fund()), balance, amount, balance.subtract(amount)));
        });
        controls.sort(java.util.Comparator.comparing(Control::accountCode).thenComparing(Control::fundCode));
        Set<Long> attributed = new HashSet<>();
        for (TxnSupplementalLine detail : em.createQuery("select l from TxnSupplementalLine l join fetch l.txnSplit where upper(l.txn.company.code) = :company and l.nmrRecipient is not null", TxnSupplementalLine.class)
                .setParameter("company", company).getResultList())
        {
            attributed.add(detail.getTxnSplit().getId());
        }
        List<LegacyFlag> legacy = splits.stream().filter(s -> s.isNmr() && !s.getTxn().getTxnDate().isAfter(cutoff)
                && !attributed(s, attributed, byTransaction, new HashSet<>()))
                .map(s -> new LegacyFlag(s.getTxn().getId(), s.getId(), s.getTxn().getTxnDate(), s.getAccount().getCode(), s.getFund().getCode())).toList();
        return new Result(cutoff, items, controls, legacy, projection.reconciliation().gaps(), projection.ready());
    }

    private static boolean attributed(TxnSplit split, Set<Long> direct,
            Map<Long, List<TxnSplit>> byTransaction, Set<Long> visited)
    {
        if (!visited.add(split.getId()))
        {
            return false;
        }
        if (direct.contains(split.getId()))
        {
            return true;
        }
        var original = split.getTxn().getReversalOf();
        if (original == null)
        {
            return false;
        }
        var originals = byTransaction.getOrDefault(original.getId(), List.of());
        var inverses = byTransaction.getOrDefault(split.getTxn().getId(), List.of());
        int index = inverses.indexOf(split);
        if (originals.size() != inverses.size() || index < 0)
        {
            return false;
        }
        TxnSplit source = originals.get(index);
        return source.getAccount().getId().equals(split.getAccount().getId())
                && source.getFund().getId().equals(split.getFund().getId())
                && source.getAmountSigned().negate().compareTo(split.getAmountSigned()) == 0
                && attributed(source, direct, byTransaction, visited);
    }
}
