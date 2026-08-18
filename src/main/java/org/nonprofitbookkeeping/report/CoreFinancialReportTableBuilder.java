package org.nonprofitbookkeeping.report;

import org.nonprofitbookkeeping.service.FinancialReportDisplayFormat;
import org.nonprofitbookkeeping.service.FinancialReportService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        List<ReportTableModel.Row> rows = new ArrayList<>();
        addStatementSection(rows, "Assets", report.assets(), "Total Assets", report.totalAssets());
        addStatementSection(rows, "Liabilities", report.liabilities(),
                "Total Liabilities", report.totalLiabilities());
        addStatementSection(rows, "Equity", report.equity(), "Total Equity", report.totalEquity());
        rows.add(row(ReportTableModel.RowStyle.TOTAL,
                "name", "Liabilities + Equity",
                "amount", report.liabilitiesAndEquity()));
        rows.add(row(report.isBalanced()
                        ? ReportTableModel.RowStyle.STATUS_SUCCESS
                        : ReportTableModel.RowStyle.STATUS_WARNING,
                "section", "Status",
                "name", report.isBalanced() ? "Balanced — PASS" : "Out of balance — REVIEW"));

        return statementTable(
                ReportDefinition.BALANCE_SHEET,
                "As of " + safeFormat(format).formatDate(report.asOf()),
                rows);
    }

    static ReportTableModel incomeStatement(
            FinancialReportService.IncomeStatementReport report,
            FinancialReportDisplayFormat format)
    {
        List<ReportTableModel.Row> rows = new ArrayList<>();
        addStatementSection(rows, "Income", report.income(), "Total Income", report.totalIncome());
        addStatementSection(rows, "Expenses", report.expenses(),
                "Total Expenses", report.totalExpense());
        rows.add(row(ReportTableModel.RowStyle.TOTAL,
                "name", "Net Income",
                "amount", report.netIncome()));

        FinancialReportDisplayFormat display = safeFormat(format);
        return statementTable(
                ReportDefinition.INCOME_STATEMENT,
                "Period: " + display.formatDate(report.start())
                        + " to " + display.formatDate(report.end()),
                rows);
    }

    private static ReportTableModel statementTable(
            ReportDefinition definition,
            String subtitle,
            List<ReportTableModel.Row> rows)
    {
        return new ReportTableModel(
                definition.id(),
                definition.displayName(),
                subtitle,
                List.of(
                        column("section", "Section", ReportTableModel.ValueFormat.TEXT, 125),
                        column("account", "Account", ReportTableModel.ValueFormat.TEXT, 110),
                        column("name", "Account Name / Description", ReportTableModel.ValueFormat.TEXT, 360),
                        column("amount", "Amount", ReportTableModel.ValueFormat.MONEY, 150)),
                rows);
    }

    private static void addStatementSection(
            List<ReportTableModel.Row> target,
            String section,
            List<FinancialReportService.StatementRow> values,
            String totalLabel,
            BigDecimal total)
    {
        target.add(row(ReportTableModel.RowStyle.SECTION, "section", section));
        for (FinancialReportService.StatementRow value : values)
        {
            target.add(row(ReportTableModel.RowStyle.DETAIL,
                    "account", value.accountCode(),
                    "name", value.accountName(),
                    "amount", value.amount()));
        }
        if (values.isEmpty())
        {
            target.add(row(ReportTableModel.RowStyle.NOTE,
                    "name", "No " + section.toLowerCase() + " balances."));
        }
        target.add(row(ReportTableModel.RowStyle.TOTAL,
                "name", totalLabel,
                "amount", total));
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
}
