package org.nonprofitbookkeeping.ui;

import javafx.stage.Window;

import java.util.Objects;
import java.util.function.Supplier;

/** Connects the shell-owned SCLX export action to the current production window. */
final class SclxExportUiRegistry
{
    private static SclxExportActions actions;
    private static Supplier<Window> ownerWindow = () -> null;

    private SclxExportUiRegistry()
    {
    }

    static synchronized void registerActions(SclxExportActions registeredActions)
    {
        actions = Objects.requireNonNull(registeredActions, "registeredActions");
    }

    static synchronized void install(ProductionWorkspaceWindow window)
    {
        Objects.requireNonNull(window, "window");
        ownerWindow = () -> window.getScene() == null ? null : window.getScene().getWindow();
        if (actions != null)
        {
            SclxExportMenuInstaller.install(window, actions);
        }
    }

    static synchronized Window ownerWindow()
    {
        return ownerWindow.get();
    }
}
