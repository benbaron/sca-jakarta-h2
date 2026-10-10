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
class JournalPaymentReferencesTest
{
    @TempDir Path directory;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private Optional<AuthenticatedUserSession> previousUser;
    private Stage stage;
    private JournalWorkspaceCompliancePanel panel;
    private org.nonprofitbookkeeping.service.FundTransferTestFixture fixture;

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
        try (var jpa = new org.nonprofitbookkeeping.persistence.Jpa(database))
        {
            fixture = org.nonprofitbookkeeping.service.FundTransferTestFixture.seed(jpa);
        }
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
    void bankLineFactsSaveReloadAndProtectAnUnsavedPaymentOnlyChange() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            panel = new JournalWorkspaceCompliancePanel();
            stage = new Stage(); stage.setScene(new Scene(new VBox(panel.root()), 950, 700)); stage.show();
            panel.onPanelShown(); return null;
        });
        await(() -> choices("journalSelectedFund").getItems().size() >= 2);
        FxTestSupport.onFx(() ->
        {
            var expense = table().getItems().get(0); var bank = table().getItems().get(1);
            expense.setAccount(new TransactionLineEditorModel.Option(fixture.expense(), "5000", "Expense"));
            expense.setFund(new TransactionLineEditorModel.Option(fixture.source(), "GENERAL", "General")); expense.setDebit("10");
            bank.setAccount(new TransactionLineEditorModel.Option(fixture.bank(), "1000", "Bank"));
            bank.setFund(expense.getFund()); bank.setCredit("10");
            bank.paymentMethod.set("CHECK"); bank.paymentReference.set("000123");
            bank.paymentIssued.set("2026-02-01"); bank.paymentDelivered.set("2026-02-02");
            button("journalWorkspaceSaveButton").fire(); return null;
        });
        await(() -> !panel.hasUnsavedChanges());
        var saved = UiServiceRegistry.transactionEntry().search(null, null, "000123", 20).get(0);
        assertEquals("000123", saved.lines().get(1).payment().reference());
        FxTestSupport.onFx(() ->
        {
            var method = (TableColumn<JournalWorkspacePanel.EditorLine, String>) table().getColumns().stream()
                    .filter(c -> "Payment method".equals(c.getText())).findFirst().orElseThrow();
            table().scrollToColumn(method); table().applyCss(); table().layout(); table().edit(1, method);
            var cell = table().lookupAll(".table-cell").stream().map(TableCell.class::cast)
                    .filter(c -> c.getIndex() == 1 && c.getTableColumn() == method).findFirst().orElseThrow();
            assertTrue(cell.isEditing(), "Initialization must not commit the method cell");
            ((ComboBox<String>) cell.getGraphic()).getSelectionModel().select("EFT");
            assertEquals("EFT", table().getItems().get(1).paymentMethod.get());
            assertTrue(panel.hasUnsavedChanges());
            table().getItems().get(1).paymentReference.set("000124");
            assertTrue(panel.hasUnsavedChanges(), "Reference-only changes must protect the draft");
            panel.onPanelShown(); return null;
        });
        await(() -> choices("journalSelectedFund").getItems().size() >= 2);
        FxTestSupport.onFx(() ->
        {
            assertEquals("000124", table().getItems().get(1).paymentReference.get());
            assertTrue(panel.hasUnsavedChanges());
            assertNotNull(field("journalPaymentBankFilter"));
            assertTrue(table().getColumns().stream().anyMatch(c -> "Check / Reference".equals(c.getText())));
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(button("journalWorkspaceSaveButton").isDisabled());
            return null;
        });
    }

    private static void await(BooleanSupplier condition) throws Exception
    {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (!FxTestSupport.onFx(condition::getAsBoolean))
        {
            assertTrue(System.nanoTime() < deadline, "Journal choices did not finish loading");
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
