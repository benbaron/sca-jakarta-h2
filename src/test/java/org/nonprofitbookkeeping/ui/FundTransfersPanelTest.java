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
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;
import org.nonprofitbookkeeping.service.FundTransferService.Choice;
import org.nonprofitbookkeeping.service.FundTransferTestFixture;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class FundTransfersPanelTest
{
    @TempDir Path directory;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private Optional<AuthenticatedUserSession> previousUser;
    private FundTransferTestFixture fixture;
    private Stage stage;
    private FundTransfersPanel panel;

    @BeforeEach
    void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        var session = MainWindow.sharedSessionState();
        previousDatabase = session.databaseSelection();
        previousCompany = session.multiCompany();
        previousUser = session.authenticatedUser();
        Path database = directory.resolve("transfer-ui");
        try (Jpa jpa = new Jpa(database))
        {
            fixture = FundTransferTestFixture.seed(jpa);
        }
        session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        UiServiceRegistry.reconnectToDatabase(database);
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            session.setAuthenticatedUser(UiPermissionTestSessions.manager());
            panel = new FundTransfersPanel(() -> false);
            CompanyTableStateBinder.applyProductionPanel(panel.root(), AppPanelId.FUND_TRANSFERS);
            stage = new Stage();
            stage.setScene(new Scene((VBox) panel.root(), 900, 700));
            stage.show();
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
            stage.close();
            return null;
        });
        Thread.sleep(500);
        FxTestSupport.onFx(() ->
        {
            var session = MainWindow.sharedSessionState();
            session.setDatabaseSelection(previousDatabase);
            session.setMultiCompany(previousCompany);
            previousUser.ifPresentOrElse(session::setAuthenticatedUser, session::clearAuthenticatedUser);
            return null;
        });
        UiServiceRegistry.reconnectToDatabase(Path.of(previousDatabase.activeDatabasePath()));
    }

    @Test
    void saveRetryDirtyRefreshJournalAndDatedReversalUseRealService()
    {
        FxTestSupport.onFx(() ->
        {
            ((DatePicker) panel.root().lookup("#fundTransfersDate")).setValue(FundTransferTestFixture.DATE);
            select("From", fixture.source());
            select("To", fixture.destination());
            select("Allocation", fixture.allocation());
            select("Equity", fixture.equity());
            text("Amount").setText("$75.00");
            ((TextArea) panel.root().lookup("#fundTransfersExplanation")).setText("Designated allocation");
            assertTrue(panel.hasUnsavedChanges());
            button("Save").fire();
            assertEquals(1, UiServiceRegistry.fundTransfers().list().size(), panel.commandResultMessage(AppCommand.SAVE_ACTIVE));
            assertFalse(panel.hasUnsavedChanges());
            button("Save").fire();
            assertEquals(1, UiServiceRegistry.fundTransfers().list().size(), "Save retry must reuse its request identity");
            var saved = UiServiceRegistry.fundTransfers().list().get(0);
            button("Journal").fire();
            assertEquals(saved.transactionId(), JournalWorkspacePanel.transactionIdFromContext(DrillThroughCoordinator.consumeContext(AppPanelId.JOURNAL_PANE)));
            button("New").fire();
            text("Amount").setText("20");
            button("Refresh").fire();
            button("New").fire();
            assertEquals("20", text("Amount").getText(), "Refresh and declined discard preserve draft");
            TableView<?> table = (TableView<?>) panel.root().lookup("#fundTransfersTable");
            table.getSelectionModel().select(0);
            ((DatePicker) panel.root().lookup("#fundTransfersReversalDate")).setValue(FundTransferTestFixture.DATE.plusDays(5));
            text("Reason").setText("Correction");
            button("Reverse").fire();
            assertEquals(2, UiServiceRegistry.fundTransfers().list().size(), panel.commandResultMessage(AppCommand.SAVE_ACTIVE));
            assertEquals("20", text("Amount").getText());
            assertTrue(panel.hasUnsavedChanges());
            assertTrue(((Label) panel.root().lookup("#fundTransfersRetention")).getText().contains("cannot be edited or deleted"));
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(button("Save").isDisabled());
            assertTrue(button("Reverse").isDisabled());
            assertTrue(text("Amount").isDisabled());
            return null;
        });
    }

    @Test
    void scaledSmallViewportProvidesIndependentDividersAndRealTwoAxisOverflow() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            stage.setWidth(520);
            stage.setHeight(550);
            panel.root().setStyle("-fx-font-size: 17px;");
            ((SplitPane) panel.root().lookup("#fundTransfersMainSplit")).setDividerPositions(0.25);
            ((SplitPane) panel.root().lookup("#fundTransfersWorkspaceSplit")).setDividerPositions(0.85);
            panel.root().applyCss();
            ((VBox) panel.root()).layout();
            return null;
        });
        await(() ->
        {
            ScrollPane editor = (ScrollPane) panel.root().lookup("#fundTransfersEditorScroll");
            return editor.getContent().getLayoutBounds().getWidth() > editor.getViewportBounds().getWidth()
                    && editor.getContent().getLayoutBounds().getHeight() > editor.getViewportBounds().getHeight()
                    && editor.lookupAll(".scroll-bar").stream().filter(node -> node instanceof ScrollBar bar && bar.isVisible()).count() == 2;
        });
        FxTestSupport.onFx(() ->
        {
            TableView<?> table = (TableView<?>) panel.root().lookup("#fundTransfersTable");
            assertTrue(CompanyTableStateBinder.isCompanyStateOwned(table));
            CompanyTableStateBinder.saveNow(table);
            assertFalse(UiServiceRegistry.companyUiPreferences().loadState("DEFAULT", "ui.table.fund_transfers.").isEmpty());
            assertEquals(2, ((SplitPane) panel.root().lookup("#fundTransfersMainSplit")).getItems().size());
            assertTrue(table.lookupAll(".scroll-bar").stream().anyMatch(node -> node instanceof ScrollBar bar && bar.isVisible()
                    && bar.getOrientation() == javafx.geometry.Orientation.HORIZONTAL));
            return null;
        });
    }

    private void await(java.util.function.BooleanSupplier condition) throws Exception
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline)
        {
            if (FxTestSupport.onFx(condition::getAsBoolean))
            {
                return;
            }
            Thread.sleep(30);
        }
        String geometry = FxTestSupport.onFx(() ->
        {
            ScrollPane editor = (ScrollPane) panel.root().lookup("#fundTransfersEditorScroll");
            var content = (javafx.scene.layout.Region) editor.getContent();
            return "content=" + content.getLayoutBounds() + ", viewport=" + editor.getViewportBounds()
                    + ", minWidth=" + content.minWidth(-1) + ", prefWidth=" + content.prefWidth(-1)
                    + ", bars=" + editor.lookupAll(".scroll-bar").stream()
                    .filter(ScrollBar.class::isInstance).map(ScrollBar.class::cast)
                    .map(bar -> bar.getOrientation() + ":" + bar.isVisible()).toList();
        });
        fail("Timed out waiting for transfer panel layout: " + geometry);
    }

    private Button button(String suffix)
    {
        return (Button) panel.root().lookup("#fundTransfers" + suffix);
    }

    private TextField text(String suffix)
    {
        return (TextField) panel.root().lookup("#fundTransfers" + suffix);
    }

    @SuppressWarnings("unchecked")
    private void select(String suffix, long id)
    {
        ComboBox<Choice> combo = (ComboBox<Choice>) panel.root().lookup("#fundTransfers" + suffix);
        combo.setValue(combo.getItems().stream().filter(c -> c.id() == id).findFirst().orElseThrow());
    }
}
