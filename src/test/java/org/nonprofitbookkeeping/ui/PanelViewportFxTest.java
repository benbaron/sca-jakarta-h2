package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PanelViewportFxTest
{
    @Test
    void initiallyDisabledTabsCanBeEnabledAfterAddingScrollContainers()
    {
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            for (boolean disableParent : new boolean[] { false, true })
            {
                Button editable = new Button("Add detail");
                Button restricted = new Button("Restricted action");
                var restriction = new javafx.beans.property.SimpleBooleanProperty(true);
                restricted.disableProperty().bind(restriction);
                VBox form = new VBox(editable, restricted);
                var tab = new javafx.scene.control.Tab("Details", form);
                var tabs = new javafx.scene.control.TabPane(tab);
                if (disableParent) tabs.setDisable(true);
                else tab.setDisable(true);
                assertTrue(form.isDisable(), "JavaFX explicitly disables the original tab content");

                PanelViewport.prepare(tabs);
                Stage stage = new Stage();
                try
                {
                    stage.setScene(new Scene(tabs, 600, 400));
                    stage.show();
                    tabs.applyCss();
                    tabs.layout();
                    if (disableParent) tabs.setDisable(false);
                    else tab.setDisable(false);

                    assertFalse(form.isDisabled(), "The tab's obsolete disable flag must not remain on the inner form");
                    assertFalse(editable.isDisabled());
                    assertTrue(restricted.isDisabled(), "Independent permission/readiness bindings must be preserved");
                    restriction.set(false);
                    assertFalse(restricted.isDisabled());
                    tab.setDisable(true);
                    assertTrue(editable.isDisabled());
                    tab.setDisable(false);
                    assertFalse(editable.isDisabled());
                }
                finally
                {
                    stage.close();
                }
            }
            return null;
        });
    }

    @Test
    void wrappingAnEnabledTabPreservesAnIndependentlyDisabledForm()
    {
        FxTestSupport.onFx(() ->
        {
            VBox form = new VBox(new Button("Busy action"));
            var tab = new javafx.scene.control.Tab("Details", form);
            var tabs = new javafx.scene.control.TabPane(tab);
            form.setDisable(true);
            PanelViewport.prepare(tabs);
            assertTrue(form.isDisable());
            return null;
        });
    }

    @Test
    void smallWindowCanScrollToLastFormControlAtLargeTextSize()
    {
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            VBox form = new VBox(12);
            form.setStyle("-fx-font-size: 22px;");
            for (int index = 0; index < 25; index++)
            {
                form.getChildren().add(new Button("A complete long form command with its full label " + index));
            }
            PanelViewport viewport = PanelViewport.wrap(form);
            Stage stage = new Stage();
            try
            {
                stage.setScene(new Scene(viewport, 300, 180));
                stage.show();
                viewport.applyCss();
                viewport.layout();
                assertVisibleScrollbars(viewport);
                viewport.setHvalue(1);
                viewport.setVvalue(1);
                viewport.layout();
                var last = form.getChildren().get(24);
                var bounds = last.localToScene(last.getBoundsInLocal());
                assertTrue(bounds.getMaxY() <= stage.getScene().getHeight(), "Last form command must be reachable");
                assertTrue(bounds.getMaxX() <= stage.getScene().getWidth(), "Right edge must be reachable");
            }
            finally
            {
                stage.close();
            }
            return null;
        });
    }

    @Test
    void tableRetainsIndependentTwoAxisScrollbarsWithinPaneViewport()
    {
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            TableView<String> table = new TableView<>();
            TableColumn<String, String> column = new TableColumn<>("Wide column");
            column.setPrefWidth(1600);
            table.getColumns().add(column);
            table.getItems().addAll(java.util.Collections.nCopies(100, "row"));
            table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
            PanelViewport viewport = PanelViewport.wrap(table);
            Stage stage = new Stage();
            try
            {
                stage.setScene(new Scene(viewport, 640, 480));
                stage.show();
                viewport.applyCss();
                viewport.layout();
                table.layout();
                assertVisibleScrollbars(table);
                assertTrue(table.getHeight() <= viewport.getViewportBounds().getHeight(),
                        "Table viewport must not expand to the complete row count");
            }
            finally
            {
                stage.close();
            }
            return null;
        });
    }

    private static void assertVisibleScrollbars(javafx.scene.Node node)
    {
        for (Orientation orientation : Orientation.values())
        {
            assertTrue(node.lookupAll(".scroll-bar").stream().filter(ScrollBar.class::isInstance)
                    .map(ScrollBar.class::cast).anyMatch(bar -> bar.isVisible() && bar.getOrientation() == orientation),
                    "Overflow must expose a visible " + orientation + " scrollbar");
        }
    }
}
