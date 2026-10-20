package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.SplitPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class BudgetPlanningPanelTest
{
    @TempDir Path directory;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private Optional<AuthenticatedUserSession> previousUser;
    private Stage stage;

    @BeforeEach
    void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        var session = MainWindow.sharedSessionState();
        previousDatabase = session.databaseSelection();
        previousCompany = session.multiCompany();
        previousUser = session.authenticatedUser();
        Path database = directory.resolve("budget-planning-ui");
        session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        UiServiceRegistry.reconnectToDatabase(database);
    }

    @AfterEach
    void cleanup() throws Exception
    {
        if (previousDatabase == null) return;
        FxTestSupport.onFx(() -> { if (stage != null) stage.close(); return null; });
        Thread.sleep(300);
        FxTestSupport.onFx(() -> {
            var session = MainWindow.sharedSessionState();
            session.setDatabaseSelection(previousDatabase);
            session.setMultiCompany(previousCompany);
            previousUser.ifPresentOrElse(session::setAuthenticatedUser, session::clearAuthenticatedUser);
            return null;
        });
        UiServiceRegistry.reconnectToDatabase(Path.of(previousDatabase.activeDatabasePath()));
    }

    @Test
    void actualJavaFxPanelsExposeFundEventPlanningAndComparisonFilters()
    {
        FxTestSupport.onFx(() -> {
            Platform.setImplicitExit(false);
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.manager());
            BudgetEditorPanel editor = new BudgetEditorPanel();
            stage = new Stage();
            stage.setScene(new Scene((javafx.scene.Parent) editor.root(), 720, 560));
            stage.show();
            assertNotNull(editor.root().lookup("#budgetEditorFundScope"));
            assertNotNull(editor.root().lookup("#budgetEditorEventScope"));
            assertNotNull(editor.root().lookup("#budgetEditorSaveDraftAmountButton"));
            assertEquals(2, ((SplitPane) ((javafx.scene.layout.BorderPane) editor.root()).getCenter()).getItems().size());
            stage.close();

            BudgetVsActualPanel comparison = new BudgetVsActualPanel();
            stage = new Stage();
            stage.setScene(new Scene((javafx.scene.Parent) comparison.root(), 720, 560));
            stage.show();
            assertNotNull(comparison.root().lookup("#budgetVsActualFundFilter"));
            assertNotNull(comparison.root().lookup("#budgetVsActualEventFilter"));
            assertTrue(((ComboBox<?>) comparison.root().lookup("#budgetVsActualEventFilter")).getItems().size() >= 2);

            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(((Button) editor.root().lookup("#budgetEditorNewDraftButton")).isDisabled());
            return null;
        });
    }
}
