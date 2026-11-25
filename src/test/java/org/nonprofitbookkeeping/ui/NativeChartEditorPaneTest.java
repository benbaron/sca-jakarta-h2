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

    void metadataEditingPreservesCurrentIdentityAndThePendingAccountDraft() throws Exception
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
            assertTrue(panel.hasUnsavedChanges());
            button("chartOfAccountsCancelAccount").fire();
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

    void secondaryCompatibilityMenuDoesNotReplaceNativeMaintenance() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            assertNotNull(button("coaNewChart"));
            assertNotNull(button("coaEditChart"));
            assertNotNull(button("coaCloneChart"));
            assertNull(node("coaImportJsonButton"),"JSON import is a menu item rather than a main editing button");
            MenuButton menu = (MenuButton) node("coaCompatibility");
            menu.show();
            assertEquals(2,menu.getItems().size());
            assertTrue(menu.getItems().stream().anyMatch(i -> i.getText().equals("Import Chart JSON…")));
            menu.hide();
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
            double before = split.getDividerPositions()[0];
            split.setDividerPositions(.40);
            layout();
            assertTrue(split.getDividerPositions()[0] > before + .04);
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
        return node("nativeChartStatus") instanceof Label label ? label.getText() : "Account page";
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
