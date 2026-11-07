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
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class JournalFundEventTaggingTest
{
    @TempDir Path directory;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private Optional<AuthenticatedUserSession> previousUser;
    private Stage stage;
    private JournalWorkspaceCompliancePanel panel;
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
        Path database = directory.resolve("journal-tags");
        session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        UiServiceRegistry.reconnectToDatabase(database);
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            session.setAuthenticatedUser(UiPermissionTestSessions.manager());
            first = UiServiceRegistry.activityAdmin().save(new ActivityCommand(null, "FAIR-2026", "Autumn Fair", true));
            second = UiServiceRegistry.activityAdmin().save(new ActivityCommand(null, "FAIR-2027", "Autumn Fair", true));
            UiServiceRegistry.fundAdmin().save(new org.nonprofitbookkeeping.service.FundCommand(null,
                    "RELIEF", "Relief Fund", org.nonprofitbookkeeping.model.FundType.UNRESTRICTED, true, null, null, null, null));
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
    void explicitSelectedLineTagsPreserveMixedDraftOnRefreshAndRespectPermissions() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            UiServiceRegistry.companyUiPreferences().saveState("DEFAULT", Map.of(
                    "journal.table.journalWorkspaceEntryLineTable.order", "activity,fund,account",
                    "journal.table.journalWorkspaceEntryLineTable.activity.width", "1100"));
            panel = new JournalWorkspaceCompliancePanel();
            stage = new Stage();
            stage.setScene(new Scene((javafx.scene.Parent) panel.root(), 900, 650));
            stage.show();
            return null;
        });
        await(() -> choices("journalSelectedEvent").getItems().size() == 2);
        FxTestSupport.onFx(() ->
        {
            assertEquals("activity", table().getColumns().get(0).getId());
            assertEquals(1100, table().getColumns().get(0).getPrefWidth());
            assertEquals(2, table().getItems().size());
            assertFalse(panel.hasUnsavedChanges());
            field("journalEventChoiceSearch").setText(" AUTUMN ");
            assertEquals(2, choices("journalSelectedEvent").getItems().size());
            choices("journalSelectedEvent").getSelectionModel().select(0);
            table().getSelectionModel().clearAndSelect(0);
            button("journalApplyEvent").fire();
            assertEquals(first.id(), table().getItems().get(0).getActivity().id());
            assertNull(table().getItems().get(1).getActivity());
            assertTrue(panel.hasUnsavedChanges(), "Applying a tag must protect the draft");
            choices("journalSelectedEvent").getSelectionModel().select(1);
            table().getSelectionModel().clearAndSelect(1);
            button("journalApplyEvent").fire();
            field("journalFundChoiceSearch").setText("reLief");
            choices("journalSelectedFund").getSelectionModel().select(0);
            table().getSelectionModel().selectAll();
            button("journalApplyFund").fire();
            assertEquals("RELIEF", table().getItems().get(0).getFund().code());
            assertEquals("RELIEF", table().getItems().get(1).getFund().code());
            assertEquals(first.id(), table().getItems().get(0).getActivity().id());
            assertEquals(second.id(), table().getItems().get(1).getActivity().id());
            table().getSelectionModel().clearAndSelect(1);
            button("journalClearEvent").fire();
            assertNull(table().getItems().get(1).getActivity());
            UiServiceRegistry.activityAdmin().save(new ActivityCommand(null, "NEW", "Autumn New", true));
            panel.onPanelShown();
            return null;
        });
        await(() -> choices("journalSelectedEvent").getItems().size() == 3);
        FxTestSupport.onFx(() ->
        {
            assertTrue(panel.hasUnsavedChanges());
            assertEquals(first.id(), table().getItems().get(0).getActivity().id());
            assertNull(table().getItems().get(1).getActivity());
            assertEquals("RELIEF", table().getItems().get(1).getFund().code());
            for (double width : new double[] {900, 600})
            {
                ((javafx.scene.control.TabPane) panel.root().lookup("#journalWorkPanes")).getSelectionModel().select(1);
                ((SplitPane) panel.root().lookup("#journalWorkspaceEditorSplit")).setDividerPositions(0.7);
                stage.setWidth(width);
                panel.root().setStyle("-fx-font-size: " + (width == 600 ? 17 : 13) + "px;");
                panel.root().applyCss();
                ((javafx.scene.Parent) panel.root()).layout();
                ScrollPane tagging = (ScrollPane) panel.root().lookup("#journalTaggingScroll");
                assertTrue(tagging.getViewportBounds().getWidth() > 0);
                assertTrue(tagging.getViewportBounds().getHeight() >= 60,
                        "Entry lines must not squeeze the Fund/Event controls out of view");
                assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, tagging.getHbarPolicy());
                assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, tagging.getVbarPolicy());
                assertTagActionsReachable(tagging);
            }
            SplitPane split = (SplitPane) panel.root().lookup("#journalWorkspaceEditorSplit");
            ScrollPane tags = (ScrollPane) panel.root().lookup("#journalTaggingScroll");
            split.setDividerPositions(0.2);
            ((javafx.scene.Parent) panel.root()).layout();
            double smallHeight = tags.getHeight();
            split.setDividerPositions(0.7);
            ((javafx.scene.Parent) panel.root()).layout();
            assertTrue(tags.getHeight() <= smallHeight, "Tags stay bounded while the entry/table region is resized");
            ((javafx.scene.control.TabPane) panel.root().lookup("#journalWorkPanes")).getSelectionModel().select(0);
            SplitPane filtersSplit = (SplitPane) panel.root().lookup("#journalWorkspaceFiltersSplit");
            ScrollPane filters = (ScrollPane) panel.root().lookup("#journalWorkspaceFiltersScroll");
            filtersSplit.setDividerPositions(0.1);
            ((javafx.scene.Parent) panel.root()).layout();
            assertTrue(filters.getContent().getBoundsInLocal().getHeight() > filters.getViewportBounds().getHeight());
            double filterHeight = filters.getHeight();
            filtersSplit.setDividerPositions(0.3);
            ((javafx.scene.Parent) panel.root()).layout();
            assertTrue(filters.getHeight() > filterHeight + 10);
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, filters.getHbarPolicy());
            ((javafx.scene.control.TabPane) panel.root().lookup("#journalWorkPanes")).getSelectionModel().select(1);
            panel.root().applyCss();
            ((javafx.scene.Parent) panel.root()).layout();
            assertTrue(table().getColumns().get(0).getWidth() > table().getWidth(), "Saved wide column retained");
            assertTrue(table().lookupAll(".scroll-bar").stream().map(ScrollBar.class::cast)
                    .anyMatch(bar -> bar.getOrientation() == javafx.geometry.Orientation.HORIZONTAL && bar.isVisible()), "Wide table should scroll horizontally");
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            choices("journalSelectedEvent").getSelectionModel().select(0);
            assertTrue(button("journalApplyEvent").isDisabled());
            assertTrue(button("journalApplyFund").isDisabled());
            assertTrue(button("journalClearEvent").isDisabled());
            assertFalse(button("journalRefreshChoices").isDisabled());
            return null;
        });
    }

    @Test
    void taggingControlsFitWithoutScrollbarsAndScrollWhenTheViewportIsReduced() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            panel = new JournalWorkspaceCompliancePanel();
            stage = new Stage();
            stage.setScene(new Scene((javafx.scene.Parent) panel.root(), 900, 900));
            stage.show();
            ((TabPane) panel.root().lookup("#journalWorkPanes")).getSelectionModel().select(1);
            ((SplitPane) panel.root().lookup("#journalWorkspaceEditorSplit")).setDividerPositions(0.25);
            panel.root().setStyle("-fx-font-size: 17px;");
            panel.root().applyCss();
            ((javafx.scene.Parent) panel.root()).layout();
            ScrollPane tags = (ScrollPane) panel.root().lookup("#journalTaggingScroll");

            // Measure the actual controls, rather than assuming a system font's wrap height.
            double fittingHeight = Math.ceil(tags.getContent().prefHeight(tags.getViewportBounds().getWidth())) + 32;
            tags.setMinHeight(fittingHeight);
            tags.setPrefHeight(fittingHeight);
            tags.setMaxHeight(fittingHeight);
            ((javafx.scene.Parent) panel.root()).layout();
            return null;
        });
        await(() ->
        {
            ((javafx.scene.Parent) panel.root()).layout();
            ScrollPane tags = (ScrollPane) panel.root().lookup("#journalTaggingScroll");
            return tags.getContent().getBoundsInLocal().getHeight() < tags.getViewportBounds().getHeight()
                    && !verticalScrollbar(tags).isVisible();
        }, "Fitting tagging controls did not settle");
        FxTestSupport.onFx(() ->
        {
            ScrollPane tags = (ScrollPane) panel.root().lookup("#journalTaggingScroll");
            assertTrue(tags.getContent().getBoundsInLocal().getHeight() < tags.getViewportBounds().getHeight());
            assertFalse(verticalScrollbar(tags).isVisible(), "Fitting controls must not require a scrollbar");
            assertTagActionsReachable(tags);

            // A controlled small viewport proves real overflow even after the help paragraph is removed.
            tags.setMinHeight(0);
            tags.setPrefHeight(64);
            tags.setMaxHeight(64);
            ((javafx.scene.Parent) panel.root()).layout();
            return null;
        });
        await(() ->
        {
            ((javafx.scene.Parent) panel.root()).layout();
            ScrollPane tags = (ScrollPane) panel.root().lookup("#journalTaggingScroll");
            return tags.getContent().getBoundsInLocal().getHeight() > tags.getViewportBounds().getHeight()
                    && verticalScrollbar(tags).isVisible();
        }, "Tagging overflow scrollbar did not settle");
        FxTestSupport.onFx(() ->
        {
            ScrollPane tags = (ScrollPane) panel.root().lookup("#journalTaggingScroll");
            assertTrue(tags.getContent().getBoundsInLocal().getHeight() > tags.getViewportBounds().getHeight());
            assertTrue(verticalScrollbar(tags).isVisible(), "Overflowing controls need a usable scrollbar");
            assertTrue(verticalScrollbar(tags).getMax() > verticalScrollbar(tags).getMin());
            assertTagActionsReachable(tags);
            return null;
        });
    }

    private void assertTagActionsReachable(ScrollPane tags)
    {
        tags.setHvalue(tags.getHmax());
        tags.setVvalue(tags.getVmax());
        tags.layout();
        var viewport = tags.lookup(".viewport");
        var visible = viewport.localToScene(viewport.getLayoutBounds());
        var last = button("journalMerchants");
        var bounds = last.localToScene(last.getBoundsInLocal());
        assertTrue(bounds.getMinY() >= visible.getMinY() - 1 && bounds.getMaxY() <= visible.getMaxY() + 1,
                "The last tagging action must be reachable vertically");
        assertTrue(bounds.getMinX() >= visible.getMinX() - 1 && bounds.getMaxX() <= visible.getMaxX() + 1,
                "The last tagging action must be reachable horizontally");
    }

    private static ScrollBar verticalScrollbar(ScrollPane scroll)
    {
        return scroll.lookupAll(".scroll-bar").stream().map(ScrollBar.class::cast)
                .filter(bar -> bar.getParent() == scroll && bar.getOrientation() == javafx.geometry.Orientation.VERTICAL)
                .findFirst().orElseThrow();
    }

    @Test
    @SuppressWarnings("unchecked")
    void comboInitializationKeepsExistingIdAndSelectionCommits() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            panel = new JournalWorkspaceCompliancePanel();
            stage = new Stage();
            stage.setScene(new Scene((javafx.scene.Parent) panel.root(), 1200, 800));
            stage.show();
            return null;
        });
        await(() -> choices("journalSelectedEvent").getItems().size() == 2);
        FxTestSupport.onFx(() ->
        {
            var activity = (TableColumn<JournalWorkspacePanel.EditorLine, TransactionLineEditorModel.Option>)
                    table().getColumns().stream().filter(column -> "activity".equals(column.getId())).findFirst().orElseThrow();
            var existing = new TransactionLineEditorModel.Option(first.id(), first.code(), first.name());
            table().getItems().get(0).setActivity(existing);
            table().scrollToColumn(activity);
            table().applyCss();
            table().layout();
            table().edit(0, activity);
            TableCell<?, ?> cell = table().lookupAll(".table-cell").stream().map(TableCell.class::cast)
                    .filter(candidate -> candidate.getIndex() == 0 && candidate.getTableColumn() == activity)
                    .findFirst().orElseThrow();
            assertTrue(cell.isEditing(), "Initializing the combo must not commit or cancel the edit");
            assertEquals(first.id(), table().getItems().get(0).getActivity().id());
            var editor = (ComboBox<TransactionLineEditorModel.Option>) cell.getGraphic();
            editor.getSelectionModel().select(editor.getItems().stream().filter(option -> option.id().equals(second.id())).findFirst().orElseThrow());
            assertEquals(second.id(), table().getItems().get(0).getActivity().id());
            assertFalse(cell.isEditing());
            assertTrue(panel.hasUnsavedChanges());
            return null;
        });
    }

    private static void await(BooleanSupplier condition) throws Exception
    {
        await(condition, "Journal choices did not finish loading");
    }

    private static void await(BooleanSupplier condition, String message) throws Exception
    {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (!FxTestSupport.onFx(condition::getAsBoolean))
        {
            assertTrue(System.nanoTime() < deadline, message);
            Thread.sleep(25);
        }
    }

    @SuppressWarnings("unchecked")
    private TableView<JournalWorkspacePanel.EditorLine> table()
    {
        return (TableView<JournalWorkspacePanel.EditorLine>) panel.root().lookup("#journalWorkspaceEntryLineTable");
    }

    @SuppressWarnings("unchecked")
    private ComboBox<TransactionLineEditorModel.Option> choices(String id)
    {
        return (ComboBox<TransactionLineEditorModel.Option>) panel.root().lookup("#" + id);
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
