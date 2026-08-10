package org.nonprofitbookkeeping.ui;

import javafx.scene.Scene;

/**
 * Installs application-wide keyboard shortcuts.
 */
public final class GlobalShortcuts
{
    private GlobalShortcuts()
    {
    }

    public static void install(Scene scene, MainWindow window)
    {
        GlobalCommandRegistry.installed().forEach(definition ->
                scene.getAccelerators().put(
                        definition.accelerator(),
                        () -> window.executeCommand(definition.command())));
    }

    public static void install(Scene scene, ProductionWorkspaceWindow window)
    {
        GlobalCommandRegistry.installed().forEach(definition ->
                scene.getAccelerators().put(
                        definition.accelerator(),
                        () -> window.executeCommand(definition.command())));
    }
}
