package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.model.UiThemePreference;
import org.nonprofitbookkeeping.model.UserPrivilegeLevel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory session state with observable preferences/company context.
 */
public class UiSessionState
{
    private AppPreferencesState preferences = new AppPreferencesState(
            UiThemePreference.SYSTEM_DEFAULT,
            false,
            true,
            UserPrivilegeLevel.ACCOUNTANT);
    private MultiCompanyState multiCompany = new MultiCompanyState("DEFAULT", List.of("DEFAULT"));
    private DatabaseSelectionState databaseSelection = new DatabaseSelectionState("data/sca-ledger.mv.db", List.of("data/sca-ledger.mv.db"));
    private String password = "";
    private boolean loggedIn = true;

    private final List<Consumer<AppPreferencesState>> preferenceListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<MultiCompanyState>> companyListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<DatabaseSelectionState>> databaseListeners = new CopyOnWriteArrayList<>();

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

    public void setPreferences(AppPreferencesState next)
    {
        this.preferences = next;
        preferenceListeners.forEach(l -> l.accept(next));
    }

    public void setMultiCompany(MultiCompanyState next)
    {
        this.multiCompany = next;
        companyListeners.forEach(l -> l.accept(next));
    }

    public void onPreferencesChanged(Consumer<AppPreferencesState> listener)
    {
        preferenceListeners.add(listener);
    }

    public void setDatabaseSelection(DatabaseSelectionState next)
    {
        this.databaseSelection = next;
        databaseListeners.forEach(l -> l.accept(next));
    }

    public void onMultiCompanyChanged(Consumer<MultiCompanyState> listener)
    {
        companyListeners.add(listener);
    }

    public void onDatabaseSelectionChanged(Consumer<DatabaseSelectionState> listener)
    {
        databaseListeners.add(listener);
    }

    public boolean hasPassword()
    {
        return password != null && !password.isBlank();
    }

    public boolean isLoggedIn()
    {
        return loggedIn;
    }

    public void setPassword(String next)
    {
        this.password = next == null ? "" : next;
        this.loggedIn = !hasPassword();
    }

    public boolean login(String attempt)
    {
        if (!hasPassword())
        {
            loggedIn = true;
            return true;
        }
        loggedIn = password.equals(attempt == null ? "" : attempt);
        return loggedIn;
    }

    public void logout()
    {
        loggedIn = false;
    }
}
