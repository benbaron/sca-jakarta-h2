package org.nonprofitbookkeeping.report;

import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.service.FinancialReportDisplayFormat;
import org.nonprofitbookkeeping.service.FinancialReportService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds formatted table previews directly from authoritative core-report projections. */
final class CoreFinancialReportTableBuilder
{
    private CoreFinancialReportTableBuilder()
    {
    }

    static ReportTableModel trialBalance(
            FinancialReportService.TrialBalanceReport report,
            FinancialReportDisplayFormat format)
    {
        List<ReportTableModel.Row> rows = new ArrayList<>();
        for (FinancialReportService.TrialBalanceRow value : report.rows())
        {
            rows.add(row(ReportTableModel.RowStyle.DETAIL,
                    "account", value.accountCode(),
                    "name", value.accountName(),
                    "debit", value.debit(),
                    "credit", value.credit()));
        }
        if (report.rows().isEmpty())
        {
            rows.add(row(ReportTableModel.RowStyle.NOTE,
                    "name", "No balances for the selected date and fund."));
        }
        rows.add(row(ReportTableModel.RowStyle.TOTAL,
                "name", "Totals",
                "debit", report.totalDebits(),
                "credit", report.totalCredits()));
        rows.add(row(report.isBalanced()
                        ? ReportTableModel.RowStyle.STATUS_SUCCESS
                        : ReportTableModel.RowStyle.STATUS_WARNING,
                "account", "Status",
                "name", report.isBalanced() ? "Balanced — PASS" : "Out of balance — REVIEW"));

        return new ReportTableModel(
                ReportDefinition.TRIAL_BALANCE.id(),
                ReportDefinition.TRIAL_BALANCE.displayName(),
                "As of " + safeFormat(format).formatDate(report.asOf())
                        + " • " + report.rows().size() + " account rows",
                List.of(
                        column("account", "Account", ReportTableModel.ValueFormat.TEXT, 110),
                        column("name", "Account Name", ReportTableModel.ValueFormat.TEXT, 320),
                        column("debit", "Debit", ReportTableModel.ValueFormat.MONEY, 140),
                        column("credit", "Credit", ReportTableModel.ValueFormat.MONEY, 140)),
                rows);
    }

    static ReportTableModel generalLedger(
            List<FinancialReportService.GeneralLedgerRow> values)
    {
        List<ReportTableModel.Row> rows = new ArrayList<>();
        for (FinancialReportService.GeneralLedgerRow value : values)
        {
            rows.add(row(ReportTableModel.RowStyle.DETAIL,
                    "date", value.txnDate(),
                    "transaction", value.txnId(),
                    "account", value.accountCode(),
                    "accountName", value.accountName(),
                    "fund", value.fundCode(),
                    "fundName", value.fundName(),
                    "payee", value.payee(),
                    "memo", value.memo(),
                    "debit", value.debit(),
                    "credit", value.credit()));
        }
        if (values.isEmpty())
        {
            rows.add(row(ReportTableModel.RowStyle.NOTE,
                    "memo", "No ledger rows for the selected period and fund."));
        }

        return new ReportTableModel(
                ReportDefinition.GENERAL_LEDGER_DETAIL.id(),
                ReportDefinition.GENERAL_LEDGER_DETAIL.displayName(),
                values.size() + " displayed ledger rows",
                List.of(
                        column("date", "Date", ReportTableModel.ValueFormat.DATE, 115),
                        column("transaction", "Transaction", ReportTableModel.ValueFormat.NUMBER, 100),
                        column("account", "Account", ReportTableModel.ValueFormat.TEXT, 100),
                        column("accountName", "Account Name", ReportTableModel.ValueFormat.TEXT, 220),
                        column("fund", "Fund", ReportTableModel.ValueFormat.TEXT, 90),
                        column("fundName", "Fund Name", ReportTableModel.ValueFormat.TEXT, 180),
                        column("payee", "Payee", ReportTableModel.ValueFormat.TEXT, 190),
                        column("memo", "Memo", ReportTableModel.ValueFormat.TEXT, 280),
                        column("debit", "Debit", ReportTableModel.ValueFormat.MONEY, 140),
                        column("credit", "Credit", ReportTableModel.ValueFormat.MONEY, 140)),
                rows);
    }

