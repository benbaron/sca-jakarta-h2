package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;
import org.nonprofitbookkeeping.service.BudgetCategoryCommand;
import org.nonprofitbookkeeping.service.BudgetCategoryView;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class BudgetCategoriesPanelTest
{
    @TempDir Path directory;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private Optional<AuthenticatedUserSession> previousUser;
    private Stage stage;
    private BudgetCategoriesPanel panel;

    @BeforeEach
    void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        UiSessionState session = MainWindow.sharedSessionState();
        previousDatabase = session.databaseSelection();
        previousCompany = session.multiCompany();
        previousUser = session.authenticatedUser();
        Path database = directory.resolve("budget-categories");
        session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        UiServiceRegistry.reconnectToDatabase(database);
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            session.setAuthenticatedUser(UiPermissionTestSessions.manager());
            return null;
        });
    }

    @AfterEach
    void cleanup() throws Exception
    {
        if (previousDatabase == null)
        {
            return;
        }
        FxTestSupport.onFx(() ->
        {
            if (stage != null) stage.close();
            return null;
        });
        Thread.sleep(500);
        FxTestSupport.onFx(() ->
        {
            UiSessionState session = MainWindow.sharedSessionState();
            session.setDatabaseSelection(previousDatabase);
            session.setMultiCompany(previousCompany);
            previousUser.ifPresentOrElse(session::setAuthenticatedUser, session::clearAuthenticatedUser);
            return null;
        });
        UiServiceRegistry.reconnectToDatabase(Path.of(previousDatabase.activeDatabasePath()));
    }

    @Test
    void stableIdEditDeactivationRefreshAndLayoutRemainUsable()
    {
        BudgetCategoryView initial = UiServiceRegistry.budgetCategoryAdmin().save(
                new BudgetCategoryCommand(null, "OPS", "Operations", true, null, null, ""));
        FxTestSupport.onFx(() ->
        {
            panel = new BudgetCategoriesPanel();
            stage = new Stage();
            stage.setScene(new Scene((VBox) panel.root(), 900, 650));
            stage.show();
            return null;
        });
        FxTestSupport.onFx(() ->
        {
            TableView<?> table = (TableView<?>) panel.root().lookup("#budgetCategoriesTable");
            assertEquals(1, table.getItems().size());
            table.getSelectionModel().select(0);
            TextField name = (TextField) panel.root().lookup("#budgetCategoriesName");
            name.setText("Retired Operations");
            CheckBox active = (CheckBox) panel.root().lookup("#budgetCategoriesActive");
            active.setSelected(false);
            assertTrue(panel.hasUnsavedChanges());
            ((Button) panel.root().lookup("#budgetCategoriesSave")).fire();
            return null;
        });
        FxTestSupport.onFx(() ->
        {
            BudgetCategoryView saved = UiServiceRegistry.budgetCategoryLookup().listForMaintenance().stream()
                    .filter(row -> row.id().equals(initial.id())).findFirst().orElseThrow();
            assertEquals("Retired Operations", saved.name());
            assertFalse(saved.active());
            ((Button) panel.root().lookup("#budgetCategoriesNew")).fire();
            ((TextField) panel.root().lookup("#budgetCategoriesCode")).setText("NEW");
            ((TextField) panel.root().lookup("#budgetCategoriesName")).setText("New Category");
            assertTrue(panel.hasUnsavedChanges());
            ((TextField) panel.root().lookup("#budgetCategoriesSearch")).setText("new");
            assertTrue(panel.hasUnsavedChanges(), "Search must not discard a draft");
            SplitPane split = (SplitPane) panel.root().lookup("#budgetCategoriesWorkspaceSplit");
            ScrollPane editor = (ScrollPane) panel.root().lookup("#budgetCategoriesEditorScroll");
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, editor.getVbarPolicy());
            split.setDividerPositions(0.25);
            panel.root().applyCss();
            ((javafx.scene.Parent) panel.root()).layout();
            assertTrue(editor.getViewportBounds().getHeight() > 0);
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(((Button) panel.root().lookup("#budgetCategoriesSave")).isDisabled());
            assertTrue(((TextField) panel.root().lookup("#budgetCategoriesName")).isDisabled());
            return null;
        });
    }
}
