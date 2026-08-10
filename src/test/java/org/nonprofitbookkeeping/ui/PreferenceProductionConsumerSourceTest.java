package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source guardrails for production preference consumers that depend on JavaFX UI actions. */
class PreferenceProductionConsumerSourceTest
{
    @Test
    void productionShellLoadsAndConsumesShellPreferences() throws Exception
    {
        String mainApp = source("MainApp.java");
        String workspace = source("ProductionWorkspaceWindow.java");
        String settings = source("SettingsPanel.java");

        assertTrue(workspace.contains("stateStore.loadPreferences()"));
        assertTrue(workspace.contains("onPreferencesChanged(this::applyPreferences)"));
        assertTrue(workspace.contains("preferences.rememberWindowState()"));
        assertTrue(mainApp.contains("WindowDecorationPolicy.stageStyle(preferences)"));
        assertTrue(mainApp.contains("stateStore.loadWindowState()"));
        assertTrue(mainApp.contains("persistWindowState(stage, stateStore)"));
        assertTrue(settings.contains("stateStore.savePreferences(preferences)"));
        assertTrue(settings.contains("stateStore.clearWorkspaceDividers()"));
        assertTrue(settings.contains("restart required"));
        assertFalse(settings.contains("ComboBox<UserPrivilegeLevel>"));
        assertFalse(settings.contains("ComboBox<ReopenScope>"));
    }

    @Test
    void accountingDefaultsReachPeriodCloseAndJournal() throws Exception
    {
        String periodClose = source("PeriodCloseRunsPanel.java");
        String journal = source("JournalWorkspacePanel.java");

        assertTrue(periodClose.contains("preferences.closedPeriodPolicy()"));
        assertTrue(periodClose.contains("preferences.requireReopenReason()"));
        assertTrue(periodClose.contains("DesktopActorIdentity.current()"));
        assertFalse(periodClose.contains("new TextField(\"ui-operator\")"));
        assertFalse(periodClose.contains("reopenPolicy.setValue(ClosedPeriodPolicy.WARN_AND_REOPEN)"));
        assertTrue(journal.contains("confirmEnteredTransactionDeletion()"));
        assertTrue(journal.contains("confirmationRequired && !confirm("));
        assertTrue(journal.contains("DesktopActorIdentity.current()"));
    }

    private static String source(String name) throws Exception
    {
        return Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui/" + name));
    }
}
