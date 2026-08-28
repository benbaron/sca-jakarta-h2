package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source guardrails for the single connected-database session authority. */
class ConnectedDatabaseSessionAuthoritySourceTest
{
    @Test
    void settingsCannotWriteDatabaseSelection() throws Exception
    {
        String source = read("SettingsPanel.java");

        assertTrue(source.contains("private final TextField activeDatabase"));
        assertTrue(source.contains("activeDatabase.setEditable(false)"));
        assertTrue(source.contains("Connected database file"));
        assertFalse(source.contains("session.setDatabaseSelection("));
        assertFalse(source.contains("readDatabaseSelection()"));
    }

    @Test
    void productionSwitchUsesPreparedAuthorityDirtyGuardAndFailurePreservation() throws Exception
    {
        String source = read("ProductionWorkspaceWindow.java");
        String method = source.substring(
                source.indexOf("private void connectDatabase(Path databaseFile)"),
                source.indexOf("private Optional<Path> chooseOpenDatabaseFile()"));

        assertTrue(source.contains("UiServiceRegistry::prepareDatabaseConnection"));
        assertTrue(method.contains("panelHost.dirtyPanelTitles()"));
        assertTrue(method.contains("databaseChangePrompt.confirmDiscard"));
        assertTrue(method.contains("databaseSessionController.connect(target)"));
        assertTrue(method.contains("Still connected to"));
        assertTrue(method.contains("workspaceContext.setDatabaseFailure(previousFailure)"));
        assertFalse(method.contains("companySessionController.restoreAuthoritativeSelection()"));
        assertFalse(method.contains("showRecoveryDashboard(ex)"));
    }

    @Test
    void serviceRegistryPreparesMigrationServicesAndCompanyBeforeActivation() throws Exception
    {
        String source = read("UiServiceRegistry.java");
        String method = source.substring(
                source.indexOf("static DatabaseSessionController.PreparedConnection prepareDatabaseConnection"),
                source.indexOf("public static void reconnectToDatabase"));

        assertTrue(method.contains("new Jpa(resolved)"));
        assertTrue(method.contains("buildServices(nextJpa)"));
        assertTrue(method.contains("resolveActiveCompany(preferredCompanyCode)"));
        assertTrue(method.contains("listActiveCompanyViews()"));
        assertTrue(source.contains("private static final class PreparedServiceBundle"));
    }

    @Test
    void retiredMainWindowCannotWriteDatabaseAuthority() throws Exception
    {
        String source = read("MainWindow.java");

        assertTrue(source.contains("ApplicationSessionContext.sharedSessionState()"));
        assertFalse(source.contains("DatabaseSessionController"));
        assertFalse(source.contains("setDatabaseSelection("));
        assertFalse(source.contains("reconnectToDatabase("));
    }

    private static String read(String fileName) throws Exception
    {
        return Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/" + fileName));
    }
}
