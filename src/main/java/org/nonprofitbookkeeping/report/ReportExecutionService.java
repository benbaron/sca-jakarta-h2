package org.nonprofitbookkeeping.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.nonprofitbookkeeping.report.template.RenderedSemanticReport;
import org.nonprofitbookkeeping.report.template.SemanticReportRenderer;
import org.nonprofitbookkeeping.report.template.SemanticReportValueSet;
import org.nonprofitbookkeeping.report.template.WorkbookSemanticReportService;
import org.nonprofitbookkeeping.service.FinancialReportDisplayFormat;
import org.nonprofitbookkeeping.service.FinancialReportRenderer;
import org.nonprofitbookkeeping.service.FinancialReportService;

import java.time.LocalDate;
import java.util.Objects;

/** Executes a validated report request against authoritative report projections. */
public final class ReportExecutionService
{
    private final FinancialReportService reports;
    private final FinancialReportDisplayFormat displayFormat;
    private final SemanticAccountingReportQueryService semanticQueries;
    private final AssetInventoryReportQueryService assetInventoryQueries;
    private final ReportPresentationMetadata presentationMetadata;

    public ReportExecutionService(
            FinancialReportService reports,
            FinancialReportDisplayFormat displayFormat)
    {
        this(reports, displayFormat, null, null, ReportPresentationMetadata.EMPTY);
    }

    public ReportExecutionService(
            FinancialReportService reports,
            FinancialReportDisplayFormat displayFormat,
            SemanticAccountingReportQueryService semanticQueries)
    {
        this(reports, displayFormat, semanticQueries, null, ReportPresentationMetadata.EMPTY);
    }

    public ReportExecutionService(
            FinancialReportService reports,
            FinancialReportDisplayFormat displayFormat,
            SemanticAccountingReportQueryService semanticQueries,
            AssetInventoryReportQueryService assetInventoryQueries)
    {
        this(reports, displayFormat, semanticQueries, assetInventoryQueries,
                ReportPresentationMetadata.EMPTY);
    }

    public ReportExecutionService(
            FinancialReportService reports,
            FinancialReportDisplayFormat displayFormat,
            SemanticAccountingReportQueryService semanticQueries,
            AssetInventoryReportQueryService assetInventoryQueries,
            ReportPresentationMetadata presentationMetadata)
    {
        this.reports = Objects.requireNonNull(reports, "reports");
        this.displayFormat = displayFormat == null
                ? FinancialReportDisplayFormat.plain()
                : displayFormat;
        this.semanticQueries = semanticQueries;
        this.assetInventoryQueries = assetInventoryQueries;
        this.presentationMetadata = presentationMetadata == null
                ? ReportPresentationMetadata.EMPTY : presentationMetadata;
    }

    public ReportResult execute(ReportRequest request)
    {
        Objects.requireNonNull(request, "request");
        if (request.definition().source() == ReportDefinition.ReportSource.SEMANTIC)
        {
            return executeSemantic(request);
        }
        return executeCore(request);
    }

    private ReportResult executeCore(ReportRequest request)
    {
        return switch (request.definition())
        {
            case TRIAL_BALANCE -> {
                FinancialReportService.TrialBalanceReport report =
                        reports.trialBalance(request.asOfDate(), request.fundCode());
                yield new ReportResult(
                        request,
                        FinancialReportRenderer.renderTrialBalanceText(report, displayFormat),
                        FinancialReportRenderer.renderTrialBalanceCsv(report),
                        null,
                        null,
                        CoreFinancialReportTableBuilder.trialBalance(report, displayFormat));
            }
            case GENERAL_LEDGER_DETAIL -> {
                ReportDomainFilter.AccountSelection filter =
                        (ReportDomainFilter.AccountSelection) request.domainFilter();
                java.util.List<FinancialReportService.GeneralLedgerRow> rows =
                        reports.generalLedgerDetail(
                                request.startDate(),
                                request.endDate(),
                                request.fundCode(),
                                request.rowLimit(),
                                filter.accountId());
                yield new ReportResult(
                        request,
                        FinancialReportRenderer.renderGeneralLedgerText(rows, displayFormat),
                        FinancialReportRenderer.renderGeneralLedgerCsv(rows),
                        null,
                        null,
                        CoreFinancialReportTableBuilder.generalLedger(rows));
            }
            case BALANCE_SHEET -> {
                LocalDate openingDate = previousDay(request.startDate());
                FinancialReportService.BalanceSheetReport opening =
                        reports.balanceSheet(openingDate, request.fundCode());
                FinancialReportService.BalanceSheetReport closing =
                        reports.balanceSheet(request.asOfDate(), request.fundCode());
                FinancialReportService.IncomeStatementReport periodIncome =
                        reports.incomeStatement(
                                request.startDate(),
                                request.endDate(),
                                request.fundCode());
                yield new ReportResult(
                        request,
                        FinancialReportRenderer.renderBalanceSheetText(closing, displayFormat),
                        FinancialReportRenderer.renderBalanceSheetCsv(closing),
                        null,
                        null,
                        CoreFinancialReportTableBuilder.balanceSheet(
                                opening,
                                closing,
                                periodIncome,
                                displayFormat,
                                presentationMetadata));
            }
            case INCOME_STATEMENT -> {
                FinancialReportService.IncomeStatementReport report =
                        reports.incomeStatement(
                                request.startDate(),
                                request.endDate(),
                                request.fundCode());
                FinancialReportService.BalanceSheetReport opening =
                        reports.balanceSheet(previousDay(request.startDate()), request.fundCode());
                FinancialReportService.BalanceSheetReport closing =
                        reports.balanceSheet(request.endDate(), request.fundCode());
                yield new ReportResult(
                        request,
                        FinancialReportRenderer.renderIncomeStatementText(report, displayFormat),
                        FinancialReportRenderer.renderIncomeStatementCsv(report),
                        null,
                        null,
                        CoreFinancialReportTableBuilder.incomeStatement(
                                report,
                                opening,
                                closing,
                                displayFormat,
                                presentationMetadata));
            }
            default -> throw new IllegalArgumentException(
                    "Report is not a core report: " + request.definition().displayName());
        };
    }

    private static LocalDate previousDay(LocalDate value)
    {
        return LocalDate.MIN.equals(value) ? value : value.minusDays(1);
    }

    private ReportResult executeSemantic(ReportRequest request)
    {
        WorkbookSemanticReportService semantic =
                new WorkbookSemanticReportService(
                        reports, semanticQueries, assetInventoryQueries);
        String templateId = request.definition().templateId();
        JsonNode template = semantic.loadTemplate(templateId);
        SemanticReportValueSet values = semantic.loadValues(request);
        RenderedSemanticReport rendered =
                new SemanticReportRenderer(displayFormat).render(template, values);
        return new ReportResult(
                request,
                rendered.text(),
                rendered.csv(),
                template,
                values,
                SemanticReportTableModelBuilder.build(
                        template,
                        values,
                        displayFormat));
    }
}
