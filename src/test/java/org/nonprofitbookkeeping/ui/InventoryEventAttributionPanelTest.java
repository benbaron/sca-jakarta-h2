package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.service.ActivityCommand;
import org.nonprofitbookkeeping.service.ActivityView;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class InventoryEventAttributionPanelTest
{
    @TempDir Path directory;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private Optional<AuthenticatedUserSession> previousUser;
    private Stage stage;
    private InventoryPanel panel;
    private ActivityView first;
    private ActivityView second;

    @BeforeEach
    void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        UiSessionState session = MainWindow.sharedSessionState();
        previousDatabase = session.databaseSelection();
        previousCompany = session.multiCompany();
        previousUser = session.authenticatedUser();
        Path database = directory.resolve("inventory-tags");
        session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        UiServiceRegistry.reconnectToDatabase(database);
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            session.setAuthenticatedUser(UiPermissionTestSessions.manager());
            first = UiServiceRegistry.activityAdmin().save(new ActivityCommand(null, "FAIR-2026", "Autumn Fair", true));
            second = UiServiceRegistry.activityAdmin().save(new ActivityCommand(null, "FAIR-2027", "Autumn Fair", true));
            UiServiceRegistry.budgetCategoryAdmin().upsert("COST", "Event Cost", true);
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
            if (stage != null)
            {
                stage.close();
            }
            return null;
        });
        // Let the existing debounced layout saves finish before restoring authentication.
        Thread.sleep(600);
        FxTestSupport.onFx(() ->
        {
            DrillThroughCoordinator.configureOpener(null);
            UiSessionState session = MainWindow.sharedSessionState();
            session.setDatabaseSelection(previousDatabase);
            session.setMultiCompany(previousCompany);
            if (previousUser.isPresent())
            {
                session.setAuthenticatedUser(previousUser.get());
            }
            else
            {
                session.clearAuthenticatedUser();
            }
            return null;
        });
        UiServiceRegistry.reconnectToDatabase(Path.of(previousDatabase.activeDatabasePath()));
    }

    @Test
    void explicitChoicesRefreshWithoutLosingDraftAndControlsRemainReachable()
    {
        FxTestSupport.onFx(() ->
        {
            panel = new InventoryPanel();
            stage = new Stage();
            stage.setScene(new Scene((javafx.scene.Parent) panel.root(), 900, 650));
            stage.show();
            var event = choices("inventoryMovementEvent");
            var budget = choices("inventoryMovementBudget");
            CheckBox nonEvent = (CheckBox) panel.root().lookup("#inventoryNonEvent");
            assertFalse(nonEvent.isSelected());
            assertNull(event.getValue());
            assertEquals(2, event.getItems().size());
            event.getSelectionModel().select(0);
            budget.getSelectionModel().select(0);
            var selectedBudget = budget.getValue();
            nonEvent.setSelected(true);
            assertNull(event.getValue());
            assertEquals(selectedBudget, budget.getValue());
            event.getSelectionModel().select(1);
            assertFalse(nonEvent.isSelected());
            var selectedEvent = event.getValue();
            TextField quantity = (TextField) panel.root().lookup("#inventoryMovementQuantity");
            TextField notes = (TextField) panel.root().lookup("#inventoryMovementNotes");
            quantity.setText("10");
            notes.setText("Event cost draft");
            UiServiceRegistry.activityAdmin().save(new ActivityCommand(null, "NEW", "New event", true));
            panel.onPanelShown();
            assertEquals(3, event.getItems().size());
            assertEquals(selectedEvent.id(), event.getValue().id());
            assertEquals(selectedBudget.id(), budget.getValue().id());
            assertEquals("10", quantity.getText());
            assertEquals("Event cost draft", notes.getText());
            UiServiceRegistry.activityAdmin().save(new ActivityCommand(second.id(), second.code(), second.name(), false));
            button("inventoryRefreshTags").fire();
            assertNull(event.getValue(), "An inactive Event cannot remain a selectable new attribution");
            assertFalse(nonEvent.isSelected(), "Refresh must not silently confirm Non-event");
            assertEquals(selectedBudget.id(), budget.getValue().id());

            SplitPane workspace = (SplitPane) panel.root().lookup("#inventoryMovementWorkspaceSplit");
            ScrollPane controls = (ScrollPane) panel.root().lookup("#inventoryMovementControlsScroll");
            SplitPane tables = (SplitPane) panel.root().lookup("#inventoryTablesSplit");
            assertEquals(2, tables.getItems().size());
            for (double width : new double[] {900, 600})
            {
                stage.setWidth(width);
                panel.root().setStyle("-fx-font-size: " + (width == 600 ? 17 : 13) + "px;");
                workspace.setDividerPositions(0.12);
                panel.root().applyCss();
                ((javafx.scene.Parent) panel.root()).layout();
                assertTrue(controls.getViewportBounds().getWidth() > 0);
                assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, controls.getHbarPolicy());
                assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, controls.getVbarPolicy());
                assertTrue(controls.getContent().getBoundsInLocal().getHeight() > controls.getViewportBounds().getHeight());
                assertTrue(controls.getContent().getBoundsInLocal().getWidth() > controls.getViewportBounds().getWidth(),
                        "Wide movement inputs must expose horizontal scrolling");
            }
            double smallHeight = controls.getHeight();
            workspace.setDividerPositions(0.6);
            ((javafx.scene.Parent) panel.root()).layout();
            assertTrue(controls.getHeight() > smallHeight + 10);
            var history = panel.root().lookupAll(".table-view").stream().map(TableView.class::cast)
                    .filter(table -> table.getColumns().stream().anyMatch(column ->
                            "Event / Activity".equals(((TableColumn<?, ?>) column).getText())))
                    .findFirst().orElseThrow();
            assertTrue(history.getColumns().stream().anyMatch(column ->
                    "Budget category".equals(((TableColumn<?, ?>) column).getText())));
            var historyRow = new org.nonprofitbookkeeping.service.InventoryMovementView(1L, 1L, "Event stock",
                    java.time.LocalDate.of(2026, 5, 15), org.nonprofitbookkeeping.model.InventoryMovement.MovementType.ISSUE,
                    java.math.BigDecimal.TEN.negate(), java.math.BigDecimal.ZERO, new java.math.BigDecimal("20"),
                    1L, "Event cost", "FAIR — Autumn Fair", "COST — Event Cost");
            history.getItems().setAll(java.util.Collections.nCopies(40, historyRow));
            ((TableColumn<?, ?>) history.getColumns().get(0)).setPrefWidth(1200);
            panel.root().applyCss();
            ((javafx.scene.Parent) panel.root()).layout();
            for (javafx.geometry.Orientation orientation : javafx.geometry.Orientation.values())
            {
                assertTrue(history.lookupAll(".scroll-bar").stream().map(ScrollBar.class::cast)
                        .anyMatch(bar -> ((ScrollBar) bar).getOrientation() == orientation && ((ScrollBar) bar).isVisible()),
                        "Movement history must scroll " + orientation);
            }
            tables.setDividerPositions(0.25);
            ((javafx.scene.Parent) panel.root()).layout();
            double historyHeight = history.getHeight();
            tables.setDividerPositions(0.75);
            ((javafx.scene.Parent) panel.root()).layout();
            assertTrue(history.getHeight() < historyHeight - 10, "History independently resizes");
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(button("inventoryReceive").isDisabled());
            assertTrue(button("inventoryIssue").isDisabled());
            assertFalse(button("inventoryRefreshTags").isDisabled());
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private ComboBox<TransactionLineEditorModel.Option> choices(String id)
    {
        return (ComboBox<TransactionLineEditorModel.Option>) panel.root().lookup("#" + id);
    }

    private Button button(String id)
    {
        return (Button) panel.root().lookup("#" + id);
    }
}
