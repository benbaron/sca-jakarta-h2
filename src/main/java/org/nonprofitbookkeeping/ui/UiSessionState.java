package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.model.UiThemePreference;
import org.nonprofitbookkeeping.model.UserPrivilegeLevel;
import org.nonprofitbookkeeping.persistence.DatabaseLocationService;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** In-memory shell/session state; credential material is never stored here. */
public class UiSessionState
{
    private AppPreferencesState preferences = new AppPreferencesState(
            UiThemePreference.SYSTEM_DEFAULT,
            false,
            true,
            UserPrivilegeLevel.ACCOUNTANT);
    private MultiCompanyState multiCompany = new MultiCompanyState("DEFAULT", List.of("DEFAULT"));
    private DatabaseSelectionState databaseSelection = defaultDatabaseSelection();
    private AuthenticatedUserSession authenticatedUser;

    private final List<Consumer<AppPreferencesState>> preferenceListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<MultiCompanyState>> companyListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<DatabaseSelectionState>> databaseListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Optional<AuthenticatedUserSession>>> authenticationListeners =
            new CopyOnWriteArrayList<>();

    private static DatabaseSelectionState defaultDatabaseSelection()
    {
        String path = DatabaseLocationService.defaultUserDatabasePath().toString();
        return new DatabaseSelectionState(path, List.of(path));
    }

    public AppPreferencesState preferences()
    {
        return preferences;
    }

    public MultiCompanyState multiCompany()
    {
        return multiCompany;
    }

    public DatabaseSelectionState databaseSelection()
    {
        return databaseSelection;
    }

    public Optional<AuthenticatedUserSession> authenticatedUser()
    {
        return Optional.ofNullable(authenticatedUser);
    }

    public boolean isAuthenticated()
    {
        return authenticatedUser != null;
    }

    public void setPreferences(AppPreferencesState next)
    {
        this.preferences = next;
        preferenceListeners.forEach(listener -> listener.accept(next));
    }

    public void setMultiCompany(MultiCompanyState next)
    {
        this.multiCompany = next;
        companyListeners.forEach(listener -> listener.accept(next));
    }

    public void setDatabaseSelection(DatabaseSelectionState next)
    {
        this.databaseSelection = next;
        databaseListeners.forEach(listener -> listener.accept(next));
    }

    public void setAuthenticatedUser(AuthenticatedUserSession next)
    {
        this.authenticatedUser = next;
        notifyAuthenticationChanged();
    }

    public Optional<AuthenticatedUserSession> clearAuthenticatedUser()
    {
        AuthenticatedUserSession previous = authenticatedUser;
        authenticatedUser = null;
        notifyAuthenticationChanged();
        return Optional.ofNullable(previous);
    }

    public void touchAuthenticatedActivity(Instant at)
    {
        if (authenticatedUser == null)
        {
            return;
        }
        authenticatedUser = authenticatedUser.withActivity(at);
    }

    public void onPreferencesChanged(Consumer<AppPreferencesState> listener)
    {
        preferenceListeners.add(listener);
    }

    public void onMultiCompanyChanged(Consumer<MultiCompanyState> listener)
    {
        companyListeners.add(listener);
    }

    public void onDatabaseSelectionChanged(Consumer<DatabaseSelectionState> listener)
    {
        databaseListeners.add(listener);
    }

    public void onAuthenticationChanged(Consumer<Optional<AuthenticatedUserSession>> listener)
    {
        authenticationListeners.add(listener);
    }

    private void notifyAuthenticationChanged()
    {
        Optional<AuthenticatedUserSession> current = authenticatedUser();
        authenticationListeners.forEach(listener -> listener.accept(current));
    }
}
