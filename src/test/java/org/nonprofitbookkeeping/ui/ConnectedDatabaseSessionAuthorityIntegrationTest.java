package org.nonprofitbookkeeping.ui;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.CompanyAdminService;
import org.nonprofitbookkeeping.service.DiagnosticsQueryService;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock("ui-service-registry")
class ConnectedDatabaseSessionAuthorityIntegrationTest
{
    @Test
    void successfulSwitchAndFailedTargetKeepRuntimeSessionAndDiagnosticsAligned(@TempDir Path tempDir)
    {
        Path source = tempDir.resolve("source.mv.db").toAbsolutePath().normalize();
        Path target = tempDir.resolve("target.mv.db").toAbsolutePath().normalize();
        Path invalid = tempDir.resolve("invalid-no-active-company.mv.db").toAbsolutePath().normalize();
        createSingleActiveCompanyDatabase(source, "SOURCE");
        createSingleActiveCompanyDatabase(target, "TARGET");
        createDatabaseWithNoActiveCompany(invalid);

        UiSessionState session = MainWindow.sharedSessionState();
        DatabaseSelectionState originalDatabase = session.databaseSelection();
        MultiCompanyState originalCompany = session.multiCompany();
        FileAppStateStore store = new FileAppStateStore(tempDir.resolve("session.properties"));

        try
        {
            session.setDatabaseSelection(new DatabaseSelectionState(source.toString(), List.of(source.toString())));
            session.setMultiCompany(new MultiCompanyState("SOURCE", List.of("SOURCE")));
            UiServiceRegistry.reconnectToDatabase(source);

            DatabaseSessionController controller = new DatabaseSessionController(
                    session,
                    store,
                    UiServiceRegistry::prepareDatabaseConnection);

            DatabaseSessionController.ConnectionResult switched = controller.connect(target);

            assertEquals(target, switched.activeDatabasePath());
            assertEquals("TARGET", switched.activeCompanyCode());
            assertEquals(target.toString(), session.databaseSelection().activeDatabasePath());
            assertEquals("TARGET", session.multiCompany().activeCompanyCode());
            assertTrue(UiServiceRegistry.companyAdmin().findCompany("TARGET").isPresent());
            assertFalse(UiServiceRegistry.companyAdmin().findCompany("SOURCE").isPresent());

            DiagnosticsQueryService.Report report = UiServiceRegistry.diagnosticsQuery().query();
            assertTrue(report.available());
            assertEquals(target, report.activeDatabasePath());
            assertEquals("TARGET", report.activeCompanyCode());

            assertThrows(IllegalStateException.class, () -> controller.connect(invalid));

            assertEquals(target.toString(), session.databaseSelection().activeDatabasePath());
            assertEquals("TARGET", session.multiCompany().activeCompanyCode());
            assertTrue(UiServiceRegistry.companyAdmin().findCompany("TARGET").isPresent());
            assertFalse(UiServiceRegistry.companyAdmin().findCompany("SOURCE").isPresent());
            DiagnosticsQueryService.Report afterFailure = UiServiceRegistry.diagnosticsQuery().query();
            assertTrue(afterFailure.available());
            assertEquals(target, afterFailure.activeDatabasePath());
            assertEquals("TARGET", afterFailure.activeCompanyCode());
        }
        finally
        {
            session.setDatabaseSelection(originalDatabase);
            session.setMultiCompany(originalCompany);
            try
            {
                UiServiceRegistry.reconnectToDatabase(Path.of(originalDatabase.activeDatabasePath()));
            }
            catch (RuntimeException ignored)
            {
                // Another isolated UI test will establish its own disposable registry state.
            }
        }
    }

    private static void createSingleActiveCompanyDatabase(Path database, String code)
    {
        try (Jpa jpa = new Jpa(database))
        {
            CompanyAdminService service = new CompanyAdminService(jpa);
            service.createCompany(code, code + " Company");
            setAllCompaniesInactiveExcept(jpa, code);
        }
    }

    private static void createDatabaseWithNoActiveCompany(Path database)
    {
        try (Jpa jpa = new Jpa(database); EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createQuery("update Company c set c.active = false").executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static void setAllCompaniesInactiveExcept(Jpa jpa, String activeCode)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createQuery("update Company c set c.active = false where c.code <> :code")
                    .setParameter("code", activeCode)
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }
}
