package org.nonprofitbookkeeping.service;

import java.util.List;

/**
 * Concrete renderer for core financial reports.
 * Supports text preview and stable CSV export outputs.
 */
public final class FinancialReportRenderer
{
    private FinancialReportRenderer()
    {
    }

    public static String renderTrialBalanceText(FinancialReportService.TrialBalanceReport report)
    {
        return renderTrialBalanceText(report, FinancialReportDisplayFormat.plain());
    }

    public static String renderTrialBalanceText(
            FinancialReportService.TrialBalanceReport report,
            FinancialReportDisplayFormat format)
    {
        FinancialReportDisplayFormat display = safeFormat(format);
        StringBuilder sb = new StringBuilder();
        sb.append("Trial Balance\n");
        sb.append("As of ").append(display.formatDate(report.asOf())).append("\n");
        sb.append("Rows: ").append(report.rows().size()).append("\n\n");
        sb.append(String.format("%-12s %-36s %14s %14s%n", "Account", "Name", "Debit", "Credit"));
        for (FinancialReportService.TrialBalanceRow row : report.rows())
        {
            sb.append(String.format("%-12s %-36s %14s %14s%n",
                    row.accountCode(),
                    truncate(row.accountName(), 36),
                    display.formatMoney(row.debit()),
                    display.formatMoney(row.credit())));
        }
        sb.append("\n");
        sb.append("Total Debits: ").append(display.formatMoney(report.totalDebits())).append("\n");
        sb.append("Total Credits: ").append(display.formatMoney(report.totalCredits())).append("\n");
        sb.append("Balanced: ").append(report.isBalanced() ? "PASS" : "FAIL");
        return sb.toString();
    }

    public static String renderGeneralLedgerText(List<FinancialReportService.GeneralLedgerRow> rows)
    {
        return renderGeneralLedgerText(rows, FinancialReportDisplayFormat.plain());
    }

    public static String renderGeneralLedgerText(
            List<FinancialReportService.GeneralLedgerRow> rows,
            FinancialReportDisplayFormat format)
    {
        FinancialReportDisplayFormat display = safeFormat(format);
        StringBuilder sb = new StringBuilder();
        sb.append("General Ledger Detail\n");
        sb.append("Rows: ").append(rows.size()).append("\n\n");
        sb.append(String.format("%-12s %-6s %-8s %-18s %-10s %-16s %14s %14s%n",
                "Date", "Txn", "Account", "Account Name", "Fund", "Payee", "Debit", "Credit"));
        for (FinancialReportService.GeneralLedgerRow row : rows)
        {
            sb.append(String.format("%-12s %-6s %-8s %-18s %-10s %-16s %14s %14s%n",
                    display.formatDate(row.txnDate()),
                    row.txnId(),
                    row.accountCode(),
                    truncate(row.accountName(), 18),
                    row.fundCode(),
                    truncate(row.payee(), 16),
                    display.formatMoney(row.debit()),
                    display.formatMoney(row.credit())));
        }
        return sb.toString();
    }

    public static String renderBalanceSheetText(FinancialReportService.BalanceSheetReport report)
    {
        return renderBalanceSheetText(report, FinancialReportDisplayFormat.plain());
    }

    public static String renderBalanceSheetText(
            FinancialReportService.BalanceSheetReport report,
            FinancialReportDisplayFormat format)
    {
        FinancialReportDisplayFormat display = safeFormat(format);
        StringBuilder sb = new StringBuilder();
        sb.append("Balance Sheet\n");
        sb.append("As of ").append(display.formatDate(report.asOf())).append("\n\n");

        appendSection(sb, "Assets", report.assets(), display);
        sb.append("Total Assets: ").append(display.formatMoney(report.totalAssets())).append("\n\n");

        appendSection(sb, "Liabilities", report.liabilities(), display);
        sb.append("Total Liabilities: ").append(display.formatMoney(report.totalLiabilities())).append("\n\n");

        appendSection(sb, "Equity", report.equity(), display);
        sb.append("Total Equity: ").append(display.formatMoney(report.totalEquity())).append("\n\n");

        sb.append("Liabilities + Equity: ")
                .append(display.formatMoney(report.liabilitiesAndEquity()))
                .append("\n");
        sb.append("Balanced: ").append(report.isBalanced() ? "PASS" : "WARN");
        return sb.toString();
    }

    public static String renderIncomeStatementText(FinancialReportService.IncomeStatementReport report)
    {
        return renderIncomeStatementText(report, FinancialReportDisplayFormat.plain());
    }

