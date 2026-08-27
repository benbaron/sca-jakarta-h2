package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards P17-C10 retirement of duplicate and parallel UI architecture. */
class LegacyUiRetirementSourceTest
{
    @Test
    void obsoleteDashboardJournalReferenceAndCustomerSourcesStayRemoved()
    {
        List<String> retiredSources = List.of(
                "src/main/java/org/nonprofitbookkeeping/ui/DashboardWorkspacePanel.java",
                "src/main/java/org/nonprofitbookkeeping/ui/DashboardExperiment.java",
                "src/main/java/org/nonprofitbookkeeping/ui/panels/DashboardPanelFX.java",
                "src/main/java/org/nonprofitbookkeeping/ui/ReferenceWorkspaceWindow.java",
                "src/main/java/org/nonprofitbookkeeping/ui/JournalPane.java",
                "src/main/java/org/nonprofitbookkeeping/ui/LedgerRegisterPanel.java",
                "src/main/java/org/nonprofitbookkeeping/ui/TransactionEditorPanel.java",
                "src/main/java/org/nonprofitbookkeeping/architecture/CustomerUiPanelCatalog.java",
                "src/main/java/org/nonprofitbookkeeping/architecture/CustomerPanelId.java",
                "src/main/java/org/nonprofitbookkeeping/architecture/CustomerPanelBlueprint.java",
                "src/main/java/org/nonprofitbookkeeping/ui/customer/CustomerPanelDescriptor.java",
                "src/main/java/org/nonprofitbookkeeping/ui/customer/CustomerPanelDesignService.java",
                "src/main/java/org/nonprofitbookkeeping/ui/customer/UserRole.java",
                "src/main/java/org/nonprofitbookkeeping/ui/customer/workspace/CustomerPanelDefinition.java",
                "src/main/java/org/nonprofitbookkeeping/ui/customer/workspace/CustomerPanelRegistry.java",
                "src/main/java/org/nonprofitbookkeeping/ui/customer/workspace/CustomerWorkspaceState.java",
                "src/main/java/org/nonprofitbookkeeping/ui/customer/workspace/LoginMode.java",
                "src/main/java/org/nonprofitbookkeeping/ui/customer/workspace/PanelAction.java");

        retiredSources.forEach(path -> assertFalse(Files.exists(Path.of(path)), path));
    }

    @Test
    void productionFactoryStillOwnsDashboardAndJournalRoutes() throws Exception
    {
        String factory = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/PanelFactory.java"));

        assertTrue(factory.contains("DashboardHomePanel::new"));
        assertTrue(factory.contains("JournalWorkspaceCompliancePanel::new"));
        assertFalse(factory.contains("DashboardWorkspacePanel"));
        assertFalse(factory.contains("DashboardExperiment"));
        assertFalse(factory.contains("DashboardPanelFX"));
        assertFalse(factory.contains("ReferenceWorkspaceWindow"));
        assertFalse(factory.contains("LedgerRegisterPanel"));
        assertFalse(factory.contains("TransactionEditorPanel"));
        assertFalse(factory.contains("JournalPane"));
    }

    @Test
    void retiredJournalIdsRemainCompatibilityAliases()
    {
        assertEquals(AppPanelId.JOURNAL_PANE, AppPanelId.canonical(AppPanelId.LEDGER_REGISTER));
        assertEquals(AppPanelId.JOURNAL_PANE, AppPanelId.canonical(AppPanelId.TXN_EDITOR));
    }
}
