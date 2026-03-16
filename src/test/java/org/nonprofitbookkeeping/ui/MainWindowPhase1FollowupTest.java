package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.model.UiThemePreference;
import org.nonprofitbookkeeping.model.UserPrivilegeLevel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MainWindowPhase1FollowupTest component.
 */
public class MainWindowPhase1FollowupTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @Test
    public void runPostValidate_usesExplicitPanelRunContract()
    {
        MainWindow.resetSessionForTests(
                new AppPreferencesState(UiThemePreference.SYSTEM_DEFAULT, false, true, UserPrivilegeLevel.ADMIN),
                new MultiCompanyState("BARONY-RED", List.of("BARONY-RED")));

        MainWindow window = FxTestSupport.onFx(MainWindow::new);
        String message = FxTestSupport.onFx(() -> {
            window.openPanel(AppPanelId.TXN_EDITOR);
            return window.panelHostForTests().runCommandActive(AppPanel.RunCommand.POST_VALIDATE).message();
        });

        assertTrue(message.contains("delegated"));
    }

    @Test
    public void jumpToPanelFromSearch_opensRequestedPanel()
    {
        MainWindow.resetSessionForTests(
                new AppPreferencesState(UiThemePreference.SYSTEM_DEFAULT, false, true, UserPrivilegeLevel.ADMIN),
                new MultiCompanyState("BARONY-RED", List.of("BARONY-RED")));

        MainWindow window = FxTestSupport.onFx(MainWindow::new);
        AppPanelId active = FxTestSupport.onFx(() -> {
            window.jumpToPanelFromSearch(AppPanelId.DIAGNOSTICS);
            return window.panelHostForTests().activePanelId();
        });

        assertEquals(AppPanelId.DIAGNOSTICS, active);
    }

    @Test
    public void panelCapabilities_describesTxnEditorRunContract()
    {
        String capabilities = MainWindow.panelCapabilities(AppPanelId.TXN_EDITOR);
        assertTrue(capabilities.contains("Post/Validate"));
        assertTrue(capabilities.contains("Journal preview"));
    }

    @Test
    public void searchHelpers_matchByPanelIdAndLabel()
    {
        assertEquals("ledger", MainWindow.normalizeSearchQuery("  LeDgEr  "));
        assertTrue(MainWindow.searchMatches("ledger", "Ledger Register", "DASHBOARD"));
        assertTrue(MainWindow.searchMatches("diag", "Diagnostics", "DIAGNOSTICS"));
        assertFalse(MainWindow.searchMatches("assets", "Funds", "HELP"));
    }

    @Test
    public void journalInspector_prefersActiveSelectionContext()
    {
        MainWindow.resetSessionForTests(
                new AppPreferencesState(UiThemePreference.SYSTEM_DEFAULT, false, true, UserPrivilegeLevel.ADMIN),
                new MultiCompanyState("BARONY-RED", List.of("BARONY-RED")));

        MainWindow window = FxTestSupport.onFx(MainWindow::new);
        String body = FxTestSupport.onFx(() -> window.buildJournalInspectorPreview(
                java.util.Optional.of(new AppPanel.JournalSelection(999_999L, "Ledger Register table"))));

        assertTrue(body.startsWith("Active selection: Ledger Register table"));
    }

    @Test
    public void buildSearchResults_filtersByQuery()
    {
        MainWindow.resetSessionForTests(
                new AppPreferencesState(UiThemePreference.SYSTEM_DEFAULT, false, true, UserPrivilegeLevel.ADMIN),
                new MultiCompanyState("BARONY-RED", List.of("BARONY-RED")));

        MainWindow window = FxTestSupport.onFx(MainWindow::new);
        String results = FxTestSupport.onFx(() -> window.buildSearchResultsForTests("diagn"));

        assertTrue(results.contains("Query: diagn"));
        assertTrue(results.contains("Diagnostics [DIAGNOSTICS]"));
        assertFalse(results.contains("Ledger Register [LEDGER_REGISTER]"));
    }
}
