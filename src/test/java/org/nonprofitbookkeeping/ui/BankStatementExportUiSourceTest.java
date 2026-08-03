package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Production-route guardrails for the final P15-S7 bank-statement export UI. */
class BankStatementExportUiSourceTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void productionPanelUsesGovernedConfiguredAccountExportOnly() throws Exception
    {
        String panel = source("BankTransactionsPanel.java");
        String factory = source("PanelFactory.java");
        String services = source("WorkspaceServicesFactory.java");
        String coordinator = source("BankStatementExportCoordinator.java");

        assertTrue(panel.contains("Export durable statement activity"));
        assertTrue(panel.contains("Export Bank CSV…"));
        assertTrue(panel.contains("Export OFX 2.x…"));
        assertTrue(panel.contains("Export QFX…"));
        assertTrue(panel.contains("listBankAccounts(activeCompany)"));
        assertTrue(panel.contains("exportActions.requestExport(account.id(), fromDate, throughDate, format)"));
        assertFalse(panel.contains("ImportExportOrchestrationService"));
        assertFalse(panel.contains("BankTransactionRecord"));
        assertFalse(panel.contains("exportSelectedRows"));
        assertFalse(panel.contains("Export Selected"));

        assertTrue(factory.contains("services.bankStatementExportActions()"));
        assertTrue(services.contains("new BankStatementExportCoordinator"));
        assertTrue(services.contains("databaseSessionController.activeDatabasePath()"));
        assertTrue(coordinator.contains("new BankStatementExportRequest"));
        assertTrue(coordinator.contains("new BankStatementOfxExportRequest"));
        assertTrue(coordinator.contains("Files.exists(destination, LinkOption.NOFOLLOW_LINKS)"));
        assertTrue(coordinator.contains("dialogs.confirmOverwrite(owner, destination, format)"));
        assertTrue(coordinator.contains("Task<BankStatementExportResult>"));
        assertTrue(coordinator.contains("result.sha256()"));
        assertTrue(coordinator.contains("result.messages()"));
    }

    @Test
    void destinationAndDefaultNameAreBoundToTheSelectedFormat()
    {
        Path csv = temporaryDirectory.resolve("review");
        assertEquals(
                temporaryDirectory.resolve("review.csv").toAbsolutePath().normalize(),
                BankStatementExportCoordinator.normalizeDestination(
                        csv, BankStatementExportFormat.NORMALIZED_CSV));
        assertEquals(
                temporaryDirectory.resolve("review.OFX").toAbsolutePath().normalize(),
                BankStatementExportCoordinator.normalizeDestination(
                        temporaryDirectory.resolve("review.OFX"), BankStatementExportFormat.OFX_2_XML));
        assertEquals(
                "ACME_West-bank-statement-2026-01-01-to-2026-01-31.qfx",
                BankStatementExportCoordinator.defaultFilename(
                        "ACME West",
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31),
                        BankStatementExportFormat.QFX_2_XML));
    }

    private static String source(String filename) throws Exception
    {
        return java.nio.file.Files.readString(
                Path.of("src/main/java/org/nonprofitbookkeeping/ui", filename));
    }
}
