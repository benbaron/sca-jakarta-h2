package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock("ui-service-registry")
class ApplicationPresentationFxTest
{
    @Test
    void actualApplicationRootPaintsAndContinuesPulsing(@TempDir Path directory) throws Exception
    {
        FxTestSupport.initToolkitOrSkip();
        var session = MainWindow.sharedSessionState();
        var oldDatabase = session.databaseSelection();
        var oldCompany = session.multiCompany();
        var oldPreferences = session.preferences();
        var oldUser = session.authenticatedUser();
        Stage[] stage = new Stage[1];
        try
        {
            Path database = directory.resolve("presentation");
            session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
            session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
            UiServiceRegistry.reconnectToDatabase(database);
            FileAppStateStore store = new FileAppStateStore(directory.resolve("state.properties"));
            store.saveDatabaseSelection(session.databaseSelection());
            store.saveMultiCompany(session.multiCompany());
            CountDownLatch pulses = new CountDownLatch(4);
            FxTestSupport.onFx(() ->
            {
                Platform.setImplicitExit(false);
                session.clearAuthenticatedUser();
                var root = new AuthenticatedWorkspaceRoot(store, UiServiceRegistry::prepareDatabaseConnection);
                var scene = new Scene(root, 1000, 740);
                scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
                scene.addPostLayoutPulseListener(() ->
                {
                    pulses.countDown();
                    if (pulses.getCount() > 0)
                    {
                        Platform.requestNextPulse();
                    }
                });
                stage[0] = new Stage();
                stage[0].setScene(scene);
                stage[0].show();
                return null;
            });
            assertTrue(pulses.await(15, TimeUnit.SECONDS), "Actual window must continue painting after startup");
            FxTestSupport.onFx(() ->
            {
                var scene = stage[0].getScene();
                var image = scene.snapshot(null);
                var pixels = image.getPixelReader();
                int darkPixels = 0;
                for (int y = 0; y < image.getHeight(); y++)
                {
                    for (int x = 0; x < image.getWidth(); x++)
                    {
                        var color = pixels.getColor(x, y);
                        if (color.getBrightness() < .7)
                        {
                            darkPixels++;
                        }
                    }
                }
                assertTrue(darkPixels > 100, "Application client area must contain visible controls/text, not an all-white frame");
                assertTrue(!scene.getRoot().lookupAll(".label").isEmpty());
                return null;
            });
        }
        finally
        {
            FxTestSupport.onFx(() ->
            {
                if (stage[0] != null)
                {
                    stage[0].hide();
                    stage[0].getScene().setRoot(new javafx.scene.layout.VBox());
                }
                session.setPreferences(oldPreferences);
                session.setDatabaseSelection(oldDatabase);
                session.setMultiCompany(oldCompany);
                return null;
            });
            UiServiceRegistry.reconnectToDatabase(Path.of(oldDatabase.activeDatabasePath()));
            FxTestSupport.onFx(() ->
            {
                if (oldUser.isPresent())
                {
                    session.setAuthenticatedUser(oldUser.get());
                }
                return null;
            });
        }
    }
}
