package org.nonprofitbookkeeping.ui;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;
import org.nonprofitbookkeeping.service.ReservedSecurityRole;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises visible save routing through the real production shell and H2 services. */
@ResourceLock("ui-service-registry")
class JournalSaveFeedbackTest
{
    @TempDir Path directory;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private Optional<AuthenticatedUserSession> previousUser;
    private Stage stage;
    private ProductionWorkspaceWindow window;
    private org.nonprofitbookkeeping.service.FundTransferTestFixture fixture;
    private long receivable;
    private long accountantId;

    @BeforeEach
    void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        UiSessionState session = MainWindow.sharedSessionState();
        previousDatabase = session.databaseSelection();
        previousCompany = session.multiCompany();
        previousUser = session.authenticatedUser();
        Path database = directory.resolve("save-coverage");
        session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        UiServiceRegistry.reconnectToDatabase(database);
        UiServiceRegistry.authentication().initializeSecurityIfUnambiguous();
        long adminId = UiServiceRegistry.userAdmin().listUsers().stream()
                .filter(user -> "ADMIN".equals(user.getUsername())).findFirst().orElseThrow().getId();
        session.setAuthenticatedUser(UiServiceRegistry.authentication().authenticate("DEFAULT", adminId, new char[0]));
        accountantId = UiServiceRegistry.userAdmin().listUsers().stream()
                .filter(user -> "ACCOUNTANT".equals(user.getUsername())).findFirst().orElseThrow().getId();
        try (var jpa = new org.nonprofitbookkeeping.persistence.Jpa(database))
        {
            fixture = org.nonprofitbookkeeping.service.FundTransferTestFixture.seed(jpa);
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                var ar = new org.nonprofitbookkeeping.model.Account();
                ar.setChart(em.find(org.nonprofitbookkeeping.model.Account.class, fixture.bank()).getChart());
                ar.setCode("1100"); ar.setName("Accounts receivable");
                ar.setAccountType(org.nonprofitbookkeeping.model.AccountType.ASSET);
                ar.setNormalBalance(org.nonprofitbookkeeping.model.NormalBalance.DEBIT);
                ar.setSubtype(org.nonprofitbookkeeping.model.AccountSubtype.RECEIVABLE);
                em.persist(ar);
                em.getTransaction().commit();
                receivable = ar.getId();
            }
        }
        FxTestSupport.onFx(() ->
        {
            javafx.application.Platform.setImplicitExit(false);
            AppStateStore stateStore = new AppStateStore()
            {
                @Override public Optional<org.nonprofitbookkeeping.model.AppPreferencesState> loadPreferences() { return Optional.empty(); }
                @Override public Optional<MultiCompanyState> loadMultiCompany() { return Optional.of(session.multiCompany()); }
                @Override public Optional<DatabaseSelectionState> loadDatabaseSelection()
                {
                    return Optional.of(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
                }
                @Override public void savePreferences(org.nonprofitbookkeeping.model.AppPreferencesState value) { }
                @Override public void saveMultiCompany(MultiCompanyState value) { }
            };
            window = new ProductionWorkspaceWindow(stateStore,
                    (path, preferred) -> new DatabaseSessionController.PreparedConnection()
                    {
                        @Override public Path databasePath() { return path.toAbsolutePath().normalize(); }
                        @Override public String activeCompanyCode() { return "DEFAULT"; }
                        @Override public List<String> activeCompanyCodes() { return List.of("DEFAULT"); }
                        @Override public void activate() { }
                        @Override public void close() { }
                    });
            assertEquals(database.toString(), session.databaseSelection().activeDatabasePath());
            session.setAuthenticatedUser(UiServiceRegistry.authentication().authenticate("DEFAULT", adminId, new char[0]));
            session.setAuthenticatedUser(UiServiceRegistry.authentication().authenticate("DEFAULT", accountantId, new char[0]));
            stage = new Stage();
            stage.setScene(new Scene(window, 1000, 740));
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
            if (stage != null) stage.close();
            DrillThroughCoordinator.configureOpener(null);
            return null;
        });
        Thread.sleep(600);
        FxTestSupport.onFx(() ->
        {
            UiSessionState session = MainWindow.sharedSessionState();
            session.setDatabaseSelection(previousDatabase);
            session.setMultiCompany(previousCompany);
            if (previousUser.isPresent()) { session.setAuthenticatedUser(previousUser.orElseThrow()); }
            else { session.clearAuthenticatedUser(); }
            return null;
        });
        UiServiceRegistry.reconnectToDatabase(Path.of(previousDatabase.activeDatabasePath()));
    }

    @Test
    void toolbarSaveReportsCommittedNewEntryAndUpdateAfterJournalRefresh() throws Exception
    {
        openJournal();
        enter(fixture.bank(), "Completion feedback");
        long before = transactions();
        clickSave();
        await(() -> !saveButton().isDisabled());
        assertEquals(before + 1, transactions(), "The real service committed before checking completion feedback");
        await(() -> ((Label) window.panelHost().activeRoot().lookup("#journalWorkspaceStatusLabel")).getText().startsWith("Loaded "));
        FxTestSupport.onFx(() ->
        {
            assertTrue(footer().getText().startsWith("Saved new transaction #"), footer().getText());
            assertEquals(footer().getText(), footer().getTooltip().getText());
            assertFalse(window.panelHost().dirtyPanelTitles().contains("Journal"));
            line(0).setDebit("125.00"); line(1).setCredit("125.00");
            saveButton().fire();
            return null;
        });
        await(() -> !saveButton().isDisabled());
        assertEquals(before + 1, transactions(), "Updating the saved entry must not create another transaction");
        FxTestSupport.onFx(() ->
        {
            assertTrue(footer().getText().startsWith("Updated transaction #"), footer().getText());
            return null;
        });
    }

    @Test
    void serviceRejectionShowsItsReasonOnEntryAndFooterAndRetainsDraftForRetry() throws Exception
    {
        openJournal();
        enter(receivable, "Receivable without required item detail");
        long before = transactions();
        clickSave();
        await(() -> !saveButton().isDisabled());
        assertEquals(before, transactions(), "Rejected receivable entry must not be persisted");
        FxTestSupport.onFx(() ->
        {
            assertTrue(footer().getText().startsWith("Save failed:"), footer().getText());
            assertTrue(footer().getText().contains("requires supplemental allocations of 100.00"), footer().getText());
            Label validation = (Label) window.panelHost().activeRoot().lookup("#journalEntryValidation");
            assertEquals(footer().getText(), validation.getText());
            assertTrue(window.panelHost().dirtyPanelTitles().contains("Journal"));
            assertEquals("100.00", line(0).getDebit());
            line(0).setAccount(option(fixture.bank()));
            saveButton().fire();
            return null;
        });
        await(() -> !saveButton().isDisabled());
        assertEquals(before + 1, transactions());
        FxTestSupport.onFx(() ->
        {
            assertTrue(footer().getText().startsWith("Saved new transaction #"), footer().getText());
            return null;
        });
    }

    @Test
    void localJournalSaveAlsoPublishesCompletionToTheFooter() throws Exception
    {
        openJournal(); enter(fixture.bank(), "Local Journal Save");
        long before = transactions();
        FxTestSupport.onFx(() ->
        {
            ((TabPane) window.panelHost().activeRoot().lookup("#journalWorkPanes")).getSelectionModel().select(0);
            window.applyCss(); window.layout();
            ((Button) window.panelHost().activeRoot().lookup("#journalWorkspaceSaveButton")).fire();
            assertEquals("Saving new journal entry...", footer().getText());
            return null;
        });
        await(() -> !saveButton().isDisabled());
        assertEquals(before + 1, transactions());
        FxTestSupport.onFx(() ->
        {
            assertTrue(footer().getText().startsWith("Saved new transaction #"), footer().getText());
            return null;
        });
    }

    @Test
    void committedSaveWithPresentationFailureReportsPersistenceInsteadOfStayingPending() throws Exception
    {
        openJournal(); enter(fixture.bank(), "Committed before editor refresh failure");
        long before = transactions();
        DatePicker picker = FxTestSupport.onFx(() ->
        {
            DatePicker value = (DatePicker) window.panelHost().activeRoot().lookup("#journalEntryDate");
            // A bound view property deterministically rejects the post-commit editor update.
            value.valueProperty().bind(new javafx.beans.property.SimpleObjectProperty<>(value.getValue()));
            return value;
        });
        try
        {
            clickSave(); await(() -> !saveButton().isDisabled());
            assertEquals(before + 1, transactions());
            FxTestSupport.onFx(() ->
            {
                assertTrue(footer().getText().startsWith("Saved transaction #"), footer().getText());
                assertTrue(footer().getText().contains("editor could not refresh"), footer().getText());
                assertFalse(footer().getText().contains("Save failed"));
                return null;
            });
        }
        finally
        {
            FxTestSupport.onFx(() -> { picker.valueProperty().unbind(); return null; });
        }
    }

    @Test
    void immediateValidationFailureNeverStartsASaveAndBothButtonsShowTheBlock() throws Exception
    {
        openJournal(); enter(fixture.bank(), "Unbalanced draft");
        long before = transactions();
        FxTestSupport.onFx(() ->
        {
            line(1).setCredit("90.00");
            saveButton().fire();
            assertTrue(footer().getText().startsWith("Save blocked:"), footer().getText());
            assertFalse(saveButton().isDisabled());
            assertTrue(window.panelHost().dirtyPanelTitles().contains("Journal"));
            ((TabPane) window.panelHost().activeRoot().lookup("#journalWorkPanes")).getSelectionModel().select(0);
            ((Button) window.panelHost().activeRoot().lookup("#journalWorkspaceSaveButton")).fire();
            assertTrue(footer().getText().startsWith("Save blocked:"), footer().getText());
            assertFalse(saveButton().isDisabled());
            return null;
        });
        assertEquals(before, transactions());
    }

    @Test
    void changingWorkspaceClearsPendingTextAndLateCompletionCannotReplaceCurrentFeedback()
    {
        FxTestSupport.onFx(() ->
        {
            java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<AppPanel.RunCommandResult>> completion =
                    new java.util.concurrent.atomic.AtomicReference<>();
            AppPanel async = new AppPanel()
            {
                private final Label root = new Label("Asynchronous feedback fixture");
                @Override public String title() { return "First workspace"; }
                @Override public javafx.scene.Node root() { return root; }
                @Override public void setCommandResultListener(java.util.function.Consumer<RunCommandResult> listener)
                {
                    completion.set(listener);
                }
            };
            AppPanel next = new AppPanel()
            {
                private final Label root = new Label("Second workspace");
                @Override public String title() { return "Second workspace"; }
                @Override public javafx.scene.Node root() { return root; }
            };
            window.panelHost().showReplacement(AppPanelId.MERCHANTS, async);
            completion.get().accept(new AppPanel.RunCommandResult(true, "Saving in first workspace..."));
            assertEquals("Saving in first workspace...", footer().getText());
            window.panelHost().showReplacement(AppPanelId.FUNDS, next);
            assertTrue(footer().getText().isEmpty());
            assertNull(footer().getTooltip());
            window.newItemInActivePanel();
            String current = footer().getText();
            completion.get().accept(new AppPanel.RunCommandResult(true, "Late first completion"));
            assertEquals(current, footer().getText());
            return null;
        });
    }

    private void openJournal() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            window.openPanel(AppPanelId.JOURNAL_PANE);
            ((TabPane) window.panelHost().activeRoot().lookup("#journalWorkPanes")).getSelectionModel().select(1);
            window.applyCss(); window.layout();
            return null;
        });
        await(() -> !saveButton().isDisabled() && lines().getItems().size() == 2);
    }

    private void enter(long debitAccount, String memo)
    {
        FxTestSupport.onFx(() ->
        {
            ((DatePicker) window.panelHost().activeRoot().lookup("#journalEntryDate")).setValue(java.time.LocalDate.of(2026, 11, 1));
            ((TextArea) window.panelHost().activeRoot().lookup("#journalEntryMemo")).setText(memo);
            line(0).setAccount(option(debitAccount)); line(0).setFund(option(fixture.source())); line(0).setDebit("100.00"); line(0).setCredit("");
            line(1).setAccount(option(fixture.income())); line(1).setFund(option(fixture.source())); line(1).setDebit(""); line(1).setCredit("100.00");
            return null;
        });
    }

    private void clickSave()
    {
        FxTestSupport.onFx(() ->
        {
            assertFalse(saveButton().isDisabled());
            saveButton().fire();
            assertEquals("Saving new journal entry...", footer().getText());
            assertTrue(saveButton().isDisabled());
            return null;
        });
    }

    private TransactionLineEditorModel.Option option(long id)
    {
        return new TransactionLineEditorModel.Option(id, "fixture", "Fixture selection");
    }

    @SuppressWarnings("unchecked")
    private TableView<JournalWorkspacePanel.EditorLine> lines()
    {
        return (TableView<JournalWorkspacePanel.EditorLine>) window.panelHost().activeRoot().lookup("#journalWorkspaceEntryLineTable");
    }

    private JournalWorkspacePanel.EditorLine line(int index)
    {
        return lines().getItems().get(index);
    }

    private Label footer()
    {
        return (Label) window.lookup("#workspaceMessage");
    }

    private Button saveButton()
    {
        ToolBar toolbar = (ToolBar) ((VBox) window.getTop()).getChildren().get(1);
        return toolbar.getItems().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getText().startsWith("Save")).findFirst().orElseThrow();
    }

    private long transactions()
    {
        try (var jpa = new org.nonprofitbookkeeping.persistence.Jpa(Path.of(MainWindow.sharedSessionState().databaseSelection().activeDatabasePath()));
             var em = jpa.em())
        {
            return em.createQuery("select count(t) from Txn t", Long.class).getSingleResult();
        }
    }

    private static void await(BooleanSupplier condition) throws Exception
    {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline)
        {
            if (FxTestSupport.onFx(condition::getAsBoolean)) return;
            Thread.sleep(25);
        }
        fail("Timed out waiting for Journal Save state");
    }
}
