package org.nonprofitbookkeeping.report;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AccountFunction;
import org.nonprofitbookkeeping.model.FundTransferStatus;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Company-scoped authoritative queries for semantic reports whose predicates
 * cannot be expressed as general-ledger approximations.
 */
public final class SemanticAccountingReportQueryService
{
    private final Jpa jpa;
    private final Supplier<String> companyCodeSupplier;

    public SemanticAccountingReportQueryService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
    }

    /**
     * Returns only persisted bank-function account splits for the selected company,
     * dates, and optional fund. Totals describe exactly the returned rows.
     */
    public BankActivityResult bankAccountActivity(
            LocalDate start,
            LocalDate end,
            String fundCode,
            int rowLimit)
    {
        LocalDate effectiveStart = Objects.requireNonNull(start, "start");
        LocalDate effectiveEnd = Objects.requireNonNull(end, "end");
        String companyCode = requireCompanyCode();
        String selectedFund = blankToNull(fundCode);

        try (EntityManager em = jpa.em())
        {
            List<Object[]> source = em.createQuery(
                            "select t.txnDate, t.id, coalesce(t.memo, ''), coalesce(p.displayName, ''), " +
                                    "a.code, a.name, f.code, f.name, a.normalBalance, s.amountSigned " +
                                    "from TxnSplit s " +
                                    "join s.txn t " +
                                    "join s.account a " +
                                    "join s.fund f " +
                                    "left join t.payee p " +
                                    "where t.company.code = :companyCode " +
                                    "and a.accountFunction = :bankFunction " +
                                    "and t.txnDate >= :start and t.txnDate <= :end " +
                                    "and (:fundCode is null or f.code = :fundCode) " +
                                    "order by t.txnDate, t.id, s.id", Object[].class)
                    .setParameter("companyCode", companyCode)
                    .setParameter("bankFunction", AccountFunction.BANK)
                    .setParameter("start", effectiveStart)
                    .setParameter("end", effectiveEnd)
                    .setParameter("fundCode", selectedFund)
                    .setMaxResults(normalizeLimit(rowLimit))
                    .getResultList();

            List<BankActivityRow> rows = new ArrayList<>();
            BigDecimal totalDebits = BigDecimal.ZERO;
            BigDecimal totalCredits = BigDecimal.ZERO;
            for (Object[] value : source)
            {
                BigDecimal amount = (BigDecimal) value[9];
                MoneyColumns money = moneyColumns((NormalBalance) value[8], amount);
                totalDebits = totalDebits.add(money.debit());
                totalCredits = totalCredits.add(money.credit());
                rows.add(new BankActivityRow(
                        (LocalDate) value[0],
                        (Long) value[1],
                        (String) value[2],
                        (String) value[3],
                        (String) value[4],
                        (String) value[5],
                        (String) value[6],
                        (String) value[7],
                        money.debit(),
                        money.credit()));
            }
            return new BankActivityResult(List.copyOf(rows), totalDebits, totalCredits);
        }
    }

    /**
     * Returns only explicit POSTED fund-transfer records that are linked to a
     * canonical transaction owned by the selected company.
     */
    public List<PostedFundTransferRow> postedFundTransfers(
            LocalDate start,
            LocalDate end,
            int transferLimit)
    {
        LocalDate effectiveStart = Objects.requireNonNull(start, "start");
        LocalDate effectiveEnd = Objects.requireNonNull(end, "end");
        String companyCode = requireCompanyCode();

        try (EntityManager em = jpa.em())
        {
            List<Object[]> source = em.createQuery(
                            "select ft.id, ft.transferDate, t.id, " +
                                    "source.code, source.name, destination.code, destination.name, " +
                                    "ft.amount, coalesce(ft.memo, '') " +
                                    "from FundTransfer ft " +
                                    "join ft.postedTxn t " +
                                    "join ft.fromFund source " +
                                    "join ft.toFund destination " +
                                    "where ft.status = :status " +
                                    "and t.company.code = :companyCode " +
                                    "and source.company = t.company and destination.company = t.company " +
                                    "and ft.transferDate >= :start and ft.transferDate <= :end " +
                                    "order by ft.transferDate, ft.id", Object[].class)
                    .setParameter("status", FundTransferStatus.POSTED)
                    .setParameter("companyCode", companyCode)
                    .setParameter("start", effectiveStart)
                    .setParameter("end", effectiveEnd)
                    .setMaxResults(normalizeLimit(transferLimit))
                    .getResultList();

            List<PostedFundTransferRow> rows = new ArrayList<>();
            for (Object[] value : source)
            {
                rows.add(new PostedFundTransferRow(
                        (Long) value[0],
                        (LocalDate) value[1],
                        (Long) value[2],
                        (String) value[3],
                        (String) value[4],
                        (String) value[5],
                        (String) value[6],
                        (BigDecimal) value[7],
                        (String) value[8]));
            }
            return List.copyOf(rows);
        }
    }

    private String requireCompanyCode()
    {
        String value = companyCodeSupplier.get();
        if (value == null || value.isBlank())
        {
            throw new IllegalStateException("An active company is required to run this report.");
        }
        return value.strip();
    }

    private static int normalizeLimit(int value)
    {
        return value <= 0 ? 500 : value;
    }

    private static String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static MoneyColumns moneyColumns(NormalBalance normalBalance, BigDecimal amount)
    {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        if (normalBalance == NormalBalance.DEBIT)
        {
            return new MoneyColumns(positive(value), positive(value.negate()));
        }
        return new MoneyColumns(positive(value.negate()), positive(value));
    }

    private static BigDecimal positive(BigDecimal value)
    {
        return value.signum() > 0 ? value : BigDecimal.ZERO;
    }

    private record MoneyColumns(BigDecimal debit, BigDecimal credit)
    {
    }

    public record BankActivityResult(
            List<BankActivityRow> rows,
            BigDecimal totalDebits,
            BigDecimal totalCredits)
    {
        public BankActivityResult
        {
            rows = List.copyOf(rows);
            totalDebits = Objects.requireNonNull(totalDebits, "totalDebits");
            totalCredits = Objects.requireNonNull(totalCredits, "totalCredits");
        }
    }

    public record BankActivityRow(
            LocalDate transactionDate,
            Long transactionId,
            String memo,
            String payee,
            String accountCode,
            String accountName,
            String fundCode,
            String fundName,
            BigDecimal debit,
            BigDecimal credit)
    {
    }

    public record PostedFundTransferRow(
            Long transferId,
            LocalDate transferDate,
            Long transactionId,
            String sourceFundCode,
            String sourceFundName,
            String destinationFundCode,
            String destinationFundName,
            BigDecimal amount,
            String memo)
    {
        public PostedFundTransferRow
        {
            amount = Objects.requireNonNull(amount, "amount");
            if (amount.signum() <= 0)
            {
                throw new IllegalStateException("A posted fund transfer must have a positive amount.");
            }
        }
    }
}
