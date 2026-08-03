package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankStatementPreviewProductionRouteSourceTest
{
    @Test
    void productionImportPreviewUsesStrictContentFirstBankParser() throws Exception
    {
        String panel = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/ImportPreviewPanel.java"));
        String reviewService = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/interchange/bank/BankStatementReviewService.java"));
        String parser = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/interchange/bank/BankStatementParser.java"));

        assertTrue(panel.contains("runPreviewOperation(\n                \"import-preview-bank\""));
        assertTrue(panel.contains("fixedScopeService.preview(file, company, account.getId())"));
        assertTrue(panel.contains("commitPreviewedBankReviewButton"));
        assertTrue(panel.contains("Confirm Atomic Bank Review Import"));
        assertTrue(panel.contains("No data was changed"));
        assertTrue(reviewService.contains("parser::parse"));
        assertTrue(reviewService.contains("transaction.rollback()"));
        assertFalse(panel.contains("previewService.previewBankStatement"));
        assertTrue(parser.contains("FEATURE_SECURE_PROCESSING"));
        assertTrue(parser.contains("disallow-doctype-decl"));
        assertTrue(parser.contains("FILENAME_CONTENT_MISMATCH"));
        assertTrue(parser.contains("MULTI_ACCOUNT"));
    }

    @Test
    void productionImportPreviewComposesNormalizedCsvWithoutMappingProfile() throws Exception
    {
        String panel = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/ImportPreviewPanel.java"));
        String factory = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/PanelFactory.java"));
        String services = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/WorkspaceServicesFactory.java"));
        String registry = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/UiServiceRegistry.java"));

        assertTrue(panel.contains("Preview Normalized Bank CSV…"));
        assertTrue(panel.contains("previewNormalizedBankCsvButton"));
        assertTrue(panel.contains("fixedScopeService.preview(file, company, account.getId())"));
        assertTrue(panel.contains("commitService.commit(preview, identityConfirmed, actor)"));
        assertTrue(panel.contains("Confirm Atomic Normalized Bank CSV Import"));
        assertTrue(panel.contains("No ledger transaction was created"));
        assertFalse(panel.contains("normalizedBankCsvReviewService.get().preview"));
        assertTrue(factory.contains("services::normalizedBankCsvReviewService"));
        assertTrue(services.contains("UiServiceRegistry::normalizedBankCsvReview"));
        assertTrue(registry.contains("new NormalizedBankCsvReviewService(services().jpa())"));
    }
}
