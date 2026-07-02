package org.nonprofitbookkeeping.ui;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** Production desktop application launcher. */
public class MainApp extends Application
{
    @Override
    public void start(Stage stage)
    {
        ReferenceWorkspaceWindow root = new ReferenceWorkspaceWindow();
        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        WorkspaceWindowSizingPolicy.WindowGeometry geometry =
                WorkspaceWindowSizingPolicy.forVisualBounds(
                        visualBounds.getMinX(),
                        visualBounds.getMinY(),
                        visualBounds.getWidth(),
                        visualBounds.getHeight());

        Scene scene = new Scene(root, geometry.width(), geometry.height());
        scene.getStylesheets().add(
                getClass().getResource("/ui/styles.css").toExternalForm());

        GlobalShortcuts.install(scene, root);

        stage.setTitle("Nonprofit Accounting (SCA-Jakarta)");
        stage.setMinWidth(geometry.minimumWidth());
        stage.setMinHeight(geometry.minimumHeight());
        stage.setX(geometry.x());
        stage.setY(geometry.y());
        stage.setScene(scene);
        stage.show();
    }
}
