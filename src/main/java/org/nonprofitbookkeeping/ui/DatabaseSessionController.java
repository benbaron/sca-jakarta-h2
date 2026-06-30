package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.persistence.DatabaseLocationService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Coordinates the selected database, runtime reconnection, and persisted UI
 * state. The selected path is changed only after a connection succeeds.
 */
final class DatabaseSessionController
{
    @FunctionalInterface
    interface Connector
    {
        void connect(Path databaseFile);
    }

    private final UiSessionState sessionState;
    private final AppStateStore stateStore;
    private final Connector connector;

    DatabaseSessionController(
            UiSessionState sessionState,
            AppStateStore stateStore,
            Connector connector)
    {
        this.sessionState = Objects.requireNonNull(sessionState, "sessionState");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.connector = Objects.requireNonNull(connector, "connector");
    }

    void restorePersistedSelection()
    {
        stateStore.loadDatabaseSelection().ifPresent(sessionState::setDatabaseSelection);
    }

    Path activeDatabasePath()
    {
        return DatabaseLocationService.resolveDatabasePath(
                sessionState.databaseSelection().activeDatabasePath());
    }

    void connect(Path databaseFile)
    {
        Objects.requireNonNull(databaseFile, "databaseFile");
        Path resolved = DatabaseLocationService.resolveDatabasePath(databaseFile.toString());

        connector.connect(resolved);

        String selected = resolved.toString();
        List<String> recents = new ArrayList<>(
                sessionState.databaseSelection().recentDatabasePaths());
        recents.remove(selected);
        recents.add(0, selected);

        DatabaseSelectionState next = new DatabaseSelectionState(selected, recents);
        sessionState.setDatabaseSelection(next);
        stateStore.saveDatabaseSelection(next);
    }
}
