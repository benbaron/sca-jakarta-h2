package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.geometry.Orientation;
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
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
@ResourceLock("ui-service-registry")
class NativeChartEditorPaneTest
{
    @TempDir Path directory;
    private DatabaseSelectionState oldDatabase;
    private MultiCompanyState oldCompany;
    private Optional<AuthenticatedUserSession> oldUser;
    private Path database;
    private Stage stage;
    private ChartOfAccountsInterchangePanel panel;
    private ChartOfAccountsPanel accounts;
    private long sourceChart;
    @BeforeEach

    void setup() throws Exception
    {
        FxTestSupport.initToolkitOrSkip();
        var session = MainWindow.sharedSessionState();
        oldDatabase = session.databaseSelection();
        oldCompany = session.multiCompany();
        oldUser = session.authenticatedUser();
        database = directory.resolve("native-chart-ui");
        try (Jpa jpa = new Jpa(database))
        {
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                Company other = new Company();
                other.setCode("OTHER");
                other.setDisplayName("Other company");
                other.setDefaultCurrency("USD");
                em.persist(other);
                em.getTransaction().commit();
            }
            var source = new ChartAdminService(jpa, () -> "OTHER", null).save(new ChartCommand(null,"Template chart","2",false,null));
            sourceChart = source.id();
            var admin = new AccountAdminService(jpa, () -> "OTHER");
            admin.save(new AccountCommand(null,"1000","Asset group",AccountType.ASSET,null,NormalBalance.DEBIT,null,null,true,sourceChart,
                new AccountDefinitionDetails(false,null,null,BigDecimal.ZERO,"Group notes")));
            admin.save(new AccountCommand(null,"1010","Template bank",AccountType.ASSET,AccountFunction.BANK,NormalBalance.DEBIT,AccountSubtype.CASH,"1000",false,sourceChart,
                new AccountDefinitionDetails(true,null,null,new BigDecimal("99.50"),"Source notes")));
        }
        session.setDatabaseSelection(new DatabaseSelectionState(database.toString(),List.of(database.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT",List.of("DEFAULT","OTHER")));
        UiServiceRegistry.reconnectToDatabase(database);
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            session.setAuthenticatedUser(UiPermissionTestSessions.manager());
            accounts = new ChartOfAccountsPanel(new ChartOfAccountsPanel.ChartActions()
            {
                @Override public ChartOfAccountsPanel.ChartData load()
                {
                    return captureLoad().get();
                }
                @Override public java.util.function.Supplier<ChartOfAccountsPanel.ChartData> captureLoad()
                {
                    return UiServiceRegistry.chartDataLoader();
                }
                @Override public void activate(long company, long chart)
                {
                    UiServiceRegistry.companyAdmin().assignActiveChart(company,chart);
                }
                @Override public Runnable captureActivation(long company, long chart)
                {
                    var service = UiServiceRegistry.companyAdmin();
                    return () -> service.assignActiveChart(company,chart);
                }
            },chart -> true);
            panel = new ChartOfAccountsInterchangePanel(accounts,(chooser,owner) -> null);
            PanelFactory.prepareProductionPanel(panel,AppPanelId.CHART_OF_ACCOUNTS);
            PanelHost host = new PanelHost();
            stage = new Stage();
            stage.setScene(new Scene(host,1050,790));
            stage.getScene().getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
            host.getStyleClass().add("production-workspace");
            host.setStyle("-fx-font-family: 'DejaVu Sans'; -fx-font-size: 12px;");
            WorkspaceTextContrastEnforcer.install(host);
            stage.show();
            host.showReplacement(AppPanelId.CHART_OF_ACCOUNTS,panel);
            return null;
        });
        await(() -> !accounts.loadingCharts());
    }
    @AfterEach

    void cleanup()
    {
        if (oldDatabase == null) return;
        FxTestSupport.onFx(() ->
        {
            if (stage != null) stage.close();
            var session = MainWindow.sharedSessionState();
            session.setDatabaseSelection(oldDatabase);
            session.setMultiCompany(oldCompany);
            oldUser.ifPresentOrElse(session::setAuthenticatedUser,session::clearAuthenticatedUser);
            return null;
        });
        UiServiceRegistry.reconnectToDatabase(Path.of(oldDatabase.activeDatabasePath()));
    }

    @Test
    void nativeCreateAndFullAccountEditingWorkBeforeExplicitActivation() throws Exception
    {
        createViaUi("My native chart");
        long chart = FxTestSupport.onFx(() -> accounts.selectedChart().id());
        assertFalse(FxTestSupport.onFx(() -> accounts.selectedChart().activeForCompany()));
        FxTestSupport.onFx(() ->
        {
            assertEquals(1,Window.getWindows().stream().filter(Window::isShowing).count());
            accounts.setFormStateForTests(new ChartOfAccountsPanel.FormState(null,"1000","Native group",AccountType.ASSET,null,NormalBalance.DEBIT,null,"",true));
            check("chartOfAccountsPosting").setSelected(false);
            @SuppressWarnings("unchecked") ComboBox<AccountSubtype> subtype = (ComboBox<AccountSubtype>) node("chartOfAccountsSubtype");
            subtype.setValue(AccountSubtype.INVENTORY);
            button("chartOfAccountsClearSubtype").fire();
            assertNull(subtype.getValue(),"Optional subtype can be cleared natively");
            assertEquals("Save Account",panel.commandLabel(AppCommand.SAVE_ACTIVE));
            panel.executeCommand(AppCommand.SAVE_ACTIVE);
            return null;
        });
        await(() -> !accounts.loadingCharts() && accountTable().getItems().size() == 1);
        FxTestSupport.onFx(() ->
        {
            button("chartOfAccountsNewAccount").fire();
            accounts.setFormStateForTests(new ChartOfAccountsPanel.FormState(null,"1010","Native bank",AccountType.ASSET,AccountFunction.BANK,NormalBalance.DEBIT,AccountSubtype.CASH,"1000",true));
            field("chartOfAccountsOpeningBalance").setText("12.345");
            ((DatePicker) node("chartOfAccountsEffectiveFrom")).getEditor().setText("1/1/2026");
            ((TextArea) node("chartOfAccountsDescription")).setText("Written without a JSON file");
            button("chartOfAccountsSaveAccount").fire();
            assertEquals("12.345",field("chartOfAccountsOpeningBalance").getText(),"Overprecision must not be rounded away");
            assertEquals(1,accountTable().getItems().size(),"Rejected input saves no account");
            field("chartOfAccountsOpeningBalance").setText("12.34");
            button("chartOfAccountsSaveAccount").fire();
            return null;
        });
        await(() -> !accounts.loadingCharts() && accountTable().getItems().size() == 2);
        try (Jpa jpa = new Jpa(database); var em = jpa.em())
        {
            var bank = em.createQuery("from Account where chart.id=:chart and code='1010'",Account.class).setParameter("chart",chart).getSingleResult();
            assertEquals(new BigDecimal("12.34"),bank.getOpeningBalance());
            assertEquals("Written without a JSON file",bank.getDescription());
            assertEquals("1000",bank.getParent().getCode());
            assertNull(em.createQuery("from Company where code='DEFAULT'",Company.class).getSingleResult().getActiveChartOfAccounts());
            assertEquals(0L,em.createQuery("select count(t) from Txn t",Long.class).getSingleResult());
        }
        FxTestSupport.onFx(() ->
        {
            button("makeActiveChart").fire();
            return null;
        });
        await(() -> !accounts.loadingCharts() && accounts.selectedChart().activeForCompany());
        assertEquals(chart,FxTestSupport.onFx(() -> accounts.selectedChart().id()));
    }