    static ReportTableModel balanceSheet(
            FinancialReportService.BalanceSheetReport report,
            FinancialReportDisplayFormat format)
    {
        FinancialReportService.IncomeStatementReport emptyIncome =
                new FinancialReportService.IncomeStatementReport(
                        report.asOf(),
                        report.asOf(),
                        List.of(),
                        List.of(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO);
        return balanceSheet(
                report,
                report,
                emptyIncome,
                format,
                ReportPresentationMetadata.EMPTY);
    }

    static ReportTableModel balanceSheet(
            FinancialReportService.BalanceSheetReport opening,
            FinancialReportService.BalanceSheetReport closing,
            FinancialReportService.IncomeStatementReport periodIncome,
            FinancialReportDisplayFormat format,
            ReportPresentationMetadata metadata)
    {
        FinancialReportDisplayFormat display = safeFormat(format);
        ReportPresentationMetadata presentation = safeMetadata(metadata);
        List<ReportTableModel.Row> rows = new ArrayList<>();

        List<ComparativeStatementRow> assets = comparative(opening.assets(), closing.assets());
        List<ComparativeStatementRow> cash = assets.stream()
                .filter(value -> isCash(value.account()))
                .toList();
        List<ComparativeStatementRow> nonCashAssets = assets.stream()
                .filter(value -> !isCash(value.account()))
                .toList();

        if (!cash.isEmpty())
        {
            rows.add(row(ReportTableModel.RowStyle.SECTION,
                    "description", "Cash and bank account breakout"));
            addComparativeDetails(rows, cash, 0);
            rows.add(comparativeTotal(
                    "Total Cash and Bank Accounts",
                    sumBeginning(cash),
                    sumEnding(cash)));
        }

        rows.add(row(ReportTableModel.RowStyle.SECTION,
                "line", "I.",
                "description", "ASSETS"));
        int assetLine = 0;
        if (!cash.isEmpty())
        {
            rows.add(comparativeDetail(
                    alphabetic(assetLine++),
                    "",
                    "Total Cash and Bank Accounts",
                    sumBeginning(cash),
                    sumEnding(cash)));
        }
        addComparativeDetails(rows, nonCashAssets, assetLine);
        rows.add(row(ReportTableModel.RowStyle.TOTAL,
                "description", "TOTAL ASSETS",
                "beginning", opening.totalAssets(),
                "ending", closing.totalAssets(),
                "difference", closing.totalAssets().subtract(opening.totalAssets())));

        rows.add(row(ReportTableModel.RowStyle.SECTION,
                "line", "II.",
                "description", "LIABILITIES"));
        List<ComparativeStatementRow> liabilities =
                comparative(opening.liabilities(), closing.liabilities());
        addComparativeDetails(rows, liabilities, 0);
        rows.add(comparativeTotal(
                "TOTAL LIABILITIES",
                opening.totalLiabilities(),
                closing.totalLiabilities()));

        BigDecimal openingNetWorth = opening.totalAssets().subtract(opening.totalLiabilities());
        BigDecimal closingNetWorth = closing.totalAssets().subtract(closing.totalLiabilities());
        BigDecimal changeInNetWorth = closingNetWorth.subtract(openingNetWorth);
        BigDecimal netIncome = periodIncome.netIncome();
        BigDecimal reconciliationDifference = changeInNetWorth.subtract(netIncome);

        rows.add(row(ReportTableModel.RowStyle.SECTION,
                "line", "III.",
                "description", "NET WORTH"));
        rows.add(comparativeTotal("NET WORTH", openingNetWorth, closingNetWorth));
        rows.add(row(ReportTableModel.RowStyle.TOTAL,
                "description", "Change in Net Worth",
                "ending", changeInNetWorth));
        rows.add(row(ReportTableModel.RowStyle.TOTAL,
                "description", "Net Income",
                "ending", netIncome));
        rows.add(row(reconciliationDifference.signum() == 0
                        ? ReportTableModel.RowStyle.STATUS_SUCCESS
                        : ReportTableModel.RowStyle.STATUS_WARNING,
                "description", reconciliationDifference.signum() == 0
                        ? "Difference — PASS" : "Difference — REVIEW",
                "ending", reconciliationDifference));

        return new ReportTableModel(
                ReportDefinition.BALANCE_SHEET.id(),
                "COMPARATIVE BALANCE STATEMENT AS OF "
                        + display.formatDate(closing.asOf()),
                currencySubtitle(presentation),
                statementHeaders(
                        presentation,
                        periodIncome.start(),
                        periodIncome.end(),
                        display),
                List.of(
                        column("line", "Line", ReportTableModel.ValueFormat.TEXT, 70),
                        column("account", "Account", ReportTableModel.ValueFormat.TEXT, 105),
                        column("description", "Category / Description",
                                ReportTableModel.ValueFormat.TEXT, 390),
                        column("beginning", display.formatDate(opening.asOf()),
                                ReportTableModel.ValueFormat.MONEY, 155),
                        column("ending", display.formatDate(closing.asOf()),
                                ReportTableModel.ValueFormat.MONEY, 155),
                        column("difference", "Difference",
                                ReportTableModel.ValueFormat.MONEY, 155)),
                rows);
    }

    static ReportTableModel incomeStatement(
            FinancialReportService.IncomeStatementReport report,
            FinancialReportDisplayFormat format)
    {
        FinancialReportService.BalanceSheetReport emptyBalance = emptyBalance(
                report.start().minusDays(1));
        return incomeStatement(
                report,
                emptyBalance,
                emptyBalance(report.end()),
                format,
                ReportPresentationMetadata.EMPTY);
    }

    static ReportTableModel incomeStatement(
            FinancialReportService.IncomeStatementReport report,
            FinancialReportService.BalanceSheetReport opening,
            FinancialReportService.BalanceSheetReport closing,
            FinancialReportDisplayFormat format,
            ReportPresentationMetadata metadata)
    {
        FinancialReportDisplayFormat display = safeFormat(format);
        ReportPresentationMetadata presentation = safeMetadata(metadata);
        List<FinancialReportService.StatementRow> income = report.income().stream()
                .sorted(Comparator.comparing(FinancialReportService.StatementRow::accountCode))
                .toList();
        List<FinancialReportService.StatementRow> expenses = report.expenses().stream()
                .sorted(Comparator.comparing(FinancialReportService.StatementRow::accountCode))
                .toList();
        List<String> allocations = expenseAllocationLabels(expenses);
        Map<String, String> allocationKeys = new LinkedHashMap<>();
        for (int index = 0; index < allocations.size(); index++)
        {
            allocationKeys.put(allocations.get(index), "allocation" + index);
        }

        List<ReportTableModel.Row> rows = new ArrayList<>();
        rows.add(row(ReportTableModel.RowStyle.SECTION,
                "description", "INCOME"));

        int line = 1;
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalCosts = BigDecimal.ZERO;
        for (FinancialReportService.StatementRow value : income)
        {
            BigDecimal amount = safeAmount(value.amount());
            boolean refundOrCost = amount.signum() < 0;
            rows.add(row(ReportTableModel.RowStyle.DETAIL,
                    "line", Integer.toString(line++),
                    "account", value.accountCode(),
                    "description", statementCategory(value),
                    "detail", statementDetail(value),
                    "gross", refundOrCost ? null : amount,
                    "cost", refundOrCost ? amount.abs() : null,
                    "total", amount));
            if (refundOrCost)
            {
                totalCosts = totalCosts.add(amount.abs());
            }
            else
            {
                totalGross = totalGross.add(amount);
            }
        }
        rows.add(row(ReportTableModel.RowStyle.TOTAL,
                "description", "TOTAL INCOME",
                "gross", totalGross,
                "cost", totalCosts,
                "total", report.totalIncome()));

        rows.add(row(ReportTableModel.RowStyle.SECTION,
                "description", "EXPENSES"));

        Map<String, ExpenseGroup> groups = expenseGroups(expenses);
        Map<String, BigDecimal> allocationTotals = new LinkedHashMap<>();
        BigDecimal allocatedSubtotal = BigDecimal.ZERO;
        for (ExpenseGroup group : groups.values())
        {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("line", Integer.toString(line++));
            values.put("account", group.code());
            values.put("description", group.name());
            for (Map.Entry<String, BigDecimal> allocation : group.allocations().entrySet())
            {
                String key = allocationKeys.get(allocation.getKey());
                if (key != null)
                {
                    values.put(key, allocation.getValue());
                    allocationTotals.merge(allocation.getKey(), allocation.getValue(), BigDecimal::add);
                }
            }
            values.put("total", group.total());
            allocatedSubtotal = allocatedSubtotal.add(group.total());
            rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.DETAIL, values));
        }

