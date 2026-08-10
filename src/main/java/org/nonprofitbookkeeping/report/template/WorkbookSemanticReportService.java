package org.nonprofitbookkeeping.report.template;

import com.fasterxml.jackson.databind.JsonNode;
import org.nonprofitbookkeeping.report.SemanticAccountingReportQueryService;
import org.nonprofitbookkeeping.service.FinancialReportService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds value sets for workbook-modeled semantic report templates. */
public class WorkbookSemanticReportService
{
    private static final int DEFAULT_ROW_LIMIT = 500;

    private final FinancialReportService financialReports;
    private final SemanticAccountingReportQueryService semanticQueries;
    private final SemanticReportRenderer renderer = new SemanticReportRenderer();

    public WorkbookSemanticReportService(FinancialReportService financialReports)
    {
        this(financialReports, null);
    }

    public WorkbookSemanticReportService(
            FinancialReportService financialReports,
            SemanticAccountingReportQueryService semanticQueries)
    {
        this.financialReports = financialReports;
        this.semanticQueries = semanticQueries;
    }

    public JsonNode loadTemplate(String templateId)
    {
        return SemanticReportTemplateLoader.load(templateId);
    }

    public RenderedSemanticReport render(String templateId, LocalDate start, LocalDate end)
    {
        return render(templateId, start, end, null, DEFAULT_ROW_LIMIT);
    }

    public RenderedSemanticReport render(
            String templateId,
            LocalDate start,
            LocalDate end,
            String fundCode,
            int rowLimit)
    {
        JsonNode template = loadTemplate(templateId);
        SemanticReportValueSet values = loadValues(templateId, start, end, fundCode, rowLimit);
        return renderer.render(template, values);
    }

    public SemanticReportValueSet loadValues(String templateId, LocalDate start, LocalDate end)
    {
        return loadValues(templateId, start, end, null, DEFAULT_ROW_LIMIT);
    }

    public SemanticReportValueSet loadValues(
            String templateId,
            LocalDate start,
            LocalDate end,
            String fundCode,
            int rowLimit)
    {
        LocalDate effectiveStart = start == null ? LocalDate.now().withDayOfYear(1) : start;
        LocalDate effectiveEnd = end == null ? LocalDate.now() : end;
        int effectiveLimit = rowLimit <= 0 ? DEFAULT_ROW_LIMIT : rowLimit;
        return switch (templateId)
        {
            case "BalanceStmt" -> balanceValues(effectiveEnd, fundCode);
            case "IncomeStmt" -> incomeValues(effectiveStart, effectiveEnd, fundCode);
            case "WorkbookSummary" -> summaryValues(effectiveStart, effectiveEnd, fundCode);
            case "TransactionsList" -> ledgerTableValues(
                    "transactionsList.rows", effectiveStart, effectiveEnd, fundCode, effectiveLimit);
            case "AllChecksTfrs" -> bankActivityValues(
                    effectiveStart, effectiveEnd, fundCode, effectiveLimit);
            case "FundTransfers" -> fundTransferValues(
                    effectiveStart, effectiveEnd, effectiveLimit);
            default -> throw new IllegalArgumentException("Unknown semantic report template: " + templateId);
        };
    }

    private SemanticReportValueSet balanceValues(LocalDate end, String fundCode)
    {
        FinancialReportService.BalanceSheetReport report = financialReports.balanceSheet(end, fundCode);
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

    private SemanticReportValueSet incomeValues(LocalDate start, LocalDate end, String fundCode)
    {
        FinancialReportService.IncomeStatementReport report =
                financialReports.incomeStatement(start, end, fundCode);
        SemanticReportValueSet values = new SemanticReportValueSet();
        values.put("incomeStmt.totalIncome", report.totalIncome());
        values.put("incomeStmt.totalExpenses", report.totalExpense());
        values.put("incomeStmt.netIncomeLoss", report.netIncome());
        putRowsByAccount(values, "incomeStmt.income.rows", report.income());
        putRowsByAccount(values, "incomeStmt.expense.rows", report.expenses());
        return values;
    }

    private SemanticReportValueSet summaryValues(LocalDate start, LocalDate end, String fundCode)
    {
        FinancialReportService.BalanceSheetReport balance = financialReports.balanceSheet(end, fundCode);
        FinancialReportService.IncomeStatementReport income =
                financialReports.incomeStatement(start, end, fundCode);
        SemanticReportValueSet values = new SemanticReportValueSet();
        values.put("context.periodStart", start);
        values.put("context.periodEnd", end);
        values.put("context.fundCode", fundCode == null ? "ALL" : fundCode);
        values.put("workbookSummary.totalAssets", balance.totalAssets());
        values.put("workbookSummary.totalLiabilities", balance.totalLiabilities());
        values.put("workbookSummary.totalNetAssets", balance.totalEquity());
        values.put("workbookSummary.netIncomeLoss", income.netIncome());
        values.put("workbookSummary.balanceCheck", balance.totalAssets().subtract(balance.liabilitiesAndEquity()));
        return values;
    }

    private SemanticReportValueSet ledgerTableValues(
            String tableKey,
            LocalDate start,
            LocalDate end,
            String fundCode,
            int rowLimit)
    {
        List<FinancialReportService.GeneralLedgerRow> rows =
                financialReports.generalLedgerDetail(start, end, fundCode, rowLimit);
        SemanticReportValueSet values = new SemanticReportValueSet();
        values.putTable(tableKey, ledgerRows(rows));
        return values;
    }

    private SemanticReportValueSet bankActivityValues(
            LocalDate start,
            LocalDate end,
            String fundCode,
            int rowLimit)
    {
        SemanticAccountingReportQueryService.BankActivityResult report =
                requireSemanticQueries().bankAccountActivity(start, end, fundCode, rowLimit);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SemanticAccountingReportQueryService.BankActivityRow source : report.rows())
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rowType", "BANK split");
            row.put("txnDate", source.transactionDate());
            row.put("txnId", source.transactionId());
            row.put("payee", source.payee());
            row.put("accountCode", source.accountCode());
            row.put("accountName", source.accountName());
            row.put("fundCode", source.fundCode());
            row.put("debit", source.debit());
            row.put("credit", source.credit());
            row.put("memo", source.memo());
            rows.add(row);
        }

