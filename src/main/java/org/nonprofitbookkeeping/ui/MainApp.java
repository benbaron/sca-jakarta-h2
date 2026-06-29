package org.nonprofitbookkeeping.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Production desktop application launcher.
 */
public class MainApp extends Application
{
    @Override
    public void start(Stage stage)
    {
        ProductionWorkspaceWindow root = new ProductionWorkspaceWindow();

        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());

        GlobalShortcuts.install(scene, root);

        stage.setTitle("SCA Ledger");
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();
    }
}
