package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DatabaseSessionControllerTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    public void restoresPersistedDatabaseSelection()
    {
        Path persisted = temporaryDirectory.resolve("persisted.mv.db");
        TestStateStore store = new TestStateStore(
                new DatabaseSelectionState(persisted.toString(), List.of(persisted.toString())));
        UiSessionState sessionState = new UiSessionState();
        DatabaseSessionController controller = new DatabaseSessionController(
                sessionState,
                store,
                (path, preferred) -> prepared(path, preferred, List.of(preferred)));

        controller.restorePersistedSelection();

        assertEquals(persisted.toAbsolutePath().normalize(), controller.activeDatabasePath());
    }

    @Test
    public void successfulConnectionPublishesValidatedDatabaseAndCompanyTogether()
    {
        TestStateStore store = new TestStateStore(null);
        UiSessionState sessionState = new UiSessionState();
        sessionState.setMultiCompany(new MultiCompanyState("OLD", List.of("OLD", "TARGET", "STALE")));
        AtomicReference<FakePreparedConnection> prepared = new AtomicReference<>();
        DatabaseSessionController controller = new DatabaseSessionController(
                sessionState,
                store,
                (path, preferred) -> {
                    FakePreparedConnection connection = prepared(path, "TARGET", List.of("TARGET", "OTHER"));
                    prepared.set(connection);
                    return connection;
                });
        Path selected = temporaryDirectory.resolve("selected.mv.db");

        DatabaseSessionController.ConnectionResult result = controller.connect(selected);

        Path expected = selected.toAbsolutePath().normalize();
        assertEquals(expected, result.activeDatabasePath());
        assertEquals("TARGET", result.activeCompanyCode());
        assertTrue(result.databaseChanged());
        assertEquals(expected.toString(), sessionState.databaseSelection().activeDatabasePath());
        assertEquals("TARGET", sessionState.multiCompany().activeCompanyCode());
        assertEquals(List.of("TARGET", "OTHER"), sessionState.multiCompany().recentCompanyCodes());
        assertEquals(expected.toString(), store.savedSelection.activeDatabasePath());
        assertEquals("TARGET", store.savedCompany.activeCompanyCode());
        assertEquals(1, store.databaseSessionSaveCount.get());
        assertEquals(1, prepared.get().activationCount.get());
    }

    @Test
    public void failedPreparationKeepsPriorDatabaseCompanyAndPersistedState()
    {
        Path original = temporaryDirectory.resolve("original.mv.db").toAbsolutePath().normalize();
        DatabaseSelectionState originalDatabase = new DatabaseSelectionState(
                original.toString(),
                List.of(original.toString()));
        MultiCompanyState originalCompany = new MultiCompanyState("SOURCE", List.of("SOURCE"));
        TestStateStore store = new TestStateStore(originalDatabase);
        UiSessionState sessionState = new UiSessionState();
        sessionState.setDatabaseSelection(originalDatabase);
        sessionState.setMultiCompany(originalCompany);
        DatabaseSessionController controller = new DatabaseSessionController(
                sessionState,
                store,
                (path, preferred) -> {
                    throw new IllegalStateException("database unavailable");
                });

        assertThrows(
                IllegalStateException.class,
                () -> controller.connect(temporaryDirectory.resolve("broken.mv.db")));

        assertEquals(originalDatabase, sessionState.databaseSelection());
        assertEquals(originalCompany, sessionState.multiCompany());
        assertNull(store.savedSelection);
        assertNull(store.savedCompany);
        assertEquals(0, store.databaseSessionSaveCount.get());
    }

    @Test
    public void failedStatePersistenceDoesNotActivatePreparedServicesOrChangeSession()
    {
        Path original = temporaryDirectory.resolve("original.mv.db").toAbsolutePath().normalize();
        DatabaseSelectionState originalDatabase = new DatabaseSelectionState(original.toString(), List.of(original.toString()));
        MultiCompanyState originalCompany = new MultiCompanyState("SOURCE", List.of("SOURCE"));
        TestStateStore store = new TestStateStore(originalDatabase);
        store.failDatabaseSessionSave = true;
        UiSessionState sessionState = new UiSessionState();
        sessionState.setDatabaseSelection(originalDatabase);
        sessionState.setMultiCompany(originalCompany);
        AtomicReference<FakePreparedConnection> prepared = new AtomicReference<>();
        DatabaseSessionController controller = new DatabaseSessionController(
                sessionState,
                store,
                (path, preferred) -> {
                    FakePreparedConnection connection = prepared(path, "TARGET", List.of("TARGET"));
                    prepared.set(connection);
                    return connection;
                });

        assertThrows(
                IllegalStateException.class,
                () -> controller.connect(temporaryDirectory.resolve("target.mv.db")));

        assertEquals(originalDatabase, sessionState.databaseSelection());
        assertEquals(originalCompany, sessionState.multiCompany());
        assertEquals(0, prepared.get().activationCount.get());
        assertTrue(prepared.get().closed);
    }

    @Test
    public void rejectsPreparedPathMismatchBeforePublishingState()
    {
        UiSessionState sessionState = new UiSessionState();
        TestStateStore store = new TestStateStore(null);
        Path other = temporaryDirectory.resolve("other.mv.db");
        DatabaseSessionController controller = new DatabaseSessionController(
                sessionState,
                store,
                (path, preferred) -> prepared(other, "DEFAULT", List.of("DEFAULT")));

        assertThrows(
                IllegalStateException.class,
                () -> controller.connect(temporaryDirectory.resolve("target.mv.db")));

        assertEquals(0, store.databaseSessionSaveCount.get());
    }

    @Test
    public void rejectsPreparedCompanyThatIsNotActiveInTarget()
    {
        UiSessionState sessionState = new UiSessionState();
        TestStateStore store = new TestStateStore(null);
        DatabaseSessionController controller = new DatabaseSessionController(
                sessionState,
                store,
                (path, preferred) -> prepared(path, "MISSING", List.of("OTHER")));

        assertThrows(
                IllegalStateException.class,
                () -> controller.connect(temporaryDirectory.resolve("target.mv.db")));

        assertEquals(0, store.databaseSessionSaveCount.get());
    }

    @Test
    public void newDatabasePathUsesH2FileSuffix()
    {
        Path selected = temporaryDirectory.resolve("new-ledger");

        assertEquals(
                Path.of(selected.toString() + ".mv.db"),
                ProductionWorkspaceWindow.normalizeNewDatabasePath(selected));
        assertEquals(
                temporaryDirectory.resolve("existing.mv.db"),
                ProductionWorkspaceWindow.normalizeNewDatabasePath(
                        temporaryDirectory.resolve("existing.mv.db")));
    }

    private static FakePreparedConnection prepared(Path path, String company, List<String> activeCompanies)
    {
        return new FakePreparedConnection(path.toAbsolutePath().normalize(), company, activeCompanies);
    }

    private static final class FakePreparedConnection implements DatabaseSessionController.PreparedConnection
    {
        private final Path path;
        private final String company;
        private final List<String> activeCompanies;
        private final AtomicInteger activationCount = new AtomicInteger();
        private boolean closed;

        private FakePreparedConnection(Path path, String company, List<String> activeCompanies)
        {
            this.path = path;
            this.company = company;
            this.activeCompanies = List.copyOf(activeCompanies);
        }

        @Override
        public Path databasePath()
        {
            return path;
        }

        @Override
        public String activeCompanyCode()
        {
            return company;
        }

        @Override
        public List<String> activeCompanyCodes()
        {
            return activeCompanies;
        }

        @Override
        public void activate()
        {
            activationCount.incrementAndGet();
        }

        @Override
        public void close()
        {
            closed = true;
        }
    }

    private static final class TestStateStore implements AppStateStore
    {
        private final DatabaseSelectionState loadedSelection;
        private DatabaseSelectionState savedSelection;
        private MultiCompanyState savedCompany;
        private final AtomicInteger databaseSessionSaveCount = new AtomicInteger();
        private boolean failDatabaseSessionSave;

        private TestStateStore(DatabaseSelectionState loadedSelection)
        {
            this.loadedSelection = loadedSelection;
        }

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
        public Optional<DatabaseSelectionState> loadDatabaseSelection()
        {
            return Optional.ofNullable(loadedSelection);
        }

        @Override
        public void savePreferences(AppPreferencesState state)
        {
        }

        @Override
        public void saveMultiCompany(MultiCompanyState state)
        {
            savedCompany = state;
        }

        @Override
        public void saveDatabaseSelection(DatabaseSelectionState state)
        {
            savedSelection = state;
        }

        @Override
        public void saveDatabaseSession(DatabaseSelectionState database, MultiCompanyState company)
        {
            if (failDatabaseSessionSave)
            {
                throw new IllegalStateException("state store unavailable");
            }
            databaseSessionSaveCount.incrementAndGet();
            savedSelection = database;
            savedCompany = company;
        }
    }
}
