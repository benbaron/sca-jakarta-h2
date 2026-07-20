package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreEditorComplianceSourceTest
{
    @Test
    void coreEditorsUseTopBottomSplitsScrollableFormsAndDirtyState() throws Exception
    {
        assertEditor("AssetsRegisterPanel.java", "assetRegisterWorkspaceSplit", "assetRegisterEditorScroll");
        assertEditor("ChartOfAccountsPanel.java", "chartOfAccountsWorkspaceSplit", "chartOfAccountsEditorScroll");
        assertEditor("BudgetEditorPanel.java", "budgetEditorSplitPane", "budgetEditorScroll");

        String banking = source("BankingPanel.java");
        assertTrue(banking.contains("bankingInstitutionsSplit"));
        assertTrue(banking.contains("bankingAccountsSplit"));
        assertTrue(banking.contains("CompanySplitPaneStateBinder.bind"));
        assertTrue(banking.contains("boolean hasUnsavedChanges()"));

        String users = source("UserAdminPanel.java");
        assertTrue(users.contains("userAdminUsersSplit"));
        assertTrue(users.contains("userAdminAssignmentsSplit"));
        assertTrue(users.contains("boolean hasUnsavedChanges()"));

        String settings = source("SettingsPanel.java");
        assertTrue(settings.contains("FormDirtyTracker"));
        assertTrue(settings.contains("boolean hasUnsavedChanges()"));

        String administration = source("AdministrationPanel.java");
        assertTrue(administration.contains("anyMatch(AppPanel::hasUnsavedChanges)"));
    }

    private static void assertEditor(String filename, String splitId, String scrollId) throws Exception
    {
        String source = source(filename);
        assertTrue(source.contains(splitId));
        assertTrue(source.contains(scrollId));
        assertTrue(source.contains("setOrientation(Orientation.VERTICAL)"));
        assertTrue(source.contains("CompanySplitPaneStateBinder.bind"));
        assertTrue(source.contains("boolean hasUnsavedChanges()"));
    }

    private static String source(String filename) throws Exception
    {
        return Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui", filename));
    }
}
