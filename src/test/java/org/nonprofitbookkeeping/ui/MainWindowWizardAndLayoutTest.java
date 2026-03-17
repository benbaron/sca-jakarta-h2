package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.model.UiThemePreference;
import org.nonprofitbookkeeping.model.UserPrivilegeLevel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MainWindowWizardAndLayoutTest component.
 */
public class MainWindowWizardAndLayoutTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @Test
    public void menuIncludesSeparateDatabaseAndCompanyWizards()
    {
        MainWindow.resetSessionForTests(
                new AppPreferencesState(UiThemePreference.SYSTEM_DEFAULT, false, true, UserPrivilegeLevel.ADMIN),
                new MultiCompanyState("DEFAULT", List.of("DEFAULT")));

        List<String> items = FxTestSupport.onFx(() -> {
            MainWindow window = new MainWindow();
            return window.menuItemTextsForTests();
        });

        assertTrue(items.contains("Database Wizard…"));
        assertTrue(items.contains("Company Wizard…"));
    }

    @Test
    public void shellUsesThreePaneSplitWithDividers()
    {
        MainWindow window = FxTestSupport.onFx(MainWindow::new);
        double[] positions = FxTestSupport.onFx(window::shellDividerPositionsForTests);

        assertEquals(2, positions.length);
        assertTrue(positions[0] > 0.0 && positions[0] < 0.5);
        assertTrue(positions[1] > 0.5 && positions[1] < 1.0);
    }
}
