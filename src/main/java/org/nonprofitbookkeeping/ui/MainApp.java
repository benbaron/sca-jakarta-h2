package org.nonprofitbookkeeping.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.WorkspaceWindowState;

/** Production desktop application launcher. */
public class MainApp extends Application
{
    @Override
    public void start(Stage stage)
    {
        AppStateStore stateStore = UserAppStateStore.create();
        AuthenticatedWorkspaceRoot root = new AuthenticatedWorkspaceRoot(
                stateStore,
                UiServiceRegistry::prepareDatabaseConnection);
        AppPreferencesState preferences = MainWindow.sharedSessionState().preferences();
        stage.initStyle(WindowDecorationPolicy.stageStyle(preferences));
        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        WorkspaceWindowState remembered = preferences.rememberWindowState()
                ? stateStore.loadWindowState().orElse(null)
                : null;
        WorkspaceWindowSizingPolicy.WindowGeometry geometry =
                WorkspaceWindowSizingPolicy.forRememberedState(
                        visualBounds.getMinX(),
                        visualBounds.getMinY(),
                        visualBounds.getWidth(),
                        visualBounds.getHeight(),
                        remembered);

        Scene scene = new Scene(root, geometry.width(), geometry.height());
        scene.getStylesheets().add(
                getClass().getResource("/ui/styles.css").toExternalForm());

        FullTextTooltipInstaller.install(root);
        GlobalShortcuts.install(scene, root);
        scene.addEventFilter(MouseEvent.ANY, event -> root.recordUserActivity());
        scene.addEventFilter(KeyEvent.ANY, event -> root.recordUserActivity());

        Timeline inactivityCheck = new Timeline(
                new KeyFrame(Duration.seconds(30), event -> root.enforceInactivityTimeout()));
        inactivityCheck.setCycleCount(Timeline.INDEFINITE);
        inactivityCheck.play();

        stage.setTitle("Nonprofit Accounting (SCA-Jakarta)");
        stage.setMinWidth(geometry.minimumWidth());
        stage.setMinHeight(geometry.minimumHeight());
        stage.setX(geometry.x());
        stage.setY(geometry.y());
        stage.setScene(scene);
        stage.setMaximized(remembered != null && remembered.maximized());
        stage.setOnHiding(event ->
        {
            inactivityCheck.stop();
            root.shutdown();
            persistWindowState(stage, stateStore);
        });
        DatabaseTransferUiRegistry.install(root.workspaceWindow());
        SclxExportUiRegistry.install(root.workspaceWindow());
        stage.show();
    }

    static void persistWindowState(Stage stage, AppStateStore stateStore)
    {
        if (!MainWindow.sharedSessionState().preferences().rememberWindowState())
        {
            stateStore.clearWindowState();
            stateStore.clearWorkspaceDividers();
            return;
        }
        stateStore.saveWindowState(new WorkspaceWindowState(
                stage.getX(),
                stage.getY(),
                stage.getWidth(),
                stage.getHeight(),
                stage.isMaximized()));
    }
}
