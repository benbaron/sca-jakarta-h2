package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxImportPreviewProductionRouteSourceTest
{
    @Test
    void productionImportPreviewAndCommitUseFixedScopeSclxServices() throws IOException
    {
        String panel = source("ImportPreviewPanel.java");
        String factory = source("PanelFactory.java");
        String workspaceServices = source("WorkspaceServices.java");
        String registry = source("UiServiceRegistry.java");
        String compactPanel = panel.replaceAll("\\s+", "");

        assertTrue(panel.contains("Preview SCLX…"));
        assertTrue(panel.contains("SCLX Active Company Files"));
        assertTrue(compactPanel.contains("runPreviewOperation(\"import-preview-sclx\""));
        assertTrue(compactPanel.contains("runCommitOperation(\"import-preview-sclx-commit\""));
        assertTrue(compactPanel.contains(
                "commitService.commit(source,preview,actor,mappingsApproved,existingCompanyImportApproved)"));
        assertTrue(panel.contains("Import Previewed SCLX…"));
        assertTrue(panel.contains("Apply SCLX Mappings"));
        assertTrue(panel.contains("Approve shown SCLX account/fund mappings"));
        assertTrue(panel.contains("Import into existing company (preserve settings)"));
        assertTrue(panel.contains("No data was changed"));
        assertTrue(compactPanel.contains("sclxPreviewOperationFactory(sclxPreviewService)"));
        assertFalse(compactPanel.contains("()->Objects.requireNonNull(sclxPreviewService.get()"));
        assertTrue(factory.contains("services::sclxImportPreviewService"));
        assertTrue(factory.contains("services::sclxImportCommitService"));
        assertTrue(workspaceServices.contains("Supplier<SclxImportPreviewService>"));
        assertTrue(workspaceServices.contains("Function<String, SclxImportCommitService>"));
        assertTrue(registry.contains("new SclxImportPreviewService(services().jpa()"));
        assertTrue(registry.contains("new SclxImportCommitService(services().jpa()"));
    }

    private static String source(String filename) throws IOException
    {
        return Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui", filename));
    }
}
