package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.service.CloseReviewEvidenceService;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class CloseReviewEvidencePaneTest
{
    @TempDir Path directory;
    private Stage stage;
    private PeriodCloseRunsPanel panel;
    private CloseReviewEvidencePane evidence;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private java.util.Optional<org.nonprofitbookkeeping.service.AuthenticatedUserSession> previousUser;

    @BeforeEach
    void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            var session = MainWindow.sharedSessionState();
            previousDatabase = session.databaseSelection(); previousCompany = session.multiCompany(); previousUser = session.authenticatedUser();
            session.setAuthenticatedUser(UiPermissionTestSessions.manager());
            session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
            Path db = directory.resolve("evidence-ui");
            session.setDatabaseSelection(new DatabaseSelectionState(db.toString(), List.of(db.toString())));
            UiServiceRegistry.reconnectToDatabase(db);
            panel = new PeriodCloseRunsPanel(); evidence = panel.reviewEvidenceForTests();
            stage = new Stage(); stage.setScene(new Scene((javafx.scene.Parent) panel.root(), 1000, 750)); stage.show();
            ((TabPane) panel.root().lookup("#periodCloseTabs")).getSelectionModel().select(2);
            return null;
        });
    }

    @AfterEach
    void cleanup()
    {
        FxTestSupport.onFx(() ->
        {
            if (stage != null) stage.close();
            var session = MainWindow.sharedSessionState();
            session.setMultiCompany(previousCompany); session.setDatabaseSelection(previousDatabase);
            previousUser.ifPresentOrElse(session::setAuthenticatedUser, session::clearAuthenticatedUser);
            return null;
        });
    }

    @Test
    void actualPreviewAndSharedSaveRecordFactsWithPermissionBusyAndReadOnlyHistory() throws Exception
    {
        FxTestSupport.onFx(() -> { button("closeReviewPreview").fire(); assertTrue(button("closeReviewPreview").isDisabled()); return null; });
        evidence.completion().get(60, TimeUnit.SECONDS);
        FxTestSupport.onFx(() ->
        {
            addReference();
            ((CheckBox) evidence.root().lookup("#closeReviewAttested")).setSelected(true);
            assertEquals("Save Review", panel.commandLabel(AppCommand.SAVE_ACTIVE));
            assertTrue(panel.supportsCommand(AppCommand.SAVE_ACTIVE)); assertTrue(panel.hasUnsavedChanges());
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE)); assertTrue(button("closeReviewSave").isDisabled());
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.manager());
            assertTrue(panel.executeCommand(AppCommand.SAVE_ACTIVE).handled());
            assertTrue(button("closeReviewSave").isDisabled());
            assertTrue(((ScrollPane) evidence.root().lookup("#closeReviewEditorScroll")).getContent().isDisabled());
            return null;
        });
        var result = (CloseReviewEvidenceService.Review) evidence.completion().get(60, TimeUnit.SECONDS);
        assertEquals("operator", result.actor()); assertTrue(result.blockers() > 0);
        FxTestSupport.onFx(() ->
        {
            assertFalse(panel.hasUnsavedChanges()); assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            assertTrue(button("closeReviewSave").isDisabled());
            TableView<?> history = table("closeReviewHistory"); assertEquals(1, history.getItems().size());
            assertTrue(history.getColumns().stream().allMatch(c -> c.isSortable() && c.isResizable() && c.isReorderable()));
            ((TabPane) evidence.root().lookup("#closeReviewDetails")).getSelectionModel().select(1);
            evidence.root().applyCss();
            var retained = (TextArea) evidence.root().lookup("#closeReviewRetainedDetails");
            assertFalse(retained.isEditable()); assertTrue(retained.getText().contains("external/ui-receipt"));
            assertTrue(((Label) evidence.root().lookup("#closeReviewStatus")).getText().contains("blocker(s) remain"));
            return null;
        });
        assertEquals(1, UiServiceRegistry.closeReviewEvidence().history(java.time.LocalDate.parse(result.payload().start()),
                java.time.LocalDate.parse(result.payload().end())).size());
    }

    @Test
    void pendingPreviewForChangedScopeIsDiscardedAndDraftIsNotRetargeted() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            button("closeReviewPreview").fire();
            evidence.invalidate(true);
            return null;
        });
        assertThrows(java.util.concurrent.CancellationException.class, () -> evidence.completion().get(60, TimeUnit.SECONDS));
        FxTestSupport.onFx(() ->
        {
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE)); assertTrue(table("closeReviewReferences").getItems().isEmpty());
            assertFalse(((CheckBox) evidence.root().lookup("#closeReviewAttested")).isSelected());
            return null;
        });
    }

    @Test
    void referencesRequireExplicitAttestationAndDraftStatePropagatesAcrossTabs() throws Exception
    {
        FxTestSupport.onFx(() -> { button("closeReviewPreview").fire(); return null; });
        evidence.completion().get(60, TimeUnit.SECONDS);
        FxTestSupport.onFx(() ->
        {
            addReference();
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE)); assertTrue(panel.hasUnsavedChanges());
            ((CheckBox) evidence.root().lookup("#closeReviewAttested")).setSelected(true);
            assertTrue(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            ScrollPane scroll = (ScrollPane) evidence.root().lookup("#closeReviewEditorScroll");
            assertTrue(scroll.isFitToWidth());
            assertTrue(scroll.getViewportBounds().getWidth() > 0);
            assertTrue(scroll.getContent().getBoundsInLocal().getHeight() > scroll.getViewportBounds().getHeight(),
                    "The long form must remain scrollable within its bounded editor viewport");
            var main = (SplitPane) ((VBox) evidence.root()).getChildren().get(3);
            main.setDividerPositions(0.3);
            evidence.root().applyCss(); ((javafx.scene.Parent) evidence.root()).layout();
            assertEquals(0.3, main.getDividerPositions()[0], 0.03);
            var table = table("closeReviewReferences");
            table.getColumns().get(1).setPrefWidth(1400);
            evidence.root().applyCss(); ((javafx.scene.Parent) evidence.root()).layout();
            assertTrue(table.lookupAll(".scroll-bar").stream().filter(node -> node instanceof ScrollBar)
                    .map(node -> (ScrollBar) node).anyMatch(bar -> bar.getOrientation() == javafx.geometry.Orientation.HORIZONTAL && bar.isVisible()));
            ((TabPane) panel.root().lookup("#periodCloseTabs")).getSelectionModel().select(0);
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE)); assertTrue(panel.hasUnsavedChanges());
            ((TabPane) panel.root().lookup("#periodCloseTabs")).getSelectionModel().select(2);
            evidence.invalidate(false);
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            assertEquals(1, table("closeReviewReferences").getItems().size());
            assertTrue(panel.hasUnsavedChanges());
            return null;
        });
    }

    @Test
    void actualCloseActionRejectsMissingReviewAndTracksBusyAndPermission() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            Button close = (Button) panel.root().lookup("#periodCloseSaveClosedRange");
            close.fire(); assertTrue(close.isDisabled());
            return null;
        });
        var failed = assertThrows(java.util.concurrent.ExecutionException.class, () -> panel.closeCompletionForTests().get(30, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, failed.getCause());
        FxTestSupport.onFx(() ->
        {
            Button close = (Button) panel.root().lookup("#periodCloseSaveClosedRange");
            assertFalse(close.isDisabled());
            assertTrue(((Label) panel.root().lookup("#periodCloseStatus")).getText().contains("Could not close range"));
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer()); assertTrue(close.isDisabled());
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private void addReference()
    {
        ((ComboBox<CloseReviewEvidenceService.ReferenceKind>) evidence.root().lookup("#closeReviewReferenceKind")).setValue(CloseReviewEvidenceService.ReferenceKind.RECEIPT);
        ((TextField) evidence.root().lookup("#closeReviewReference")).setText("external/ui-receipt");
        button("closeReviewAddReference").fire();
    }

    private Button button(String id) { return (Button) evidence.root().lookup("#" + id); }
    private TableView<?> table(String id)
    {
        return CompanyTableStateBinder.findTables(evidence.root()).stream().filter(t -> id.equals(t.getId())).findFirst().orElseThrow();
    }
}
