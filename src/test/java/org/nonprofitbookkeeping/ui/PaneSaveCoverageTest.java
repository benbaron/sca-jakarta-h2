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
class PaneSaveCoverageTest
{
    @TempDir Path directory;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private Optional<AuthenticatedUserSession> previousUser;
    private Stage stage;
    private ProductionWorkspaceWindow window;

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
        try (var jpa = new org.nonprofitbookkeeping.persistence.Jpa(database))
        {
            org.nonprofitbookkeeping.service.FundTransferTestFixture.seed(jpa);
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
            stage.close();
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
    void nestedAuthenticationSaveCommitsTypedTimeoutAndPreservesSeparatePasswordDraft()
    {
        FxTestSupport.onFx(() ->
        {
            window.openPanel(AppPanelId.SETTINGS);
            TabPane outer = (TabPane) window.lookup("#administrationTabs");
            selectTab(outer, "User Admin");
            TabPane inner = (TabPane) window.lookup("#userAdminMaintenanceTabs");
            selectTab(inner, "Authentication");
            Spinner<?> timeout = (Spinner<?>) window.lookup("#securityInactivityTimeoutMinutes");
            PasswordField password = (PasswordField) window.lookup("#securityCredentialPassword");
            password.setText("separate-password-draft");
            timeout.getEditor().setText("17");
            assertEquals("Save Timeout", saveMenu().getText());
            assertFalse(saveMenu().isDisable());
            saveMenu().fire();
            assertEquals(17, UiServiceRegistry.securityAdmin().settings().inactivityTimeoutMinutes());
            assertEquals("separate-password-draft", password.getText());
            assertTrue(window.panelHost().dirtyPanelTitles().contains("Administration"));
            timeout.getEditor().setText("23");
            selectTab(inner, "Users");
            selectTab(inner, "Authentication");
            assertEquals("23", timeout.getEditor().getText(), "Reselecting a subpane must preserve its draft.");
            ((Button) window.lookup("#securitySaveTimeout")).fire();
            assertEquals(23, UiServiceRegistry.securityAdmin().settings().inactivityTimeoutMinutes());
            assertEquals("Save Password", ((Button) window.lookup("#securitySetPassword")).getText());
            ((Button) window.lookup("#securitySetPassword")).fire();
            assertEquals("", password.getText());
            assertFalse(window.panelHost().dirtyPanelTitles().contains("Administration"));
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(saveMenu().isDisable());
            assertTrue(((Button) window.lookup("#securitySetPassword")).isDisabled());
            assertTrue(((Button) window.lookup("#securitySaveTimeout")).isDisabled());
            return null;
        });
    }

    @Test
    void importSaveFollowsSelectedResultAndCancellationLeavesH2Unchanged() throws Exception
    {
        Path csv = directory.resolve("accounts.csv");
        Files.writeString(csv, "code,name,account_type,normal_balance,parent_code\n9910,Save coverage account,ASSET,DEBIT,\n");
        ImportPreviewPanel panel = FxTestSupport.onFx(() ->
        {
            window.openPanel(AppPanelId.IMPORT_PREVIEW);
            ImportPreviewPanel imported = new ImportPreviewPanel();
            stage.getScene().setRoot((javafx.scene.Parent) imported.root());
            imported.root().applyCss();
            imported.previewCoa(csv);
            return imported;
        });
        await(() -> panel.supportsCommand(AppCommand.SAVE_ACTIVE));
        FxTestSupport.onFx(() ->
        {
            AtomicInteger notifications = new AtomicInteger();
            panel.setCommandCapabilitiesChangedListener(notifications::incrementAndGet);
            TabPane results = (TabPane) panel.root().lookup("#importPreviewResultTabs");
            results.getSelectionModel().select(1);
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE), "An SCLX tab must not save the previous COA preview.");
            assertFalse(panel.executeCommand(AppCommand.SAVE_ACTIVE).handled());
            results.getSelectionModel().select(0);
            assertEquals("Save Accepted COA Rows", panel.commandLabel(AppCommand.SAVE_ACTIVE));
            assertTrue(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            // Close only the newly opened confirmation, exercising the existing cancellation boundary.
            javafx.application.Platform.runLater(() -> Window.getWindows().stream()
                    .filter(Window::isShowing).filter(value -> value != stage).toList()
                    .forEach(value -> ((Stage) value).close()));
            assertTrue(panel.executeCommand(AppCommand.SAVE_ACTIVE).handled());
            assertTrue(panel.commandResultMessage(AppCommand.SAVE_ACTIVE).contains("cancelled"));
            assertTrue(notifications.get() >= 3);
            assertEquals(0, UiServiceRegistry.accountLookup().listActivePostingAccounts().stream()
                    .filter(account -> "9910".equals(account.getCode())).count());
            javafx.application.Platform.runLater(() -> Window.getWindows().stream()
                    .filter(Window::isShowing).filter(value -> value != stage).toList()
                    .forEach(value ->
                    {
                        DialogPane dialog = (DialogPane) value.getScene().getRoot();
                        ((Button) dialog.lookupButton(ButtonType.OK)).fire();
                    }));
            assertTrue(panel.executeCommand(AppCommand.SAVE_ACTIVE).handled());
            return null;
        });
        await(() -> UiServiceRegistry.accountLookup().listActivePostingAccounts().stream()
                .anyMatch(account -> "9910".equals(account.getCode())));
    }

