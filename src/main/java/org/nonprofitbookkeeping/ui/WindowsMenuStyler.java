package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.PopupControl;

/** Applies the Windows menu palette to detached dropdown and submenu scenes. */
final class WindowsMenuStyler
{
    private WindowsMenuStyler()
    {
    }

    static void install(MenuBar menuBar)
    {
        menuBar.getMenus().forEach(WindowsMenuStyler::install);
    }

    private static void install(Menu menu)
    {
        menu.setOnShown(event ->
        {
            for (var item : menu.getItems())
            {
                Node node = item.getStyleableNode();
                if (node != null && node.getScene() != null
                        && node.getScene().getWindow() instanceof PopupControl popup)
                {
                    var root = node.getScene().getRoot();
                    root.getStyleClass().remove("windows-menu-dark");
                    popup.getStyleClass().remove("windows-menu-dark");
                    if (AtlantaFxThemeManager.isDark(MainWindow.sharedSessionState().preferences().themePreference()))
                    {
                        root.getStyleClass().add("windows-menu-dark");
                        if (!popup.getStyleClass().contains("windows-menu-dark"))
                        {
                            popup.getStyleClass().add("windows-menu-dark");
                        }
                    }
                    break;
                }
            }
        });
        menu.getItems().stream().filter(Menu.class::isInstance).map(Menu.class::cast)
                .forEach(WindowsMenuStyler::install);
    }
}
