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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class EventDiscoveryPanelTest
{
    @TempDir Path directory;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private Optional<AuthenticatedUserSession> previousUser;
    private Stage stage;
    private ActivitiesPanel panel;
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
        Path database = directory.resolve("event-discovery");
        session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        UiServiceRegistry.reconnectToDatabase(database);
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            session.setAuthenticatedUser(UiPermissionTestSessions.manager());
            first = UiServiceRegistry.activityAdmin().save(new ActivityCommand(null, "FAIR-2026", "Autumn Fair", true));
            second = UiServiceRegistry.activityAdmin().save(new ActivityCommand(null, "FAIR-2027", "Autumn Fair", false));
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
    void searchPreservesDraftAndRepeatedNamesRemainDistinct() throws Exception
    {
        showPanel();
        FxTestSupport.onFx(() ->
        {
            field("activitiesSearch").setText("  aUTUmn  ");
            assertEquals(List.of(first.id(), second.id()), table().getItems().stream().map(ActivityView::id).toList());
            table().getSelectionModel().select(first);
            field("activitiesName").setText("Unsaved rename");
            field("activitiesSearch").setText("2027");
            assertEquals(List.of(second.id()), table().getItems().stream().map(ActivityView::id).toList());
            assertEquals("Unsaved rename", field("activitiesName").getText());
            assertTrue(panel.hasUnsavedChanges());
            table().getSelectionModel().select(second);
            assertEquals("Unsaved rename", field("activitiesName").getText());
            field("activitiesSearch").clear();
            table().getSelectionModel().select(first);
            assertEquals("Unsaved rename", field("activitiesName").getText());
            button("activitiesSave").fire();
            return null;
        });
        await(() -> table().getSelectionModel().getSelectedItem() != null && !panel.hasUnsavedChanges());
        assertEquals("Unsaved rename", UiServiceRegistry.activityLookup().listAllActivities().stream()
                .filter(row -> row.id().equals(first.id())).findFirst().orElseThrow().name());
        FxTestSupport.onFx(() ->
        {
            button("activitiesNewEvent").fire();
            field("activitiesCode").setText("NEW-2028");
            field("activitiesName").setText("Unsaved new event");
            table().getSelectionModel().select(second);
            assertNull(table().getSelectionModel().getSelectedItem());
            assertEquals("Unsaved new event", field("activitiesName").getText());
            assertTrue(panel.hasUnsavedChanges());
            button("activitiesSave").fire();
            return null;
        });
        await(() -> table().getItems().size() == 3 && table().getSelectionModel().getSelectedItem() != null);
        FxTestSupport.onFx(() ->
        {
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            for (String id : List.of("activitiesNewEvent", "activitiesEdit", "activitiesSave", "activitiesName", "activitiesCode", "activitiesActive"))
            {
                assertTrue(panel.root().lookup("#" + id).isDisabled(), id);
            }
            assertFalse(field("activitiesSearch").isDisabled());
            return null;
        });
    }

    @Test
    void existingLayoutScrollsAndAccountingRoutesToSameMaintenance() throws Exception
    {
        for (int i = 0; i < 20; i++)
        {
            UiServiceRegistry.activityAdmin().save(new ActivityCommand(null, "EVENT-" + i, "Event " + i, true));
        }
        UiServiceRegistry.companyUiPreferences().saveState("DEFAULT", Map.of(
                "activities.divider", "0.73", "activities.table.width.name", "1100",
                "activities.table.order", "name,code,active", "activities.table.sort", "name:DESC"));
        showPanel();
        FxTestSupport.onFx(() ->
        {
            assertEquals("name", table().getColumns().get(0).getUserData());
            assertEquals(TableColumn.SortType.DESCENDING, table().getColumns().get(0).getSortType());
            SplitPane split = (SplitPane) panel.root().lookup("#activitiesWorkspaceSplit");
            ScrollPane editor = (ScrollPane) panel.root().lookup("#activitiesEditorScroll");
            ScrollPane header = (ScrollPane) panel.root().lookup("#activitiesHeaderScroll");
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, editor.getHbarPolicy());
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, editor.getVbarPolicy());
            for (double width : new double[] {900, 600})
            {
                stage.setWidth(width);
                panel.root().setStyle("-fx-font-size: " + (width == 600 ? 17 : 13) + "px;");
                ((VBox) panel.root()).applyCss();
                ((VBox) panel.root()).layout();
                assertTrue(editor.getViewportBounds().getWidth() > 0);
                assertTrue(header.getViewportBounds().getWidth() > 0);
                assertTrue(table().lookupAll(".scroll-bar").stream().map(ScrollBar.class::cast)
                        .anyMatch(bar -> bar.getOrientation() == javafx.geometry.Orientation.HORIZONTAL && bar.isVisible()));
                assertTrue(table().getColumns().get(0).getWidth() > table().getWidth());
                assertTrue(table().lookupAll(".scroll-bar").stream().map(ScrollBar.class::cast)
                        .anyMatch(bar -> bar.getOrientation() == javafx.geometry.Orientation.VERTICAL && bar.isVisible()));
            }
            double oldHeight = editor.getHeight();
            split.setDividerPositions(0.35);
            ((VBox) panel.root()).layout();
            assertTrue(editor.getHeight() > oldHeight + 10);
            // Narrow the lower region enough to require vertical editor scrolling.
            split.setDividerPositions(0.85);
            ((VBox) panel.root()).layout();
            assertTrue(editor.getContent().getBoundsInLocal().getHeight() > editor.getViewportBounds().getHeight());
            AtomicReference<AppPanelId> route = new AtomicReference<>();
            DrillThroughCoordinator.configureOpener(route::set);
            EventAccountingPanel accounting = new EventAccountingPanel();
            stage.setScene(new Scene((javafx.scene.Parent) accounting.root(), 900, 600));
            accounting.root().applyCss();
            ((javafx.scene.Parent) accounting.root()).layout();
            ActivityView added = UiServiceRegistry.activityAdmin().save(new ActivityCommand(null, "RETURN", "Newly created event", true));
            accounting.onPanelShown();
            ComboBox<?> choices = (ComboBox<?>) accounting.root().lookup("#eventAccountingActivity");
            assertTrue(choices.getItems().stream().anyMatch(choice -> choice.toString().contains(added.name())));
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            Button manage = (Button) accounting.root().lookup("#eventAccountingManageEvents");
            assertFalse(manage.isDisabled());
            manage.fire();
            assertEquals(AppPanelId.ACTIVITIES, route.get());
            return null;
        });
    }

    private void showPanel() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            panel = new ActivitiesPanel(() -> false);
            stage = new Stage();
            stage.setScene(new Scene((javafx.scene.Parent) panel.root(), 900, 650));
            stage.show();
            return null;
        });
        await(() -> table().getItems().size() >= 2);
    }

    private static void await(BooleanSupplier condition) throws Exception
    {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (!FxTestSupport.onFx(condition::getAsBoolean))
        {
            assertTrue(System.nanoTime() < deadline, "Asynchronous event list did not finish loading");
            Thread.sleep(25);
        }
    }

    @SuppressWarnings("unchecked")
    private TableView<ActivityView> table()
    {
        return (TableView<ActivityView>) panel.root().lookup("#activitiesTable");
    }

    private TextField field(String id)
    {
        return (TextField) panel.root().lookup("#" + id);
    }

    private Button button(String id)
    {
        return (Button) panel.root().lookup("#" + id);
    }
}
