package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.model.UiThemePreference;
import org.nonprofitbookkeeping.model.UserPrivilegeLevel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SettingsPanelDatabaseSelectionTest component.
 */
public class SettingsPanelDatabaseSelectionTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @Test
    public void readDatabaseSelection_prioritizesSelectedPathAtFront()
    {
        DatabaseSelectionState state = FxTestSupport.onFx(() -> {
            UiSessionState session = new UiSessionState();
            session.setPreferences(new AppPreferencesState(UiThemePreference.SYSTEM_DEFAULT, false, true, UserPrivilegeLevel.ACCOUNTANT));
            session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
            session.setDatabaseSelection(new DatabaseSelectionState("/data/old.mv.db", List.of("/data/old.mv.db", "/data/other.mv.db")));

            SettingsPanel panel = new SettingsPanel(session);
            panel.readDatabaseSelection();
            panel.setActiveDatabaseForTests("/data/new.mv.db");
            return panel.readDatabaseSelection();
        });

        assertEquals("/data/new.mv.db", state.activeDatabasePath());
        assertEquals("/data/new.mv.db", state.recentDatabasePaths().get(0));
    }

    @Test
    public void readDatabaseSelection_deduplicatesRecents()
    {
        DatabaseSelectionState state = FxTestSupport.onFx(() -> {
            UiSessionState session = new UiSessionState();
            session.setDatabaseSelection(new DatabaseSelectionState("/data/a.mv.db", List.of("/data/a.mv.db", "/data/a.mv.db", "/data/b.mv.db")));

            SettingsPanel panel = new SettingsPanel(session);
            panel.setRecentDatabasesForTests(List.of("/data/a.mv.db", "/data/a.mv.db", "/data/b.mv.db", "/data/b.mv.db"));
            panel.setActiveDatabaseForTests("/data/a.mv.db");
            return panel.readDatabaseSelection();
        });

        assertEquals(List.of("/data/a.mv.db", "/data/b.mv.db"), state.recentDatabasePaths());
    }
}
