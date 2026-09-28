package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.SupplementalItemEffect;
import org.nonprofitbookkeeping.model.TxnSupplementalLine;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Canonical company-scoped as-of projection for transaction-attached supplemental open items.
 * Balances are derived from dated allocations and their canonical reversal chains;
 * no mutable open/closed balance is persisted.
 */
public final class SupplementalOpenItemQueryService
{
    private static final BigDecimal ZERO = new BigDecimal("0.0000");

    private final Jpa jpa;
    private final Supplier<String> companyCodeSupplier;

    public SupplementalOpenItemQueryService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
    }

    public Result query(Kind kind, LocalDate asOfDate)
    {
        try (EntityManager em = jpa.em())
        {
            return query(em, companyCodeSupplier.get(), kind, asOfDate);
        }
    }

    /** Shares the projection with save-time validation inside its existing transaction. */
    static Result query(EntityManager em, String companyCode, Kind kind, LocalDate asOfDate)
    {
        return query(em, companyCode, kind, asOfDate, true);
    }

    static Result query(EntityManager em, String companyCode, Kind kind, LocalDate asOfDate, boolean reconcile)
    {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(asOfDate, "asOfDate");
        if (companyCode == null || companyCode.isBlank())
        {
            throw new IllegalStateException("An active company is required for supplemental reporting.");
        }

        List<TxnSupplementalLine> lines = em.createQuery("""
                select l from TxnSupplementalLine l
                join fetch l.txn t
                left join fetch l.txnSplit s
                left join fetch s.account a
                where upper(t.company.code) = :companyCode
                  and t.status in ('ENTERED', 'REVERSED')
                  and l.kind = :kind
                order by t.txnDate, l.id
                """, TxnSupplementalLine.class)
                .setParameter("companyCode", companyCode.trim().toUpperCase(java.util.Locale.ROOT))
                .setParameter("kind", kind.name())
                .getResultList();

        // Load all dates: a permitted inverse may precede its source, and inverse
        // transactions can themselves be reversed. Status alone cannot reconstruct this.
        Map<Long, Txn> reversals = new LinkedHashMap<>();
        for (Txn reversal : em.createQuery("""
                select t from Txn t join fetch t.reversalOf original
                where upper(t.company.code) = :companyCode
                  and t.company = original.company
                  and t.status in ('ENTERED', 'REVERSED')
                """, Txn.class)
                .setParameter("companyCode", companyCode.trim().toUpperCase(java.util.Locale.ROOT))
                .getResultList())
        {
            reversals.put(reversal.getReversalOf().getId(), reversal);
        }

        Map<UUID, Accumulator> grouped = new LinkedHashMap<>();
        List<Row> diagnostics = new ArrayList<>();
        for (TxnSupplementalLine line : lines)
        {
            List<DatedEffect> effects = datedEffects(line.getTxn(), reversals, asOfDate);
            if (effects.isEmpty())
            {
                continue;
            }
            if (!lifecycleComplete(line))
            {
                diagnostics.add(legacyDiagnostic(line, kind));
                continue;
            }
            String mismatch = lifecycleMismatch(line, kind);
            if (mismatch != null)
            {
                diagnostics.add(inconsistentDiagnostic(line, kind, mismatch));
                continue;
            }
            Accumulator accumulator = grouped.computeIfAbsent(
                    line.getItemId(), ignored -> new Accumulator(kind, line.getItemId()));
            for (DatedEffect effect : effects)
            {
                accumulator.accept(line, effect);
            }
        }

        List<Row> rows = new ArrayList<>();
        for (Accumulator accumulator : grouped.values())
        {
            rows.add(accumulator.finish());
        }
        boolean authorityAvailable = rows.stream().anyMatch(Row::authoritative);
        rows.addAll(diagnostics);
        return new Result(kind, asOfDate, rows, authorityAvailable,
                reconcile ? SupplementalReconciliationService.query(em, companyCode, kind, asOfDate)
                        : new SupplementalReconciliationService.Reconciliation(List.of(), List.of()));
    }

    private record DatedEffect(int direction, boolean backdated)
    {
    }

    private static List<DatedEffect> datedEffects(Txn source, Map<Long, Txn> reversals, LocalDate asOfDate)
    {
        List<DatedEffect> effects = new ArrayList<>();
        var visited = new HashSet<Long>();
        Txn current = source;
        int direction = 1;
        boolean backdated = false;
        while (current != null)
        {
            if (!visited.add(current.getId()))
            {
                throw new IllegalStateException("Cyclic transaction reversal history for transaction " + source.getId());
            }
            if (!current.getTxnDate().isAfter(asOfDate))
            {
                effects.add(new DatedEffect(direction, backdated));
            }
            Txn next = reversals.get(current.getId());
            backdated = next != null && next.getTxnDate().isBefore(current.getTxnDate());
            current = next;
            direction = -direction;
        }
        return effects;
    }

    public Map<Kind, Result> queryAll(LocalDate asOfDate)
    {
        Map<Kind, Result> result = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values())
        {
            result.put(kind, query(kind, asOfDate));
        }
        return Map.copyOf(result);
    }

    private static boolean lifecycleComplete(TxnSupplementalLine line)
    {
        return line.getItemId() != null && line.getTxnSplit() != null && line.getItemEffect() != null;
    }

    static String lifecycleMismatch(TxnSupplementalLine line, Kind kind)
    {
        if (line.getTxnSplit().getTxn() == null
                || !Objects.equals(line.getTxnSplit().getTxn().getId(), line.getTxn().getId()))
        {
            return "Linked ledger split belongs to a different transaction.";
        }
        if (line.getTxnSplit().getAccount() == null
                || line.getTxnSplit().getAccount().getSubtype() != kind.accountSubtype())
        {
            return "Linked ledger account subtype does not match the supplemental kind.";
        }
        int sign = line.getTxnSplit().getAmountSigned().signum();
        if (line.getItemEffect() == SupplementalItemEffect.INCREASE && sign <= 0)
        {
            return "INCREASE is linked to a non-increasing accounting movement.";
        }
        if (line.getItemEffect() == SupplementalItemEffect.DECREASE && sign >= 0)
        {
            return "DECREASE is linked to a non-reducing accounting movement.";
        }
        if (line.getAmount() == null || line.getAmount().signum() <= 0)
        {
            return "Lifecycle allocation amount must be greater than zero.";
        }
        return null;
    }

    private static Row legacyDiagnostic(TxnSupplementalLine line, Kind kind)
    {
        return new Row(
                null, kind, line.getTxn().getId(), line.getTxn().getTxnDate(), line.getEntryRef(),
                line.getCounterparty(), line.getDescription(), line.getReference(),
                scale(line.getAmount()), ZERO, ZERO, line.getDueDate(), line.getStartDate(), line.getEndDate(),
                "UNMATCHED_LEGACY", line.getNotes(), false,
                "Legacy supplemental detail has no canonical item/split lifecycle link; no open balance is inferred.");
    }

    private static Row inconsistentDiagnostic(TxnSupplementalLine line, Kind kind, String explanation)
    {
        return new Row(
                line.getItemId(), kind, line.getTxn().getId(), line.getTxn().getTxnDate(), line.getEntryRef(),
                line.getCounterparty(), line.getDescription(), line.getReference(),
                ZERO, ZERO, ZERO, line.getDueDate(), line.getStartDate(), line.getEndDate(),
                "INCONSISTENT", line.getNotes(), false, explanation);
    }

    private static BigDecimal scale(BigDecimal value)
    {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    public enum Kind
    {
        RECEIVABLE("Accounts Receivable", AccountSubtype.RECEIVABLE),
        PAYABLE("Accounts Payable", AccountSubtype.PAYABLE),
        PREPAID_EXPENSE("Prepaid Expenses", AccountSubtype.PREPAID),
        DEFERRED_REVENUE("Deferred Revenue", AccountSubtype.DEFERRED_REVENUE),
        OTHER_ASSET("Other Assets", AccountSubtype.OTHER_ASSET),
        OTHER_LIABILITY("Other Liabilities", AccountSubtype.OTHER_LIABILITY);

        private final String displayName;
        private final AccountSubtype accountSubtype;

        Kind(String displayName, AccountSubtype accountSubtype)
        {
            this.displayName = displayName;
            this.accountSubtype = accountSubtype;
        }

        public String displayName() { return displayName; }
        public AccountSubtype accountSubtype() { return accountSubtype; }
    }

    public record Result(Kind kind, LocalDate asOfDate, List<Row> rows, boolean authorityAvailable,
                         SupplementalReconciliationService.Reconciliation reconciliation)
    {
        public Result
        {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }

        public boolean ready()
        {
            return reconciliation.ready() && rows.stream().allMatch(row -> row.authoritative() && row.openBalance().signum() >= 0);
        }

        public List<Row> authoritativeRows()
        {
            return rows.stream().filter(Row::authoritative).toList();
        }

        public long openCount()
        {
            return authoritativeRows().stream().filter(row -> row.openBalance().signum() > 0).count();
        }

        public BigDecimal openAmount()
        {
            return authoritativeRows().stream()
                    .map(Row::openBalance)
                    .filter(value -> value.signum() > 0)
                    .reduce(ZERO, BigDecimal::add);
        }
    }

    public record Row(
            UUID itemId,
            Kind kind,
            long openingTransactionId,
            LocalDate openingDate,
            String entryRef,
            String counterparty,
            String description,
            String reference,
            BigDecimal increases,
            BigDecimal reductions,
            BigDecimal openBalance,
            LocalDate dueDate,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            String notes,
            boolean authoritative,
            String explanation)
    {
    }

    private static final class Accumulator
    {
        private final Kind kind;
        private final UUID itemId;
        private TxnSupplementalLine firstIncrease;
        private TxnSupplementalLine firstEvent;
        private boolean backdated;
        private BigDecimal increases = ZERO;
        private BigDecimal reductions = ZERO;

        private Accumulator(Kind kind, UUID itemId)
        {
            this.kind = kind;
            this.itemId = itemId;
        }

        private void accept(TxnSupplementalLine line, DatedEffect effect)
        {
            backdated |= effect.backdated();
            BigDecimal amount = scale(line.getAmount()).multiply(BigDecimal.valueOf(effect.direction()));
            if (firstEvent == null)
            {
                firstEvent = line;
            }
            if (line.getItemEffect() == SupplementalItemEffect.INCREASE)
            {
                if (firstIncrease == null && effect.direction() > 0)
                {
                    firstIncrease = line;
                }
                increases = increases.add(amount);
            }
            else
            {
                reductions = reductions.add(amount);
            }
        }

        private Row finish()
        {
            TxnSupplementalLine source = firstIncrease == null ? firstEvent : firstIncrease;
            BigDecimal open = increases.subtract(reductions).setScale(4, RoundingMode.HALF_UP);
            String status;
            String explanation = "";
            boolean authoritative = firstIncrease != null;
            if (firstIncrease == null)
            {
                status = "UNMATCHED_REDUCTION";
                explanation = "The item has reductions/recognition but no creation/increase as of this date.";
            }
            else if (open.signum() > 0)
            {
                status = "OPEN";
            }
            else if (open.signum() == 0)
            {
                status = "CLOSED";
            }
            else
            {
                status = "OVER_APPLIED";
                explanation = "Reductions/recognition exceed increases; the mismatch is not silently allocated.";
            }
            if (backdated)
            {
                explanation = (explanation.isEmpty() ? "" : explanation + " ")
                        + "Backdated reversal precedes its source transaction; effects use their own dates.";
            }
            return new Row(
                    itemId, kind, source.getTxn().getId(), source.getTxn().getTxnDate(), source.getEntryRef(),
                    source.getCounterparty(), source.getDescription(), source.getReference(),
                    increases, reductions, open, source.getDueDate(), source.getStartDate(), source.getEndDate(),
                    status, source.getNotes(), authoritative, explanation);
        }
    }
}
