package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proves Preferences displays database authority rather than mutating it. */
public class SettingsPanelDatabaseSelectionTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @Test
    public void connectedDatabasePathIsReadOnlyFactualState()
    {
        FxTestSupport.onFx(() -> {
            UiSessionState session = new UiSessionState();
            session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
            session.setDatabaseSelection(new DatabaseSelectionState(
                    "/data/connected.mv.db",
                    List.of("/data/connected.mv.db", "/data/older.mv.db")));

            SettingsPanel panel = new SettingsPanel(session);

            assertEquals("/data/connected.mv.db", panel.activeDatabaseTextForTests());
            assertFalse(panel.activeDatabaseEditableForTests());
            return null;
        });
    }

    @Test
    public void sourceContainsNoPreferencesDatabaseSelectionWriter() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/ui/SettingsPanel.java"));

        assertTrue(source.contains("Connected database file"));
        assertTrue(source.contains("activeDatabase.setEditable(false)"));
        assertTrue(source.contains("Use File → Select Database File…"));
        assertFalse(source.contains("session.setDatabaseSelection("));
        assertFalse(source.contains("readDatabaseSelection()"));
        assertFalse(source.contains("activeDatabase.setEditable(true)"));
    }
}
