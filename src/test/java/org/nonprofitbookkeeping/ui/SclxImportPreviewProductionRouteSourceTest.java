package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxImportPreviewProductionRouteSourceTest
{
    @Test
    void productionImportPreviewUsesTheReadOnlySclxService() throws IOException
    {
        String panel = source("ImportPreviewPanel.java");
        String factory = source("PanelFactory.java");
        String workspaceServices = source("WorkspaceServices.java");
        String registry = source("UiServiceRegistry.java");

        assertTrue(panel.contains("Preview SCLX…"));
        assertTrue(panel.contains("SCLX Active Company Files"));
        assertTrue(panel.contains("UiAsync.run(\"import-preview-sclx\""));
        assertTrue(panel.contains("No data was changed"));
        assertTrue(factory.contains("services::sclxImportPreviewService"));
        assertTrue(workspaceServices.contains("Supplier<SclxImportPreviewService>"));
        assertTrue(registry.contains("new SclxImportPreviewService(services().jpa()"));
    }

    private static String source(String filename) throws IOException
    {
        return Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui", filename));
    }
}
