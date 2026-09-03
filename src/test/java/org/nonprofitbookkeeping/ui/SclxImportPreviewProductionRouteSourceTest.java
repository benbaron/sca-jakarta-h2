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
        String compactRegistry = registry.replaceAll("\\s+", "");

        assertTrue(panel.contains("Preview SCLX…"));
        assertTrue(panel.contains("Re-preview Same SCLX"));
        assertTrue(panel.contains("repreviewSameSclxButton"));
        assertTrue(panel.contains("SCLX Active Company Files"));
        assertTrue(compactPanel.contains("runPreviewOperation(\"import-preview-sclx\""));
        assertTrue(compactPanel.contains("runCommitOperation(\"import-preview-sclx-commit\""));
        assertTrue(compactPanel.contains(
                "commitService.commit(source,preview,actor,mappingsApproved,existingCompanyImportApproved)"));
        assertTrue(panel.contains("Import Previewed SCLX…"));
        assertTrue(panel.contains("Re-preview with SCLX Choices"));
        assertTrue(panel.contains("Preview message"));
        assertTrue(panel.contains("SclxImportDisposition.MAKE_SUGGESTED_CORRECTION"));
        assertTrue(panel.contains("wrappingColumn(\"Detail\""));
        assertTrue(panel.contains("Conflict Choice"));
        assertTrue(panel.contains("Approve shown SCLX account/fund mappings"));
        assertTrue(panel.contains("Import into existing company (preserve settings)"));
        assertTrue(panel.contains("No data was changed"));
        assertTrue(compactPanel.contains("sclxPreviewOperationFactory(sclxPreviewService)"));
        assertFalse(compactPanel.contains("()->Objects.requireNonNull(sclxPreviewService.get()"));
        assertTrue(factory.contains("services::sclxImportPreviewService"));
        assertTrue(factory.contains("services::sclxImportCommitService"));
        assertTrue(workspaceServices.contains("Supplier<SclxImportPreviewService>"));
        assertTrue(workspaceServices.contains("Function<String, SclxImportCommitService>"));
        assertTrue(compactRegistry.contains("newSclxImportPreviewService(services().jpa(),()->fixedCompanyCode)"));
        assertTrue(compactRegistry.contains(
                "newSclxImportCommitService(current.jpa(),()->fixedCompanyCode,current.authorizationGuard())"));
        assertFalse(compactRegistry.contains(
                "newSclxImportCommitService(services().jpa(),()->fixedCompanyCode)"));
    }

    private static String source(String filename) throws IOException
    {
        return Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui", filename));
    }
}
