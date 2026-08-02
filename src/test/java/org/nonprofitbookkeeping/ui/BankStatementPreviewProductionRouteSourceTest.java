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
        String previewService = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/service/ImportPreviewService.java"));
        String parser = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/interchange/bank/BankStatementParser.java"));

        assertTrue(panel.contains("UiAsync.run(\"import-preview-bank\""));
        assertTrue(panel.contains("result.variant()"));
        assertTrue(panel.contains("result.maskedAccountId()"));
        assertTrue(panel.contains("No data was changed"));
        assertTrue(previewService.contains("bankStatementParser.parse(path)"));
        assertFalse(previewService.substring(
                previewService.indexOf("public BankPreviewResult previewBankStatement"),
                previewService.indexOf("public NormalizedBankPreviewResult previewNormalizedBankStatement"))
                .contains("importBankDataFile"));
        assertTrue(parser.contains("FEATURE_SECURE_PROCESSING"));
        assertTrue(parser.contains("disallow-doctype-decl"));
        assertTrue(parser.contains("FILENAME_CONTENT_MISMATCH"));
        assertTrue(parser.contains("MULTI_ACCOUNT"));
    }
}
