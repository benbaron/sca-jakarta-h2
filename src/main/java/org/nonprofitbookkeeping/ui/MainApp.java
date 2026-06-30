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
        ReferenceWorkspaceWindow root = new ReferenceWorkspaceWindow();

        Scene scene = new Scene(root, 1400, 860);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());

        GlobalShortcuts.install(scene, root);

        stage.setTitle("SCA Ledger");
        stage.setMinWidth(980);
        stage.setMinHeight(680);
        stage.setScene(scene);
        stage.show();
    }
}
