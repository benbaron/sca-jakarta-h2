package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import java.util.List;
import java.util.Objects;

/** Installs selected-company SCLX export in the production File menu. */
final class SclxExportMenuInstaller
{
    static final String EXPORT_MENU_ID = "exportActiveCompanySclxMenuItem";
    static final String EXPORT_MENU_TEXT = "Export Active Company to SCLX…";

    private SclxExportMenuInstaller()
    {
    }

    static void install(ProductionWorkspaceWindow window, SclxExportActions actions)
    {
        Objects.requireNonNull(window, "window");
        MenuBar menuBar = findMenuBar(window);
        if (menuBar == null)
        {
            throw new IllegalStateException("The production File menu is unavailable.");
        }
        install(menuBar, actions);
    }

    static void install(MenuBar menuBar, SclxExportActions actions)
    {
        Objects.requireNonNull(menuBar, "menuBar");
        Objects.requireNonNull(actions, "actions");

        Menu fileMenu = menuBar.getMenus().stream()
                .filter(menu -> "File".equals(menu.getText()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The production File menu is unavailable."));
        if (fileMenu.getItems().stream().anyMatch(item -> EXPORT_MENU_ID.equals(item.getId())))
        {
            return;
        }

        MenuItem export = new MenuItem(EXPORT_MENU_TEXT);
        export.setId(EXPORT_MENU_ID);
        export.setOnAction(event -> actions.requestExport());
        export.disableProperty().bind(
                actions.busyProperty().or(actions.availableProperty().not()));

        int transferIndex = indexOf(fileMenu, DatabaseTransferMenuInstaller.SWITCH_MENU_ID);
        if (transferIndex >= 0)
        {
            fileMenu.getItems().add(transferIndex + 1, export);
            return;
        }

        int recoveryIndex = indexOfText(fileMenu, "Retry / Repair Current Database");
        int insertionIndex = recoveryIndex >= 0 ? recoveryIndex + 1 : Math.min(3, fileMenu.getItems().size());
        fileMenu.getItems().addAll(
                insertionIndex,
                List.of(
                        new SeparatorMenuItem(),
                        export,
                        new SeparatorMenuItem()));
    }

    private static int indexOf(Menu menu, String id)
    {
        for (int index = 0; index < menu.getItems().size(); index++)
        {
            if (id.equals(menu.getItems().get(index).getId()))
            {
                return index;
            }
        }
        return -1;
    }

    private static int indexOfText(Menu menu, String text)
    {
        for (int index = 0; index < menu.getItems().size(); index++)
        {
            if (text.equals(menu.getItems().get(index).getText()))
            {
                return index;
            }
        }
        return -1;
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
