package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportLibraryTypedSourceTest
{
    @Test
    void panelUsesTypedCatalogRequestAndCompanyFormatting() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/ReportLibraryPanel.java"));

        assertTrue(source.contains("ListView<ReportDefinition>"));
        assertTrue(source.contains("ReportDefinition.catalog()"));
        assertTrue(source.contains("new ReportRequest("));
        assertTrue(source.contains("ReportFundOption.ALL_FUNDS"));
        assertTrue(source.contains("new CompanyUiFormat("));
        assertTrue(source.contains("new SemanticReportFxRenderer(companyFormat)"));
        assertTrue(source.contains("new FormattedReportFxRenderer(companyFormat)"));
        assertTrue(source.contains("ReportPresentationMetadata.from("));
        assertTrue(source.contains("result.tabular()"));
        assertTrue(source.contains("CompanyTableStateBinder.apply("));
        assertTrue(source.contains("Orientation.VERTICAL"));
        assertTrue(source.contains("STATE_PREVIEW_DIVIDER"));
        assertTrue(source.contains("currentResult.request().equals(request)"));
        assertTrue(source.contains("preferencesService.saveState"));
        assertTrue(source.contains("ComboBox<AssetInventoryReportQueryService.FilterOption>"));
        assertTrue(source.contains("domainFilterMode()"));
        assertTrue(source.contains("listPostingAccountsIncludingInactive()"));
        assertTrue(source.contains("new ReportDomainFilter.AccountSelection("));
        assertTrue(source.contains("\"All accounts\""));
        assertTrue(source.contains("new JasperPdfFinancialReportAdapter(companyFormat)"));
        assertTrue(source.contains("result.tableModel()"));
        assertFalse(source.contains("Report not implemented"));
        assertFalse(source.contains("ListView<String>"));
    }

    @Test
    void corePreviewsDefineCompleteTablesWrappingAndWorkbookColors() throws Exception
    {
        String execution = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/report/ReportExecutionService.java"));
        String renderer = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/FormattedReportFxRenderer.java"));
        String statements = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/report/CoreFinancialReportTableBuilder.java"));
        String styles = Files.readString(Path.of("src/main/resources/ui/styles.css"));

        assertTrue(execution.contains("CoreFinancialReportTableBuilder.trialBalance"));
        assertTrue(execution.contains("CoreFinancialReportTableBuilder.generalLedger"));
        assertTrue(execution.contains("CoreFinancialReportTableBuilder.balanceSheet"));
        assertTrue(execution.contains("CoreFinancialReportTableBuilder.incomeStatement"));
        assertTrue(renderer.contains("TableView<ReportTableModel.Row>"));
        assertTrue(renderer.contains("content.setWrapText"));
        assertTrue(renderer.contains("new Tooltip(display)"));
        assertTrue(renderer.contains("UNCONSTRAINED_RESIZE_POLICY"));
        assertTrue(renderer.contains("metadataHeader(model.headerLines())"));
        assertTrue(styles.contains(".formatted-report-section-row"));
        assertTrue(styles.contains(".formatted-report-metadata-primary"));
        assertTrue(styles.contains(".formatted-report-total-row"));
        assertTrue(styles.contains(".formatted-report-success-row"));
        assertTrue(styles.contains("#d9eaf7"));
        assertTrue(styles.contains("#cfe2f3"));
        assertFalse(statements.contains("Society for Creative Anachronism"));
        assertFalse(statements.contains("The Barony of"));
        assertFalse(statements.contains("Office & Admin"));
        assertFalse(statements.contains("Fund Raising"));
    }
}