    @Test
    void savesTheWholeClonedChartBeforeReturningToImportAndKeepsRejectedEditsOpen() throws Exception
    {
        openClone();
        FxTestSupport.onFx(() ->
        {
            field("nativeChartName").setText("Import account copy");
            button("nativeChartSave").fire();
            return null;
        });
        await(() -> !accounts.loadingCharts() && accounts.selectedChart() != null
                && accounts.selectedChart().name().equals("Import account copy"));
        long chartId = FxTestSupport.onFx(() -> accounts.selectedChart().id());

        FxTestSupport.onFx(() ->
        {
            panel.onPanelShownFrom(AppPanelId.IMPORT_PREVIEW, "Import");
            return null;
        });
        await(() -> !accounts.loadingCharts() && accountTable().getItems().size() == 2);
        FxTestSupport.onFx(() ->
        {
            selectAccount("1010");
            assertTrue(button("coaSaveChart").isVisible());
            assertEquals("Save Account", button("chartOfAccountsSaveAccount").getText());
            field("chartOfAccountsName").setText("First saved edit");
            button("chartOfAccountsSaveAccount").fire();
            return null;
        });
        await(() -> !accounts.loadingCharts() && accountStatus().startsWith("Saved account 1010"));

        FxTestSupport.onFx(() ->
        {
            button("coaEditChart").fire();
            return null;
        });
        await(() -> field("nativeChartName") != null && field("nativeChartName").getText().equals("Import account copy"));
        FxTestSupport.onFx(() ->
        {
            field("nativeChartName").setText("Edited full chart");
            button("nativeChartBack").fire();
            return null;
        });
        await(() -> !accounts.loadingCharts());
        var feedback = new java.util.concurrent.atomic.AtomicReference<AppPanel.RunCommandResult>();
        try
        {
            FxTestSupport.onFx(() ->
            {
                var host = (PanelHost) stage.getScene().getRoot();
                host.setCommandResultListener(feedback::set);
                DrillThroughCoordinator.configureOpener(host::show);
                field("chartOfAccountsCode").setText("1000");
                field("chartOfAccountsName").setText("Final saved edit");
                Button finish = button("coaSaveChartAndReturn");
                assertTrue(finish.isVisible());
                assertEquals("Save Chart and Return to Import", finish.getText());
                screenshot("chart-save-and-return");
                finish.fire();
                return null;
            });
            await(() -> globalStatus().contains("Account code already exists"));
            FxTestSupport.onFx(() ->
            {
                var host = (PanelHost) stage.getScene().getRoot();
                assertSame(panel.root(), host.activeRoot());
                assertTrue(panel.hasUnsavedChanges());
                assertFalse(feedback.get().handled());
                assertEquals("Final saved edit", field("chartOfAccountsName").getText());
                return null;
            });
            try (Jpa jpa = new Jpa(database); var em = jpa.em())
            {
                assertEquals("Import account copy", em.find(ChartOfAccounts.class, chartId).getName());
                assertEquals("First saved edit", em.createQuery("from Account where chart.id = :chart and code = '1010'", Account.class)
                        .setParameter("chart", chartId).getSingleResult().getName());
            }
            FxTestSupport.onFx(() ->
            {
                field("chartOfAccountsCode").setText("4015");
                button("coaSaveChartAndReturn").fire();
                return null;
            });
            await(() -> "Import".equals(((PanelHost) stage.getScene().getRoot()).getActiveTitle()));
            assertFalse(FxTestSupport.onFx(panel::hasUnsavedChanges));
            assertTrue(feedback.get().handled());
            assertTrue(feedback.get().message().startsWith("Saved chart Edited full chart"));
        }
        finally
        {
            FxTestSupport.onFx(() -> { DrillThroughCoordinator.configureOpener(null); return null; });
        }

        try (Jpa jpa = new Jpa(database); var em = jpa.em())
        {
            var saved = em.createQuery("from Account where chart.id = :chart and code = '4015'", Account.class)
                    .setParameter("chart", chartId).getSingleResult();
            assertEquals("Final saved edit", saved.getName());
            assertEquals(ChartStatus.DRAFT, saved.getChart().getStatus());
            assertEquals("Edited full chart", saved.getChart().getName());
            assertEquals("DEFAULT", saved.getChart().getCompany().getCode());
            assertEquals("Template chart", em.find(ChartOfAccounts.class, sourceChart).getName());
            assertEquals("Template bank", em.createQuery("from Account where chart.id = :chart and code = '1010'", Account.class)
                    .setParameter("chart", sourceChart).getSingleResult().getName());
        }
    }

    @Test
    void clonesOtherCompanyDefinitionsWithZeroOpeningsAndBusyControls() throws Exception
    {
        openClone();
        FxTestSupport.onFx(() ->
        {
            assertTrue(((ComboBox<?>) node("nativeChartCloneSource")).getValue().toString().contains("OTHER"));
            assertTrue(((Label) node("nativeChartDetails")).getText().contains("reset to zero (1"));
            field("nativeChartName").setText("Local copy");
            field("nativeChartVersion").setText("Q4");
            assertEquals("Save Cloned Chart",panel.commandLabel(AppCommand.SAVE_ACTIVE));
            panel.executeCommand(AppCommand.SAVE_ACTIVE);
            assertTrue(button("nativeChartBack").isDisabled());
            assertTrue(button("nativeChartCancel").isDisabled());
            assertTrue(button("coaCloneChart").isDisabled());
            assertTrue(field("nativeChartName").isDisabled());
            return null;
        });
        await(() -> !accounts.loadingCharts() && accounts.selectedChart() != null && accounts.selectedChart().name().equals("Local copy"));
        assertFalse(FxTestSupport.onFx(() -> accounts.selectedChart().activeForCompany()));
        try (Jpa jpa = new Jpa(database); var em = jpa.em())
        {
            var copy = em.createQuery("from ChartOfAccounts where name='Local copy'",ChartOfAccounts.class).getSingleResult();
            var rows = em.createQuery("from Account where chart=:chart",Account.class).setParameter("chart",copy).getResultList();
            assertEquals(2,rows.size());
            assertTrue(rows.stream().allMatch(a -> a.getOpeningBalance().signum() == 0));
            assertTrue(rows.stream().filter(a -> a.getParent() != null).allMatch(a -> a.getParent().getChart().getId().equals(copy.getId())));
            assertEquals("DEFAULT",copy.getCompany().getCode());
            assertEquals(ChartStatus.DRAFT,copy.getStatus());
            assertEquals(new BigDecimal("99.50"),em.createQuery("from Account where chart.id=:chart and code='1010'",Account.class).setParameter("chart",sourceChart).getSingleResult().getOpeningBalance());
            assertEquals(0L,em.createQuery("select count(t) from Txn t",Long.class).getSingleResult());
        }
    }

