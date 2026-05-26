package org.nonprofitbookkeeping.report.template;

import org.nonprofitbookkeeping.service.FinancialReportService;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds value sets for workbook-modeled semantic report templates. */
public class WorkbookSemanticReportService
{
    private final FinancialReportService financialReports;
    private final SemanticReportRenderer renderer = new SemanticReportRenderer();

    public WorkbookSemanticReportService(FinancialReportService financialReports)
    {
        this.financialReports = financialReports;
    }

    public RenderedSemanticReport render(String templateId, LocalDate start, LocalDate end)
    {
        LocalDate effectiveStart = start == null ? LocalDate.now().withDayOfYear(1) : start;
        LocalDate effectiveEnd = end == null ? LocalDate.now() : end;
        JsonNode template = SemanticReportTemplateLoader.load(templateId);
        SemanticReportValueSet values = loadValues(templateId, effectiveStart, effectiveEnd);
        return renderer.render(template, values);
    }

    private SemanticReportValueSet loadValues(String templateId, LocalDate start, LocalDate end)
    {
        return switch (templateId)
        {
            case "BalanceStmt" -> balanceValues(end);
            case "IncomeStmt" -> incomeValues(start, end);
            case "WorkbookSummary" -> summaryValues(start, end);
            case "TransactionsList" -> ledgerTableValues("transactionsList.rows", start, end);
            case "AllChecksTfrs" -> ledgerTableValues("allChecksTfrs.rows", start, end);
            case "FundTransfers" -> fundTransferValues(start, end);
            default -> new SemanticReportValueSet();
        };
    }

    private SemanticReportValueSet balanceValues(LocalDate end)
    {
        FinancialReportService.BalanceSheetReport report = financialReports.balanceSheet(end, null);
        SemanticReportValueSet values = new SemanticReportValueSet();
        values.put("balanceStmt.totalAssets", report.totalAssets());
        values.put("balanceStmt.totalLiabilities", report.totalLiabilities());
        values.put("balanceStmt.totalNetAssets", report.totalEquity());
        values.put("balanceStmt.totalLiabilitiesAndNetAssets", report.liabilitiesAndEquity());
        values.put("balanceStmt.balanceCheck", report.totalAssets().subtract(report.liabilitiesAndEquity()));
        putRowsByAccount(values, "balanceStmt.assets.rows", report.assets());
        putRowsByAccount(values, "balanceStmt.liabilities.rows", report.liabilities());
        putRowsByAccount(values, "balanceStmt.netAssets.rows", report.equity());
        return values;
    }

    private SemanticReportValueSet incomeValues(LocalDate start, LocalDate end)
    {
        FinancialReportService.IncomeStatementReport report = financialReports.incomeStatement(start, end, null);
        SemanticReportValueSet values = new SemanticReportValueSet();
        values.put("incomeStmt.totalIncome", report.totalIncome());
        values.put("incomeStmt.totalExpenses", report.totalExpense());
        values.put("incomeStmt.netIncomeLoss", report.netIncome());
        putRowsByAccount(values, "incomeStmt.income.rows", report.income());
        putRowsByAccount(values, "incomeStmt.expense.rows", report.expenses());
        return values;
    }

    private SemanticReportValueSet summaryValues(LocalDate start, LocalDate end)
    {
        FinancialReportService.BalanceSheetReport balance = financialReports.balanceSheet(end, null);
        FinancialReportService.IncomeStatementReport income = financialReports.incomeStatement(start, end, null);
        SemanticReportValueSet values = new SemanticReportValueSet();
        values.put("context.periodStart", start);
        values.put("context.periodEnd", end);
        values.put("workbookSummary.totalAssets", balance.totalAssets());
        values.put("workbookSummary.totalLiabilities", balance.totalLiabilities());
        values.put("workbookSummary.totalNetAssets", balance.totalEquity());
        values.put("workbookSummary.netIncomeLoss", income.netIncome());
        values.put("workbookSummary.balanceCheck", balance.totalAssets().subtract(balance.liabilitiesAndEquity()));
        return values;
    }

    private SemanticReportValueSet ledgerTableValues(String tableKey, LocalDate start, LocalDate end)
    {
        List<FinancialReportService.GeneralLedgerRow> rows = financialReports.generalLedgerDetail(start, end, null, 500);
        SemanticReportValueSet values = new SemanticReportValueSet();
        values.putTable(tableKey, ledgerRows(rows));
        return values;
    }

    private SemanticReportValueSet fundTransferValues(LocalDate start, LocalDate end)
    {
        List<FinancialReportService.GeneralLedgerRow> rows = financialReports.generalLedgerDetail(start, end, null, 500);
        Map<String, BigDecimal> byFund = new LinkedHashMap<>();
        for (FinancialReportService.GeneralLedgerRow row : rows)
        {
            String fund = row.fundCode() == null || row.fundCode().isBlank() ? "(none)" : row.fundCode();
            byFund.merge(fund, row.debit().subtract(row.credit()), BigDecimal::add);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : byFund.entrySet())
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fundCode", entry.getKey());
            row.put("netEffect", entry.getValue());
            out.add(row);
        }
        SemanticReportValueSet values = new SemanticReportValueSet();
        values.putTable("fundTransfers.rows", out);
        return values;
    }

    private void putRowsByAccount(SemanticReportValueSet values,
                                  String tableKey,
                                  List<FinancialReportService.StatementRow> statementRows)
    {
        List<Map<String, Object>> out = new ArrayList<>();
        for (FinancialReportService.StatementRow source : statementRows)
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("accountCode", source.accountCode());
            row.put("accountName", source.accountName());
            row.put("amount", source.amount());
            out.add(row);
        }
        values.putTable(tableKey, out);
    }

    private List<Map<String, Object>> ledgerRows(List<FinancialReportService.GeneralLedgerRow> rows)
    {
        List<Map<String, Object>> out = new ArrayList<>();
        for (FinancialReportService.GeneralLedgerRow source : rows)
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("txnDate", source.txnDate());
            row.put("txnId", source.txnId());
            row.put("memo", source.memo());
            row.put("payee", source.payee());
            row.put("accountCode", source.accountCode());
            row.put("accountName", source.accountName());
            row.put("fundCode", source.fundCode());
            row.put("fundName", source.fundName());
            row.put("debit", source.debit());
            row.put("credit", source.credit());
            out.add(row);
        }
        return out;
    }
}
