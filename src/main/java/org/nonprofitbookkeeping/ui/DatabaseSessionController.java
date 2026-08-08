package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.persistence.DatabaseLocationService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Coordinates the connected database, authoritative company selection, runtime
 * service composition, and persisted shell state.
 *
 * <p>A target database is prepared and validated before any visible or persisted
 * session authority changes. The prepared connection becomes active only after
 * the target company state and shell persistence are known to be valid.</p>
 */
final class DatabaseSessionController
{
    /**
     * A validated target database that owns all not-yet-active runtime resources.
     * {@link #activate()} must not throw; preparation is the failure boundary.
     */
    interface PreparedConnection extends AutoCloseable
    {
        Path databasePath();

        String activeCompanyCode();

        List<String> activeCompanyCodes();

        void activate();

        @Override
        void close();
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

    ConnectionResult connect(Path databaseFile)
    {
        Objects.requireNonNull(databaseFile, "databaseFile");
        Path previousPath = activeDatabasePath();
        Path resolved = DatabaseLocationService.resolveDatabasePath(databaseFile.toString());
        DatabaseSelectionState previousDatabase = sessionState.databaseSelection();
        MultiCompanyState previousCompany = sessionState.multiCompany();

        try (PreparedConnection prepared = connector.prepare(
                resolved,
                previousCompany.activeCompanyCode()))
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

            // FileAppStateStore persists these two selection facts in one write.
            // If persistence fails, the prepared services are closed and the
            // currently active session remains untouched.
            stateStore.saveDatabaseSession(targetDatabase, targetCompany);

            prepared.activate();
            sessionState.setDatabaseSelection(targetDatabase);
            sessionState.setMultiCompany(targetCompany);

            return new ConnectionResult(
                    previousPath,
                    resolved,
                    targetCompany.activeCompanyCode(),
                    !previousPath.equals(resolved));
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
