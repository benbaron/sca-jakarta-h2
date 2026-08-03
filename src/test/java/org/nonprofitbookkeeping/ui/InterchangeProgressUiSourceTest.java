package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source guardrails for the final P15-S8 visible operation lifecycle. */
class InterchangeProgressUiSourceTest
{
    @Test
    void everyImportPreviewUsesOneTransientProgressAndCancellationBoundary() throws Exception
    {
        String panel = source("ImportPreviewPanel.java");
        String controller = source("InterchangeTaskController.java");

        assertTrue(panel.contains("importPreviewOperationProgress"));
        assertTrue(panel.contains("cancelImportPreviewOperationButton"));
        assertTrue(panel.contains("importPreviewControlsScroll"));
        assertTrue(panel.contains("runPreviewOperation(\n                \"import-preview-coa\""));
        assertTrue(panel.contains("runPreviewOperation(\n                \"import-preview-sclx\""));
        assertTrue(panel.contains("runPreviewOperation(\n                \"import-preview-bank\""));
        assertTrue(panel.contains("runPreviewOperation(\n                \"import-preview-bank-csv\""));
        assertTrue(panel.contains("runPreviewOperation(\n                \"import-preview-normalized-bank-csv\""));
        assertTrue(panel.contains("runCommitOperation(\"import-preview-sclx-commit\""));
        assertTrue(panel.contains("runCommitOperation(\"import-preview-bank-commit\""));
        assertTrue(panel.contains("commit cannot be cancelled"));
        assertFalse(panel.contains("UiAsync.run("));

        assertTrue(controller.contains("InterchangeProgress"));
        assertTrue(controller.contains("task.cancel(true)"));
        assertTrue(controller.contains("!commitStarted"));
        assertFalse(controller.contains("static List"));
        assertFalse(controller.contains("EntityManager"));
    }

    @Test
    void denseBankExportScopeRemainsScrollableAtLaptopWidth() throws Exception
    {
        String panel = source("BankTransactionsPanel.java");

        assertTrue(panel.contains("bankStatementExportControlsScroll"));
        assertTrue(panel.contains("ScrollBarPolicy.AS_NEEDED"));
        assertTrue(panel.contains("bankStatementExportProgress"));
        assertTrue(panel.contains("exportActions.busyProperty()"));
    }

    private static String source(String filename) throws Exception
    {
        return Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui", filename));
    }
}
