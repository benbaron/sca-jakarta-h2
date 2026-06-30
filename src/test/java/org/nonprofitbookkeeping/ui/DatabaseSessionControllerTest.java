package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
                ignored -> { });

        controller.restorePersistedSelection();

        assertEquals(persisted.toAbsolutePath().normalize(), controller.activeDatabasePath());
    }

    @Test
    public void successfulConnectionUpdatesAndPersistsSelection()
    {
        TestStateStore store = new TestStateStore(null);
        UiSessionState sessionState = new UiSessionState();
        AtomicReference<Path> connectedPath = new AtomicReference<>();
        DatabaseSessionController controller = new DatabaseSessionController(
                sessionState,
                store,
                connectedPath::set);
        Path selected = temporaryDirectory.resolve("selected.mv.db");

        controller.connect(selected);

        Path expected = selected.toAbsolutePath().normalize();
        assertEquals(expected, connectedPath.get());
        assertEquals(expected.toString(), sessionState.databaseSelection().activeDatabasePath());
        assertEquals(expected.toString(), store.savedSelection.activeDatabasePath());
    }

    @Test
    public void failedConnectionKeepsPriorSelection()
    {
        Path original = temporaryDirectory.resolve("original.mv.db");
        DatabaseSelectionState originalState = new DatabaseSelectionState(
                original.toString(),
                List.of(original.toString()));
        TestStateStore store = new TestStateStore(originalState);
        UiSessionState sessionState = new UiSessionState();
        sessionState.setDatabaseSelection(originalState);
        DatabaseSessionController controller = new DatabaseSessionController(
                sessionState,
                store,
                ignored ->
                {
                    throw new IllegalStateException("database unavailable");
                });

        assertThrows(
                IllegalStateException.class,
                () -> controller.connect(temporaryDirectory.resolve("broken.mv.db")));

        assertEquals(original.toString(), sessionState.databaseSelection().activeDatabasePath());
        assertNull(store.savedSelection);
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

    private static final class TestStateStore implements AppStateStore
    {
        private final DatabaseSelectionState loadedSelection;
        private DatabaseSelectionState savedSelection;

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
        }

        @Override
        public void saveDatabaseSelection(DatabaseSelectionState state)
        {
            savedSelection = state;
        }
    }
}