        Map<String, Object> total = new LinkedHashMap<>();
        total.put("rowType", "Displayed total");
        total.put("debit", report.totalDebits());
        total.put("credit", report.totalCredits());
        total.put("memo", "Totals include only the returned BANK-account splits.");
        rows.add(total);

        SemanticReportValueSet values = new SemanticReportValueSet();
        values.put("bankActivity.totalDebits", report.totalDebits());
        values.put("bankActivity.totalCredits", report.totalCredits());
        values.putTable("allChecksTfrs.rows", rows);
        return values;
    }

    private SemanticReportValueSet fundTransferValues(LocalDate start, LocalDate end, int rowLimit)
    {
        List<SemanticAccountingReportQueryService.PostedFundTransferRow> transfers =
                requireSemanticQueries().postedFundTransfers(start, end, rowLimit);
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, BigDecimal> byFund = new LinkedHashMap<>();

        for (SemanticAccountingReportQueryService.PostedFundTransferRow transfer : transfers)
        {
            rows.add(transferLeg(
                    transfer,
                    "Transfer source",
                    transfer.sourceFundCode(),
                    transfer.destinationFundCode(),
                    transfer.amount().negate()));
            rows.add(transferLeg(
                    transfer,
                    "Transfer destination",
                    transfer.destinationFundCode(),
                    transfer.sourceFundCode(),
                    transfer.amount()));
            byFund.merge(transfer.sourceFundCode(), transfer.amount().negate(), BigDecimal::add);
            byFund.merge(transfer.destinationFundCode(), transfer.amount(), BigDecimal::add);
        }

        BigDecimal allFundsNet = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : byFund.entrySet())
        {
            Map<String, Object> total = new LinkedHashMap<>();
            total.put("rowType", "Fund total");
            total.put("fundCode", entry.getKey());
            total.put("netEffect", entry.getValue());
            total.put("memo", "Net of explicit posted transfer legs for this fund.");
            rows.add(total);
            allFundsNet = allFundsNet.add(entry.getValue());
        }

        Map<String, Object> total = new LinkedHashMap<>();
        total.put("rowType", "All funds net");
        total.put("netEffect", allFundsNet);
        total.put("memo", "Must be zero for complete posted transfer pairs.");
        rows.add(total);

        SemanticReportValueSet values = new SemanticReportValueSet();
        values.put("fundTransfers.allFundsNet", allFundsNet);
        values.putTable("fundTransfers.rows", rows);
        return values;
    }

    private Map<String, Object> transferLeg(
            SemanticAccountingReportQueryService.PostedFundTransferRow transfer,
            String rowType,
            String fundCode,
            String counterFundCode,
            BigDecimal effect)
    {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rowType", rowType);
        row.put("transferDate", transfer.transferDate());
        row.put("txnId", transfer.transactionId());
        row.put("transferId", transfer.transferId());
        row.put("fundCode", fundCode);
        row.put("counterFundCode", counterFundCode);
        row.put("netEffect", effect);
        row.put("memo", transfer.memo());
        return row;
    }

    private SemanticAccountingReportQueryService requireSemanticQueries()
    {
        if (semanticQueries == null)
        {
            throw new IllegalStateException(
                    "Bank activity and fund transfer reports require a company-scoped semantic query service.");
        }
        return semanticQueries;
    }

    private void putRowsByAccount(
            SemanticReportValueSet values,
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
