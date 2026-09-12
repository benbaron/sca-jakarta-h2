package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AccountFunction;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Company-scoped read model for Activity/event accounting over canonical Journal splits.
 * This service never creates or mutates accounting facts.
 */
public final class EventAccountingQueryService
{
    private final Jpa jpa;
    private final Supplier<String> companyCodeSupplier;

    public EventAccountingQueryService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
    }

    /** Lists active and inactive Activities for the selected company. */
    public List<ActivityOption> listActivities()
    {
        String companyCode = requireCompanyCode();
        try (EntityManager em = jpa.em())
        {
            List<Object[]> rows = em.createQuery(
                            "select a.id, a.code, a.name, a.active " +
                                    "from Activity a " +
                                    "where a.company.code = :companyCode " +
                                    "order by a.active desc, upper(a.code), a.id", Object[].class)
                    .setParameter("companyCode", companyCode)
                    .getResultList();
            return rows.stream()
                    .map(row -> new ActivityOption(
                            (Long) row[0], (String) row[1], (String) row[2], (Boolean) row[3]))
                    .toList();
        }
    }

    /** Lists active and inactive funds for historical Activity filtering. */
    public List<FundOption> listFunds()
    {
        String companyCode = requireCompanyCode();
        try (EntityManager em = jpa.em())
        {
            List<Object[]> rows = em.createQuery(
                            "select f.id, f.code, f.name, f.active " +
                                    "from Fund f " +
                                    "where f.company.code = :companyCode " +
                                    "order by f.active desc, upper(f.code), f.id", Object[].class)
                    .setParameter("companyCode", companyCode)
                    .getResultList();
            return rows.stream()
                    .map(row -> new FundOption(
                            (Long) row[0], (String) row[1], (String) row[2], (Boolean) row[3]))
                    .toList();
        }
    }

    /** Derives the standard fiscal-year-to-selected-period scope for the active company. */
    public FiscalPeriodRange fiscalRange(LocalDate selectedPeriodStart)
    {
        Objects.requireNonNull(selectedPeriodStart, "selectedPeriodStart");
        String companyCode = requireCompanyCode();
        try (EntityManager em = jpa.em())
        {
            Object[] row = em.createQuery(
                            "select c.fiscalYearStartMonth, c.fiscalYearStartDay " +
                                    "from Company c where c.code = :companyCode", Object[].class)
                    .setParameter("companyCode", companyCode)
                    .getSingleResult();
            return FiscalPeriodRange.of((Integer) row[0], (Integer) row[1], selectedPeriodStart);
        }
    }

    /**
     * Returns one Activity workspace within an inclusive date range and optional stable-ID fund scope.
     */
    public Workspace workspace(long activityId, LocalDate start, LocalDate end, Long fundId)
    {
        LocalDate effectiveStart = Objects.requireNonNull(start, "start");
        LocalDate effectiveEnd = Objects.requireNonNull(end, "end");
        if (effectiveEnd.isBefore(effectiveStart))
        {
            throw new IllegalArgumentException("Through date must be on or after From date.");
        }
        String companyCode = requireCompanyCode();

        try (EntityManager em = jpa.em())
        {
            ActivityOption activity = requireActivity(em, companyCode, activityId);
            FundOption fund = fundId == null ? null : requireFund(em, companyCode, fundId);
            List<LinkedSplitRow> linkedRows = linkedRows(
                    em, companyCode, activityId, effectiveStart, effectiveEnd, fundId);
            List<RelatedBankRow> bankRows = relatedBankRows(
                    em, companyCode, activityId, effectiveStart, effectiveEnd, fundId);
            Summary summary = summarize(linkedRows, bankRows);
            List<ReviewFact> reviewFacts = reviewFacts(
                    activity, effectiveStart, effectiveEnd, fund, summary);
            return new Workspace(
                    activity,
                    effectiveStart,
                    effectiveEnd,
                    fund,
                    summary,
                    linkedRows,
                    bankRows,
                    reviewFacts);
        }
    }

    private ActivityOption requireActivity(EntityManager em, String companyCode, long activityId)
    {
        List<Object[]> rows = em.createQuery(
                        "select a.id, a.code, a.name, a.active " +
                                "from Activity a " +
                                "where a.id = :activityId and a.company.code = :companyCode", Object[].class)
                .setParameter("activityId", activityId)
                .setParameter("companyCode", companyCode)
                .getResultList();
        if (rows.isEmpty())
        {
            throw new IllegalArgumentException(
                    "Activity ID " + activityId + " does not belong to company " + companyCode + ".");
        }
        Object[] row = rows.get(0);
        return new ActivityOption((Long) row[0], (String) row[1], (String) row[2], (Boolean) row[3]);
    }

    private FundOption requireFund(EntityManager em, String companyCode, long fundId)
    {
        List<Object[]> rows = em.createQuery(
                        "select f.id, f.code, f.name, f.active " +
                                "from Fund f where f.id = :fundId and f.company.code = :companyCode", Object[].class)
                .setParameter("fundId", fundId)
                .setParameter("companyCode", companyCode)
                .getResultList();
        if (rows.isEmpty())
        {
            throw new IllegalArgumentException(
                    "Fund ID " + fundId + " does not belong to company " + companyCode + ".");
        }
        Object[] row = rows.get(0);
        return new FundOption((Long) row[0], (String) row[1], (String) row[2], (Boolean) row[3]);
    }

    private List<LinkedSplitRow> linkedRows(
            EntityManager em,
            String companyCode,
            long activityId,
            LocalDate start,
            LocalDate end,
            Long fundId)
    {
        List<Object[]> source = em.createQuery(
                        "select s.id, t.id, t.txnDate, coalesce(p.displayName, ''), coalesce(t.memo, ''), " +
                                "a.code, a.name, a.accountType, a.normalBalance, " +
                                "f.id, f.code, f.name, s.amountSigned " +
                                "from TxnSplit s " +
                                "join s.txn t " +
                                "join s.account a " +
                                "join s.fund f " +
                                "left join t.payee p " +
                                "where t.company.code = :companyCode " +
                                "and s.activity.id = :activityId " +
                                "and s.activity.company = t.company " +
                                "and t.txnDate >= :start and t.txnDate <= :end " +
                                "and (:fundId is null or f.id = :fundId) " +
                                "order by t.txnDate, t.id, s.id", Object[].class)
                .setParameter("companyCode", companyCode)
                .setParameter("activityId", activityId)
                .setParameter("start", start)
                .setParameter("end", end)
                .setParameter("fundId", fundId)
                .getResultList();

        List<LinkedSplitRow> rows = new ArrayList<>();
        for (Object[] value : source)
        {
            BigDecimal amount = safeAmount((BigDecimal) value[12]);
            MoneyColumns money = moneyColumns((NormalBalance) value[8], amount);
            rows.add(new LinkedSplitRow(
                    (Long) value[0],
                    (Long) value[1],
                    (LocalDate) value[2],
                    (String) value[3],
                    (String) value[4],
                    (String) value[5],
                    (String) value[6],
                    (AccountType) value[7],
                    (Long) value[9],
                    (String) value[10],
                    (String) value[11],
                    amount,
                    money.debit(),
                    money.credit()));
        }
        return List.copyOf(rows);
    }

    private List<RelatedBankRow> relatedBankRows(
            EntityManager em,
            String companyCode,
            long activityId,
            LocalDate start,
            LocalDate end,
            Long fundId)
    {
        List<Object[]> source = em.createQuery(
                        "select s.id, t.id, t.txnDate, coalesce(p.displayName, ''), coalesce(t.memo, ''), " +
                                "cba.id, cba.name, a.code, a.name, f.id, f.code, f.name, " +
                                "s.amountSigned, s.bankCleared, s.bankClearedOn " +
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
                                "and t.txnDate >= :start and t.txnDate <= :end " +
                                "and (:fundId is null or f.id = :fundId) " +
                                "and exists (select eventSplit.id from TxnSplit eventSplit " +
                                "where eventSplit.txn = t " +
                                "and eventSplit.activity.id = :activityId " +
                                "and eventSplit.activity.company = t.company " +
                                "and (:fundId is null or eventSplit.fund.id = :fundId)) " +
                                "order by t.txnDate, t.id, s.id", Object[].class)
                .setParameter("companyCode", companyCode)
                .setParameter("activityId", activityId)
                .setParameter("assetType", AccountType.ASSET)
                .setParameter("bankFunction", AccountFunction.BANK)
                .setParameter("debitNormal", NormalBalance.DEBIT)
                .setParameter("start", start)
                .setParameter("end", end)
                .setParameter("fundId", fundId)
                .getResultList();

        List<RelatedBankRow> rows = new ArrayList<>();
        for (Object[] value : source)
        {
            BigDecimal amount = safeAmount((BigDecimal) value[12]);
            rows.add(new RelatedBankRow(
                    (Long) value[0],
                    (Long) value[1],
                    (LocalDate) value[2],
                    (String) value[3],
                    (String) value[4],
                    (Long) value[5],
                    (String) value[6],
                    (String) value[7],
                    (String) value[8],
                    (Long) value[9],
                    (String) value[10],
                    (String) value[11],
                    positive(amount),
                    positive(amount.negate()),
                    Boolean.TRUE.equals(value[13]),
                    (LocalDate) value[14]));
        }
        return List.copyOf(rows);
    }

    private static Summary summarize(List<LinkedSplitRow> linkedRows, List<RelatedBankRow> bankRows)
    {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        java.util.Set<Long> transactionIds = new java.util.LinkedHashSet<>();
        for (LinkedSplitRow row : linkedRows)
        {
            transactionIds.add(row.transactionId());
            if (row.accountType() == AccountType.INCOME)
            {
                income = income.add(row.naturalAmount());
            }
            else if (row.accountType() == AccountType.EXPENSE)
            {
                expenses = expenses.add(row.naturalAmount());
            }
        }

        BigDecimal inflows = BigDecimal.ZERO;
        BigDecimal outflows = BigDecimal.ZERO;
        int uncleared = 0;
        for (RelatedBankRow row : bankRows)
        {
            inflows = inflows.add(row.inflow());
            outflows = outflows.add(row.outflow());
            if (!row.cleared())
            {
                uncleared++;
            }
        }
        return new Summary(
                income,
                expenses,
                income.subtract(expenses),
                inflows,
                outflows,
                transactionIds.size(),
                linkedRows.size(),
                bankRows.size(),
                uncleared);
    }

    private static List<ReviewFact> reviewFacts(
            ActivityOption activity,
            LocalDate start,
            LocalDate end,
            FundOption fund,
            Summary summary)
    {
        String journalStatus = summary.linkedTransactionCount() == 0 ? "None" : "Linked";
        String journalDetail = summary.linkedTransactionCount() == 0
                ? "No canonical Journal transactions contain Activity-tagged splits in this scope."
                : summary.linkedTransactionCount() + " canonical Journal transaction(s) contain "
                        + summary.linkedSplitCount() + " Activity-tagged split(s).";

        String bankStatus;
        String bankDetail;
        if (summary.relatedBankRowCount() == 0)
        {
            bankStatus = "None";
            bankDetail = "No related configured-bank split appears in a transaction containing this Activity in the selected scope.";
        }
        else if (summary.unclearedRelatedBankRowCount() == 0)
        {
            bankStatus = "All cleared";
            bankDetail = summary.relatedBankRowCount() + " related configured-bank movement(s) are cleared.";
        }
        else
        {
            bankStatus = "Attention";
            bankDetail = summary.unclearedRelatedBankRowCount() + " of " + summary.relatedBankRowCount()
                    + " related configured-bank movement(s) are uncleared.";
        }

        return List.of(
                new ReviewFact("Journal linkage", journalStatus, journalDetail),
                new ReviewFact("Related bank clearing", bankStatus, bankDetail),
                new ReviewFact("Activity lifecycle", activity.active() ? "Active" : "Inactive",
                        activity.active()
                                ? "The Activity remains available for new Journal lines."
                                : "The Activity is inactive and remains available for historical review."),
                new ReviewFact("Selected scope", "Read only",
                        start + " through " + end + "; "
                                + (fund == null ? "all funds" : "fund " + fund.code() + " — " + fund.name()) + "."));
    }

    private String requireCompanyCode()
    {
        String value = companyCodeSupplier.get();
        if (value == null || value.isBlank())
        {
            throw new IllegalStateException("An active company is required for Event Accounting.");
        }
        return value.strip();
    }

    private static BigDecimal safeAmount(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static MoneyColumns moneyColumns(NormalBalance normalBalance, BigDecimal amount)
    {
        if (normalBalance == NormalBalance.DEBIT)
        {
            return new MoneyColumns(positive(amount), positive(amount.negate()));
        }
        return new MoneyColumns(positive(amount.negate()), positive(amount));
    }

    private static BigDecimal positive(BigDecimal value)
    {
        return value.signum() > 0 ? value : BigDecimal.ZERO;
    }

    private record MoneyColumns(BigDecimal debit, BigDecimal credit)
    {
    }

    public record ActivityOption(Long id, String code, String name, boolean active)
    {
        public ActivityOption
        {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(name, "name");
        }

        @Override
        public String toString()
        {
            return code + " — " + name + (active ? "" : " (inactive)");
        }
    }

    public record FundOption(Long id, String code, String name, boolean active)
    {
        public FundOption
        {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(name, "name");
        }

        @Override
        public String toString()
        {
            return code + " — " + name + (active ? "" : " (inactive)");
        }
    }

    public record LinkedSplitRow(
            Long splitId,
            Long transactionId,
            LocalDate transactionDate,
            String payee,
            String memo,
            String accountCode,
            String accountName,
            AccountType accountType,
            Long fundId,
            String fundCode,
            String fundName,
            BigDecimal naturalAmount,
            BigDecimal debit,
            BigDecimal credit)
    {
    }

    public record RelatedBankRow(
            Long splitId,
            Long transactionId,
            LocalDate transactionDate,
            String payee,
            String memo,
            Long configuredBankAccountId,
            String configuredBankAccountName,
            String accountCode,
            String accountName,
            Long fundId,
            String fundCode,
            String fundName,
            BigDecimal inflow,
            BigDecimal outflow,
            boolean cleared,
            LocalDate clearedOn)
    {
    }

    public record Summary(
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal net,
            BigDecimal relatedBankInflows,
            BigDecimal relatedBankOutflows,
            int linkedTransactionCount,
            int linkedSplitCount,
            int relatedBankRowCount,
            int unclearedRelatedBankRowCount)
    {
    }

    public record ReviewFact(String label, String status, String detail)
    {
    }

    public record Workspace(
            ActivityOption activity,
            LocalDate start,
            LocalDate end,
            FundOption fund,
            Summary summary,
            List<LinkedSplitRow> linkedRows,
            List<RelatedBankRow> relatedBankRows,
            List<ReviewFact> reviewFacts)
    {
        public Workspace
        {
            linkedRows = List.copyOf(linkedRows);
            relatedBankRows = List.copyOf(relatedBankRows);
            reviewFacts = List.copyOf(reviewFacts);
        }
    }
}
