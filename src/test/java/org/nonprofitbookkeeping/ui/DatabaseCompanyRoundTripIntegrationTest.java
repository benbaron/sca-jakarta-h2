package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.CompanyAdminService;
import org.nonprofitbookkeeping.service.CompanyCommand;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DatabaseCompanyRoundTripIntegrationTest
{
    @Test
    void recentCompanyStateNeverCreatesOrSelectsAFictionalCompany(@TempDir Path tempDir)
    {
        Path database = tempDir.resolve("company-round-trip");
        Path stateFile = tempDir.resolve("ui-state.properties");
        FileAppStateStore store = new FileAppStateStore(stateFile);

        try (Jpa jpa = new Jpa(database))
        {
            CompanyAdminService service = new CompanyAdminService(jpa);
            UiSessionState session = new UiSessionState();
            CompanySessionController controller = new CompanySessionController(session, store, () -> service);

            controller.createAndSelect(new CompanyCommand(
                    null, "COMPANY-A", "Company A", "Company A", null, null, true, 1, 1, "USD"));
            assertEquals("COMPANY-A", session.multiCompany().activeCompanyCode());
            assertEquals("COMPANY-A", store.loadMultiCompany().orElseThrow().activeCompanyCode());
        }

        store.saveMultiCompany(new MultiCompanyState(
                "FICTIONAL",
                List.of("FICTIONAL", "COMPANY-A", "ALSO-FICTIONAL")));

        try (Jpa jpa = new Jpa(database))
        {
            CompanyAdminService service = new CompanyAdminService(jpa);
            UiSessionState restartedSession = new UiSessionState();
            CompanySessionController restarted = new CompanySessionController(
                    restartedSession,
                    store,
                    () -> service);

            restarted.restoreAuthoritativeSelection();

            String selected = restartedSession.multiCompany().activeCompanyCode();
            service.requireActiveCompany(selected);
            assertFalse(restartedSession.multiCompany().recentCompanyCodes().contains("FICTIONAL"));
            assertFalse(restartedSession.multiCompany().recentCompanyCodes().contains("ALSO-FICTIONAL"));
        }
    }
}