    @Test
    void savingChartDetailsAlsoSavesThePendingAccountAndPreservesCurrentIdentity() throws Exception
    {
        createViaUi("Current native");
        long chart = FxTestSupport.onFx(() -> accounts.selectedChart().id());
        FxTestSupport.onFx(() ->
        {
            button("makeActiveChart").fire();
            return null;
        });
        await(() -> !accounts.loadingCharts() && accounts.selectedChart().activeForCompany());
        FxTestSupport.onFx(() ->
        {
            accounts.setFormStateForTests(new ChartOfAccountsPanel.FormState(null,"PENDING","Pending account",AccountType.ASSET,null,NormalBalance.DEBIT,null,"",true));
            assertTrue(button("coaNewChart").isDisabled());
            assertTrue(button("coaCloneChart").isDisabled());
            assertFalse(button("coaEditChart").isDisabled());
            button("coaEditChart").fire();
            return null;
        });
        await(() -> field("nativeChartName") != null && field("nativeChartName").getText().equals("Current native") && !field("nativeChartName").isDisabled());
        FxTestSupport.onFx(() ->
        {
            assertTrue(check("nativeChartRetired").isDisabled());
            field("nativeChartName").setText("Renamed native");
            field("nativeChartVersion").setText("2027");
            panel.executeCommand(AppCommand.SAVE_ACTIVE);
            return null;
        });
        await(() -> !accounts.loadingCharts() && accounts.selectedChart().name().equals("Renamed native"));
        FxTestSupport.onFx(() ->
        {
            assertEquals(chart,accounts.selectedChart().id());
            assertTrue(accounts.selectedChart().activeForCompany());
            assertEquals("Pending account",accounts.readFormStateForTests().name());
            assertFalse(panel.hasUnsavedChanges());
            assertEquals("PENDING", ((Account) accountTable().getItems().get(0)).getCode());
            return null;
        });
    }

