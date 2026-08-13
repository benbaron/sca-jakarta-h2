package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source guard proving Reconciliation no longer owns a second bank-file import authority. */
public class ReconciliationBankImportAuthoritySourceTest
{
    @Test
    void reconciliationRoutesFileImportToGovernedImportPreview() throws Exception
    {
        String panel = source("src/main/java/org/nonprofitbookkeeping/ui/ReconciliationRunsPanel.java");
        String service = source("src/main/java/org/nonprofitbookkeeping/service/BankReconciliationWorkspaceService.java");
        String preview = source("src/main/java/org/nonprofitbookkeeping/ui/ImportPreviewPanel.java");
        String compactPreview = preview.replaceAll("\\s+", "");

        assertTrue(panel.contains("Import Bank Statement…"));
        assertTrue(panel.contains("AppPanelId.IMPORT_PREVIEW"));
        assertTrue(panel.contains("BankImportNavigationContext.forReconciliation"));
        assertTrue(panel.contains("Bank import committed; reconciliation refreshed from durable bank-review facts."));

        assertFalse(panel.contains("Import Pasted Text"));
        assertFalse(panel.contains("CSV Import"));
        assertFalse(panel.contains("OFX Import"));
        assertFalse(panel.contains("QIF Import"));
        assertFalse(panel.contains("Files.readString"));
        assertFalse(panel.contains("importStatementText("));

        assertFalse(service.contains("ImportStatementCommand"));
        assertFalse(service.contains("StatementSource"));
        assertFalse(service.contains("parseCsv("));
        assertFalse(service.contains("parseOfx("));
        assertFalse(service.contains("parseQif("));
        assertFalse(service.contains("Pattern.compile"));
        assertFalse(service.contains("new BankImportBatch"));
        assertFalse(service.contains("new BankStatementLine"));

        assertTrue(preview.contains("BankImportNavigationContext.parseImportRequest"));
        assertTrue(preview.contains("reconciliationImportBankAccountId"));
        assertTrue(compactPreview.contains("bankAccount.setDisable(disabled||reconciliationImportBankAccountId!=null)"));
        assertTrue(preview.contains("returnToReconciliationAfterBankCommit"));
        assertTrue(preview.contains("Preview Bank OFX/QFX…"));
        assertTrue(preview.contains("Preview Mapped Bank CSV…"));
        assertTrue(preview.contains("Preview Normalized Bank CSV…"));
        assertFalse(preview.contains("Preview QIF"));
    }

    private static String source(String path) throws Exception
    {
        return Files.readString(Path.of(path));
    }
}
