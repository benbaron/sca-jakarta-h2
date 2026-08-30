package org.nonprofitbookkeeping.ui;

import javafx.scene.Scene;

/** Installs application-wide keyboard shortcuts through the authenticated production gate. */
public final class GlobalShortcuts
{
    private GlobalShortcuts()
    {
    }

    public static void install(Scene scene, AuthenticatedWorkspaceRoot root)
    {
        GlobalCommandRegistry.installed().forEach(definition ->
                scene.getAccelerators().put(
                        definition.accelerator(),
                        () -> root.executeCommand(definition.command())));
    }

    /** Compatibility overload for source/test callers that directly host the production shell. */
    public static void install(Scene scene, ProductionWorkspaceWindow window)
    {
        GlobalCommandRegistry.installed().forEach(definition ->
                scene.getAccelerators().put(
                        definition.accelerator(),
                        () -> window.executeCommand(definition.command())));
    }
}
