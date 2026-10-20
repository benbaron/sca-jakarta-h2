package org.nonprofitbookkeeping.ui;

import jakarta.persistence.EntityManager;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.ExpenseFunction;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;
import org.nonprofitbookkeeping.service.SupplementalOpenItemQueryService;
import org.nonprofitbookkeeping.service.TransactionCommand;
import org.nonprofitbookkeeping.service.TransactionEntryService;
import org.nonprofitbookkeeping.service.TransactionLineCommand;
import org.nonprofitbookkeeping.service.TransactionSupplementalLineCommand;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class RecognitionAssistantPanelTest
{
    @TempDir Path directory;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private Optional<AuthenticatedUserSession> previousUser;
    private LocalDate previousPeriod;
    private Stage stage;
    private RecognitionAssistantPanel panel;

    @BeforeEach
    void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        var session = MainWindow.sharedSessionState();
        previousDatabase = session.databaseSelection();
        previousCompany = session.multiCompany();
        previousUser = session.authenticatedUser();
        previousPeriod = ActivePeriodContext.get();
        Path database = directory.resolve("recognition-ui");
        session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        session.setAuthenticatedUser(UiPermissionTestSessions.manager());
        ActivePeriodContext.set(LocalDate.of(2026, 12, 31));
        UiServiceRegistry.reconnectToDatabase(database);
        try (Jpa jpa = new Jpa(database))
        {
            seed(jpa);
            UUID item = UUID.randomUUID();
            new TransactionEntryService(jpa, () -> "DEFAULT").enter(new TransactionCommand(
                    LocalDate.of(2026, 1, 15), null, "Annual insurance", null,
                    List.of(line(1004L, "1200", "0", null), line(1007L, "0", "1200", null)),
                    List.of(new TransactionSupplementalLineCommand("PREPAID_EXPENSE", "POLICY-2026",
                            "Insurer", "Annual insurance", "invoice-1", new BigDecimal("1200"), null,
                            LocalDate.of(2026, 1, 15), LocalDate.of(2026, 12, 31), null, 0,
                            item, "INCREASE", 0))));
        }
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
            ActivePeriodContext.set(previousPeriod);
            return null;
        });
        UiServiceRegistry.reconnectToDatabase(Path.of(previousDatabase.activeDatabasePath()));
    }

    @Test
    void actualJavaFxPreviewSharedSaveAndViewerGateUseCanonicalRecognition()
    {
        FxTestSupport.onFx(() -> {
            Platform.setImplicitExit(false);
            panel = new RecognitionAssistantPanel();
            stage = new Stage();
            stage.setScene(new Scene((javafx.scene.Parent) panel.root(), 900, 700));
            stage.show();
            assertNotNull(panel.root().lookup("#recognitionWorkspaceSplit"));
            assertNotNull(panel.root().lookup("#recognitionEditorScroll"));
            itemTable().getSelectionModel().selectFirst();
            destination().getSelectionModel().selectFirst();
            function().setValue(ExpenseFunction.OA);
            button("recognitionPreview").fire();
            assertEquals(12, proposalTable().getItems().size());
            assertTrue(panel.commandCapabilities().contains(AppCommand.SAVE_ACTIVE));
            assertEquals("Save Selected Recognition", panel.commandLabel(AppCommand.SAVE_ACTIVE));
            panel.onSave();
            assertEquals(11, proposalTable().getItems().size());
            assertTrue(panel.commandResultMessage(AppCommand.SAVE_ACTIVE).contains("Saved recognition transaction"));
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(button("recognitionSave").isDisabled());
            return null;
        });
        assertEquals(2, UiServiceRegistry.transactionEntry().search(null, null, null, 20).size());
        assertEquals(new BigDecimal("1100.0000"), UiServiceRegistry.supplementalOpenItems()
                .query(SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE, LocalDate.of(2026, 1, 31))
                .openAmount());
    }

    @SuppressWarnings("unchecked")
    private TableView<SupplementalOpenItemQueryService.Row> itemTable()
    { return (TableView<SupplementalOpenItemQueryService.Row>) panel.root().lookup("#recognitionItems"); }
    @SuppressWarnings("unchecked")
    private TableView<?> proposalTable() { return (TableView<?>) panel.root().lookup("#recognitionProposals"); }
    @SuppressWarnings("unchecked")
    private ComboBox<org.nonprofitbookkeeping.model.Account> destination()
    { return (ComboBox<org.nonprofitbookkeeping.model.Account>) panel.root().lookup("#recognitionDestination"); }
    @SuppressWarnings("unchecked")
    private ComboBox<ExpenseFunction> function()
    { return (ComboBox<ExpenseFunction>) panel.root().lookup("#recognitionExpenseFunction"); }
    private Button button(String id) { return (Button) panel.root().lookup("#" + id); }

    private static TransactionLineCommand line(long account, String debit, String credit, ExpenseFunction function)
    {
        return new TransactionLineCommand(account, 1001L, 1101L, 101L, null,
                new BigDecimal(debit), new BigDecimal(credit), false, null, null, function);
    }

    private static void seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("insert into company (id, code, display_name) values (100,'DEFAULT','Default')").executeUpdate();
            em.createNativeQuery("insert into chart_of_accounts (id, company_id, name, version, status) values (100,100,'Default','1','ACTIVE')").executeUpdate();
            em.createNativeQuery("update company set active_chart_of_accounts_id = 100 where id = 100").executeUpdate();
            em.createNativeQuery("""
                    insert into account (id, chart_id, code, name, account_type, subtype, normal_balance)
                    values (1004,100,'1200','Prepaid','ASSET','PREPAID','DEBIT'),
                           (1006,100,'5000','Expense','EXPENSE',null,'DEBIT'),
                           (1007,100,'1000','Cash','ASSET','CASH','DEBIT')
                    """).executeUpdate();
            em.createNativeQuery("insert into fund (id, company_id, code, name, fund_type) values (1001,100,'OPERATING','Operating','UNRESTRICTED')").executeUpdate();
            em.createNativeQuery("insert into activity (id, company_id, code, name, is_active) values (101,100,'EVENT-A','Event A',true)").executeUpdate();
            em.createNativeQuery("insert into budget_category (id, company_id, code, name, is_active) values (1101,100,'PROGRAM','Program',true)").executeUpdate();
            em.getTransaction().commit();
        }
    }
}
