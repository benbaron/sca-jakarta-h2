package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FundsPanelLifecycleSourceTest
{
    @Test
    void panelUsesStableIdLifecycleServiceAndCompliantWorkspace() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/FundsPanel.java"));

        assertTrue(source.contains("private Long editingFundId"));
        assertTrue(source.contains("new FundCommand("));
        assertTrue(source.contains("fundAdmin().save(command)"));
        assertTrue(source.contains("fundAdmin().usage(editingFundId)"));
        assertTrue(source.contains("fundAdmin().deleteUnused(editingFundId)"));
        assertTrue(source.contains("new SplitPane()"));
        assertTrue(source.contains("CompanyUiFormat"));
        assertTrue(source.contains("companyFormat.install(effectiveFromField)"));
        assertTrue(source.contains("preferencesService.saveState"));
        assertTrue(source.contains("hasUnsavedChanges()"));
        assertFalse(source.contains("fundAdmin().upsert("));
        assertFalse(source.contains("Delete disabled"));
    }
}
