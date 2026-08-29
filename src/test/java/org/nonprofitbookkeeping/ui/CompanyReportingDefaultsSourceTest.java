package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyReportingDefaultsSourceTest
{
    @Test
    void companyAdminAndReportLibraryShareCompanyOwnedOpeningDefaults() throws Exception
    {
        String admin = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/CompanyAdminPanel.java"));
        String reportLibrary = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/ReportLibraryPanel.java"));
        String preferences = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/service/CompanyUiPreferencesService.java"));

        assertTrue(admin.contains("new Label(\"Reporting defaults\")"));
        assertTrue(admin.contains("preferencesService.saveReportingDefaults(company.code(), defaults)"));
        assertTrue(admin.contains("They apply the next time Report Library is opened"));
        assertFalse(admin.contains("Tax filing and reporting-default editors are deferred"));

        assertTrue(reportLibrary.contains("preferencesService.loadReportingDefaults(companyCode)"));
        assertTrue(reportLibrary.contains("select(reportingDefaults.defaultExportFormat())"));
        assertTrue(reportLibrary.contains("openingReport = reportingDefaults.defaultReport()"));
        assertFalse(reportLibrary.contains(
                "exportFormat.getSelectionModel().select(FinancialReportExportFormat.TEXT);"));
        assertFalse(reportLibrary.contains(
                "reportList.getSelectionModel().select(ReportDefinition.TRIAL_BALANCE);"));

        assertTrue(preferences.contains(
                "REPORTING_DEFAULTS_PREFIX = \"reportingDefaults.\""));
        assertTrue(preferences.contains(
                "DEFAULT_REPORT_KEY = REPORTING_DEFAULTS_PREFIX + \"defaultReportId\""));
        assertTrue(preferences.contains(
                "DEFAULT_EXPORT_FORMAT_KEY = REPORTING_DEFAULTS_PREFIX + \"defaultExportFormat\""));
        assertTrue(preferences.contains("saveState(companyCode, Map.of("));
    }
}
