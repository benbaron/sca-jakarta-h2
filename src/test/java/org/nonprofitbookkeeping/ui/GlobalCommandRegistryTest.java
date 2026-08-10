package org.nonprofitbookkeeping.ui;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalCommandRegistryTest
{
    @Test
    void registryOwnsExactProductionCommandLabelsAndAccelerators()
    {
        List<GlobalCommandRegistry.Definition> definitions = GlobalCommandRegistry.installed();

        assertEquals(List.of(
                AppCommand.NEW_ACTIVE,
                AppCommand.SAVE_ACTIVE,
                AppCommand.CLOSE_ALL_TABS,
                AppCommand.CLOSE_INSPECTOR),
                definitions.stream().map(GlobalCommandRegistry.Definition::command).toList());
        assertEquals(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN),
                definitions.get(0).accelerator());
        assertEquals(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN),
                definitions.get(1).accelerator());
        assertEquals(new KeyCodeCombination(
                        KeyCode.W,
                        KeyCombination.CONTROL_DOWN,
                        KeyCombination.SHIFT_DOWN),
                definitions.get(2).accelerator());
        assertEquals(new KeyCodeCombination(KeyCode.ESCAPE),
                definitions.get(3).accelerator());
    }

    @Test
    void generatedHelpContainsNoUninstalledCommandClaim()
    {
        String help = GlobalCommandRegistry.shortcutHelpText();

        assertTrue(help.contains("Ctrl+N: New"));
        assertTrue(help.contains("Ctrl+S: Save"));
        assertTrue(help.contains("Ctrl+Shift+W: Close All Tabs"));
        assertTrue(help.contains("Esc: Close Inspector"));
        assertFalse(help.contains("Ctrl+F"));
        assertFalse(help.contains("Ctrl+K"));
        assertFalse(help.contains("Ctrl+G"));
        assertFalse(help.contains("Ctrl+C"));
        assertFalse(help.contains("Ctrl+V"));
    }
}
