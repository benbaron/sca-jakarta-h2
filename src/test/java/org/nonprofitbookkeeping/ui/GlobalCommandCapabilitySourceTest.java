package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalCommandCapabilitySourceTest
{
    @Test
    void appPanelHasNoEmptyCommandOrClipboardHooks() throws Exception
    {
        String appPanel = source("AppPanel.java");

        assertTrue(appPanel.contains("default Set<AppCommand> commandCapabilities()"));
        assertTrue(appPanel.contains("default RunCommandResult executeCommand(AppCommand command)"));
        assertFalse(appPanel.contains("onCopy"));
        assertFalse(appPanel.contains("onPaste"));
        assertFalse(appPanel.contains("default void onSave() {}"));
        assertFalse(appPanel.contains("default void onNew() {}"));
    }

    @Test
    void productionShortcutsDoNotInterceptNativeTextEditingOrAdvertisedGhosts() throws Exception
    {
        String shortcuts = source("GlobalShortcuts.java");
        String help = source("HelpPanel.java");
        String navigation = source("NavigationPane.java");
        String production = source("ProductionWorkspaceWindow.java");
        String reference = source("ReferenceWorkspaceWindow.java");

        assertTrue(shortcuts.contains("GlobalCommandRegistry.installed()"));
        assertFalse(shortcuts.contains("KeyCode.C,"));
        assertFalse(shortcuts.contains("KeyCode.V,"));
        assertFalse(shortcuts.contains("KeyCode.F,"));
        assertFalse(shortcuts.contains("KeyCode.K,"));
        assertFalse(shortcuts.contains("KeyCode.G,"));
        assertTrue(help.contains("GlobalCommandRegistry.shortcutHelpText()"));
        assertFalse(help.contains("Ctrl+F"));
        assertFalse(help.contains("Ctrl+K"));
        assertFalse(help.contains("Ctrl+G"));
        assertFalse(navigation.contains("toolbar Find/Journal"));
        assertFalse(reference.contains("item(\"Copy\""));
        assertFalse(reference.contains("item(\"Paste\""));
        assertTrue(production.contains("panelHost.activeCommandCapabilities()"));
        assertTrue(production.contains("menuItem.setDisable(!supported)"));
        assertTrue(production.contains("button.setDisable(!supported)"));
    }

    private static String source(String filename) throws Exception
    {
        return Files.readString(Path.of("src/main/java/org/nonprofitbookkeeping/ui", filename));
    }
}
