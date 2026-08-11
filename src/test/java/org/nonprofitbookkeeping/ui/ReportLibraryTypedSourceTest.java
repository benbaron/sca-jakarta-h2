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
        assertTrue(source.contains("currentResult.request().equals(request)"));
        assertTrue(source.contains("preferencesService.saveState"));
        assertTrue(source.contains("ComboBox<AssetInventoryReportQueryService.FilterOption>"));
        assertTrue(source.contains("domainFilterMode()"));
        assertFalse(source.contains("Report not implemented"));
        assertFalse(source.contains("ListView<String>"));
    }
}
