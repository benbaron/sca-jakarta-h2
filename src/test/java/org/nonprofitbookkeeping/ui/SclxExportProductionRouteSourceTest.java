package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxExportProductionRouteSourceTest
{
    @Test
    void productionShellUsesTheRealSelectedCompanyExportService() throws IOException
    {
        String mainApp = source("MainApp.java");
        String factory = source("WorkspaceServicesFactory.java");
        String registry = source("UiServiceRegistry.java");
        String menu = source("SclxExportMenuInstaller.java");

        assertTrue(mainApp.contains("SclxExportUiRegistry.install(root.workspaceWindow())"));
        assertTrue(factory.contains("UiServiceRegistry::sclxFileExport"));
        assertTrue(factory.contains("new SclxExportCoordinator"));
        assertTrue(registry.contains("new SclxCoreSnapshotQueryService"));
        assertTrue(registry.contains("new SclxFileExportService"));
        assertTrue(menu.contains("Export Active Company to SCLX…"));
        assertTrue(menu.contains("actions.requestExport()"));
    }

    private static String source(String filename) throws IOException
    {
        return Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui", filename));
    }
}
