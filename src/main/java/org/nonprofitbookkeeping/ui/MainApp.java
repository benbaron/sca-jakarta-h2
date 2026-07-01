package org.nonprofitbookkeeping.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Production desktop application launcher. */
public class MainApp extends Application
{
    @Override
    public void start(Stage stage)
    {
        ProductionWorkspaceWindow root = new ProductionWorkspaceWindow();

        Scene scene = new Scene(root, 1440, 900);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());

        GlobalShortcuts.install(scene, root);

        stage.setTitle("Nonprofit Accounting (SCA-Jakarta)");
        stage.setMinWidth(1024);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.show();
    }
}
