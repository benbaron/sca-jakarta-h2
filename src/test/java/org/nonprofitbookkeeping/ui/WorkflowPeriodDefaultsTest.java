package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.*;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class WorkflowPeriodDefaultsTest
{
    private static final LocalDate START = LocalDate.of(2027, 6, 15);
    private static final LocalDate END = LocalDate.of(2027, 6, 30);
    @TempDir Path directory;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private Optional<AuthenticatedUserSession> previousUser;
    private LocalDate previousPeriod;
    private final List<Stage> stages = new ArrayList<>();
    private AssetAcquisitionTestFixture fixture;
    private Path database;

    @BeforeEach
    void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            var session = MainWindow.sharedSessionState();
            previousDatabase = session.databaseSelection();
            previousCompany = session.multiCompany();
            previousUser = session.authenticatedUser();
            previousPeriod = ActivePeriodContext.get();
            database = directory.resolve("workflow-defaults");
            session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
            session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
            session.setAuthenticatedUser(UiPermissionTestSessions.manager());
            UiServiceRegistry.reconnectToDatabase(database);
            try (Jpa jpa = new Jpa(database))
            {
                fixture = AssetAcquisitionTestFixture.seed(jpa);
                try (var em = jpa.em())
                {
                    em.getTransaction().begin();
                    Company company = new CompanyOwnershipService(jpa).requireCompany(em, "DEFAULT");
                    company.setFiscalYearStartMonth(7);
                    Bank bank = new Bank(); bank.setCompany(company); bank.setName("Synthetic bank"); em.persist(bank);
                    CompanyBankAccount account = new CompanyBankAccount();
                    account.setCompany(company); account.setBank(bank); account.setName("Synthetic checking");
                    account.setAccount(em.find(Account.class, fixture.base().bank()));
                    account.setOpeningDate(LocalDate.of(2025, 1, 1));
                    em.persist(account);
                    Company other = new Company(); other.setCode("OTHER"); other.setDisplayName("Other company");
                    em.persist(other);
                    em.getTransaction().commit();
                }
            }
            ActivePeriodContext.set(START);
            return null;
        });
    }

    @AfterEach
    void cleanup()
    {
        if (previousDatabase == null) return;
        FxTestSupport.onFx(() ->
        {
            stages.forEach(Stage::close);
            var session = MainWindow.sharedSessionState();
            session.setDatabaseSelection(previousDatabase);
            session.setMultiCompany(previousCompany);
            previousUser.ifPresentOrElse(session::setAuthenticatedUser, session::clearAuthenticatedUser);
            ActivePeriodContext.set(previousPeriod);
            return null;
        });
    }

    @Test
    void bankExportsUseWholeFiscalPeriodAndSubmitExactDatesForEveryFormat()
    {
        FxTestSupport.onFx(() ->
        {
            var captured = new ArrayList<String>();
            BankStatementExportActions actions = new BankStatementExportActions()
            {
                private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper();
                private final ReadOnlyStringWrapper status = new ReadOnlyStringWrapper("");
                public javafx.beans.property.ReadOnlyBooleanProperty busyProperty() { return busy.getReadOnlyProperty(); }
                public javafx.beans.property.ReadOnlyStringProperty statusProperty() { return status.getReadOnlyProperty(); }
                public void requestExport(long bank, LocalDate from, LocalDate through, BankStatementExportFormat format)
                {
                    captured.add(from + "/" + through + "/" + format);
                }
            };
            BankTransactionsPanel panel = new BankTransactionsPanel(UiServiceRegistry.ledgerQuery(),
                    UiServiceRegistry.bankReviewQuery(), () -> "DEFAULT", UiServiceRegistry::bankConfiguration,
                    actions, UiServiceRegistry::reviewedStatementAcceptance, UiServiceRegistry::transactionReferenceData);
            showBank(panel);
            assertEquals(START, date(panel, "bankStatementExportFrom").getValue());
            assertEquals(END, date(panel, "bankStatementExportThrough").getValue());
            for (String id : List.of("bankStatementExportCsvButton", "bankStatementExportOfxButton", "bankStatementExportQfxButton"))
            {
                ((Button) panel.root().lookup("#" + id)).fire();
            }
            assertEquals(3, captured.size());
            assertTrue(captured.stream().allMatch(value -> value.startsWith(START + "/" + END + "/")), captured.toString());
            try (Jpa jpa = new Jpa(database); var em = jpa.em())
            {
                em.getTransaction().begin();
                new CompanyOwnershipService(jpa).requireCompany(em, "DEFAULT").setFiscalYearStartMonth(1);
                em.getTransaction().commit();
            }
            panel.onPanelShown();
            assertEquals(LocalDate.of(2027, 7, 14), date(panel, "bankStatementExportThrough").getValue(),
                    "Untouched defaults refresh against current company fiscal authority");
            ActivePeriodContext.select(YearMonth.of(2028, 2), 1);
            assertEquals(LocalDate.of(2028, 2, 1), date(panel, "bankStatementExportFrom").getValue());
            assertEquals(LocalDate.of(2028, 2, 29), date(panel, "bankStatementExportThrough").getValue());

            date(panel, "bankStatementExportFrom").setValue(LocalDate.of(2028, 2, 4));
            date(panel, "bankStatementExportThrough").setValue(null);
            ActivePeriodContext.select(YearMonth.of(2028, 3), 10);
            panel.onPanelShown();
            assertEquals(LocalDate.of(2028, 2, 4), date(panel, "bankStatementExportFrom").getValue());
            assertNull(date(panel, "bankStatementExportThrough").getValue());
            return null;
        });
    }

    @Test
    void exportRawTextDetachesDefaultsAndCompanyFiscalBoundsAreIndependent()
    {
        FxTestSupport.onFx(() ->
        {
            BankTransactionsPanel panel = new BankTransactionsPanel(); showBank(panel);
            DatePicker from = date(panel, "bankStatementExportFrom");
            from.getEditor().setText("uncommitted date draft");
            ActivePeriodContext.select(YearMonth.of(2027, 8), 10);
            assertEquals("uncommitted date draft", from.getEditor().getText());
            assertEquals(START, from.getValue());
            assertEquals(END, date(panel, "bankStatementExportThrough").getValue());

            MainWindow.sharedSessionState().setMultiCompany(new MultiCompanyState("OTHER", List.of("OTHER")));
            ActivePeriodContext.set(START);
            BankTransactionsPanel other = new BankTransactionsPanel(); showBank(other);
            assertEquals(LocalDate.of(2027, 7, 14), date(other, "bankStatementExportThrough").getValue());
            return null;
        });
    }

    @Test
    void cutoffsUsePeriodEndButFinancialDraftDatesUseStartAndStayIndependent()
    {
        FxTestSupport.onFx(() ->
        {
            RecognitionAssistantPanel recognition = new RecognitionAssistantPanel(); show(recognition);
            CheckExceptionsPane checks = new CheckExceptionsPane(); show(checks);
            MerchantSettlementAssistantPanel settlement = new MerchantSettlementAssistantPanel(); show(settlement);
            InventoryPanel inventory = new InventoryPanel(); show(inventory);
            assertEquals(END, date(recognition, "recognitionRunThrough").getValue());
            assertEquals(END, date(checks, "checkReviewThrough").getValue());
            assertEquals(START, date(checks, "checkActionDate").getValue());
            assertEquals(START, date(settlement, "merchantSettlementDate").getValue());
            assertEquals(START, date(inventory, "inventoryMovementDate").getValue());
            LocalDate custom = START.plusDays(2);
            date(recognition, "recognitionRunThrough").setValue(custom);
            date(checks, "checkActionDate").setValue(custom);
            date(inventory, "inventoryMovementDate").setValue(custom);
            ActivePeriodContext.select(YearMonth.of(2027, 8), 10);
            assertEquals(custom, date(recognition, "recognitionRunThrough").getValue());
            assertEquals(END, date(checks, "checkReviewThrough").getValue());
            assertEquals(custom, date(checks, "checkActionDate").getValue());
            assertEquals(custom, date(inventory, "inventoryMovementDate").getValue());
            assertEquals(START, date(settlement, "merchantSettlementDate").getValue());
            assertNull(date(recognition, "recognitionServiceStart").getValue());
            assertNull(date(recognition, "recognitionServiceEnd").getValue());
            return null;
        });
    }

    @Test
    void onlyUntouchedNewPurchaseDatesAdoptTheAccountingDefault() throws Exception
    {
        AssetsRegisterPanel panel = FxTestSupport.onFx(() -> { var p = new AssetsRegisterPanel(); show(p); return p; });
        awaitReady(panel);
        FxTestSupport.onFx(() ->
        {
            ComboBox<AssetAcquisitionSource> source = combo(panel, "assetAcquisitionSource");
            DatePicker acquired = date(panel, "assetAcquisitionDate");
            assertEquals(LocalDate.now(), acquired.getValue());
            source.setValue(AssetAcquisitionSource.NEW_PURCHASE);
            assertEquals(START, acquired.getValue());
            ActivePeriodContext.select(YearMonth.of(2027, 8), 10);
            assertEquals(START, acquired.getValue(), "Applying a period must not rewrite an existing purchase draft");
            source.setValue(AssetAcquisitionSource.REGISTER_ONLY);
            assertEquals(LocalDate.now(), acquired.getValue());
            LocalDate factual = LocalDate.of(2025, 3, 12);
            acquired.setValue(factual);
            source.setValue(AssetAcquisitionSource.NEW_PURCHASE);
            assertEquals(factual, acquired.getValue());
            acquired.getEditor().setText("uncommitted acquisition date");
            source.setValue(AssetAcquisitionSource.EXISTING_ENTRY);
            assertEquals("uncommitted acquisition date", acquired.getEditor().getText());
            return null;
        });
    }

    @Test
    void historicalAcquisitionCustodyConversionAndItemFactsDoNotBecomePeriodDates() throws Exception
    {
        try (Jpa jpa = new Jpa(database))
        {
            fixture.service(jpa).create(fixture.command("Historical equipment", "100"));
        }
        AssetsRegisterPanel assets = FxTestSupport.onFx(() -> { var p = new AssetsRegisterPanel(); show(p); return p; });
        awaitReady(assets);
        FxTestSupport.onFx(() ->
        {
            TableView<?> table = (TableView<?>) assets.root().lookup("#assetRegisterTable");
            table.getSelectionModel().selectFirst();
            assertEquals(AssetAcquisitionTestFixture.DATE, date(assets, "assetAcquisitionDate").getValue());
            InventoryPanel inventory = new InventoryPanel(); show(inventory); inventory.onNew(); inventory.root().applyCss(); inventory.root().getScene().getRoot().layout();
            assertEquals(LocalDate.now(), date(inventory, "inventoryAcquisitionDate").getValue());
            PropertyCustodyPanel custody = new PropertyCustodyPanel(); show(custody); custody.onNew();
            assertEquals(LocalDate.now(), date(custody, "propertyCustodyEventDate").getValue());
            OpeningConversionPanel conversion = new OpeningConversionPanel(); show(conversion);
            assertEquals(LocalDate.now(), date(conversion, "openingConversionAsOf").getValue());
            ActivePeriodContext.select(YearMonth.of(2028, 2), 1);
            assertEquals(AssetAcquisitionTestFixture.DATE, date(assets, "assetAcquisitionDate").getValue());
            assertEquals(LocalDate.now(), date(custody, "propertyCustodyEventDate").getValue());
            return null;
        });
    }

    @Test
    void assetAccountingDialogsDefaultToPeriodStartAndPreserveEditedDates()
    {
        assertDialogDate("assetLifecycleDate", () -> FixedAssetLifecycleDialog.show(List.of(), CompanyUiFormat.activeCompany()));
        assertDialogDate("assetLifecycleReversalDate", () -> FixedAssetLifecycleDialog.showReversal(CompanyUiFormat.activeCompany()));
    }

    private void assertDialogDate(String id, Runnable open)
    {
        FxTestSupport.onFx(() ->
        {
            LocalDate expected = ActivePeriodContext.get();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Platform.runLater(() ->
            {
                Window dialog = null;
                try
                {
                    dialog = Window.getWindows().stream().filter(Window::isShowing)
                            .filter(w -> w.getScene().lookup("#" + id) != null).findFirst().orElseThrow();
                    DatePicker picker = (DatePicker) dialog.getScene().lookup("#" + id);
                    assertEquals(expected, picker.getValue());
                    picker.setValue(expected.plusDays(2));
                    ActivePeriodContext.select(YearMonth.of(2028, 2), 1);
                    assertEquals(expected.plusDays(2), picker.getValue());
                }
                catch (Throwable ex) { failure.set(ex); }
                finally { if (dialog != null) ((Stage) dialog).close(); }
            });
            open.run();
            if (failure.get() != null) throw new AssertionError(failure.get());
            return null;
        });
    }

    private void showBank(BankTransactionsPanel panel)
    {
        show(panel);
        ((TabPane) panel.root().lookup("#bankTransactionsAuthorityTabs")).getSelectionModel().select(1);
        panel.root().applyCss();
    }

    private void show(AppPanel panel)
    {
        PanelViewport.prepare(panel.root());
        Stage stage = new Stage(); stages.add(stage);
        stage.setScene(new Scene(PanelViewport.wrap(panel.root()), 1100, 760));
        stage.show();
    }

    private static DatePicker date(AppPanel panel, String id)
    {
        DatePicker date = (DatePicker) panel.root().lookup("#" + id);
        assertNotNull(date, id);
        return date;
    }

    @SuppressWarnings("unchecked")
    private static <T> ComboBox<T> combo(AppPanel panel, String id) { return (ComboBox<T>) panel.root().lookup("#" + id); }

    private void awaitReady(AssetsRegisterPanel panel) throws Exception
    {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline)
        {
            if (FxTestSupport.onFx(() -> !panel.commandCapabilities().isEmpty())) return;
            Thread.sleep(25);
        }
        fail("Asset references did not load");
    }
}
