package org.nonprofitbookkeeping.report;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TruthfulSemanticReportSourceTest
{
    @Test
    void productionUsesCompanyScopedSemanticQueries() throws Exception
    {
        String registry = source("src/main/java/org/nonprofitbookkeeping/ui/UiServiceRegistry.java");
        String panel = source("src/main/java/org/nonprofitbookkeeping/ui/ReportLibraryPanel.java");
        String workbook = source(
                "src/main/java/org/nonprofitbookkeeping/report/template/WorkbookSemanticReportService.java");

        assertTrue(registry.contains("new SemanticAccountingReportQueryService("));
        assertTrue(registry.contains("UiServiceRegistry::activeCompanyCode"));
        assertTrue(panel.contains("UiServiceRegistry.semanticAccountingReports()"));
        assertTrue(workbook.contains("requireSemanticQueries().bankAccountActivity("));
        assertTrue(workbook.contains("requireSemanticQueries().postedFundTransfers("));
        assertFalse(workbook.contains(
                "\"AllChecksTfrs\" -> ledgerTableValues("));
        assertFalse(workbook.contains(
                "financialReports.generalLedgerDetail(start, end, null, rowLimit)"));
    }

    @Test
    void visibleTemplatesContainNoApproximationClaim() throws Exception
    {
        String definition = source(
                "src/main/java/org/nonprofitbookkeeping/report/ReportDefinition.java");
        String bank = source(
                "src/main/resources/org/nonprofitbookkeeping/report/templates/AllChecksTfrs.report.json");
        String transfers = source(
                "src/main/resources/org/nonprofitbookkeeping/report/templates/FundTransfers.report.json");

        assertTrue(definition.contains("Bank Account Activity (SCA workbook)"));
        assertFalse(definition.contains("AllChecksTfrs (SCA workbook)"));
        assertTrue(bank.contains("\"title\": \"Bank Account Activity\""));
        assertTrue(bank.contains("BANK-account splits only"));
        assertFalse(bank.contains("First-pass"));
        assertTrue(transfers.contains("Explicit POSTED fund-transfer records"));
        assertFalse(transfers.contains("Fund summary derived from live ledger detail"));
    }

    private static String source(String path) throws Exception
    {
        return Files.readString(Path.of(path));
    }
}
