package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.persistence.DatabaseLocationService;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Coordinates validated connected-database activation and session authority. */
final class DatabaseSessionController
{
    interface PreparedConnection extends AutoCloseable
    {
        Path databasePath();
        String activeCompanyCode();
        List<String> activeCompanyCodes();
        void activate();
        @Override void close();
    }

    @FunctionalInterface
    interface Connector
    {
        PreparedConnection prepare(Path databaseFile, String preferredCompanyCode);
    }

    record ConnectionResult(
            Path previousDatabasePath,
            Path activeDatabasePath,
            String activeCompanyCode,
            boolean databaseChanged)
    {
    }

    private final UiSessionState sessionState;
    private final AppStateStore stateStore;
    private final Connector connector;
    private final Consumer<AuthenticatedUserSession> successfulDatabaseSwitchLogoutRecorder;

    DatabaseSessionController(
            UiSessionState sessionState,
            AppStateStore stateStore,
            Connector connector)
    {
        this(sessionState, stateStore, connector, ignored -> { });
    }

    DatabaseSessionController(
            UiSessionState sessionState,
            AppStateStore stateStore,
            Connector connector,
            Consumer<AuthenticatedUserSession> successfulDatabaseSwitchLogoutRecorder)
    {
        this.sessionState = Objects.requireNonNull(sessionState, "sessionState");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.connector = Objects.requireNonNull(connector, "connector");
        this.successfulDatabaseSwitchLogoutRecorder = Objects.requireNonNull(
                successfulDatabaseSwitchLogoutRecorder, "successfulDatabaseSwitchLogoutRecorder");
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

    ConnectionResult connect(Path databaseFile)
    {
        Objects.requireNonNull(databaseFile, "databaseFile");
        Path previousPath = activeDatabasePath();
        Path resolved = DatabaseLocationService.resolveDatabasePath(databaseFile.toString());
        DatabaseSelectionState previousDatabase = sessionState.databaseSelection();
        MultiCompanyState previousCompany = sessionState.multiCompany();

        try (PreparedConnection prepared = connector.prepare(resolved, previousCompany.activeCompanyCode()))
        {
            Path preparedPath = DatabaseLocationService.resolveDatabasePath(
                    Objects.requireNonNull(prepared.databasePath(), "prepared database path").toString());
            if (!resolved.equals(preparedPath))
            {
                throw new IllegalStateException(
                        "Prepared database path does not match requested target. Requested "
                                + resolved + " but prepared " + preparedPath + ".");
            }

            MultiCompanyState targetCompany = companyState(
                    prepared.activeCompanyCode(),
                    prepared.activeCompanyCodes(),
                    previousCompany.recentCompanyCodes());
            DatabaseSelectionState targetDatabase = databaseState(
                    resolved,
                    previousDatabase.recentDatabasePaths());
            boolean databaseChanged = !previousPath.equals(resolved);

            stateStore.saveDatabaseSession(targetDatabase, targetCompany);

            if (databaseChanged)
            {
                sessionState.authenticatedUser().ifPresent(session ->
                {
                    try
                    {
                        successfulDatabaseSwitchLogoutRecorder.accept(session);
                    }
                    catch (RuntimeException ex)
                    {
                        System.err.println("[NPBK] Could not record pre-switch logout event: " + ex.getMessage());
                    }
                });
            }

            prepared.activate();
            sessionState.setDatabaseSelection(targetDatabase);
            sessionState.setMultiCompany(targetCompany);
            if (databaseChanged)
            {
                sessionState.clearAuthenticatedUser();
            }

            return new ConnectionResult(previousPath, resolved, targetCompany.activeCompanyCode(), databaseChanged);
        }
    }

    private static DatabaseSelectionState databaseState(Path selected, List<String> previousRecents)
    {
        String selectedPath = selected.toString();
        List<String> recents = new ArrayList<>();
        recents.add(selectedPath);
        for (String recent : previousRecents == null ? List.<String>of() : previousRecents)
        {
            if (recent == null || recent.isBlank())
            {
                continue;
            }
            Path normalized = DatabaseLocationService.resolveDatabasePath(recent);
            String value = normalized.toString();
            if (!recents.contains(value))
            {
                recents.add(value);
            }
        }
        return new DatabaseSelectionState(selectedPath, List.copyOf(recents));
    }

    private static MultiCompanyState companyState(
            String selectedCompany,
            List<String> activeCompanies,
            List<String> previousRecents)
    {
        String selected = requireText(selectedCompany, "active company");
        List<String> active = activeCompanies == null ? List.of() : activeCompanies.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .toList();
        String canonicalSelected = active.stream()
                .filter(code -> code.equalsIgnoreCase(selected))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Prepared database did not expose its resolved active company " + selected + "."));

        List<String> recents = new ArrayList<>();
        recents.add(canonicalSelected);
        for (String recent : previousRecents == null ? List.<String>of() : previousRecents)
        {
            active.stream()
                    .filter(code -> recent != null && code.equalsIgnoreCase(recent))
                    .findFirst()
                    .filter(code -> recents.stream().noneMatch(existing -> existing.equalsIgnoreCase(code)))
                    .ifPresent(recents::add);
        }
        for (String code : active)
        {
            if (recents.stream().noneMatch(existing -> existing.equalsIgnoreCase(code)))
            {
                recents.add(code);
            }
        }
        return new MultiCompanyState(canonicalSelected, List.copyOf(recents));
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalStateException(label + " is required for a prepared database session.");
        }
        return value.strip();
    }
}
