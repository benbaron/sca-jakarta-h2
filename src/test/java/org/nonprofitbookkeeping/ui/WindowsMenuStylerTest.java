package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.UiThemePreference;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class WindowsMenuStylerTest
{
    @Test
    void detachedMenusAndSubmenusUseReadableLightAndDarkPalettes()
    {
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            var session = MainWindow.sharedSessionState();
            var original = session.preferences();
            Menu database = new Menu("Database", null, new MenuItem("Select Database File…"));
            Menu file = new Menu("File", null, database);
            MenuBar bar = new MenuBar(file);
            WindowsMenuStyler.install(bar);
            Stage stage = new Stage();
            try
            {
                Scene scene = new Scene(new VBox(bar), 600, 300);
                scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
                stage.setScene(scene);
                stage.show();
                for (UiThemePreference theme : new UiThemePreference[] { UiThemePreference.LIGHT, UiThemePreference.DARK })
                {
                    session.setPreferences(new AppPreferencesState(theme, true, true, original.defaultPrivilege()));
                    file.show();
                    var node = database.getStyleableNode();
                    assertNotNull(node);
                    var root = node.getScene().getRoot();
                    root.applyCss();
                    assertEquals(theme == UiThemePreference.DARK, root.getStyleClass().contains("windows-menu-dark"));
                    Label label = (Label) node.lookup(".label");
                    assertNotNull(label);
                    assertEquals(theme == UiThemePreference.DARK ? Color.WHITE : Color.web("#1a1a1a"), label.getTextFill());
                    assertNotNull(database.getOnShown(), "Cascading submenus receive the same popup styling");
                    file.hide();
                }
            }
            finally
            {
                stage.close();
                session.setPreferences(original);
            }
            return null;
        });
    }
}
