package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class PropertyCustodyPanelTest
{
    @TempDir Path directory;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private Optional<AuthenticatedUserSession> previousUser;
    private Stage stage;

    @BeforeEach void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        var session = MainWindow.sharedSessionState(); previousDatabase = session.databaseSelection();
        previousCompany = session.multiCompany(); previousUser = session.authenticatedUser();
        Path database = directory.resolve("property-custody-ui");
        session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        UiServiceRegistry.reconnectToDatabase(database);
    }

    @AfterEach void cleanup() throws Exception
    {
        if (previousDatabase == null) return;
        FxTestSupport.onFx(() -> { if (stage != null) stage.close(); return null; }); Thread.sleep(300);
        FxTestSupport.onFx(() -> { var s=MainWindow.sharedSessionState(); s.setDatabaseSelection(previousDatabase); s.setMultiCompany(previousCompany); previousUser.ifPresentOrElse(s::setAuthenticatedUser,s::clearAuthenticatedUser); return null; });
        UiServiceRegistry.reconnectToDatabase(Path.of(previousDatabase.activeDatabasePath()));
    }

    @Test void actualJavaFxPanelExposesSaveHistoryScrollingAndRoleGate()
    {
        FxTestSupport.onFx(() -> {
            Platform.setImplicitExit(false); MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.manager());
            PropertyCustodyPanel panel = new PropertyCustodyPanel(); stage = new Stage();
            stage.setScene(new Scene((javafx.scene.Parent)panel.root(), 620, 520)); stage.show();
            Button save = (Button)panel.root().lookup("#propertyCustodySave");
            assertNotNull(save); assertEquals("Save Custody Event", save.getText()); assertFalse(save.isDisabled());
            assertEquals("Save Custody Event", panel.commandLabel(AppCommand.SAVE_ACTIVE));
            assertEquals(2, ((SplitPane)((javafx.scene.layout.BorderPane)panel.root()).getCenter()).getItems().size());
            ScrollPane editor = (ScrollPane)panel.root().lookup("#propertyCustodyEditorScroll");
            assertTrue(editor.isFitToWidth());
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(save.isDisabled()); return null;
        });
    }
}
