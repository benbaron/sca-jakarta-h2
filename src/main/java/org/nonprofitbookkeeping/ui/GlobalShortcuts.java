package org.nonprofitbookkeeping.ui;

import javafx.scene.Scene;

/**
 * Installs application-wide keyboard shortcuts for the production workspace shell.
 */
public final class GlobalShortcuts
{
    private GlobalShortcuts()
    {
    }

    public static void install(Scene scene, ProductionWorkspaceWindow window)
    {
        GlobalCommandRegistry.installed().forEach(definition ->
                scene.getAccelerators().put(
                        definition.accelerator(),
                        () -> window.executeCommand(definition.command())));
    }
}