    public static String renderIncomeStatementText(
            FinancialReportService.IncomeStatementReport report,
            FinancialReportDisplayFormat format)
    {
        FinancialReportDisplayFormat display = safeFormat(format);
        StringBuilder sb = new StringBuilder();
        sb.append("Income Statement\n");
        sb.append("Period: ")
                .append(display.formatDate(report.start()))
                .append(" to ")
                .append(display.formatDate(report.end()))
                .append("\n\n");

        appendSection(sb, "Income", report.income(), display);
        sb.append("Total Income: ").append(display.formatMoney(report.totalIncome())).append("\n\n");

        appendSection(sb, "Expenses", report.expenses(), display);
        sb.append("Total Expenses: ").append(display.formatMoney(report.totalExpense())).append("\n\n");

        sb.append("Net Income: ").append(display.formatMoney(report.netIncome()));
        return sb.toString();
    }

    public static String renderTrialBalanceCsv(FinancialReportService.TrialBalanceReport report)
    {
        StringBuilder sb = new StringBuilder("account_code,account_name,debit,credit\n");
        for (FinancialReportService.TrialBalanceRow row : report.rows())
        {
            sb.append(csv(row.accountCode())).append(',')
                    .append(csv(row.accountName())).append(',')
                    .append(row.debit().toPlainString()).append(',')
                    .append(row.credit().toPlainString()).append('\n');
        }
        return sb.toString();
    }

    public static String renderGeneralLedgerCsv(List<FinancialReportService.GeneralLedgerRow> rows)
    {
        StringBuilder sb = new StringBuilder("txn_date,txn_id,memo,payee,account_code,account_name,fund_code,fund_name,debit,credit\n");
        for (FinancialReportService.GeneralLedgerRow row : rows)
        {
            sb.append(row.txnDate()).append(',')
                    .append(row.txnId()).append(',')
                    .append(csv(row.memo())).append(',')
                    .append(csv(row.payee())).append(',')
                    .append(csv(row.accountCode())).append(',')
                    .append(csv(row.accountName())).append(',')
                    .append(csv(row.fundCode())).append(',')
                    .append(csv(row.fundName())).append(',')
                    .append(row.debit().toPlainString()).append(',')
                    .append(row.credit().toPlainString()).append('\n');
        }
        return sb.toString();
    }

    public static String renderBalanceSheetCsv(FinancialReportService.BalanceSheetReport report)
    {
        StringBuilder sb = new StringBuilder("section,account_code,account_name,amount\n");
        appendStatementCsv(sb, "ASSET", report.assets());
        appendStatementCsv(sb, "LIABILITY", report.liabilities());
        appendStatementCsv(sb, "EQUITY", report.equity());
        return sb.toString();
    }

    public static String renderIncomeStatementCsv(FinancialReportService.IncomeStatementReport report)
    {
        StringBuilder sb = new StringBuilder("section,account_code,account_name,amount\n");
        appendStatementCsv(sb, "INCOME", report.income());
        appendStatementCsv(sb, "EXPENSE", report.expenses());
        return sb.toString();
    }

    private static void appendStatementCsv(
            StringBuilder sb,
            String section,
            List<FinancialReportService.StatementRow> rows)
    {
        for (FinancialReportService.StatementRow row : rows)
        {
            sb.append(section).append(',')
                    .append(csv(row.accountCode())).append(',')
                    .append(csv(row.accountName())).append(',')
                    .append(row.amount().toPlainString()).append('\n');
        }
    }

    private static void appendSection(
            StringBuilder sb,
            String title,
            List<FinancialReportService.StatementRow> rows,
            FinancialReportDisplayFormat format)
    {
        sb.append(title).append("\n");
        for (FinancialReportService.StatementRow row : rows)
        {
            sb.append("- ")
                    .append(row.accountCode())
                    .append(" ")
                    .append(row.accountName())
                    .append(": ")
                    .append(format.formatMoney(row.amount()))
                    .append("\n");
        }
        if (rows.isEmpty())
        {
            sb.append("- (none)\n");
        }
    }

    private static FinancialReportDisplayFormat safeFormat(FinancialReportDisplayFormat format)
    {
        return format == null ? FinancialReportDisplayFormat.plain() : format;
    }

    private static String truncate(String value, int max)
    {
        if (value == null)
        {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String csv(String value)
    {
        if (value == null)
        {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\""))
        {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
