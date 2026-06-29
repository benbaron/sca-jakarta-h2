package org.nonprofitbookkeeping.ui.experiment;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Launches the dashboard experiment with dimensions constrained to the usable screen area.
 */
public final class DashboardExperimentLauncher extends Application
{
    @Override
    public void start(Stage stage) throws Exception
    {
        DashboardExperimentApp application = new DashboardExperimentApp();
        application.start(stage);

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        WindowSizingPolicy.WindowDimensions dimensions = WindowSizingPolicy.forVisualBounds(
                visualBounds.getWidth(), visualBounds.getHeight());

        stage.setMinWidth(dimensions.minimumWidth());
        stage.setMinHeight(dimensions.minimumHeight());
        stage.setWidth(dimensions.width());
        stage.setHeight(dimensions.height());
        stage.centerOnScreen();
    }

    public static void main(String[] args)
    {
        launch(args);
    }
}
