package org.nonprofitbookkeeping.ui;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import java.util.List;

/** One factual registry for installed production-shell commands and shortcuts. */
public final class GlobalCommandRegistry
{
    public record Definition(
            AppCommand command,
            String label,
            String shortcutText,
            KeyCodeCombination accelerator)
    {
    }

    private static final List<Definition> INSTALLED = List.of(
            new Definition(
                    AppCommand.NEW_ACTIVE,
                    "New",
                    "Ctrl+N",
                    new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN)),
            new Definition(
                    AppCommand.SAVE_ACTIVE,
                    "Save",
                    "Ctrl+S",
                    new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN)),
            new Definition(
                    AppCommand.CLOSE_ALL_TABS,
                    "Close All Tabs",
                    "Ctrl+Shift+W",
                    new KeyCodeCombination(
                            KeyCode.W,
                            KeyCombination.CONTROL_DOWN,
                            KeyCombination.SHIFT_DOWN)),
            new Definition(
                    AppCommand.CLOSE_INSPECTOR,
                    "Close Inspector",
                    "Esc",
                    new KeyCodeCombination(KeyCode.ESCAPE)));

    private GlobalCommandRegistry()
    {
    }

    public static List<Definition> installed()
    {
        return INSTALLED;
    }

    public static Definition definition(AppCommand command)
    {
        return INSTALLED.stream()
                .filter(definition -> definition.command() == command)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Command has no installed shell definition: " + command));
    }

    public static String label(AppCommand command)
    {
        if (command == AppCommand.POST_VALIDATE)
        {
            return "Validate";
        }
        return definition(command).label();
    }

    public static String shortcutHelpText()
    {
        StringBuilder text = new StringBuilder("Installed shortcuts\n");
        for (Definition definition : INSTALLED)
        {
            text.append(definition.shortcutText())
                    .append(": ")
                    .append(definition.label())
                    .append('\n');
        }
        return text.toString().stripTrailing();
    }
}
