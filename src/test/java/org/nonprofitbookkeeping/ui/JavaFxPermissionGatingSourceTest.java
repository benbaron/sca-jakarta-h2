package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaFxPermissionGatingSourceTest
{
    @Test
    void globalCommandsDeclareAndEnforcePermissionRequirements() throws Exception
    {
        String appPanel = source("AppPanel.java");
        String host = source("PanelHost.java");
        String production = source("ProductionWorkspaceWindow.java");

        assertTrue(appPanel.contains("requiredPermission(AppCommand command)"));
        assertTrue(host.contains("activeRequiredPermission(AppCommand command)"));
        assertTrue(production.contains("panelHost.activeRequiredPermission(command)"));
        assertTrue(production.contains("UiPermissionGate.deniedExplanation(permission"));
        assertTrue(production.contains("ApplicationPermission.DATABASE_ADMIN"));
    }

    @Test
    void representativeDurablePanelsUseTheFixedPermissionPolicy() throws Exception
    {
        assertContains("JournalWorkspacePanel.java", "ApplicationPermission.BOOKKEEPING_WRITE");
        assertContains("BankingPanel.java", "ApplicationPermission.COMPANY_ADMIN");
        assertContains("CompanyAdminPanel.java", "ApplicationPermission.COMPANY_ADMIN");
        assertContains("UserAdminPanel.java", "ApplicationPermission.SECURITY_ADMIN");
        assertContains("SecurityAdminPane.java", "ApplicationPermission.SECURITY_ADMIN");
        assertContains("CompanyOwnershipDiagnosticsPanel.java", "ApplicationPermission.DATABASE_ADMIN");
        assertContains("DatabaseTransferPanel.java", "ApplicationPermission.DATABASE_ADMIN");
        assertContains("DatabaseTransferMenuInstaller.java", "ApplicationPermission.DATABASE_ADMIN");
        assertContains("SettingsPanel.java", "ApplicationPermission.UI_PREFERENCE_WRITE");
        assertContains("BankTransactionsPanel.java", "ApplicationPermission.EXPORT");
        assertContains("BankTransactionsPanel.java", "ApplicationPermission.BOOKKEEPING_WRITE");
        assertContains("ReviewedStatementAcceptanceDialog.java", "ApplicationPermission.BOOKKEEPING_WRITE");
        assertContains("ImportPreviewPanel.java", "ApplicationPermission.BOOKKEEPING_WRITE");
        assertContains("ChartOfAccountsInterchangePanel.java", "ApplicationPermission.BOOKKEEPING_WRITE");
        assertContains("ChartOfAccountsInterchangePanel.java", "ApplicationPermission.EXPORT");
        assertContains("ReportLibraryPanel.java", "ApplicationPermission.EXPORT");
        assertContains("ReconciliationRunsPanel.java", "ApplicationPermission.BOOKKEEPING_WRITE");
    }

    private static void assertContains(String filename, String token) throws Exception
    {
        assertTrue(source(filename).contains(token), filename + " must contain " + token);
    }

    private static String source(String filename) throws Exception
    {
        return Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui", filename));
    }
}
