package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportExportJobsEliminationSourceTest
{
    @Test
    void productionShellAndSessionStoreDoNotExposeGenericJobs() throws Exception
    {
        String appPanelId = source("AppPanelId.java");
        String navigation = source("NavigationPane.java");
        String mainWindow = source("MainWindow.java");
        String panelFactory = source("PanelFactory.java");
        String bankTransactions = source("BankTransactionsPanel.java");

        for (String source : new String[] {appPanelId, navigation, mainWindow, panelFactory})
        {
            assertFalse(source.contains("IMPORT_EXPORT_JOBS"));
            assertFalse(source.contains("Import / Export Jobs"));
        }
        assertFalse(Files.exists(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/ImportExportJobsPanel.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/UiWorkspaceDataStore.java")));
        assertFalse(mainWindow.contains("appendJob"));
        assertFalse(bankTransactions.contains("appendJob"));

        assertTrue(mainWindow.contains("importChartOfAccountsCsvFile"));
        assertFalse(mainWindow.contains("importBankDataFile"));
        assertTrue(mainWindow.contains("exportChartOfAccountsCsvFile"));
        assertFalse(mainWindow.contains("UiWorkspaceDataStore"));
        assertTrue(bankTransactions.contains("BankReviewQueryService"));
        assertTrue(bankTransactions.contains("reviewQuery.listRows"));
    }

    private static String source(String fileName) throws Exception
    {
        return Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui", fileName));
    }
}