    @Test
    void policySclxPreviewSavePersistsVersionsAndRequiresCompanyAdmin() throws Exception
    {
        Path source = directory.resolve("policy.sclx");
        try (var jpa = new org.nonprofitbookkeeping.persistence.Jpa(directory.resolve("policy-source")))
        {
            org.nonprofitbookkeeping.interchange.sclx.SclxNmrPolicyRoundTripTest.seed(jpa);
            org.nonprofitbookkeeping.interchange.sclx.SclxNmrPolicyRoundTripTest.policies(jpa).save(java.util.UUID.randomUUID(),
                    org.nonprofitbookkeeping.interchange.sclx.SclxNmrPolicyRoundTripTest.terms(
                            java.time.LocalDate.of(2026, 1, 1), null));
            org.nonprofitbookkeeping.interchange.sclx.SclxNmrPolicyRoundTripTest.export(jpa, source);
        }
        var preview = UiServiceRegistry.sclxImportPreview().preview(source);
        assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
        ImportPreviewPanel panel = FxTestSupport.onFx(() ->
        {
            ImportPreviewPanel imported = new ImportPreviewPanel();
            stage.getScene().setRoot((javafx.scene.Parent) imported.root());
            imported.root().applyCss();
            imported.applySclxPreview(source, preview);
            ((CheckBox) imported.root().lookup("#confirmExistingCompanySclxImport")).setSelected(true);
            ((CheckBox) imported.root().lookup("#confirmSclxMappings")).setSelected(true);
            assertTrue(imported.supportsCommand(AppCommand.SAVE_ACTIVE));
            var counts = (ListView<?>) imported.root().lookup("#sclxPreviewCounts");
            assertTrue(counts.getItems().stream().anyMatch(value -> value.toString().contains("nmrPolicyVersions")));
            var session = MainWindow.sharedSessionState();
            var admin = session.authenticatedUser().orElseThrow();
            var accountant = new AuthenticatedUserSession(admin.userId(), admin.username(), admin.displayName(),
                    admin.companyCode(), java.util.Set.of(ReservedSecurityRole.ACCOUNTANT), admin.authenticatedAt(), admin.lastActivityAt());
            session.setAuthenticatedUser(accountant);
            assertFalse(imported.supportsCommand(AppCommand.SAVE_ACTIVE));
            assertTrue(((Button) imported.root().lookup("#commitPreviewedSclxButton")).getTooltip().getText().contains("Company Administration"));
            session.setAuthenticatedUser(new AuthenticatedUserSession(admin.userId(), admin.username(), admin.displayName(),
                    admin.companyCode(), java.util.Set.of(ReservedSecurityRole.VIEWER), admin.authenticatedAt(), admin.lastActivityAt()));
            assertFalse(imported.supportsCommand(AppCommand.SAVE_ACTIVE));
            session.setAuthenticatedUser(accountant);
            assertFalse(imported.supportsCommand(AppCommand.SAVE_ACTIVE));
            assertTrue(((Button) imported.root().lookup("#commitPreviewedSclxButton")).getTooltip().getText().contains("Company Administration"));
            session.setAuthenticatedUser(admin);
            assertTrue(imported.supportsCommand(AppCommand.SAVE_ACTIVE));
            javafx.application.Platform.runLater(() -> Window.getWindows().stream()
                    .filter(Window::isShowing).filter(value -> value != stage).toList()
                    .forEach(value -> ((Button) ((DialogPane) value.getScene().getRoot()).lookupButton(ButtonType.OK)).fire()));
            assertTrue(imported.executeCommand(AppCommand.SAVE_ACTIVE).handled());
            return imported;
        });
        await(() -> UiServiceRegistry.nmrPolicies().list().size() == 1);
        FxTestSupport.onFx(() ->
        {
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE), "Committed preview must not remain an editable Save draft.");
            return null;
        });
        assertEquals(new java.math.BigDecimal("15.1250"), UiServiceRegistry.nmrPolicies().list().get(0).terms().rate());
    }

    @Test
    void nmrLinePreviewSavePreservesEnteredFlagsWithBookkeepingPermission() throws Exception
    {
        Path source = org.nonprofitbookkeeping.interchange.sclx.SclxNmrLineRoundTripTest.writeHistory(directory);
        try (var jpa = new org.nonprofitbookkeeping.persistence.Jpa(
                Path.of(MainWindow.sharedSessionState().databaseSelection().activeDatabasePath())))
        {
            org.nonprofitbookkeeping.interchange.sclx.SclxNmrLineRoundTripTest.seedEmptyTarget(jpa);
        }
        ImportPreviewPanel panel = FxTestSupport.onFx(() ->
        {
            UiSessionState session = MainWindow.sharedSessionState();
            var admin = session.authenticatedUser().orElseThrow();
            String company = org.nonprofitbookkeeping.interchange.sclx.SclxNmrLineRoundTripTest.COMPANY;
            session.setMultiCompany(new MultiCompanyState(company, List.of("DEFAULT", company)));
            var accountant = new AuthenticatedUserSession(admin.userId(), admin.username(), admin.displayName(),
                    company, java.util.Set.of(ReservedSecurityRole.ACCOUNTANT), admin.authenticatedAt(), admin.lastActivityAt());
            session.setAuthenticatedUser(accountant);
            var preview = UiServiceRegistry.sclxImportPreview().preview(source);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertTrue(preview.operation().messages().stream().anyMatch(m -> m.code().equals("SCLX_NMR_LINE_FLAGS")));
            ImportPreviewPanel imported = new ImportPreviewPanel();
            stage.getScene().setRoot((javafx.scene.Parent) imported.root());
            imported.root().applyCss();
            imported.applySclxPreview(source, preview);
            assertTrue(imported.supportsCommand(AppCommand.SAVE_ACTIVE));
            session.setAuthenticatedUser(new AuthenticatedUserSession(admin.userId(), admin.username(), admin.displayName(),
                    company, java.util.Set.of(ReservedSecurityRole.VIEWER), admin.authenticatedAt(), admin.lastActivityAt()));
            assertFalse(imported.supportsCommand(AppCommand.SAVE_ACTIVE));
            session.setAuthenticatedUser(accountant);
            assertTrue(imported.supportsCommand(AppCommand.SAVE_ACTIVE));
            javafx.application.Platform.runLater(() -> Window.getWindows().stream()
                    .filter(Window::isShowing).filter(value -> value != stage).toList()
                    .forEach(value -> ((Button) ((DialogPane) value.getScene().getRoot()).lookupButton(ButtonType.OK)).fire()));
            assertTrue(imported.executeCommand(AppCommand.SAVE_ACTIVE).handled());
            return imported;
        });
        var from = java.time.LocalDate.of(2026, 6, 1);
        await(() -> UiServiceRegistry.transactionEntry().search(from, from.plusDays(2), "", 100).size() == 3);
        var transactions = UiServiceRegistry.transactionEntry().search(from, from.plusDays(2), "", 100);
        assertEquals(3, transactions.stream().flatMap(t -> t.lines().stream()).filter(line -> line.nmr()).count());
        assertTrue(UiServiceRegistry.nmrPolicies().list().isEmpty());
        FxTestSupport.onFx(() ->
        {
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            return null;
        });
    }

    @Test
    void inventoryAndReadOnlyViewsNeverAdvertiseAnInactiveSave()
    {
        FxTestSupport.onFx(() ->
        {
            window.openPanel(AppPanelId.INVENTORY);
            assertTrue(saveMenu().isDisable());
            assertTrue(saveButton().getTooltip().getText().contains("Open New Item"));
            window.executeCommand(AppCommand.NEW_ACTIVE);
            assertFalse(saveMenu().isDisable());
            ((Button) window.panelHost().activeRoot().lookup("#inventoryItemCancelButton")).fire();
            assertTrue(saveMenu().isDisable());
            window.openPanel(AppPanelId.HELP);
            assertTrue(saveMenu().isDisable());
            assertTrue(saveButton().getTooltip().getText().contains("no editable draft"));
            assertFalse(window.executeCommand(AppCommand.SAVE_ACTIVE).handled());
            return null;
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void reconciliationSaveTargetsCheckReviewWithoutSavingOrFinalizingAnotherSubpane() throws Exception
    {
        Path database = Path.of(MainWindow.sharedSessionState().databaseSelection().activeDatabasePath());
        try (var jpa = new org.nonprofitbookkeeping.persistence.Jpa(database))
        {
            try (var em = jpa.em())
            {
                long bank = em.createQuery("select a.id from Account a where a.code = '1000'", Long.class).getSingleResult();
                long expense = em.createQuery("select a.id from Account a where a.code = '5000'", Long.class).getSingleResult();
                long fund = em.createQuery("select f.id from Fund f where f.code = 'GENERAL'", Long.class).getSingleResult();
                var date = java.time.LocalDate.of(2026, 2, 1);
                new org.nonprofitbookkeeping.service.TransactionEntryService(jpa).enter(
                        new org.nonprofitbookkeeping.service.TransactionCommand(date, null, "Menu check", null, List.of(
                                org.nonprofitbookkeeping.service.FundTransferTestFixture.line(expense, fund, "10", "0"),
                                new org.nonprofitbookkeeping.service.TransactionLineCommand(bank, fund, null, null, null,
                                        java.math.BigDecimal.ZERO, java.math.BigDecimal.TEN, false, null,
                                        new org.nonprofitbookkeeping.service.PaymentReference(
                                                org.nonprofitbookkeeping.service.PaymentReference.Method.CHECK, "menu-check", date, null)))));
            }
        }
        FxTestSupport.onFx(() ->
        {
            window.openPanel(AppPanelId.RECONCILIATION_RUNS);
            assertTrue(saveMenu().isDisable());
            TabPane workflow = (TabPane) window.lookup("#reconciliationWorkflowTabs");
            selectTab(workflow, "5. Checks / Exceptions");
            window.applyCss();
            return null;
        });
        await(() -> !((TableView<?>) window.lookup("#checkExceptionsTable")).getItems().isEmpty());
        FxTestSupport.onFx(() ->
        {
            ((TableView<?>) window.lookup("#checkExceptionsTable")).getSelectionModel().selectFirst();
            ((TextArea) window.lookup("#checkReviewNote")).setText("Saved from File menu");
            ((TextField) window.lookup("#checkEvidenceReference")).setText("menu-evidence");
            assertEquals("Save Review", saveMenu().getText());
            assertFalse(saveMenu().isDisable());
            saveMenu().fire();
            assertTrue(saveMenu().isDisable(), "Save is disabled during the asynchronous write.");
            return null;
        });
        await(() -> UiServiceRegistry.checkExceptions().report(null, java.time.LocalDate.of(2026, 12, 31), null)
                .stream().anyMatch(row -> row.facts() != null && "menu-evidence".equals(row.facts().evidenceReference())));
    }

    @Test
    void nestedNmrPolicySaveUsesFileMenuAndToolbarReadiness()
    {
        FxTestSupport.onFx(() ->
        {
            window.openPanel(AppPanelId.SETTINGS);
            selectTab((TabPane) window.lookup("#administrationTabs"), "NMR Policies");
            assertTrue(saveMenu().isDisable());
            window.executeCommand(AppCommand.NEW_ACTIVE);
            assertEquals("Save Policy Version", saveMenu().getText());
            assertFalse(saveMenu().isDisable());
            assertFalse(saveButton().isDisabled());
            window.applyCss();
            window.layout();
            NmrPoliciesPanelTest.fill(window, "MENU-SAVE", "12.3456");
            saveMenu().fire();
            assertEquals(1, UiServiceRegistry.nmrPolicies().list().size());
            assertEquals(new java.math.BigDecimal("12.3456"), UiServiceRegistry.nmrPolicies().list().get(0).terms().rate());
            assertTrue(saveMenu().isDisable(), "Retained history has no editable Save draft.");
            assertTrue(saveButton().isDisabled());
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(((Button) window.lookup("#nmrPolicyNew")).isDisabled());
            assertTrue(((Button) window.lookup("#nmrPolicyEnd")).isDisabled());
            return null;
        });
    }

    private MenuItem saveMenu()
    {
        MenuBar menus = (MenuBar) ((VBox) window.getTop()).getChildren().get(0);
        return menus.getMenus().stream().filter(menu -> "File".equals(menu.getText())).findFirst().orElseThrow()
                .getItems().stream().filter(item -> item.getText() != null && item.getText().startsWith("Save"))
                .findFirst().orElseThrow();
    }

    private Button saveButton()
    {
        ToolBar toolbar = (ToolBar) ((VBox) window.getTop()).getChildren().get(1);
        return toolbar.getItems().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getText().startsWith("Save")).findFirst().orElseThrow();
    }

    private static void selectTab(TabPane tabs, String label)
    {
        tabs.getSelectionModel().select(tabs.getTabs().stream()
                .filter(tab -> label.equals(tab.getText())).findFirst().orElseThrow());
    }

    private static void await(BooleanSupplier condition) throws Exception
    {
        long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (!FxTestSupport.onFx(condition::getAsBoolean))
        {
            assertTrue(System.nanoTime() < end, "Import preview did not become ready.");
            Thread.sleep(25);
        }
    }
}
