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
