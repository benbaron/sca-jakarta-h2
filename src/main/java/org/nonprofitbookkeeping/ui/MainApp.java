package org.nonprofitbookkeeping.ui;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.WorkspaceWindowState;

/** Production desktop application launcher. */
public class MainApp extends Application
{
    @Override
    public void start(Stage stage)
    {
        AppStateStore stateStore = UserAppStateStore.create();
        ProductionWorkspaceWindow root = new ProductionWorkspaceWindow(
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

        stage.setTitle("Nonprofit Accounting (SCA-Jakarta)");
        stage.setMinWidth(geometry.minimumWidth());
        stage.setMinHeight(geometry.minimumHeight());
        stage.setX(geometry.x());
        stage.setY(geometry.y());
        stage.setScene(scene);
        stage.setMaximized(remembered != null && remembered.maximized());
        stage.setOnHiding(event -> persistWindowState(stage, stateStore));
        DatabaseTransferUiRegistry.install(root);
        SclxExportUiRegistry.install(root);
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