        if (!groups.isEmpty())
        {
            Map<String, Object> subtotal = new LinkedHashMap<>();
            subtotal.put("description", "SUB-TOTAL — ALLOCATED EXPENSE CATEGORIES");
            for (String allocation : allocations)
            {
                subtotal.put(allocationKeys.get(allocation),
                        allocationTotals.getOrDefault(allocation, BigDecimal.ZERO));
            }
            subtotal.put("total", allocatedSubtotal);
            rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.TOTAL, subtotal));
        }

        for (FinancialReportService.StatementRow value : flatExpenses(expenses))
        {
            rows.add(row(ReportTableModel.RowStyle.DETAIL,
                    "line", Integer.toString(line++),
                    "account", value.accountCode(),
                    "description", value.accountName(),
                    "total", safeAmount(value.amount())));
        }

        rows.add(row(ReportTableModel.RowStyle.TOTAL,
                "description", "TOTAL EXPENSES",
                "total", report.totalExpense()));
        rows.add(row(ReportTableModel.RowStyle.TOTAL,
                "description", "NET INCOME",
                "total", report.netIncome()));

        BigDecimal openingNetWorth = opening.totalAssets().subtract(opening.totalLiabilities());
        BigDecimal closingNetWorth = closing.totalAssets().subtract(closing.totalLiabilities());
        BigDecimal netWorthChange = closingNetWorth.subtract(openingNetWorth);
        BigDecimal difference = netWorthChange.subtract(report.netIncome());
        rows.add(row(ReportTableModel.RowStyle.TOTAL,
                "description", "Change in Net Worth, from Balance Statement",
                "total", netWorthChange));
        rows.add(row(difference.signum() == 0
                        ? ReportTableModel.RowStyle.STATUS_SUCCESS
                        : ReportTableModel.RowStyle.STATUS_WARNING,
                "description", difference.signum() == 0
                        ? "Difference — PASS" : "Difference — REVIEW",
                "total", difference));

        List<ReportTableModel.Column> columns = new ArrayList<>();
        columns.add(column("line", "Line", ReportTableModel.ValueFormat.TEXT, 70));
        columns.add(column("account", "Account", ReportTableModel.ValueFormat.TEXT, 105));
        columns.add(column("description", "Category", ReportTableModel.ValueFormat.TEXT, 330));
        columns.add(column("detail", "Detail", ReportTableModel.ValueFormat.TEXT, 240));
        for (String allocation : allocations)
        {
            columns.add(column(
                    allocationKeys.get(allocation),
                    allocation,
                    ReportTableModel.ValueFormat.MONEY,
                    155));
        }
        columns.add(column("gross", "Gross", ReportTableModel.ValueFormat.MONEY, 145));
        columns.add(column("cost", "Cost / Refunds", ReportTableModel.ValueFormat.MONEY, 145));
        columns.add(column("total", "Net / Total", ReportTableModel.ValueFormat.MONEY, 145));

        return new ReportTableModel(
                ReportDefinition.INCOME_STATEMENT.id(),
                "STATEMENT OF NET INCOME FOR THE PERIOD: "
                        + display.formatDate(report.start())
                        + " TO " + display.formatDate(report.end()),
                "Gross − Cost / Refunds = Net"
                        + (presentation.currency().isBlank()
                        ? "" : " • " + presentation.currency()),
                statementHeaders(presentation, report.start(), report.end(), display),
                columns,
                rows);
    }

    private static List<ReportTableModel.HeaderLine> statementHeaders(
            ReportPresentationMetadata metadata,
            java.time.LocalDate start,
            java.time.LocalDate end,
            FinancialReportDisplayFormat format)
    {
        return List.of(
                new ReportTableModel.HeaderLine(
                        metadata.organizationHeading(),
                        metadata.exchequerReportHeading(),
                        ReportTableModel.HeaderStyle.PRIMARY),
                new ReportTableModel.HeaderLine(
                        metadata.companyHeading(),
                        metadata.periodHeading(start, end, format),
                        ReportTableModel.HeaderStyle.SECONDARY));
    }

    private static String currencySubtitle(ReportPresentationMetadata metadata)
    {
        return metadata.currency().isBlank() ? "" : "Currency: " + metadata.currency();
    }

    private static List<ComparativeStatementRow> comparative(
            List<FinancialReportService.StatementRow> beginning,
            List<FinancialReportService.StatementRow> ending)
    {
        Map<String, FinancialReportService.StatementRow> beginningByCode = byCode(beginning);
        Map<String, FinancialReportService.StatementRow> endingByCode = byCode(ending);
        Set<String> codes = new LinkedHashSet<>();
        codes.addAll(beginningByCode.keySet());
        codes.addAll(endingByCode.keySet());
        return codes.stream()
                .sorted()
                .map(code -> {
                    FinancialReportService.StatementRow account = endingByCode.get(code);
                    if (account == null)
                    {
                        account = beginningByCode.get(code);
                    }
                    return new ComparativeStatementRow(
                            account,
                            amount(beginningByCode.get(code)),
                            amount(endingByCode.get(code)));
                })
                .toList();
    }

    private static Map<String, FinancialReportService.StatementRow> byCode(
            List<FinancialReportService.StatementRow> values)
    {
        Map<String, FinancialReportService.StatementRow> result = new LinkedHashMap<>();
        for (FinancialReportService.StatementRow value : values)
        {
            result.put(value.accountCode(), value);
        }
        return result;
    }

    private static void addComparativeDetails(
            List<ReportTableModel.Row> target,
            List<ComparativeStatementRow> values,
            int ordinalOffset)
    {
        for (int index = 0; index < values.size(); index++)
        {
            ComparativeStatementRow value = values.get(index);
            target.add(comparativeDetail(
                    alphabetic(ordinalOffset + index),
                    value.account().accountCode(),
                    value.account().accountName(),
                    value.beginning(),
                    value.ending()));
        }
    }

    private static ReportTableModel.Row comparativeDetail(
            String line,
            String account,
            String description,
            BigDecimal beginning,
            BigDecimal ending)
    {
        return row(ReportTableModel.RowStyle.DETAIL,
                "line", line,
                "account", account,
                "description", description,
                "beginning", beginning,
                "ending", ending,
                "difference", ending.subtract(beginning));
    }

    private static ReportTableModel.Row comparativeTotal(
            String description,
            BigDecimal beginning,
            BigDecimal ending)
    {
        return row(ReportTableModel.RowStyle.TOTAL,
                "description", description,
                "beginning", beginning,
                "ending", ending,
                "difference", ending.subtract(beginning));
    }

    private static BigDecimal sumBeginning(List<ComparativeStatementRow> rows)
    {
        return rows.stream().map(ComparativeStatementRow::beginning)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumEnding(List<ComparativeStatementRow> rows)
    {
        return rows.stream().map(ComparativeStatementRow::ending)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean isCash(FinancialReportService.StatementRow row)
    {
        return row.accountType() == AccountType.ASSET && row.subtype() == AccountSubtype.CASH;
    }

    private static String alphabetic(int ordinal)
    {
        if (ordinal < 0 || ordinal >= 26)
        {
            return Integer.toString(ordinal + 1);
        }
        return Character.toString((char) ('a' + ordinal)) + ")";
    }

    private static List<String> expenseAllocationLabels(
            List<FinancialReportService.StatementRow> expenses)
    {
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        for (FinancialReportService.StatementRow value : expenses)
        {
            if (isAllocatedExpense(value))
            {
                labels.add(value.accountName());
            }
        }
        return List.copyOf(labels);
    }

    private static Map<String, ExpenseGroup> expenseGroups(
            List<FinancialReportService.StatementRow> expenses)
    {
        Map<String, MutableExpenseGroup> mutable = new LinkedHashMap<>();
        for (FinancialReportService.StatementRow value : expenses)
        {
            if (!isAllocatedExpense(value))
            {
                continue;
            }
            String key = value.parentCode() == null ? value.parentName() : value.parentCode();
            MutableExpenseGroup group = mutable.computeIfAbsent(
                    key,
                    ignored -> new MutableExpenseGroup(value.parentCode(), value.parentName()));
            BigDecimal amount = safeAmount(value.amount());
            group.allocations.merge(value.accountName(), amount, BigDecimal::add);
            group.total = group.total.add(amount);
        }

        Map<String, ExpenseGroup> result = new LinkedHashMap<>();
        for (Map.Entry<String, MutableExpenseGroup> entry : mutable.entrySet())
        {
            MutableExpenseGroup value = entry.getValue();
            result.put(entry.getKey(), new ExpenseGroup(
                    value.code,
                    value.name,
                    Map.copyOf(value.allocations),
                    value.total));
        }
        return result;
    }

    private static List<FinancialReportService.StatementRow> flatExpenses(
            List<FinancialReportService.StatementRow> expenses)
    {
        return expenses.stream().filter(value -> !isAllocatedExpense(value)).toList();
    }

    private static boolean isAllocatedExpense(FinancialReportService.StatementRow value)
    {
        return value.parentCode() != null && value.grandparentCode() != null;
    }

    private static String statementCategory(FinancialReportService.StatementRow value)
    {
        return value.grandparentCode() == null || value.parentName() == null
                ? value.accountName() : value.parentName();
    }

    private static String statementDetail(FinancialReportService.StatementRow value)
    {
        return value.grandparentCode() == null ? "" : value.accountName();
    }

    private static BigDecimal amount(FinancialReportService.StatementRow value)
    {
        return value == null ? BigDecimal.ZERO : safeAmount(value.amount());
    }

    private static BigDecimal safeAmount(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static ReportPresentationMetadata safeMetadata(ReportPresentationMetadata value)
    {
        return value == null ? ReportPresentationMetadata.EMPTY : value;
    }

    private static FinancialReportService.BalanceSheetReport emptyBalance(java.time.LocalDate date)
    {
        return new FinancialReportService.BalanceSheetReport(
                date,
                List.of(),
                List.of(),
                List.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    private static ReportTableModel.Column column(
            String key,
            String label,
            ReportTableModel.ValueFormat format,
            double width)
    {
        return new ReportTableModel.Column(key, label, format, width);
    }

    private static ReportTableModel.Row row(
            ReportTableModel.RowStyle style,
            Object... keyValues)
    {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2)
        {
            Object value = keyValues[i + 1];
            if (value != null)
            {
                values.put(String.valueOf(keyValues[i]), value);
            }
        }
        return new ReportTableModel.Row(style, values);
    }

    private static FinancialReportDisplayFormat safeFormat(FinancialReportDisplayFormat format)
    {
        return format == null ? FinancialReportDisplayFormat.plain() : format;
    }

    private record ComparativeStatementRow(
            FinancialReportService.StatementRow account,
            BigDecimal beginning,
            BigDecimal ending)
    {
    }

    private record ExpenseGroup(
            String code,
            String name,
            Map<String, BigDecimal> allocations,
            BigDecimal total)
    {
    }

    private static final class MutableExpenseGroup
    {
        private final String code;
        private final String name;
        private final Map<String, BigDecimal> allocations = new LinkedHashMap<>();
        private BigDecimal total = BigDecimal.ZERO;

        private MutableExpenseGroup(String code, String name)
        {
            this.code = code == null ? "" : code;
            this.name = name == null ? "" : name;
        }
    }
}
