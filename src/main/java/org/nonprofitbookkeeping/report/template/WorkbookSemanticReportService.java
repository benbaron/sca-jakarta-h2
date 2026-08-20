package org.nonprofitbookkeeping.report.template;

import com.fasterxml.jackson.databind.JsonNode;
import org.nonprofitbookkeeping.report.AssetInventoryReportQueryService;
import org.nonprofitbookkeeping.report.ReportDomainFilter;
import org.nonprofitbookkeeping.report.ReportRequest;
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
    private final AssetInventoryReportQueryService assetInventoryQueries;
    private final SemanticReportRenderer renderer = new SemanticReportRenderer();

    public WorkbookSemanticReportService(FinancialReportService financialReports)
    {
        this(financialReports, null, null);
    }

    public WorkbookSemanticReportService(
            FinancialReportService financialReports,
            SemanticAccountingReportQueryService semanticQueries)
    {
        this(financialReports, semanticQueries, null);
    }

    public WorkbookSemanticReportService(
            FinancialReportService financialReports,
            SemanticAccountingReportQueryService semanticQueries,
            AssetInventoryReportQueryService assetInventoryQueries)
    {
        this.financialReports = financialReports;
        this.semanticQueries = semanticQueries;
        this.assetInventoryQueries = assetInventoryQueries;
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
            case "WorkbookSummary" -> summaryValues(effectiveStart, effectiveEnd, fundCode);
            case "TransactionsList" -> ledgerTableValues(
                    "transactionsList.rows", effectiveStart, effectiveEnd, fundCode, effectiveLimit, null);
            case "AllChecksTfrs" -> bankActivityValues(
                    effectiveStart, effectiveEnd, fundCode, effectiveLimit);
            case "FundTransfers" -> fundTransferValues(
                    effectiveStart, effectiveEnd, effectiveLimit);
            default -> throw new IllegalArgumentException("Unknown semantic report template: " + templateId);
        };
    }

    public SemanticReportValueSet loadValues(ReportRequest request)
    {
        return switch (request.definition().domainFilterMode())
        {
            case NONE -> loadValues(
                    request.definition().templateId(),
                    request.startDate(),
                    request.endDate(),
                    request.fundCode(),
                    request.rowLimit());
            case ACCOUNT -> accountValues(request);
            case FIXED_ASSET -> fixedAssetValues(request);
            case INVENTORY -> inventoryValues(request);
        };
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
            int rowLimit,
            Long accountId)
    {
        List<FinancialReportService.GeneralLedgerRow> rows =
                financialReports.generalLedgerDetail(start, end, fundCode, rowLimit, accountId);
        SemanticReportValueSet values = new SemanticReportValueSet();
        values.putTable(tableKey, ledgerRows(rows));
        return values;
    }

    private SemanticReportValueSet accountValues(ReportRequest request)
    {
        ReportDomainFilter.AccountSelection filter =
                (ReportDomainFilter.AccountSelection) request.domainFilter();
        if (!"TransactionsList".equals(request.definition().templateId()))
        {
            throw new IllegalArgumentException(
                    "Unknown account-filtered report template: " + request.definition().templateId());
        }
        return ledgerTableValues(
                "transactionsList.rows",
                request.startDate(),
                request.endDate(),
                request.fundCode(),
                request.rowLimit(),
                filter.accountId());
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

    private SemanticReportValueSet fixedAssetValues(ReportRequest request)
    {
        ReportDomainFilter.FixedAssetSelection filter =
                (ReportDomainFilter.FixedAssetSelection) request.domainFilter();
        AssetInventoryReportQueryService.FixedAssetReportRequest query =
                new AssetInventoryReportQueryService.FixedAssetReportRequest(
                        request.startDate(),
                        request.endDate(),
                        request.fund().id(),
                        filter.accountId(),
                        filter.assetId(),
                        filter.status(),
                        request.rowLimit());
        return switch (request.definition().templateId())
        {
            case "FixedAssetRegister" -> fixedAssetRegisterValues(
                    requireAssetInventoryQueries().fixedAssetRegister(query));
            case "FixedAssetDepreciation" -> fixedAssetDepreciationValues(
                    requireAssetInventoryQueries().fixedAssetDepreciation(query));
            default -> throw new IllegalArgumentException(
                    "Unknown fixed-asset report template: " + request.definition().templateId());
        };
    }

    private SemanticReportValueSet inventoryValues(ReportRequest request)
    {
        ReportDomainFilter.InventorySelection filter =
                (ReportDomainFilter.InventorySelection) request.domainFilter();
        AssetInventoryReportQueryService.InventoryReportRequest query =
                new AssetInventoryReportQueryService.InventoryReportRequest(
                        request.startDate(),
                        request.endDate(),
                        request.fund().id(),
                        filter.accountId(),
                        filter.itemId(),
                        filter.status(),
                        request.rowLimit());
        return switch (request.definition().templateId())
        {
            case "InventoryValuation" -> inventoryValuationValues(
                    requireAssetInventoryQueries().inventoryValuation(query));
            case "InventoryMovementHistory" -> inventoryMovementValues(
                    requireAssetInventoryQueries().inventoryMovementHistory(query));
            default -> throw new IllegalArgumentException(
                    "Unknown inventory report template: " + request.definition().templateId());
        };
    }

    private SemanticReportValueSet fixedAssetRegisterValues(
            AssetInventoryReportQueryService.FixedAssetRegisterResult report)
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AssetInventoryReportQueryService.FixedAssetRegisterRow source : report.rows())
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rowType", "Asset");
            row.put("assetId", source.assetId());
            row.put("assetName", source.assetName());
            row.put("status", source.status());
            row.put("acquisitionDate", source.acquisitionDate());
            row.put("accountCode", source.assetAccountCode());
            row.put("fundCode", source.fundCode());
            row.put("recognizedCost", source.recognizedCost());
            row.put("accumulatedDepreciation", source.accumulatedDepreciation());
            row.put("impairment", source.impairment());
            row.put("recognizedContra", source.recognizedContra());
            row.put("bookValue", source.bookValue());
            rows.add(row);
        }
        rows.add(reconciliationRow("Domain total", report.domainGross(), report.domainContra(),
                report.domainNet(), null));
        rows.add(reconciliationRow("Ledger control total", report.ledgerGross(), report.ledgerContra(),
                report.ledgerNet(), null));
        rows.add(reconciliationRow("Difference", null, null, report.difference(),
                report.explanation()));
        if (report.openingBalanceExcluded().signum() != 0)
        {
            rows.add(reconciliationRow("Fund-unallocated opening balance", null, null,
                    report.openingBalanceExcluded(), report.explanation()));
        }
        SemanticReportValueSet values = new SemanticReportValueSet();
        values.putTable("fixedAssetRegister.rows", rows);
        return values;
    }

    private Map<String, Object> reconciliationRow(
            String type,
            BigDecimal gross,
            BigDecimal contra,
            BigDecimal net,
            String notes)
    {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rowType", type);
        row.put("recognizedCost", gross);
        row.put("recognizedContra", contra);
        row.put("bookValue", net);
        row.put("notes", notes);
        return row;
    }

    private SemanticReportValueSet fixedAssetDepreciationValues(
            AssetInventoryReportQueryService.FixedAssetDepreciationResult report)
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AssetInventoryReportQueryService.DepreciationReportRow source : report.rows())
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rowType", source.rowType());
            row.put("date", source.date());
            row.put("txnId", source.transactionId());
            row.put("assetId", source.assetId());
            row.put("assetName", source.assetName());
            row.put("accountCode", source.accountCode());
            row.put("fundCode", source.fundCode());
            row.put("amount", source.amount());
            row.put("remainingDepreciable", source.remainingDepreciable());
            row.put("remainingPeriods", source.remainingPeriods());
            row.put("notes", source.notes());
            rows.add(row);
        }
        rows.add(depreciationSummaryRow("Domain contra total", report.domainContra(), null));
        rows.add(depreciationSummaryRow("Ledger contra total", report.ledgerContra(), null));
        rows.add(depreciationSummaryRow("Difference", report.difference(), report.explanation()));
        if (report.truncated())
        {
            rows.add(depreciationSummaryRow("Row limit reached", null,
                    "Detail and schedule rows were limited by the request."));
        }
        SemanticReportValueSet values = new SemanticReportValueSet();
        values.putTable("fixedAssetDepreciation.rows", rows);
        return values;
    }

    private Map<String, Object> depreciationSummaryRow(
            String rowType,
            BigDecimal amount,
            String notes)
    {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rowType", rowType);
        row.put("amount", amount);
        row.put("notes", notes);
        return row;
    }

    private SemanticReportValueSet inventoryValuationValues(
            AssetInventoryReportQueryService.InventoryValuationResult report)
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AssetInventoryReportQueryService.InventoryValuationRow source : report.rows())
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rowType", "Item");
            row.put("itemId", source.itemId());
            row.put("itemName", source.itemName());
            row.put("itemType", source.itemType());
            row.put("status", source.status());
            row.put("accountCode", source.accountCode());
            row.put("fundCode", source.fundCode());
            row.put("quantity", source.quantity());
            row.put("unit", source.unit());
            row.put("unitValue", source.unitValue());
            row.put("totalValue", source.totalValue());
            row.put("movementId", source.latestMovementId());
            row.put("txnId", source.transactionId());
            rows.add(row);
        }
        rows.add(inventorySummaryRow("Domain valuation", report.domainValue(), null));
        rows.add(inventorySummaryRow("Ledger control total", report.ledgerValue(), null));
        rows.add(inventorySummaryRow("Difference", report.difference(), report.explanation()));
        rows.add(inventorySummaryRow("Unlinked movement net", report.unlinkedMovementNet(),
                "Signed value of selected movements without a canonical transaction."));
        if (report.openingBalanceExcluded().signum() != 0)
        {
            rows.add(inventorySummaryRow("Fund-unallocated opening balance",
                    report.openingBalanceExcluded(), report.explanation()));
        }
        SemanticReportValueSet values = new SemanticReportValueSet();
        values.putTable("inventoryValuation.rows", rows);
        return values;
    }

    private Map<String, Object> inventorySummaryRow(
            String rowType,
            BigDecimal value,
            String notes)
    {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rowType", rowType);
        row.put("totalValue", value);
        row.put("notes", notes);
        return row;
    }

    private SemanticReportValueSet inventoryMovementValues(
            AssetInventoryReportQueryService.InventoryMovementResult report)
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AssetInventoryReportQueryService.InventoryMovementReportRow source : report.rows())
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rowType", source.movementType());
            row.put("date", source.movementDate());
            row.put("txnId", source.transactionId());
            row.put("movementId", source.movementId());
            row.put("itemId", source.itemId());
            row.put("itemName", source.itemName());
            row.put("accountCode", source.accountCode());
            row.put("fundCode", source.fundCode());
            row.put("quantityChange", source.quantityChange());
            row.put("resultingQuantity", source.resultingQuantity());
            row.put("unitValue", source.unitValue());
            row.put("signedValue", source.signedValue());
            row.put("accountingState", source.accountingState());
            row.put("notes", source.notes());
            rows.add(row);
        }
        rows.add(inventoryMovementSummaryRow("Domain movement net", report.domainNet(), null));
        rows.add(inventoryMovementSummaryRow("Ledger account activity", report.ledgerActivity(), null));
        rows.add(inventoryMovementSummaryRow("Difference", report.difference(), report.explanation()));
        rows.add(inventoryMovementSummaryRow("Unlinked movement net", report.unlinkedMovementNet(),
                "Signed value of displayed movements without a canonical transaction."));
        SemanticReportValueSet values = new SemanticReportValueSet();
        values.putTable("inventoryMovementHistory.rows", rows);
        return values;
    }

    private Map<String, Object> inventoryMovementSummaryRow(
            String rowType,
            BigDecimal value,
            String notes)
    {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rowType", rowType);
        row.put("signedValue", value);
        row.put("notes", notes);
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

    private AssetInventoryReportQueryService requireAssetInventoryQueries()
    {
        if (assetInventoryQueries == null)
        {
            throw new IllegalStateException(
                    "Fixed-asset and inventory reports require a company-scoped query service.");
        }
        return assetInventoryQueries;
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
