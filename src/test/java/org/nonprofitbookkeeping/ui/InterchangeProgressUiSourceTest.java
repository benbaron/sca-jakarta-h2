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
        String compactPanel = panel.replaceAll("\\s+", "");

        assertTrue(panel.contains("importPreviewOperationProgress"));
        assertTrue(panel.contains("cancelImportPreviewOperationButton"));
        assertTrue(panel.contains("importPreviewControlsScroll"));
        assertTrue(compactPanel.contains("runPreviewOperation(\"import-preview-coa\""));
        assertTrue(compactPanel.contains("runPreviewOperation(\"import-preview-sclx\""));
        assertTrue(compactPanel.contains("runPreviewOperation(\"import-preview-bank\""));
        assertTrue(compactPanel.contains("runPreviewOperation(\"import-preview-bank-csv\""));
        assertTrue(compactPanel.contains("runPreviewOperation(\"import-preview-normalized-bank-csv\""));
        assertTrue(compactPanel.contains("runCommitOperation(\"import-preview-sclx-commit\""));
        assertTrue(compactPanel.contains("runCommitOperation(\"import-preview-bank-commit\""));
        assertTrue(panel.contains("commit cannot be cancelled"));
        assertFalse(compactPanel.contains("UiAsync.run("));

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
