package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Text/CSV renderers for workbook-modeled reports imported from the
 * npbk-javafx-h2 prototype into the sca-jakarta-h2 Report Library.
 *
 * These are intentionally adapter renderers over the mature sca-jakarta-h2
 * reporting services. They do not execute Excel formulas and do not load the
 * workbook at runtime.
 */
public final class WorkbookModeledReportRenderer
{
    private WorkbookModeledReportRenderer()
    {
    }

    public static String renderBalanceStmtText(FinancialReportService.BalanceSheetReport report)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("BalanceStmt - SCA Workbook Modeled Balance Statement\n");
        sb.append("As of ").append(report.asOf()).append("\n");
        sb.append("Source form: BalanceStmt workbook page\n\n");

        appendStatementSection(sb, "I. ASSETS", report.assets());
        sb.append(String.format("%-12s %-50s %14s%n", "", "Total Assets", money(report.totalAssets())));
        sb.append("\n");

        appendStatementSection(sb, "II. LIABILITIES", report.liabilities());
        sb.append(String.format("%-12s %-50s %14s%n", "", "Total Liabilities", money(report.totalLiabilities())));
        sb.append("\n");

        appendStatementSection(sb, "III. NET ASSETS", report.equity());
        sb.append(String.format("%-12s %-50s %14s%n", "", "Total Net Assets", money(report.totalEquity())));
        sb.append(String.format("%-12s %-50s %14s%n", "", "Total Liabilities and Net Assets", money(report.liabilitiesAndEquity())));
        sb.append(String.format("%-12s %-50s %14s%n", "", "Balance Check", money(report.totalAssets().subtract(report.liabilitiesAndEquity()))));
        sb.append("\nBalanced: ").append(report.isBalanced() ? "PASS" : "WARN");
        return sb.toString();
    }

    public static String renderBalanceStmtCsv(FinancialReportService.BalanceSheetReport report)
    {
        StringBuilder sb = new StringBuilder("workbook_report,section,account_code,account_name,amount\n");
        appendStatementCsv(sb, "BalanceStmt", "ASSETS", report.assets());
        appendStatementCsv(sb, "BalanceStmt", "LIABILITIES", report.liabilities());
        appendStatementCsv(sb, "BalanceStmt", "NET ASSETS", report.equity());
        appendTotalCsv(sb, "BalanceStmt", "TOTAL", "Total Assets", report.totalAssets());
        appendTotalCsv(sb, "BalanceStmt", "TOTAL", "Total Liabilities", report.totalLiabilities());
        appendTotalCsv(sb, "BalanceStmt", "TOTAL", "Total Net Assets", report.totalEquity());
        return sb.toString();
    }

    public static String renderIncomeStmtText(FinancialReportService.IncomeStatementReport report)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("IncomeStmt - SCA Workbook Modeled Income Statement\n");
        sb.append("Period: ").append(report.start()).append(" to ").append(report.end()).append("\n");
        sb.append("Source form: IncomeStmt workbook page\n\n");

        appendStatementSection(sb, "INCOME", report.income());
        sb.append(String.format("%-12s %-50s %14s%n%n", "", "Total Income", money(report.totalIncome())));

        appendStatementSection(sb, "EXPENSES", report.expenses());
        sb.append(String.format("%-12s %-50s %14s%n%n", "", "Total Expenses", money(report.totalExpense())));
        sb.append(String.format("%-12s %-50s %14s%n", "", "Net Income / (Loss)", money(report.netIncome())));
        return sb.toString();
    }

    public static String renderIncomeStmtCsv(FinancialReportService.IncomeStatementReport report)
    {
        StringBuilder sb = new StringBuilder("workbook_report,section,account_code,account_name,amount\n");
        appendStatementCsv(sb, "IncomeStmt", "INCOME", report.income());
        appendStatementCsv(sb, "IncomeStmt", "EXPENSES", report.expenses());
        appendTotalCsv(sb, "IncomeStmt", "TOTAL", "Total Income", report.totalIncome());
        appendTotalCsv(sb, "IncomeStmt", "TOTAL", "Total Expenses", report.totalExpense());
        appendTotalCsv(sb, "IncomeStmt", "TOTAL", "Net Income / (Loss)", report.netIncome());
        return sb.toString();
    }

    public static String renderWorkbookSummaryText(FinancialReportService.BalanceSheetReport balance,
                                                   FinancialReportService.IncomeStatementReport income)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("WorkbookSummary - SCA Workbook Modeled Summary\n");
        sb.append("Balance date: ").append(balance.asOf()).append("\n");
        sb.append("Income period: ").append(income.start()).append(" to ").append(income.end()).append("\n\n");
        sb.append(String.format("%-42s %14s%n", "Total Assets", money(balance.totalAssets())));
        sb.append(String.format("%-42s %14s%n", "Total Liabilities", money(balance.totalLiabilities())));
        sb.append(String.format("%-42s %14s%n", "Total Net Assets", money(balance.totalEquity())));
        sb.append(String.format("%-42s %14s%n", "Net Income / (Loss)", money(income.netIncome())));
        sb.append(String.format("%-42s %14s%n", "Balance Check", money(balance.totalAssets().subtract(balance.liabilitiesAndEquity()))));
        return sb.toString();
    }

    public static String renderWorkbookSummaryCsv(FinancialReportService.BalanceSheetReport balance,
                                                  FinancialReportService.IncomeStatementReport income)
    {
        StringBuilder sb = new StringBuilder("workbook_report,metric,amount\n");
        appendMetricCsv(sb, "WorkbookSummary", "Total Assets", balance.totalAssets());
        appendMetricCsv(sb, "WorkbookSummary", "Total Liabilities", balance.totalLiabilities());
        appendMetricCsv(sb, "WorkbookSummary", "Total Net Assets", balance.totalEquity());
        appendMetricCsv(sb, "WorkbookSummary", "Net Income / (Loss)", income.netIncome());
        appendMetricCsv(sb, "WorkbookSummary", "Balance Check", balance.totalAssets().subtract(balance.liabilitiesAndEquity()));
        return sb.toString();
    }

    public static String renderTransactionsListText(List<FinancialReportService.GeneralLedgerRow> rows)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("TransactionsList - SCA Workbook Modeled Transaction Listing\n");
        sb.append("Rows: ").append(rows.size()).append("\n\n");
        appendLedgerTable(sb, rows);
        return sb.toString();
    }

    public static String renderTransactionsListCsv(List<FinancialReportService.GeneralLedgerRow> rows)
    {
        return ledgerCsv("TransactionsList", rows);
    }

    public static String renderAllChecksTfrsText(List<FinancialReportService.GeneralLedgerRow> rows)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("AllChecksTfrs - SCA Workbook Modeled Checks and Transfers\n");
        sb.append("Rows use available general-ledger detail until bank/check-specific fields are fully mapped.\n");
        sb.append("Rows: ").append(rows.size()).append("\n\n");
        appendLedgerTable(sb, rows);
        return sb.toString();
    }

    public static String renderAllChecksTfrsCsv(List<FinancialReportService.GeneralLedgerRow> rows)
    {
        return ledgerCsv("AllChecksTfrs", rows);
    }

    public static String renderFundTransfersText(List<FinancialReportService.GeneralLedgerRow> rows)
    {
        Map<String, BigDecimal> byFund = fundEffects(rows);
        StringBuilder sb = new StringBuilder();
        sb.append("FundTransfers - SCA Workbook Modeled Fund Transfer Summary\n");
        sb.append("Derived from available ledger detail by fund. Dedicated transfer classification can refine this later.\n\n");
        sb.append(String.format("%-16s %14s%n", "Fund", "Net Effect"));
        for (Map.Entry<String, BigDecimal> entry : byFund.entrySet())
        {
            sb.append(String.format("%-16s %14s%n", entry.getKey(), money(entry.getValue())));
        }
        if (byFund.isEmpty())
        {
            sb.append("(no fund activity for selected period)\n");
        }
        return sb.toString();
    }

    public static String renderFundTransfersCsv(List<FinancialReportService.GeneralLedgerRow> rows)
    {
        StringBuilder sb = new StringBuilder("workbook_report,fund_code,net_effect\n");
        for (Map.Entry<String, BigDecimal> entry : fundEffects(rows).entrySet())
        {
            sb.append("FundTransfers,").append(csv(entry.getKey())).append(',').append(entry.getValue().toPlainString()).append('\n');
        }
        return sb.toString();
    }

    private static void appendStatementSection(StringBuilder sb,
                                               String section,
                                               List<FinancialReportService.StatementRow> rows)
    {
        sb.append(section).append('\n');
        sb.append(String.format("%-12s %-50s %14s%n", "Account", "Name", "Amount"));
        for (FinancialReportService.StatementRow row : rows)
        {
            sb.append(String.format("%-12s %-50s %14s%n",
                    row.accountCode(),
                    truncate(row.accountName(), 50),
                    money(row.amount())));
        }
        if (rows.isEmpty())
        {
            sb.append("(none)\n");
        }
    }

    private static void appendLedgerTable(StringBuilder sb, List<FinancialReportService.GeneralLedgerRow> rows)
    {
        sb.append(String.format("%-10s %-6s %-10s %-24s %-10s %-18s %12s %12s%n",
                "Date", "Txn", "Account", "Account Name", "Fund", "Payee", "Debit", "Credit"));
        for (FinancialReportService.GeneralLedgerRow row : rows)
        {
            sb.append(String.format("%-10s %-6s %-10s %-24s %-10s %-18s %12s %12s%n",
                    row.txnDate(),
                    row.txnId(),
                    row.accountCode(),
                    truncate(row.accountName(), 24),
                    row.fundCode(),
                    truncate(row.payee(), 18),
                    money(row.debit()),
                    money(row.credit())));
        }
        if (rows.isEmpty())
        {
            sb.append("(no rows)\n");
        }
    }

    private static String ledgerCsv(String reportName, List<FinancialReportService.GeneralLedgerRow> rows)
    {
        StringBuilder sb = new StringBuilder("workbook_report,txn_date,txn_id,memo,payee,account_code,account_name,fund_code,fund_name,debit,credit\n");
        for (FinancialReportService.GeneralLedgerRow row : rows)
        {
            sb.append(reportName).append(',')
                    .append(row.txnDate()).append(',')
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

    private static Map<String, BigDecimal> fundEffects(List<FinancialReportService.GeneralLedgerRow> rows)
    {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (FinancialReportService.GeneralLedgerRow row : rows)
        {
            String fund = row.fundCode() == null || row.fundCode().isBlank() ? "(none)" : row.fundCode();
            BigDecimal effect = row.debit().subtract(row.credit());
            out.merge(fund, effect, BigDecimal::add);
        }
        return out;
    }

    private static void appendStatementCsv(StringBuilder sb,
                                           String reportName,
                                           String section,
                                           List<FinancialReportService.StatementRow> rows)
    {
        for (FinancialReportService.StatementRow row : rows)
        {
            sb.append(reportName).append(',')
                    .append(csv(section)).append(',')
                    .append(csv(row.accountCode())).append(',')
                    .append(csv(row.accountName())).append(',')
                    .append(row.amount().toPlainString()).append('\n');
        }
    }

    private static void appendTotalCsv(StringBuilder sb,
                                       String reportName,
                                       String section,
                                       String label,
                                       BigDecimal amount)
    {
        sb.append(reportName).append(',')
                .append(csv(section)).append(',')
                .append(',')
                .append(csv(label)).append(',')
                .append(amount.toPlainString()).append('\n');
    }

    private static void appendMetricCsv(StringBuilder sb, String reportName, String metric, BigDecimal amount)
    {
        sb.append(reportName).append(',')
                .append(csv(metric)).append(',')
                .append(amount.toPlainString()).append('\n');
    }

    private static String money(BigDecimal value)
    {
        if (value == null || value.signum() == 0)
        {
            return "-";
        }
        return value.toPlainString();
    }

    private static String truncate(String value, int max)
    {
        if (value == null)
        {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "...";
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
