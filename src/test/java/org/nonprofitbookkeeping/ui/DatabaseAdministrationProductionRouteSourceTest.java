package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseAdministrationProductionRouteSourceTest
{
    @Test
    void productionDatabaseAdministrationUsesCurrentBundleGuard() throws Exception
    {
        String registry = compact(read("src/main/java/org/nonprofitbookkeeping/ui/UiServiceRegistry.java"));
        String factory = compact(read("src/main/java/org/nonprofitbookkeeping/ui/WorkspaceServicesFactory.java"));
        String coordinator = compact(read("src/main/java/org/nonprofitbookkeeping/ui/DatabaseTransferCoordinator.java"));

        assertTrue(registry.contains(
                "newCompanyOwnershipService(current.jpa(),current.authorizationGuard())"));
        assertTrue(registry.contains(
                "newSampleCompanyService(jpa,authorizationGuard)"));
        assertTrue(registry.contains(
                "newDatabaseAdministrationService(transferService,()->services().authorizationGuard())"));
        assertTrue(factory.contains(
                "UiServiceRegistry.databaseAdministration(databaseTransferService)"));
        assertTrue(coordinator.contains("privatefinalDatabaseAdministrationServicetransferService"));
        assertFalse(coordinator.contains("privatefinalDatabaseTransferServicetransferService"));
    }

    @Test
    void preLoginDatabaseSelectionRemainsOutsideDatabaseAdminBoundary() throws Exception
    {
        String window = compact(read("src/main/java/org/nonprofitbookkeeping/ui/ProductionWorkspaceWindow.java"));
        assertTrue(window.contains("executeDatabaseRecoveryCommand(DatabaseRecoveryCommand.SELECT_EXISTING)"));
        assertTrue(window.contains("executeDatabaseRecoveryCommand(DatabaseRecoveryCommand.CREATE_NEW)"));
        assertTrue(window.contains("executeDatabaseRecoveryCommand(DatabaseRecoveryCommand.RETRY_CURRENT)"));
    }

    @Test
    void ownershipRepairActorIsAuthenticatedPresentationOnly() throws Exception
    {
        String panel = read("src/main/java/org/nonprofitbookkeeping/ui/CompanyOwnershipDiagnosticsPanel.java");
        assertTrue(panel.contains("new TextField(DesktopActorIdentity.current())"));
        assertTrue(panel.contains("actor.setEditable(false)"));
        assertFalse(panel.contains("new TextField(\"ui-operator\")"));
    }

    private static String read(String path) throws Exception
    {
        return Files.readString(Path.of(path));
    }

    private static String compact(String source)
    {
        return source.replaceAll("\\s+", "");
    }
}
