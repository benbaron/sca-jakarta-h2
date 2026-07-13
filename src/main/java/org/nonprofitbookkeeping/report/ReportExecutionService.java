package org.nonprofitbookkeeping.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.nonprofitbookkeeping.report.template.RenderedSemanticReport;
import org.nonprofitbookkeeping.report.template.SemanticReportRenderer;
import org.nonprofitbookkeeping.report.template.SemanticReportValueSet;
import org.nonprofitbookkeeping.report.template.WorkbookSemanticReportService;
import org.nonprofitbookkeeping.service.FinancialReportDisplayFormat;
import org.nonprofitbookkeeping.service.FinancialReportRenderer;
import org.nonprofitbookkeeping.service.FinancialReportService;

import java.util.Objects;

/** Executes a validated report request against authoritative report projections. */
public final class ReportExecutionService
{
    private final FinancialReportService reports;
    private final FinancialReportDisplayFormat displayFormat;

    public ReportExecutionService(
            FinancialReportService reports,
            FinancialReportDisplayFormat displayFormat)
    {
        this.reports = Objects.requireNonNull(reports, "reports");
        this.displayFormat = displayFormat == null
                ? FinancialReportDisplayFormat.plain()
                : displayFormat;
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
                        null);
            }
            case GENERAL_LEDGER_DETAIL -> {
                java.util.List<FinancialReportService.GeneralLedgerRow> rows =
                        reports.generalLedgerDetail(
                                request.startDate(),
                                request.endDate(),
                                request.fundCode(),
                                request.rowLimit());
                yield new ReportResult(
                        request,
                        FinancialReportRenderer.renderGeneralLedgerText(rows, displayFormat),
                        FinancialReportRenderer.renderGeneralLedgerCsv(rows),
                        null,
                        null);
            }
            case BALANCE_SHEET -> {
                FinancialReportService.BalanceSheetReport report =
                        reports.balanceSheet(request.asOfDate(), request.fundCode());
                yield new ReportResult(
                        request,
                        FinancialReportRenderer.renderBalanceSheetText(report, displayFormat),
                        FinancialReportRenderer.renderBalanceSheetCsv(report),
                        null,
                        null);
            }
            case INCOME_STATEMENT -> {
                FinancialReportService.IncomeStatementReport report =
                        reports.incomeStatement(
                                request.startDate(),
                                request.endDate(),
                                request.fundCode());
                yield new ReportResult(
                        request,
                        FinancialReportRenderer.renderIncomeStatementText(report, displayFormat),
                        FinancialReportRenderer.renderIncomeStatementCsv(report),
                        null,
                        null);
            }
            default -> throw new IllegalArgumentException(
                    "Report is not a core report: " + request.definition().displayName());
        };
    }

    private ReportResult executeSemantic(ReportRequest request)
    {
        WorkbookSemanticReportService semantic = new WorkbookSemanticReportService(reports);
        String templateId = request.definition().templateId();
        JsonNode template = semantic.loadTemplate(templateId);
        SemanticReportValueSet values = semantic.loadValues(
                templateId,
                request.startDate(),
                request.endDate(),
                request.fundCode(),
                request.rowLimit());
        RenderedSemanticReport rendered = new SemanticReportRenderer().render(template, values);
        return new ReportResult(
                request,
                rendered.text(),
                rendered.csv(),
                template,
                values);
    }
}
