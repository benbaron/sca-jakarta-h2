package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.service.ApplicationPermission;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import java.util.List;
import java.util.Objects;

/** Installs the shared database-transfer actions in the production File menu. */
final class DatabaseTransferMenuInstaller
{
    static final String BACKUP_MENU_ID = "backupDatabaseMenuItem";
    static final String RESTORE_MENU_ID = "restoreDatabaseMenuItem";
    static final String SWITCH_MENU_ID = "switchValidatedDatabaseMenuItem";

    private DatabaseTransferMenuInstaller()
    {
    }

    static void install(ProductionWorkspaceWindow window, DatabaseTransferActions actions)
    {
        Objects.requireNonNull(window, "window");
        MenuBar menuBar = findMenuBar(window);
        if (menuBar == null)
        {
            throw new IllegalStateException("The production File menu is unavailable.");
        }
        install(menuBar, actions);
    }

    static void install(MenuBar menuBar, DatabaseTransferActions actions)
    {
        Objects.requireNonNull(menuBar, "menuBar");
        Objects.requireNonNull(actions, "actions");

        Menu fileMenu = menuBar.getMenus().stream()
                .filter(menu -> "File".equals(menu.getText()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The production File menu is unavailable."));
        if (fileMenu.getItems().stream().anyMatch(item -> BACKUP_MENU_ID.equals(item.getId())))
        {
            return;
        }

        MenuItem backup = new MenuItem("Backup Database…");
        backup.setId(BACKUP_MENU_ID);
        backup.setOnAction(event -> actions.requestBackup());
        backup.disableProperty().bind(
                actions.busyProperty().or(UiPermissionGate.deniedProperty(ApplicationPermission.DATABASE_ADMIN)));

        MenuItem restore = new MenuItem("Restore Database Copy…");
        restore.setId(RESTORE_MENU_ID);
        restore.setOnAction(event -> actions.requestRestore());
        restore.disableProperty().bind(
                actions.busyProperty().or(UiPermissionGate.deniedProperty(ApplicationPermission.DATABASE_ADMIN)));

        MenuItem switchDatabase = new MenuItem("Switch to Validated Copy");
        switchDatabase.setId(SWITCH_MENU_ID);
        switchDatabase.setOnAction(event -> actions.requestSwitchToValidatedCopy());
        switchDatabase.disableProperty().bind(
                actions.busyProperty()
                        .or(actions.switchAvailableProperty().not())
                        .or(UiPermissionGate.deniedProperty(ApplicationPermission.DATABASE_ADMIN)));

        int insertionIndex = insertionIndex(fileMenu);
        fileMenu.getItems().addAll(
                insertionIndex,
                List.of(
                        new SeparatorMenuItem(),
                        backup,
                        restore,
                        switchDatabase,
                        new SeparatorMenuItem()));
    }

    private static int insertionIndex(Menu fileMenu)
    {
        for (int index = 0; index < fileMenu.getItems().size(); index++)
        {
            MenuItem item = fileMenu.getItems().get(index);
            if ("Retry / Repair Current Database".equals(item.getText()))
            {
                return index + 1;
            }
        }
        return Math.min(3, fileMenu.getItems().size());
    }

    private static MenuBar findMenuBar(Node node)
    {
        if (node instanceof MenuBar menuBar)
        {
            return menuBar;
        }
        if (node instanceof Parent parent)
        {
            for (Node child : parent.getChildrenUnmodifiable())
            {
                MenuBar found = findMenuBar(child);
                if (found != null)
                {
                    return found;
                }
            }
        }
        return null;
    }
}
