package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source-level guardrails for reachable company, database, and user administration. */
class AdministrationNavigationSourceTest
{
    @Test
    void settingsDestinationRoutesToAdministrationHub() throws Exception
    {
        String panelFactory = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/PanelFactory.java"));
        String navigationPane = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/NavigationPane.java"));
        String administrationPanel = Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/AdministrationPanel.java"));

        assertTrue(panelFactory.contains("AppPanelId.SETTINGS, administrationFactory"));
        assertTrue(panelFactory.contains("services.companySessionController()"));
        assertTrue(panelFactory.contains("services.databaseTransferActions()"));
        assertTrue(navigationPane.contains("AppPanelId.SETTINGS, \"Administration\""));
        assertTrue(administrationPanel.contains("tab(\"Preferences\", settings)"));
        assertTrue(administrationPanel.contains("tab(\"Database Transfer\", transfers)"));
        assertTrue(administrationPanel.contains("tab(\"Company Admin\", companies)"));
        assertTrue(administrationPanel.contains(
                "tab(\"Company Ownership Diagnostics\", ownershipDiagnostics)"));
        assertTrue(administrationPanel.contains("tab(\"User Admin\", users)"));
    }

    @Test
    void companyAdminUsesPersistedLifecycleFieldsWithoutPlaceholderTabs() throws Exception
    {
        String companyPanel = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/CompanyAdminPanel.java"));
        String service = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/service/CompanyAdminService.java"));
        String workspace = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/ProductionWorkspaceWindow.java"));

        assertTrue(companyPanel.contains("new CompanyCommand("));
        assertTrue(companyPanel.contains("fiscalMonth"));
        assertTrue(companyPanel.contains("fiscalDay"));
        assertTrue(companyPanel.contains("defaultCurrency"));
        assertTrue(companyPanel.contains("companyController.select"));
        assertTrue(companyPanel.contains("split.setOrientation(Orientation.VERTICAL)"));
        assertTrue(!companyPanel.contains("taxPlaceholder"));
        assertTrue(!companyPanel.contains("tab(\"Chart of Accounts\""));
        assertTrue(!companyPanel.contains("tab(\"Reporting Defaults\""));
        assertTrue(service.contains("At least one company must remain active"));
        assertTrue(service.contains("Select another active company before deactivating"));
        assertTrue(workspace.contains("activeCompanySelector"));
        assertTrue(workspace.contains("panelHost.refreshOpenPanels()"));
    }

    @Test
    void ownershipDiagnosticsExposeExplicitAuditedRepairWithoutBulkGuessing() throws Exception
    {
        String panel = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/CompanyOwnershipDiagnosticsPanel.java"));
        String service = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/service/CompanyOwnershipService.java"));

        assertTrue(panel.contains("ownershipDiagnosticsTable"));
        assertTrue(panel.contains("ownershipDiagnosticsTargetCompany"));
        assertTrue(panel.contains("ownershipDiagnosticsActor"));
        assertTrue(panel.contains("ownershipDiagnosticsReason"));
        assertTrue(panel.contains("Assign Owner…"));
        assertTrue(panel.contains("No accounting reference is changed automatically"));
        assertTrue(service.contains("COMPANY_OWNERSHIP_ASSIGNED"));
        assertTrue(service.contains("PESSIMISTIC_WRITE"));
        assertTrue(!panel.contains("Assign All"));
    }
}