    @Test
    void backResumeCancelPermissionsAndScopePreserveTheChartDraft() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            button("coaNewChart").fire();
            layout();
            field("nativeChartName").setText("Retained name");
            field("nativeChartVersion").setText("99");
            button("nativeChartBack").fire();
            layout();
            button("coaNewChart").fire();
            layout();
            assertEquals("Retained name",field("nativeChartName").getText());
            var session = MainWindow.sharedSessionState();
            session.setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(button("nativeChartSave").isDisabled());
            session.setAuthenticatedUser(UiPermissionTestSessions.manager());
            session.setMultiCompany(new MultiCompanyState("OTHER",List.of("DEFAULT","OTHER")));
            assertTrue(button("nativeChartSave").isDisabled());
            assertEquals("Retained name",field("nativeChartName").getText());
            session.setMultiCompany(new MultiCompanyState("DEFAULT",List.of("DEFAULT","OTHER")));
            assertFalse(button("nativeChartSave").isDisabled());
            button("nativeChartCancel").fire();
            layout();
            assertFalse(panel.hasUnsavedChanges());
            button("coaNewChart").fire();
            layout();
            assertEquals("",field("nativeChartName").getText());
            return null;
        });
        assertEquals(0,targetCharts());
    }

    @Test
    void rejectedSourceDriftRetainsEditsAndExplicitRefreshAllowsReviewAndRetry() throws Exception
    {
        openClone();
        FxTestSupport.onFx(() ->
        {
            field("nativeChartName").setText("Keep my clone name");
            return null;
        });
        try (Jpa jpa = new Jpa(database))
        {
            var service = new ChartAdminService(jpa,() -> "OTHER",null);
            var original = service.load(sourceChart);
            service.save(new ChartCommand(sourceChart,"Changed template",original.version(),false,original.revision()));
        }
        FxTestSupport.onFx(() ->
        {
            button("nativeChartSave").fire();
            return null;
        });
        await(() -> status().contains("changed"));
        assertEquals(0,targetCharts());
        FxTestSupport.onFx(() ->
        {
            assertEquals("Keep my clone name",field("nativeChartName").getText());
            button("nativeChartRefresh").fire();
            return null;
        });
        await(() -> status().startsWith("Chart details refreshed"));
        FxTestSupport.onFx(() ->
        {
            assertEquals("Keep my clone name",field("nativeChartName").getText());
            assertTrue(((Label) node("nativeChartDetails")).getText().contains("Changed template"));
            button("nativeChartSave").fire();
            return null;
        });
        await(() -> !accounts.loadingCharts() && accounts.selectedChart() != null && accounts.selectedChart().name().equals("Keep my clone name"));
        assertEquals(1,targetCharts());
    }

    @Test
    void visibleJsonActionsCoexistWithNativeMaintenance() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            assertNotNull(button("coaNewChart"));
            assertNotNull(button("coaEditChart"));
            assertNotNull(button("coaCloneChart"));
            assertTrue(button("coaImportJsonButton").isVisible());
            assertTrue(button("coaExportJsonButton").isVisible());
            assertEquals("Import Chart JSON…",button("coaImportJsonButton").getText());
            assertEquals("Export Chart JSON…",button("coaExportJsonButton").getText());
            return null;
        });
        createViaUi("Native without files");
        assertEquals(1,targetCharts());
    }

    @Test
    void nativeChartAndAccountFormsHaveIndependentOverflowAndMovableDividers() throws Exception
    {
        openClone();
        FxTestSupport.onFx(() ->
        {
            screenshot("normal");
            stage.setWidth(550);
            stage.setHeight(590);
            stage.getScene().getRoot().setStyle("-fx-font-size: 18px;");
            return null;
        });
        await(() -> stage.getWidth() <= 550);
        FxTestSupport.onFx(() ->
        {
            SplitPane split = (SplitPane) node("nativeChartEditorSplit");
            screenshot("narrow-before-divider");
            split.setDividerPositions(.20);
            layout();
            double before = split.getDividerPositions()[0];
            split.setDividerPositions(.60);
            layout();
            assertTrue(split.getDividerPositions()[0] > before + .04, "Divider before="+before+", after="+split.getDividerPositions()[0]+", height="+split.getHeight());
            javafx.scene.Node cursor = field("nativeChartName");
            while (!(cursor instanceof ScrollPane)) cursor = cursor.getParent();
            ScrollPane scroll = (ScrollPane) cursor;
            var bars = scroll.lookupAll(".scroll-bar").stream().filter(ScrollBar.class::isInstance).map(ScrollBar.class::cast).filter(ScrollBar::isVisible).toList();
            assertTrue(bars.stream().anyMatch(b -> b.getOrientation() == Orientation.HORIZONTAL && b.getMax() > b.getMin()));
            assertTrue(bars.stream().anyMatch(b -> b.getOrientation() == Orientation.VERTICAL && b.getMax() > b.getMin()));
            scroll.setVvalue(1);
            scroll.setHvalue(1);
            layout();
            assertEquals(1,scroll.getVvalue());
            assertEquals(1,scroll.getHvalue());
            scroll.setVvalue(0);
            scroll.setHvalue(0);
            screenshot("narrow");
            return null;
        });
    }

    @Test
    void accountantUsesNativeCreationWhileActivationRetainsItsAdministrationPermission() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.session(java.util.Set.of(ReservedSecurityRole.ACCOUNTANT)));
            assertFalse(button("coaNewChart").isDisabled());
            assertFalse(button("coaCloneChart").isDisabled());
            return null;
        });
        createViaUi("Accountant native chart");
        FxTestSupport.onFx(() ->
        {
            assertTrue(button("makeActiveChart").isDisabled());
            assertTrue(accounts.commandCapabilities().contains(AppCommand.SAVE_ACTIVE));
            return null;
        });
    }

    @Test
    void draftDeletionChecksChildrenThenDeletesLeafAndGroupWithBusyControls() throws Exception
    {
        draftAccounts();
        java.util.concurrent.atomic.AtomicInteger confirmed = new java.util.concurrent.atomic.AtomicInteger();
        FxTestSupport.onFx(() ->
        {
            accounts.setDeleteConfirmationForTests(preview ->
            {
                confirmed.incrementAndGet();
                assertTrue(accounts.deletingAccount());
                assertTrue(panel.commandCapabilities().isEmpty());
                assertTrue(button("coaImportJsonButton").isDisabled());
                assertTrue(button("coaNewChart").isDisabled());
                assertTrue(button("chartOfAccountsSaveAccount").isDisabled());
                return true;
            });
            selectAccount("1000");
            button("chartOfAccountsDeleteAccount").fire();
            return null;
        });
        await(() -> !accounts.deletingAccount() && accountStatus().contains("child accounts"));
        assertEquals(0,confirmed.get());
        FxTestSupport.onFx(() ->
        {
            selectAccount("1010");
            button("chartOfAccountsDeleteAccount").fire();
            return null;
        });
        await(() -> !accounts.deletingAccount() && !accounts.loadingCharts() && accountTable().getItems().size()==1);
        FxTestSupport.onFx(() ->
        {
            assertEquals("",field("chartOfAccountsCode").getText());
            selectAccount("1000");
            button("chartOfAccountsDeleteAccount").fire();
            return null;
        });
        await(() -> !accounts.deletingAccount() && !accounts.loadingCharts() && accountTable().getItems().isEmpty());
        assertEquals(2,confirmed.get());
        try (Jpa jpa=new Jpa(database);var em=jpa.em())
        {
            assertEquals(2L,em.createQuery("select count(a) from AuditEvent a where a.actionType='ACCOUNT_DELETED'",Long.class).getSingleResult());
            assertEquals(0L,em.createQuery("select count(t) from Txn t",Long.class).getSingleResult());
        }
    }

    @Test
    void dirtyCancelRoleAndNonDraftDeletionRulesAreVisible() throws Exception
    {
        draftAccounts();
        FxTestSupport.onFx(() ->
        {
            selectAccount("1010");
            field("chartOfAccountsName").setText("Unapplied");
            assertTrue(button("chartOfAccountsDeleteAccount").isDisabled());
            assertTrue(((Label)node("chartOfAccountsLifecycle")).getText().contains("Save or Cancel"));
            button("chartOfAccountsCancelAccount").fire();
            assertFalse(button("chartOfAccountsDeleteAccount").isDisabled());
            accounts.setDeleteConfirmationForTests(preview -> false);
            button("chartOfAccountsDeleteAccount").fire();
            return null;
        });
        await(() -> !accounts.deletingAccount() && accountStatus().contains("cancelled"));
        FxTestSupport.onFx(() ->
        {
            assertEquals("Account 1010",field("chartOfAccountsName").getText());
            assertEquals(2,accountTable().getItems().size());
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.session(java.util.Set.of(ReservedSecurityRole.VIEWER)));
            assertTrue(button("chartOfAccountsDeleteAccount").isDisabled());
            assertTrue(((Label)node("chartOfAccountsLifecycle")).getText().contains("BOOKKEEPING_WRITE"));
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.manager());
            button("makeActiveChart").fire();
            return null;
        });
        await(() -> !accounts.loadingCharts() && accounts.selectedChart().activeForCompany());
        FxTestSupport.onFx(() ->
        {
            assertTrue(button("chartOfAccountsDeleteAccount").isDisabled());
            assertTrue(((Label)node("chartOfAccountsLifecycle")).getText().contains("DRAFT"));
            return null;
        });
    }

    @Test
    void changedDefinitionOrCompanyDuringConfirmationPreservesTheAccountAndEditor() throws Exception
    {
        draftAccounts();
        FxTestSupport.onFx(() ->
        {
            selectAccount("1010");
            accounts.setDeleteConfirmationForTests(preview ->
            {
                try(Jpa jpa=new Jpa(database);var em=jpa.em())
                {
                    em.getTransaction().begin();
                    em.find(Account.class,preview.accountId()).setName("Changed elsewhere");
                    em.getTransaction().commit();
                }
                return true;
            });
            button("chartOfAccountsDeleteAccount").fire();
            return null;
        });
        await(() -> !accounts.deletingAccount() && accountStatus().contains("changed"));
        FxTestSupport.onFx(() ->
        {
            assertEquals("Account 1010",field("chartOfAccountsName").getText());
            assertEquals(2,accountTable().getItems().size());
            accounts.setDeleteConfirmationForTests(preview ->
            {
                MainWindow.sharedSessionState().setMultiCompany(new MultiCompanyState("OTHER",List.of("DEFAULT","OTHER")));
                return true;
            });
            button("chartOfAccountsDeleteAccount").fire();
            return null;
        });
        await(() -> !accounts.deletingAccount() && accountStatus().contains("Company/database changed"));
        try(Jpa jpa=new Jpa(database);var em=jpa.em())
        {
            assertEquals(2L,em.createQuery("select count(a) from Account a where a.chart.company.code='DEFAULT'",Long.class).getSingleResult());
            assertEquals(0L,em.createQuery("select count(a) from AuditEvent a where a.actionType='ACCOUNT_DELETED'",Long.class).getSingleResult());
        }
    }

    @Test
    void visibleJsonButtonsExportTheViewedDraftAndOpenItsInlineImport() throws Exception
    {
        draftAccounts();
        Path exported=directory.resolve("viewed-draft.json");
        FxTestSupport.onFx(() ->
        {
            panel=new ChartOfAccountsInterchangePanel(accounts,(chooser,owner) -> exported.toFile(),(chooser,owner) -> exported.toFile());
            PanelFactory.prepareProductionPanel(panel,AppPanelId.CHART_OF_ACCOUNTS);
            ((PanelHost)stage.getScene().getRoot()).showReplacement(AppPanelId.CHART_OF_ACCOUNTS,panel);
            return null;
        });
        await(() -> !accounts.loadingCharts() && !button("coaExportJsonButton").isDisabled());
        FxTestSupport.onFx(() ->
        {
            selectAccount("1010");
            field("chartOfAccountsName").setText("Unapplied name");
            button("coaExportJsonButton").fire();
            assertTrue(button("coaImportJsonButton").isDisabled());
            return null;
        });
        await(() -> java.nio.file.Files.exists(exported) && ((Label)node("coaInterchangeStatus")).getText().startsWith("Exported chart"));
        String json=java.nio.file.Files.readString(exported);
        assertTrue(json.contains("Deletion draft"));
        assertTrue(json.contains("1010"));
        assertTrue(json.contains("DRAFT"));
        assertFalse(json.contains("Unapplied name"));
        FxTestSupport.onFx(() ->
        {
            assertFalse(accounts.selectedChart().activeForCompany());
            button("coaExportJsonButton").fire();
            assertTrue(((Label)node("coaInterchangeStatus")).getText().contains("destination exists"));
            button("coaImportJsonButton").fire();
            layout();
            assertNotNull(node("coaJsonImportMode"));
            assertNotNull(node("coaReviewPreview"));
            assertTrue(button("coaImportJsonButton").isVisible());
            return null;
        });
        try(Jpa jpa=new Jpa(database);var em=jpa.em())
        {
            assertNull(em.createQuery("from Company where code='DEFAULT'",Company.class).getSingleResult().getActiveChartOfAccounts());
        }
    }

    @Test
    void jsonExportDoesNotRedirectIfTheCompanyChangesDuringFileSelection() throws Exception
    {
        draftAccounts();
        Path destination=directory.resolve("must-not-export.json");
        FxTestSupport.onFx(() ->
        {
            panel=new ChartOfAccountsInterchangePanel(accounts,(chooser,owner) -> null,(chooser,owner) ->
            {
                MainWindow.sharedSessionState().setMultiCompany(new MultiCompanyState("OTHER",List.of("DEFAULT","OTHER")));
                return destination.toFile();
            });
            PanelFactory.prepareProductionPanel(panel,AppPanelId.CHART_OF_ACCOUNTS);
            ((PanelHost)stage.getScene().getRoot()).showReplacement(AppPanelId.CHART_OF_ACCOUNTS,panel);
            return null;
        });
        await(() -> !accounts.loadingCharts());
        FxTestSupport.onFx(() ->
        {
            button("coaExportJsonButton").fire();
            assertTrue(((Label)node("coaInterchangeStatus")).getText().contains("export cancelled"));
            return null;
        });
        assertFalse(java.nio.file.Files.exists(destination));
    }

    @Test
    void accountDeleteAndJsonControlsRemainReachableWithIndependentEditorScrolling() throws Exception
    {
        draftAccounts();
        FxTestSupport.onFx(() ->
        {
            selectAccount("1010");
            screenshot("draft-accounts-normal");
            stage.setWidth(550);
            stage.setHeight(650);
            stage.getScene().getRoot().setStyle("-fx-font-size: 18px;");
            return null;
        });
        await(() -> stage.getScene().getWidth()<=550 && stage.getScene().getHeight()<=650);
        FxTestSupport.onFx(() ->
        {
            layout();
            var wrapperRoot=(javafx.scene.layout.BorderPane)panel.root();
            var globalHeader=wrapperRoot.getTop().localToScene(wrapperRoot.getTop().getBoundsInLocal());
            var accountRoot=accounts.root().localToScene(accounts.root().getBoundsInLocal());
            assertTrue(accountRoot.getMinY()>=globalHeader.getMaxY()-1,"Account content overlaps the wrapper header");
            ScrollPane toolbarScroll=(ScrollPane)node("coaChartToolbarScroll");
            for (String id : List.of("coaNewChart","coaDeleteDraftChart","coaImportJsonButton","coaExportJsonButton"))
            {
                var control=button(id);
                var bounds=toolbarScroll.getContent().sceneToLocal(control.localToScene(control.getBoundsInLocal()));
                double range=toolbarScroll.getContent().getLayoutBounds().getHeight()-toolbarScroll.getViewportBounds().getHeight();
                toolbarScroll.setVvalue(range <= 0 ? 0 : Math.min(1,Math.max(0,bounds.getMinY()/range)));
                layout();
                var viewport=toolbarScroll.lookup(".viewport");
                var visible=viewport.localToScene(viewport.getBoundsInLocal());
                var actual=control.localToScene(control.getBoundsInLocal());
                assertTrue(actual.getMinY()>=visible.getMinY()-1 && actual.getMaxY()<=visible.getMaxY()+1,id+" must be fully reachable in toolbar viewport");
            }
            toolbarScroll.setVvalue(0);
            SplitPane chrome=(SplitPane)node("chartOfAccountsChromeSplit");
            chrome.setDividerPositions(.25);
            layout();
            double before=chrome.getDividerPositions()[0];
            chrome.setDividerPositions(.50);
            layout();
            assertTrue(chrome.getDividerPositions()[0]>before+.05);
            chrome.setDividerPositions(.30);
            layout();
            var body=(SplitPane)node("chartOfAccountsWorkspaceSplit");
            body.setDividerPositions(.50);
            layout();
            body.layout();
            assertTrue(body.getHeight()>80,"Header must leave a usable account table/editor body");
            assertTrue(accountTable().getHeight()>20);
            accountTable().layout();
            screenshot("draft-before-row-check");
            var flow=accountTable().lookup(".virtual-flow");
            var row=accountTable().lookupAll(".table-row-cell").stream().filter(TableRow.class::isInstance)
                    .map(TableRow.class::cast).filter(r -> !r.isEmpty()).findFirst().orElseThrow();
            var horizontal=accountTable().lookupAll(".scroll-bar").stream().filter(ScrollBar.class::isInstance).map(ScrollBar.class::cast)
                    .filter(b -> b.isVisible() && b.getOrientation()==Orientation.HORIZONTAL).findFirst().orElseThrow();
            assertTrue(flow.getBoundsInLocal().getHeight()+.5>=row.getHeight()+horizontal.getHeight(),
                    "A complete account row must fit above the rendered scrollbar: flow="+flow.getBoundsInLocal().getHeight()
                    +", row="+row.getHeight()+", scrollbar="+horizontal.getHeight());
            var header=(ScrollPane)node("chartOfAccountsHeaderScroll");
            assertTrue(header.lookupAll(".scroll-bar").stream().filter(ScrollBar.class::isInstance).map(ScrollBar.class::cast)
                .anyMatch(b -> b.isVisible() && b.getOrientation()==Orientation.VERTICAL));
            header.setVvalue(1);
            layout();
            assertEquals(1,header.getVvalue());
            header.setVvalue(0);
            layout();
            var deleteBounds=header.getContent().sceneToLocal(button("chartOfAccountsDeleteAccount").localToScene(button("chartOfAccountsDeleteAccount").getBoundsInLocal()));
            double range=header.getContent().getLayoutBounds().getHeight()-header.getViewportBounds().getHeight();
            header.setVvalue(Math.min(1,Math.max(0,deleteBounds.getMinY()/range)));
            layout();
            var viewport=header.lookup(".viewport");
            var viewportBounds=viewport.localToScene(viewport.getBoundsInLocal());
            var visibleDelete=button("chartOfAccountsDeleteAccount").localToScene(button("chartOfAccountsDeleteAccount").getBoundsInLocal());
            assertTrue(visibleDelete.getMinY()>=viewportBounds.getMinY()-1 && visibleDelete.getMaxY()<=viewportBounds.getMaxY()+1,"Delete must be fully reachable by scrolling the header");
            for(String id:List.of("coaImportJsonButton","coaExportJsonButton","chartOfAccountsDeleteAccount"))
            {
                var control=button(id);
                var bounds=control.localToScene(control.getBoundsInLocal());
                assertTrue(bounds.getMinX()>=0 && bounds.getMaxX()<=stage.getScene().getWidth(),id+" clipped horizontally");
                assertTrue(bounds.getMinY()>=0 && bounds.getMaxY()<=stage.getScene().getHeight(),id+" clipped vertically");
            }
            ScrollPane editor=(ScrollPane)node("chartOfAccountsEditorScroll");
            editor.layout();
            assertTrue(editor.getViewportBounds().getHeight()>20,"Editor viewport="+editor.getViewportBounds().getHeight()+", body="+body.getHeight()+", divider="+body.getDividerPositions()[0]);
            var bars=editor.lookupAll(".scroll-bar").stream().filter(ScrollBar.class::isInstance).map(ScrollBar.class::cast).filter(ScrollBar::isVisible).toList();
            assertTrue(bars.stream().anyMatch(b -> b.getOrientation()==Orientation.HORIZONTAL));
            assertTrue(bars.stream().anyMatch(b -> b.getOrientation()==Orientation.VERTICAL));
            editor.setHvalue(1);
            editor.setVvalue(1);
            layout();
            assertEquals(1,editor.getHvalue());
            assertEquals(1,editor.getVvalue());
            editor.setHvalue(0);
            editor.setVvalue(0);
            screenshot("draft-accounts-narrow");
            return null;
        });
    }
    @Test
    void actualFactoryToolbarSurvivesNativeSaveRepeatedCreationAndSubviewReturns() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            panel=(ChartOfAccountsInterchangePanel)new PanelFactory().create(AppPanelId.CHART_OF_ACCOUNTS);
            accounts=panel.accountPanelForTests();
            ((PanelHost)stage.getScene().getRoot()).showReplacement(AppPanelId.CHART_OF_ACCOUNTS,panel);
            return null;
        });
        await(() -> !accounts.loadingCharts());
        createViaUi("First factory chart");
        assertChartToolbar();
        createViaUi("Second factory chart");
        assertChartToolbar();
        assertEquals(2,targetCharts());
        FxTestSupport.onFx(() ->
        {
            screenshot("factory-after-two-charts");
            return null;
        });
        FxTestSupport.onFx(() ->
        {
            button("coaEditChart").fire();
            return null;
        });
        await(() -> field("nativeChartName")!=null && field("nativeChartName").getText().equals("Second factory chart"));
        FxTestSupport.onFx(() ->
        {
            button("nativeChartCancel").fire();
            return null;
        });
        await(() -> !accounts.loadingCharts());
        assertChartToolbar();
        FxTestSupport.onFx(() ->
        {
            button("coaCloneChart").fire();
            return null;
        });
        await(() -> field("nativeChartName") != null && !field("nativeChartName").isDisabled()
            && field("nativeChartName").getText().equals("Second factory chart copy"));
        FxTestSupport.onFx(() ->
        {
            assertTrue(panel.root().lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label -> label.getText().contains("unused, non-current DRAFT") && label.getText().contains("Delete Draft Chart")));
            button("nativeChartBack").fire();
            return null;
        });
        await(() -> !accounts.loadingCharts());
        assertChartToolbar();
    }

    private void assertChartToolbar()
    {
        FxTestSupport.onFx(() ->
        {
            for(String id:List.of("coaNewChart","coaEditChart","coaCloneChart","coaDeleteDraftChart","coaImportJsonButton","coaExportJsonButton"))
            {
                assertNotNull(node(id),"Chart action disappeared after production view change: "+id);
                assertTrue(node(id).isVisible());
            }
            return null;
        });
    }
    @Test
    void deletesAnEmptyDraftAfterCancellationAndKeepsTheToolbarForCreatingAnother() throws Exception
    {
        createViaUi("Empty partial draft");
        FxTestSupport.onFx(() ->
        {
            assertFalse(button("coaDeleteDraftChart").isDisabled());
            panel.setChartDeleteConfirmationForTests(preview ->
            {
                assertEquals(0,preview.accounts());
                assertTrue(button("coaNewChart").isDisabled());
                assertTrue(panel.commandCapabilities().isEmpty());
                return false;
            });
            button("coaDeleteDraftChart").fire();
            return null;
        });
        await(() -> globalStatus().contains("deletion cancelled"));
        assertEquals(1,targetCharts());
        FxTestSupport.onFx(() ->
        {
            panel.setChartDeleteConfirmationForTests(preview -> true);
            button("coaDeleteDraftChart").fire();
            return null;
        });
        await(() -> globalStatus().contains("Deleted draft chart") && !accounts.loadingCharts() && accounts.selectedChart()==null);
        assertEquals(0,targetCharts());
        assertChartToolbar();
        createViaUi("Replacement chart");
        assertEquals(1,targetCharts());
        assertChartToolbar();
    }
    @Test
    void chartDeletionCannotFollowACompanySwitchDuringConfirmation() throws Exception
    {
        createViaUi("Keep original company draft");
        FxTestSupport.onFx(() ->
        {
            panel.setChartDeleteConfirmationForTests(preview ->
            {
                MainWindow.sharedSessionState().setMultiCompany(new MultiCompanyState("OTHER",List.of("DEFAULT","OTHER")));
                return true;
            });
            button("coaDeleteDraftChart").fire();
            return null;
        });
        await(() -> globalStatus().contains("Company/database changed; draft chart deletion cancelled"));
        assertEquals(1,targetCharts());
        try (Jpa jpa = new Jpa(database); var em = jpa.em())
        {
            assertEquals(0L,em.createQuery("select count(a) from AuditEvent a where a.actionType='CHART_DELETED'",Long.class).getSingleResult());
        }
    }
    @Test
    void partialChartDeletionProtectsUnappliedEditsRolesAndBankLinksThenRemovesItsUnusedTree() throws Exception
    {
        draftAccounts();
        long chart=FxTestSupport.onFx(() -> accounts.selectedChart().id());
        FxTestSupport.onFx(() ->
        {
            selectAccount("1010");
            field("chartOfAccountsName").setText("Unapplied");
            assertTrue(button("coaDeleteDraftChart").isDisabled());
            button("chartOfAccountsCancelAccount").fire();
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.session(java.util.Set.of(ReservedSecurityRole.VIEWER)));
            assertTrue(button("coaDeleteDraftChart").isDisabled());
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.session(java.util.Set.of(ReservedSecurityRole.ACCOUNTANT)));
            assertFalse(button("coaDeleteDraftChart").isDisabled());
            return null;
        });
        try(Jpa jpa=new Jpa(database);var em=jpa.em())
        {
            em.getTransaction().begin();
            var a=em.createQuery("from Account where chart.id=:id and code='1010'",Account.class).setParameter("id",chart).getSingleResult();
            var bank=new CompanyBankAccount();
            bank.setCompany(a.getChart().getCompany());
            bank.setAccount(a);
            bank.setName("Draft bank link");
            em.persist(bank);
            em.getTransaction().commit();
        }
        java.util.concurrent.atomic.AtomicInteger confirmations=new java.util.concurrent.atomic.AtomicInteger();
        FxTestSupport.onFx(() ->
        {
            panel.setChartDeleteConfirmationForTests(preview ->
            {
                confirmations.incrementAndGet();
                assertEquals(2,preview.accounts());
                return true;
            });
            button("coaDeleteDraftChart").fire();
            return null;
        });
        await(() -> globalStatus().contains("configured bank accounts"));
        assertEquals(0,confirmations.get());
        try(Jpa jpa=new Jpa(database);var em=jpa.em())
        {
            em.getTransaction().begin();
            em.createQuery("delete from CompanyBankAccount where name='Draft bank link'").executeUpdate();
            em.getTransaction().commit();
        }
        FxTestSupport.onFx(() ->
        {
            button("coaDeleteDraftChart").fire();
            return null;
        });
        await(() -> globalStatus().contains("Deleted draft chart") && !accounts.loadingCharts() && accounts.selectedChart()==null);
        assertEquals(1,confirmations.get());
        assertEquals(0,targetCharts());
        assertChartToolbar();
        try(Jpa jpa=new Jpa(database);var em=jpa.em())
        {
            assertEquals(2L,em.createQuery("select count(a) from Account a where a.chart.company.code='OTHER'",Long.class).getSingleResult());
            assertEquals(0L,em.createQuery("select count(t) from Txn t",Long.class).getSingleResult());
        }
    }
    @Test
    void accountantImportsANewChartThenMergesIntoTheViewedDraftAndExportsWithoutActivation() throws Exception
    {
        draftAccounts();
        long target=FxTestSupport.onFx(() -> accounts.selectedChart().id());
        Path file=directory.resolve("new-and-merge.json"),exported=directory.resolve("merged-draft.json");
        String source="""
                {"format":"SCA-COA","version":"1.0","chart":{"name":"Source","chartVersion":"1","status":"DRAFT","currency":"USD"},
                 "accounts":[{"code":"1000","name":"Account 1000","type":"ASSET","normalBalance":"DEBIT","posting":false,"active":true,"openingBalance":"0.00"},
                 {"code":"1010","name":"Imported leaf","type":"ASSET","normalBalance":"DEBIT","parentCode":"1000","posting":true,"active":true,"openingBalance":"0.00"}]}
                """;
        java.nio.file.Files.writeString(file,source);
        FxTestSupport.onFx(() ->
        {
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.session(java.util.Set.of(ReservedSecurityRole.ACCOUNTANT)));
            panel=new ChartOfAccountsInterchangePanel(accounts,(chooser,owner) -> file.toFile(),(chooser,owner) -> exported.toFile());
            PanelFactory.prepareProductionPanel(panel,AppPanelId.CHART_OF_ACCOUNTS);
            ((PanelHost)stage.getScene().getRoot()).showReplacement(AppPanelId.CHART_OF_ACCOUNTS,panel);
            return null;
        });
        await(() -> !accounts.loadingCharts());
        FxTestSupport.onFx(() ->
        {
            button("coaImportJsonButton").fire();
            layout();
            importMode().setValue(org.nonprofitbookkeeping.interchange.coa.CoaImportMode.CREATE_NEW_CHART);
            field("coaJsonTargetChartName").setText("From JSON");
            field("coaJsonTargetChartVersion").setText("1");
            assertEquals("Create a new chart",importMode().getConverter().toString(importMode().getValue()));
            button("coaReviewPreview").fire();
            return null;
        });
        commitJsonReview();
        FxTestSupport.onFx(() ->
        {
            button("coaReviewBack").fire();
            return null;
        });
        await(() -> !accounts.loadingCharts() && accounts.chartChoices().size()==2);
        assertEquals(2,targetCharts());
        assertChartToolbar();
        assertEquals(target,FxTestSupport.onFx(() -> accounts.selectedChart().id()));
        java.nio.file.Files.writeString(file,source.replace("Imported leaf","Merged leaf"));
        FxTestSupport.onFx(() ->
        {
            button("coaImportJsonButton").fire();
            layout();
            importMode().setValue(org.nonprofitbookkeeping.interchange.coa.CoaImportMode.MERGE_BY_CODE);
            @SuppressWarnings("unchecked") ComboBox<CompanyChartView> targets=(ComboBox<CompanyChartView>)node("coaJsonTargetChart");
            assertEquals(target,targets.getValue().id());
            assertEquals("Merge into an existing chart",importMode().getConverter().toString(importMode().getValue()));
            button("coaReviewPreview").fire();
            return null;
        });
        commitJsonReview();
        assertChartToolbar();
        try(Jpa jpa=new Jpa(database);var em=jpa.em())
        {
            assertEquals("Merged leaf",em.createQuery("from Account where chart.id=:id and code='1010'",Account.class).setParameter("id",target).getSingleResult().getName());
            assertEquals("Imported leaf",em.createQuery("from Account where chart.name='From JSON' and code='1010'",Account.class).getSingleResult().getName());
            assertNull(em.createQuery("from Company where code='DEFAULT'",Company.class).getSingleResult().getActiveChartOfAccounts());
        }
        FxTestSupport.onFx(() ->
        {
            button("coaReviewBack").fire();
            return null;
        });
        await(() -> !accounts.loadingCharts());
        FxTestSupport.onFx(() ->
        {
            assertTrue(button("makeActiveChart").isDisabled());
            button("coaExportJsonButton").fire();
            return null;
        });
        await(() -> java.nio.file.Files.exists(exported) && globalStatus().startsWith("Exported chart"));
        assertTrue(java.nio.file.Files.readString(exported).contains("Merged leaf"));
        assertChartToolbar();
        FxTestSupport.onFx(() ->
        {
            panel.onPanelShownFrom(AppPanelId.REPORT_LIBRARY,"Report Library");
            return null;
        });
        await(() -> !accounts.loadingCharts());
        assertChartToolbar();
    }
    @SuppressWarnings("unchecked")

    private ComboBox<org.nonprofitbookkeeping.interchange.coa.CoaImportMode> importMode()
    {
        return (ComboBox<org.nonprofitbookkeeping.interchange.coa.CoaImportMode>)node("coaJsonImportMode");
    }

    private String globalStatus()
    {
        return ((Label)node("coaInterchangeStatus")).getText();
    }

    private void commitJsonReview() throws Exception
    {
        await(() -> node("coaJsonPreviewTable") instanceof TableView<?> table && table.getItems().size()==2 && !check("coaReviewAcknowledged").isDisabled());
        FxTestSupport.onFx(() ->
        {
            check("coaReviewAcknowledged").setSelected(true);
            assertFalse(button("coaReviewCommit").isDisabled(),"Reviewed import must be ready: "+((Label)node("coaReviewStatus")).getText());
            button("coaReviewCommit").fire();
            return null;
        });
        await(() -> ((Label)node("coaReviewStatus")).getText().startsWith("Imported reviewed accounts"));
        assertChartToolbar();
    }

    private void draftAccounts() throws Exception
    {
        createViaUi("Deletion draft");
        long chart=FxTestSupport.onFx(() -> accounts.selectedChart().id());
        try(Jpa jpa=new Jpa(database))
        {
            var admin=new AccountAdminService(jpa);
            for(int i=0;i<2;i++) admin.save(new AccountCommand(null,i==0?"1000":"1010",i==0?"Account 1000":"Account 1010",
                AccountType.ASSET,null,NormalBalance.DEBIT,null,i==0?null:"1000",true,chart,
                new AccountDefinitionDetails(i!=0,null,null,BigDecimal.ZERO,"Notes")));
        }
        FxTestSupport.onFx(() ->
        {
            accounts.onPanelShown();
            return null;
        });
        await(() -> !accounts.loadingCharts() && accountTable().getItems().size()==2);
    }

    private void selectAccount(String code)
    {
        int index=0;
        for(Object value:accountTable().getItems())
        {
            if(((Account)value).getCode().equals(code))
            {
                accountTable().getSelectionModel().select(index);
                return;
            }
            index++;
        }
        fail("Missing account "+code);
    }

    private String accountStatus()
    {
        return ((Label)node("chartOfAccountsStatus")).getText();
    }

    private void createViaUi(String name) throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            button("coaNewChart").fire();
            layout();
            field("nativeChartName").setText(name);
            field("nativeChartVersion").setText("1");
            assertEquals("Save Chart",panel.commandLabel(AppCommand.SAVE_ACTIVE));
            panel.executeCommand(AppCommand.SAVE_ACTIVE);
            return null;
        });
        await(() -> !accounts.loadingCharts() && accounts.selectedChart() != null && accounts.selectedChart().name().equals(name));
    }

    private void openClone() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            button("coaCloneChart").fire();
            return null;
        });
        await(() -> field("nativeChartName") != null && !field("nativeChartName").isDisabled() && field("nativeChartName").getText().equals("Template chart copy"));
    }

    private long targetCharts()
    {
        try (Jpa jpa = new Jpa(database); var em = jpa.em())
        {
            return em.createQuery("select count(c) from ChartOfAccounts c where c.company.code='DEFAULT'",Long.class).getSingleResult();
        }
    }

    private void await(BooleanSupplier condition) throws Exception
    {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
        while (!FxTestSupport.onFx(() ->
        {
            layout();
            return condition.getAsBoolean();
        }))
        {
            assertTrue(System.nanoTime() < until,"Native chart operation did not finish: " + FxTestSupport.onFx(this::status));
            Thread.sleep(25);
        }
    }

    private void layout()
    {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
    }

    private javafx.scene.Node node(String id)
    {
        return panel.root().lookup("#" + id);
    }

    private Button button(String id)
    {
        return (Button) node(id);
    }

    private TextField field(String id)
    {
        return (TextField) node(id);
    }

    private CheckBox check(String id)
    {
        return (CheckBox) node(id);
    }

    private TableView<?> accountTable()
    {
        return (TableView<?>) accounts.root().lookup(".table-view");
    }

    private String status()
    {
        return node("nativeChartStatus") instanceof Label label ? label.getText()
        : node("chartOfAccountsStatus") instanceof Label label ? label.getText()+" / "+(node("coaInterchangeStatus") instanceof Label global ? global.getText() : "Chart toolbar missing") : "Review page";
    }

    private void screenshot(String suffix) throws Exception
    {
        String base = System.getProperty("native.chart.screenshot");
        if (base == null) return;
        var image = stage.getScene().snapshot(null);
        var buffered = new java.awt.image.BufferedImage((int) image.getWidth(),(int) image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<image.getHeight();y++) for (int x=0;x<image.getWidth();x++) buffered.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        javax.imageio.ImageIO.write(buffered,"png",Path.of(base+"-"+suffix+".png").toFile());
    }
}
