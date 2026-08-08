package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WorkspaceCompositionTest
{
    @Test
    public void workspaceContextFollowsSessionDatabaseCompanyAndPeriod()
    {
        UiSessionState session = new UiSessionState();
        session.setDatabaseSelection(new DatabaseSelectionState("data/original.mv.db", List.of("data/original.mv.db")));
        session.setMultiCompany(new MultiCompanyState("OLD", List.of("OLD")));
        ActivePeriodContext.set(LocalDate.of(2026, 1, 31));

        WorkspaceServices services = WorkspaceServicesFactory.create(
                session,
                new NoopAppStateStore(),
                WorkspaceCompositionTest::preparedConnection);
        WorkspaceContext context = services.context();

        session.setDatabaseSelection(new DatabaseSelectionState("data/next.mv.db", List.of("data/next.mv.db")));
        session.setMultiCompany(new MultiCompanyState("NEW", List.of("NEW")));
        ActivePeriodContext.set(LocalDate.of(2026, 2, 28));

        assertEquals(Path.of("data/next.mv.db").toAbsolutePath().normalize(), context.activeDatabasePath());
        assertEquals("NEW", context.activeCompanyCode());
        assertEquals(LocalDate.of(2026, 2, 28), context.activePeriodDate());
    }

    @Test
    public void panelHostUsesLifecycleOwnedPanelFactory()
    {
        UiSessionState session = new UiSessionState();
        session.setDatabaseSelection(new DatabaseSelectionState("data/sca-ledger.mv.db", List.of("data/sca-ledger.mv.db")));
        session.setMultiCompany(new MultiCompanyState("SCA", List.of("SCA")));

        WorkspaceServices services = WorkspaceServicesFactory.create(
                session,
                new NoopAppStateStore(),
                WorkspaceCompositionTest::preparedConnection);
        FxTestSupport.onFx(() -> {
            PanelHost host = new PanelHost(services.panelFactory());
            host.show(AppPanelId.HELP);

            assertEquals(AppPanelId.HELP, host.activePanelId());
            assertFalse(host.isClosable(AppPanelId.DASHBOARD));
            return null;
        });
        assertTrue(services.panelFactory().supportedPanelIds().contains(AppPanelId.DASHBOARD));
    }

    @Test
    public void databaseControllerUpdatesContextAfterSuccessfulConnection()
    {
        UiSessionState session = new UiSessionState();
        session.setDatabaseSelection(new DatabaseSelectionState("data/original.mv.db", List.of("data/original.mv.db")));
        session.setMultiCompany(new MultiCompanyState("SCA", List.of("SCA")));
        AtomicReference<Path> connected = new AtomicReference<>();

        WorkspaceServices services = WorkspaceServicesFactory.create(
                session,
                new NoopAppStateStore(),
                (path, preferred) ->
                {
                    connected.set(path);
                    return preparedConnection(path, preferred);
                });

        services.databaseSessionController().connect(Path.of("data/connected.mv.db"));

        assertEquals(Path.of("data/connected.mv.db").toAbsolutePath().normalize(), connected.get());
        assertEquals(Path.of("data/connected.mv.db").toAbsolutePath().normalize(), services.context().activeDatabasePath());
    }


    private static DatabaseSessionController.PreparedConnection preparedConnection(Path path, String preferred)
    {
        String company = preferred == null || preferred.isBlank() ? "DEFAULT" : preferred;
        Path resolved = path.toAbsolutePath().normalize();
        return new DatabaseSessionController.PreparedConnection()
        {
            @Override
            public Path databasePath()
            {
                return resolved;
            }

            @Override
            public String activeCompanyCode()
            {
                return company;
            }

            @Override
            public List<String> activeCompanyCodes()
            {
                return List.of(company);
            }

            @Override
            public void activate()
            {
            }

            @Override
            public void close()
            {
            }
        };
    }

    private static final class NoopAppStateStore implements AppStateStore
    {
        @Override
        public Optional<AppPreferencesState> loadPreferences()
        {
            return Optional.empty();
        }

        @Override
        public Optional<MultiCompanyState> loadMultiCompany()
        {
            return Optional.empty();
        }

        @Override
        public void savePreferences(AppPreferencesState state)
        {
        }

        @Override
        public void saveMultiCompany(MultiCompanyState state)
        {
        }

        @Override
        public Optional<DatabaseSelectionState> loadDatabaseSelection()
        {
            return Optional.empty();
        }

        @Override
        public void saveDatabaseSelection(DatabaseSelectionState state)
        {
        }

        @Override
        public List<org.nonprofitbookkeeping.model.ViewPresetState> loadViewPresets()
        {
            return List.of();
        }

        @Override
        public void saveViewPresets(List<org.nonprofitbookkeeping.model.ViewPresetState> presets)
        {
        }
    }
}
