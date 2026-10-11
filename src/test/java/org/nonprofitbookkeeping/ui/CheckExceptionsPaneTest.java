package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.*;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class CheckExceptionsPaneTest
{
    @TempDir Path directory;
    DatabaseSelectionState previousDatabase;
    MultiCompanyState previousCompany;
    Optional<AuthenticatedUserSession> previousUser;
    Stage stage;
    CheckExceptionsPane pane;

    @BeforeEach
    void setup() throws Exception
    {
        FxTestSupport.initToolkitOrSkip();
        var session = MainWindow.sharedSessionState();
        previousDatabase = session.databaseSelection(); previousCompany = session.multiCompany(); previousUser = session.authenticatedUser();
        Path path = directory.resolve("check-review");
        session.setDatabaseSelection(new DatabaseSelectionState(path.toString(), List.of(path.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        UiServiceRegistry.reconnectToDatabase(path);
        try (Jpa jpa = new Jpa(path))
        {
            var f = FundTransferTestFixture.seed(jpa);
            new TransactionEntryService(jpa).enter(new TransactionCommand(FundTransferTestFixture.DATE, null, "Check", null, List.of(
                    FundTransferTestFixture.line(f.expense(), f.source(), "10", "0"),
                    new TransactionLineCommand(f.bank(), f.source(), null, null, null, java.math.BigDecimal.ZERO,
                            java.math.BigDecimal.TEN, false, null,
                            new PaymentReference(PaymentReference.Method.CHECK, "000123", FundTransferTestFixture.DATE, null)))));
        }
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false); session.setAuthenticatedUser(UiPermissionTestSessions.manager());
            pane = new CheckExceptionsPane(); stage = new Stage(); stage.setScene(new Scene((javafx.scene.Parent)pane.root(), 950, 700)); stage.show();
            date("checkReviewThrough").setValue(LocalDate.of(2026, 10, 1));
            date("checkActionDate").setValue(LocalDate.of(2026, 10, 1)); return null;
        });
        await(() -> !table().getItems().isEmpty());
    }

    @AfterEach
    void cleanup() throws Exception
    {
        if (previousDatabase == null) { return; }
        FxTestSupport.onFx(() -> { if (stage != null) { stage.close(); } return null; });
        Thread.sleep(600);
        FxTestSupport.onFx(() ->
        {
            var session = MainWindow.sharedSessionState(); session.setDatabaseSelection(previousDatabase); session.setMultiCompany(previousCompany);
            if (previousUser.isPresent()) { session.setAuthenticatedUser(previousUser.get()); } else { session.clearAuthenticatedUser(); }
            return null;
        });
        UiServiceRegistry.reconnectToDatabase(Path.of(previousDatabase.activeDatabasePath()));
    }

    @Test
    void factualReviewSavesAndLivePermissionsAndScrollingRemainUsable() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            table().getSelectionModel().selectFirst();
            ((TextArea)pane.root().lookup("#checkReviewNote")).setText("Payee confirms valid obligation");
            ((TextField)pane.root().lookup("#checkEvidenceReference")).setText("receipt/000123");
            assertTrue(pane.hasUnsavedChanges());
            button("checkRecordReview").fire(); return null;
        });
        await(() -> !pane.hasUnsavedChanges());
        assertEquals("receipt/000123", UiServiceRegistry.checkExceptions().report(null, LocalDate.of(2026, 10, 1), null).get(0).facts().evidenceReference());
        FxTestSupport.onFx(() ->
        {
            assertTrue(table().getColumns().stream().anyMatch(c -> "Replacement".equals(c.getText())));
            stage.setWidth(500); stage.setHeight(380); pane.root().applyCss(); pane.root().autosize();
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(button("checkRecordReview").isDisabled()); assertTrue(button("checkReissue").isDisabled());
            assertFalse(button("checkReviewRefresh").isDisabled()); return null;
        });
        await(() -> table().lookupAll(".scroll-bar").stream().filter(ScrollBar.class::isInstance)
                .map(ScrollBar.class::cast).anyMatch(s -> s.getOrientation() == javafx.geometry.Orientation.HORIZONTAL && s.isVisible()));
        await(() ->
        {
            var editor = (ScrollPane)pane.root().lookup("#checkReviewEditorScroll");
            var bars = editor.lookupAll(".scroll-bar").stream().filter(ScrollBar.class::isInstance).map(ScrollBar.class::cast).toList();
            return editor.getContent().getBoundsInLocal().getWidth() > editor.getViewportBounds().getWidth()
                    && editor.getContent().getBoundsInLocal().getHeight() > editor.getViewportBounds().getHeight()
                    && bars.stream().anyMatch(s -> s.getOrientation() == javafx.geometry.Orientation.HORIZONTAL && s.isVisible())
                    && bars.stream().anyMatch(s -> s.getOrientation() == javafx.geometry.Orientation.VERTICAL && s.isVisible());
        });
        FxTestSupport.onFx(() ->
        {
            var split = (SplitPane)pane.root().lookup("#checkReviewDetailSplit");
            double before = split.getDividerPositions()[0]; split.setDividerPositions(.4);
            assertNotEquals(before, split.getDividerPositions()[0]);
            var editor = (ScrollPane)pane.root().lookup("#checkReviewEditorScroll");
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, editor.getVbarPolicy());
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, editor.getHbarPolicy()); return null;
        });
    }

    @SuppressWarnings("unchecked")
    TableView<CheckExceptionService.Row> table() { return (TableView<CheckExceptionService.Row>)pane.root().lookup("#checkExceptionsTable"); }
    Button button(String id) { return (Button)pane.root().lookup("#"+id); }
    DatePicker date(String id) { return (DatePicker)pane.root().lookup("#"+id); }
    private static void await(BooleanSupplier condition) throws Exception
    {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (!FxTestSupport.onFx(condition::getAsBoolean))
        {
            assertTrue(System.nanoTime() < deadline, "Check review did not finish loading"); Thread.sleep(25);
        }
    }
}
