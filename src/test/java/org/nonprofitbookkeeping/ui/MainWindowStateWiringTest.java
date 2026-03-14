package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.model.UiThemePreference;
import org.nonprofitbookkeeping.model.UserPrivilegeLevel;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainWindowStateWiringTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @Test
    public void restoresAndAppliesThemeNativeAndCompanyFromStore()
    {
        AppPreferencesState prefs = new AppPreferencesState(UiThemePreference.DARK, true, true, UserPrivilegeLevel.MANAGER);
        MultiCompanyState company = new MultiCompanyState("BARONY-GREEN", List.of("BARONY-GREEN"));

        DatabaseSelectionState db = new DatabaseSelectionState("/tmp/dragon.mv.db", List.of("/tmp/dragon.mv.db"));
        InMemoryAppStateStore store = new InMemoryAppStateStore(Optional.of(prefs), Optional.of(company), Optional.of(db));

        MainWindow.resetSessionForTests(
                new AppPreferencesState(UiThemePreference.SYSTEM_DEFAULT, false, true, UserPrivilegeLevel.ACCOUNTANT),
                new MultiCompanyState("DEFAULT", List.of("DEFAULT")));

        MainWindow window = FxTestSupport.onFx(() -> new MainWindow(store));

        assertTrue(window.usesDarkThemeFlag());
        assertTrue(window.usesNativeDecorationsFlag());
        assertEquals("BARONY-GREEN", window.activeCompanyCode());
        assertEquals("/tmp/dragon.mv.db", window.activeDatabasePath());
    }

    @Test
    public void saveActivePanel_persistsCurrentSessionState()
    {
        InMemoryAppStateStore store = new InMemoryAppStateStore(Optional.empty(), Optional.empty(), Optional.empty());

        MainWindow.resetSessionForTests(
                new AppPreferencesState(UiThemePreference.LIGHT, false, true, UserPrivilegeLevel.ADMIN),
                new MultiCompanyState("BARONY-RED", List.of("BARONY-RED", "BARONY-BLUE")));

        MainWindow window = FxTestSupport.onFx(() -> new MainWindow(store));
        FxTestSupport.onFx(() -> {
            window.saveActivePanel();
            return null;
        });

        assertEquals(UiThemePreference.LIGHT, store.savedPreferences.themePreference());
        assertEquals("BARONY-RED", store.savedCompany.activeCompanyCode());
        assertEquals("data/sca-ledger.mv.db", store.savedDatabaseSelection.activeDatabasePath());
    }


    @Test
    public void selectTheme_updatesSessionPreferencesAndThemeFlags()
    {
        InMemoryAppStateStore store = new InMemoryAppStateStore(Optional.empty(), Optional.empty(), Optional.empty());

        MainWindow.resetSessionForTests(
                new AppPreferencesState(UiThemePreference.SYSTEM_DEFAULT, false, true, UserPrivilegeLevel.ADMIN),
                new MultiCompanyState("BARONY-RED", List.of("BARONY-RED")));

        MainWindow window = FxTestSupport.onFx(() -> new MainWindow(store));

        FxTestSupport.onFx(() -> {
            window.selectTheme(UiThemePreference.DARK);
            return null;
        });
        assertTrue(window.usesDarkThemeFlag());

        FxTestSupport.onFx(() -> {
            window.selectTheme(UiThemePreference.LIGHT);
            return null;
        });
        assertEquals(UiThemePreference.LIGHT, MainWindow.sharedSessionState().preferences().themePreference());
    }

    private static final class InMemoryAppStateStore implements AppStateStore
    {
        private Optional<AppPreferencesState> preferences;
        private Optional<MultiCompanyState> multiCompany;
        private Optional<DatabaseSelectionState> databaseSelection;

        private AppPreferencesState savedPreferences;
        private MultiCompanyState savedCompany;
        private DatabaseSelectionState savedDatabaseSelection;

        private InMemoryAppStateStore(Optional<AppPreferencesState> preferences,
                                      Optional<MultiCompanyState> multiCompany,
                                      Optional<DatabaseSelectionState> databaseSelection)
        {
            this.preferences = preferences;
            this.multiCompany = multiCompany;
            this.databaseSelection = databaseSelection;
        }

        @Override
        public Optional<AppPreferencesState> loadPreferences()
        {
            return preferences;
        }

        @Override
        public Optional<MultiCompanyState> loadMultiCompany()
        {
            return multiCompany;
        }

        @Override
        public void savePreferences(AppPreferencesState state)
        {
            savedPreferences = state;
            preferences = Optional.of(state);
        }

        @Override
        public void saveMultiCompany(MultiCompanyState state)
        {
            savedCompany = state;
            multiCompany = Optional.of(state);
        }

        @Override
        public Optional<DatabaseSelectionState> loadDatabaseSelection()
        {
            return databaseSelection;
        }

        @Override
        public void saveDatabaseSelection(DatabaseSelectionState state)
        {
            savedDatabaseSelection = state;
            databaseSelection = Optional.of(state);
        }
    }
}
