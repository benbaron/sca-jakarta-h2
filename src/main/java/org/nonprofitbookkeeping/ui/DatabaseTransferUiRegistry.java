package org.nonprofitbookkeeping.ui;

import javafx.stage.Window;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Connects the shell-owned transfer coordinator to the current production window. */
final class DatabaseTransferUiRegistry
{
    private static DatabaseTransferActions actions;
    private static Supplier<Window> ownerWindow = () -> null;
    private static Consumer<Path> databaseSwitcher = path ->
    {
        throw new IllegalStateException("The production workspace is not ready to switch databases.");
    };

    private DatabaseTransferUiRegistry()
    {
    }

    static synchronized void registerActions(DatabaseTransferActions registeredActions)
    {
        actions = Objects.requireNonNull(registeredActions, "registeredActions");
    }

    static synchronized void install(ProductionWorkspaceWindow window)
    {
        Objects.requireNonNull(window, "window");
        ownerWindow = () -> window.getScene() == null ? null : window.getScene().getWindow();
        databaseSwitcher = window::connectDatabaseForTests;
        if (actions != null)
        {
            DatabaseTransferMenuInstaller.install(window, actions);
        }
    }

    static synchronized Window ownerWindow()
    {
        return ownerWindow.get();
    }

    static synchronized void switchDatabase(Path databaseFile)
    {
        databaseSwitcher.accept(databaseFile);
    }
}
