package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Parent;
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

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class BankingWorkspacePanelTest
{
    @TempDir Path directory;
    DatabaseSelectionState previousDatabase;
    MultiCompanyState previousCompany;
    Optional<AuthenticatedUserSession> previousUser;
    Jpa jpa;
    BankingWorkspacePanel panel;
    Stage stage;
    long sessionId;

    @BeforeEach
    void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        var session = MainWindow.sharedSessionState();
        previousDatabase = session.databaseSelection();
        previousCompany = session.multiCompany();
        previousUser = session.authenticatedUser();
        Path database = directory.resolve("banking");
        jpa = new Jpa(database);
        BankReconciliationMutationIntegrityTest.seed(jpa);
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("insert into app_user(id,username,display_name,is_active,created_at,updated_at) values(9001,'operator','Operator',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)").executeUpdate();
            em.getTransaction().commit();
        }
        FxTestSupport.onFx(() ->
        {
            session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
            UiServiceRegistry.reconnectToDatabase(database);
            session.setMultiCompany(new MultiCompanyState("SCA", List.of("SCA")));
            return null;
        });
        var service = new BankReconciliationWorkspaceService(jpa);
        sessionId = service.start(new BankReconciliationWorkspaceService.StartCommand("SCA", 3001L,
                                  LocalDate.of(2026, 3, 31), new BigDecimal("-25.75"), null, null)).sessionId();
        FxTestSupport.onFx(()->
        {
            Platform.setImplicitExit(false);
            session.setAuthenticatedUser(UiPermissionTestSessions.admin().withCompany("SCA", java.util.Set.of(ReservedSecurityRole.ADMIN), java.time.Instant.now()));
            panel = new BankingWorkspacePanel(()->new BankingWorkspaceQuery(jpa), ()->service,
                                              ()->new BankConfigurationService(jpa), ()->new TransactionEntryService(jpa, ()->"SCA"),
                                              ()->"SCA", database::toString, ()->new StubPanel(), ()->new StubPanel());
            PanelViewport.prepare(panel.root(), "banking-workspace-production-test");
            stage = new Stage();
            Scene scene = new Scene((Parent)panel.root(), 1000, 740);
            scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
            stage.setScene(scene); stage.show();
            panel.onPanelShown();
            return null;
        });
        await(()->accounts().getItems().size() == 2 && !account().isDisabled());
    }

    @AfterEach
    void cleanup() throws Exception
    {
        if (previousDatabase == null)
        {
            return;
        }
        FxTestSupport.onFx(()-> {if (stage != null)stage.close(); return null;});
        Thread.sleep(650);
        if (jpa != null)
        {
            jpa.close();
        }
        FxTestSupport.onFx(()->
        {
            var session = MainWindow.sharedSessionState(); session.setDatabaseSelection(previousDatabase); session.setMultiCompany(previousCompany);
            previousUser.ifPresentOrElse(session::setAuthenticatedUser, session::clearAuthenticatedUser); return null;
        });
        UiServiceRegistry.reconnectToDatabase(Path.of(previousDatabase.activeDatabasePath()));
    }

    @Test
    void firstOpeningMatchUndoReferenceAndCompletionUseRealServices()
    {
        FxTestSupport.onFx(()->
        {
            pages().getSelectionModel().select(2);
            assertTrue(panel.root().lookup("#bankingWorkspaceFirstOpeningPane").isVisible());
            assertTrue(button("bankingWorkspaceComplete").isDisabled());
            text("bankingWorkspaceFirstOpening").clear();
            ((CheckBox)panel.root().lookup("#bankingWorkspaceOpeningReviewed")).setSelected(true);
            button("bankingWorkspaceConfirmOpening").fire();
            assertFalse(account().isDisabled());
            assertTrue(panel.root().lookup("#bankingWorkspaceFirstOpeningPane").isVisible());
            text("bankingWorkspaceFirstOpening").setText("0.00");
            button("bankingWorkspaceConfirmOpening").fire(); return null;
        });
        await(()->!account().isDisabled() && !panel.root().lookup("#bankingWorkspaceFirstOpeningPane").isVisible());
        FxTestSupport.onFx(() ->
        {
            assertFalse(button("bankingWorkspaceCorrectOpening").isDisabled());
            button("bankingWorkspaceCorrectOpening").fire();
            text("bankingWorkspaceFirstOpening").setText("0.00");
            ((CheckBox)panel.root().lookup("#bankingWorkspaceOpeningReviewed")).setSelected(true);
            assertEquals("Confirm starting balance",panel.commandLabel(AppCommand.SAVE_ACTIVE));
            panel.onSave();
            return null;
        });
        await(() -> !account().isDisabled() && !panel.root().lookup("#bankingWorkspaceFirstOpeningPane").isVisible());
        FxTestSupport.onFx(()->
        {
            reconciliationPages().getSelectionModel().select(1);
            statements().getSelectionModel().selectFirst(); candidates().getSelectionModel().selectFirst();
            assertFalse(button("bankingWorkspaceMatch").isDisabled()); button("bankingWorkspaceMatch").fire(); return null;
        });
        await(()->!account().isDisabled() && candidates().getItems().get(0).cleared());
        FxTestSupport.onFx(()->
        {
            assertFalse(button("bankingWorkspaceComplete").isDisabled());
            statements().getSelectionModel().selectFirst(); candidates().getSelectionModel().selectFirst();
            button("bankingWorkspaceUnmatch").fire(); return null;
        });
        await(()->!account().isDisabled() && !candidates().getItems().get(0).cleared());
        FxTestSupport.onFx(()->
        {
            pages().getSelectionModel().select(1);
            text("bankingWorkspaceReference").setText("Check-000042");
            panel.onSave(); return null;
        });
        await(()->!account().isDisabled() && journal().getItems().get(0).reference().equals("Check-000042"));
        assertEquals("Check-000042", new BankingWorkspaceQuery(jpa).journal("SCA", 3001, LocalDate.of(2026, 3,
                     31)).get(0).reference());
        FxTestSupport.onFx(()->
        {
            pages().getSelectionModel().select(2); statements().getSelectionModel().selectFirst(); candidates().getSelectionModel().selectFirst();
            button("bankingWorkspaceMatch").fire(); return null;
        });
        await(()->!account().isDisabled() && candidates().getItems().get(0).cleared());
        FxTestSupport.onFx(()->
        {
            snapshot("reconcile-balanced");
            Platform.runLater(()->
            {
                for (var window : List.copyOf(javafx.stage.Window.getWindows()))
                {
                    var pane = window.getScene()
                    == null ? null : window.getScene().getRoot().lookup(".dialog-pane");
                    if (pane instanceof DialogPane dialog && dialog.getButtonTypes().contains(ButtonType.OK))
                    {
                        ((Button)dialog.lookupButton(ButtonType.OK)).fire();
                    }
                }
            });
            button("bankingWorkspaceComplete").fire(); return null;
        });
        await(()->!account().isDisabled() &&
              ((Label)panel.root().lookup("#bankingWorkspaceResult")).getText().contains("Completed"));
        FxTestSupport.onFx(()-> {assertTrue(button("bankingWorkspaceUnmatch").isDisabled()); assertTrue(button("bankingWorkspaceSaveProgress").isDisabled()); return null;});
    }

    @Test
    void accountDraftsAreIndependentAndAllFourViewsRemainScrollableAtLargeText()
    {
        try (var em=jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("delete from txn_split where txn_id=4003").executeUpdate();
            em.createNativeQuery("delete from txn where id=4003").executeUpdate();
            em.createNativeQuery("update company_bank_account set opening_date=DATE '2026-03-10' where id=3002").executeUpdate();
            em.getTransaction().commit();
        }
        FxTestSupport.onFx(() -> {panel.onPanelShown();return null;});
        await(() -> !account().isDisabled());
        FxTestSupport.onFx(()-> {snapshot("accounts"); text("bankingWorkspaceNickname").setText("Checking draft"); account().getSelectionModel().select(1); return null;});
        await(()->!account().isDisabled() && account().getValue().id() == 3002);
        FxTestSupport.onFx(()->
        {
            pages().getSelectionModel().select(1);
            ((Parent)panel.root()).applyCss(); ((Parent)panel.root()).layout();
            assertTrue(((Label)panel.root().lookup("#bankingWorkspaceEntryBank")).getText().contains("Savings"));
            assertEquals("",((Label)panel.root().lookup("#bankingWorkspaceEntryDate")).getText());
            assertTrue(button("bankingWorkspaceOpenJournal").isDisabled());
            pages().getSelectionModel().select(2);
            ((Parent)panel.root()).applyCss(); ((Parent)panel.root()).layout();
            assertEquals(LocalDate.of(2026,3,10),((DatePicker)panel.root().lookup("#bankingWorkspaceFirstDate")).getValue());
            assertEquals("",text("bankingWorkspaceFirstOpening").getText());
            pages().getSelectionModel().select(0);
            text("bankingWorkspaceNickname").setText("Savings draft"); account().getSelectionModel().select(0); return null;
        });
        await(()->!account().isDisabled() && account().getValue().id() == 3001);
        FxTestSupport.onFx(()->
        {
            assertEquals("Checking draft", text("bankingWorkspaceNickname").getText()); assertTrue(panel.hasUnsavedChanges());
            pages().getSelectionModel().select(1); ((Parent)panel.root()).applyCss(); ((Parent)panel.root()).layout(); snapshot("bank-journal");
            pages().getSelectionModel().select(2); ((Parent)panel.root()).applyCss(); ((Parent)panel.root()).layout(); snapshot("first-opening");
            stage.setWidth(650); stage.setHeight(520); panel.root().setStyle("-fx-font-size: 19px;");
            for (int page = 0; page < 3; page++)
            {
                pages().getSelectionModel().select(page);
                if (page == 2) reconciliationPages().getSelectionModel().select(1);
                ((Parent)panel.root()).applyCss();
                ((Parent)panel.root()).layout();
                var split = (SplitPane)panel.root().lookup(page == 0 ? "#banking-workspace-accounts" : page == 1 ?
                        "#banking-workspace-journal" : "#bankingWorkspaceMatchingSplit");
                assertNotNull(split);
                assertEquals(page == 2 ? Orientation.HORIZONTAL : Orientation.VERTICAL, split.getOrientation());
                split.setDividerPositions(.5);
                assertFalse(split.lookupAll(".split-pane-divider").isEmpty());
                assertEquals(2, visibleSplitLeaves(panel.root()), "Count header splits too; never allow a third pane");
                for (var node : panel.root().lookupAll(".table-view"))
                {
                    assertEquals(TableView.UNCONSTRAINED_RESIZE_POLICY, ((TableView<?>)node).getColumnResizePolicy());
                }
            }
            reconciliationPages().getSelectionModel().select(0);
            ((Parent)panel.root()).applyCss();
            ((Parent)panel.root()).layout();
            assertEquals(1, visibleSplitLeaves(panel.root()), "Statement setup uses one full-height viewport");
            assertTrue(panel.root().lookup("#bankingWorkspaceFirstOpeningPane").isVisible());
            assertFalse(panel.root().lookupAll(".scroll-pane").isEmpty());
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer().withCompany("SCA", java.util.Set.of(ReservedSecurityRole.VIEWER), java.time.Instant.now()));
            assertTrue(button("bankingWorkspaceConfirmOpening").isDisabled()); return null;
        });
    }

    @Test
    void firstStatementImportReturnsToTheSameAccountAndUsesReviewedHeaderWithoutAssumingOpening()
    {
        var opened=new java.util.concurrent.atomic.AtomicReference<AppPanelId>();
        FxTestSupport.onFx(()->
        {
            account().getSelectionModel().select(1);
            pages().getSelectionModel().select(2);
            DrillThroughCoordinator.configureOpener(opened::set);
            return null;
        });
        await(()->!account().isDisabled() && account().getValue().id()==3002);
        FxTestSupport.onFx(()->
        {
            assertFalse(button("bankingWorkspaceImport").isDisabled());
            button("bankingWorkspaceImport").fire();
            assertEquals(AppPanelId.IMPORT_PREVIEW,opened.get());
            var request=BankImportNavigationContext.parseImportRequest(DrillThroughCoordinator.consumeContext(AppPanelId.IMPORT_PREVIEW)).orElseThrow();
            assertEquals(3002,request.bankAccountId()); assertEquals(0,request.reconciliationSessionId());
            return null;
        });
        try(var em=jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("update bank_import_batch set statement_start_date=DATE '2026-03-01',statement_end_date=DATE '2026-03-31',ledger_balance=-7 where id=6002").executeUpdate();
            em.getTransaction().commit();
        }
        FxTestSupport.onFx(()->
        {
            DrillThroughCoordinator.openPanelWithContext(AppPanelId.BANKING,BankImportNavigationContext.returnToAccount(3002));
            panel.onPanelShown();return null;
        });
        await(()->!account().isDisabled() && !button("bankingWorkspaceUseImported").isDisabled());
        FxTestSupport.onFx(()->
        {
            assertEquals(3002,account().getValue().id());
            text("bankingWorkspaceStatementEnding").clear();
            button("bankingWorkspaceSaveProgress").fire();
            assertFalse(account().isDisabled());
            assertTrue(new BankReconciliationWorkspaceService(jpa).listSessionsForAccount("SCA",3002).isEmpty());
            button("bankingWorkspaceUseImported").fire();
            assertEquals(LocalDate.of(2026,3,31),((DatePicker)panel.root().lookup("#bankingWorkspaceStatementEnd")).getValue());
            button("bankingWorkspaceSaveProgress").fire();return null;
        });
        await(()->!account().isDisabled() && panel.root().lookup("#bankingWorkspaceFirstOpeningPane").isVisible());
        var sessions=new BankReconciliationWorkspaceService(jpa).listSessionsForAccount("SCA",3002);
        assertEquals(1,sessions.size());
        assertFalse(new BankReconciliationWorkspaceService(jpa).opening(sessions.get(0).id()).confirmed());
        FxTestSupport.onFx(()->{DrillThroughCoordinator.configureOpener(null);return null;});
    }

    @Test
    void pinnedCheckReviewCannotShowAnotherAccountsInitialUnscopedResults()
    {
        var date=LocalDate.of(2026,3,21);
        var service=new TransactionEntryService(jpa,()->"SCA");
        for(long ledger:new long[]{1001,1003})
        {
            var check=new TransactionLineCommand(ledger,2001L,null,null,null,BigDecimal.ZERO,BigDecimal.TEN,false,null,
                    new PaymentReference(PaymentReference.Method.CHECK,"BANK-"+ledger,date,null));
            service.enter(new TransactionCommand(date,null,"Scoped check "+ledger,null,List.of(
                    FundTransferTestFixture.line(1002,2001,"10","0"),check)));
        }
        var secondary=new java.util.concurrent.atomic.AtomicReference<Stage>();
        FxTestSupport.onFx(()->
        {
            var checks=new CheckExceptionsPane();
            checks.pinConfiguredAccount(3002);
            var window=new Stage();
            window.setScene(new Scene((Parent)checks.root(),950,700));window.show();secondary.set(window);
            return null;
        });
        try
        {
            await(()->((Label)secondary.get().getScene().lookup("#checkReviewStatus")).getText().startsWith("Check and bank exception report loaded"));
            FxTestSupport.onFx(()->
            {
                var table=(TableView<?>)secondary.get().getScene().lookup("#checkExceptionsTable");
                assertFalse(table.getItems().isEmpty());
                assertTrue(table.getItems().stream().map(CheckExceptionService.Row.class::cast)
                        .allMatch(row->row.accountId()==1003),"Only the selected savings ledger's checks may be shown");
                assertTrue(secondary.get().getScene().lookup("#checkReviewBank").isDisabled());
                assertTrue(secondary.get().getScene().lookup("#checkReviewAllBanks").isDisabled());
                return null;
            });
        }
        finally
        {
            FxTestSupport.onFx(()->{secondary.get().close();return null;});
        }
    }

    @Test
    void failedManualStatementDraftsStayWithTheirAccountAndPeriodUntilRetryOrCancel()
    {
        new BankReconciliationWorkspaceService(jpa).start(new BankReconciliationWorkspaceService.StartCommand(
                "SCA",3002L,LocalDate.of(2026,3,31),new BigDecimal("-7"),null,null));
        FxTestSupport.onFx(() -> {pages().getSelectionModel().select(2);panel.onPanelShown();return null;});
        await(() -> !account().isDisabled());
        manualDialog(pane ->
        {
            ((DatePicker)pane.lookup("#bankingWorkspaceManualDate")).setValue(LocalDate.of(2026,3,20));
            ((TextField)pane.lookup("#bankingWorkspaceManualDescription")).setText("Checking draft");
            ((TextField)pane.lookup("#bankingWorkspaceManualReference")).setText("A-000042");
            ((TextField)pane.lookup("#bankingWorkspaceManualAmount")).setText("0.00");
            ((Button)pane.lookupButton(ButtonType.OK)).fire();
        });
        await(() -> !account().isDisabled());
        FxTestSupport.onFx(() -> {assertTrue(panel.hasUnsavedChanges());account().getSelectionModel().select(1);return null;});
        await(() -> !account().isDisabled() && account().getValue().id()==3002);
        manualDialog(pane ->
        {
            assertEquals("",((TextField)pane.lookup("#bankingWorkspaceManualDescription")).getText());
            assertEquals("",((TextField)pane.lookup("#bankingWorkspaceManualReference")).getText());
            assertEquals(LocalDate.of(2026,3,31),((DatePicker)pane.lookup("#bankingWorkspaceManualDate")).getValue());
            ((Button)pane.lookupButton(ButtonType.CANCEL)).fire();
        });
        FxTestSupport.onFx(() -> {assertTrue(panel.hasUnsavedChanges());account().getSelectionModel().select(0);return null;});
        await(() -> !account().isDisabled() && account().getValue().id()==3001);
        manualDialog(pane ->
        {
            assertEquals("Checking draft",((TextField)pane.lookup("#bankingWorkspaceManualDescription")).getText());
            assertEquals("A-000042",((TextField)pane.lookup("#bankingWorkspaceManualReference")).getText());
            assertEquals(LocalDate.of(2026,3,20),((DatePicker)pane.lookup("#bankingWorkspaceManualDate")).getValue());
            ((Button)pane.lookupButton(ButtonType.CANCEL)).fire();
        });
        FxTestSupport.onFx(() -> {assertFalse(panel.hasUnsavedChanges());return null;});
    }

    @Test
    void reconciliationSetupAndMatchViewsPreserveDraftsAndHaveAtMostTwoActualPanes()
    {
        FxTestSupport.onFx(() ->
        {
            pages().getSelectionModel().select(2);
            text("bankingWorkspaceStatementEnding").setText("123.45");
            text("bankingWorkspaceFirstOpening").setText("99.00");
            ((DatePicker)panel.root().lookup("#bankingWorkspaceStatementEnd")).setValue(LocalDate.of(2026,3,31));
            for (int pass = 0; pass < 2; pass++)
            {
                reconciliationPages().getSelectionModel().select(1);
                ((Parent)panel.root()).applyCss(); ((Parent)panel.root()).layout();
                assertEquals(2, visibleSplitLeaves(panel.root()));
                assertNotNull(statements()); assertNotNull(candidates());
                reconciliationPages().getSelectionModel().select(0);
                ((Parent)panel.root()).applyCss(); ((Parent)panel.root()).layout();
                assertEquals(1, visibleSplitLeaves(panel.root()));
                assertEquals("123.45",text("bankingWorkspaceStatementEnding").getText());
                assertEquals("99.00",text("bankingWorkspaceFirstOpening").getText());
                assertEquals(LocalDate.of(2026,3,31),((DatePicker)panel.root().lookup("#bankingWorkspaceStatementEnd")).getValue());
                assertTrue(panel.hasUnsavedChanges());
            }
            assertTrue(((javafx.scene.layout.BorderPane)panel.root()).getTop().getBoundsInParent().getHeight() < 140,
                    "Shared bank selector must stay compact at normal window size");
            return null;
        });
    }

    @Test
    void exactSessionDrillThroughOpensItsMatchingView()
    {
        FxTestSupport.onFx(() ->
        {
            DrillThroughCoordinator.configureOpener(ignored -> { });
            DrillThroughCoordinator.openPanelWithContext(AppPanelId.BANKING,
                    BankImportNavigationContext.forReconciliationSession(sessionId));
            panel.onPanelShown();
            DrillThroughCoordinator.configureOpener(null);
            return null;
        });
        await(() -> !account().isDisabled() && pages().getSelectionModel().getSelectedIndex() == 2);
        FxTestSupport.onFx(() ->
        {
            assertEquals(3001,account().getValue().id());
            assertEquals(1,reconciliationPages().getSelectionModel().getSelectedIndex());
            assertFalse(statements().getItems().isEmpty());
            assertFalse(candidates().getItems().isEmpty());
            return null;
        });
    }

    /** Counts actual selected split leaves, deliberately without the old control-header exception. */
    private static int visibleSplitLeaves(javafx.scene.Node node)
    {
        if (node instanceof SplitPane split)
            return split.getItems().stream().mapToInt(BankingWorkspacePanelTest::visibleSplitLeaves).sum();
        if (node instanceof TabPane tabs)
            return visibleSplitLeaves(tabs.getSelectionModel().getSelectedItem().getContent());
        if (node instanceof ScrollPane scroll)
            return visibleSplitLeaves(scroll.getContent());
        if (node instanceof Parent parent && !(node instanceof Control))
            return parent.getChildrenUnmodifiable().stream().mapToInt(BankingWorkspacePanelTest::visibleSplitLeaves).max().orElse(1);
        return 1;
    }

    private void manualDialog(java.util.function.Consumer<DialogPane> action)
    {
        var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        FxTestSupport.onFx(() ->
        {
            Platform.runLater(() ->
            {
                for(var window:List.copyOf(javafx.stage.Window.getWindows()))
                {
                    if(window.getScene()==null || !(window.getScene().getRoot() instanceof DialogPane pane))continue;
                    if(pane.lookup("#bankingWorkspaceManualAmount")==null)continue;
                    try {action.accept(pane);}
                    catch(Throwable error)
                    {
                        failure.set(error);
                        ((Button)pane.lookupButton(ButtonType.CANCEL)).fire();
                    }
                    return;
                }
                failure.set(new AssertionError("Manual statement dialog did not appear"));
            });
            button("bankingWorkspaceManual").fire();
            if(failure.get()!=null)throw new AssertionError(failure.get());
            return null;
        });
    }

    private void snapshot(String name)
    {
        String destination = System.getProperty("bankingSnapshots");
        if (destination == null)
        {
            return;
        }
        try
        {
            var pixels = stage.getScene().snapshot(null);
            var image = new java.awt.image.BufferedImage((int)pixels.getWidth(), (int)pixels.getHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < image.getHeight(); y++)
                for (int x = 0; x < image.getWidth(); x++)
                {
                    image.setRGB(x, y, pixels.getPixelReader().getArgb(x, y));
                }
            javax.imageio.ImageIO.write(image, "png", Path.of(destination, name + ".png").toFile());
        }
        catch (java.io.IOException ex)
        {
            throw new AssertionError(ex);
        }
    }

    @SuppressWarnings("unchecked") ComboBox<BankingWorkspaceQuery.AccountView> account()
    {
        return (ComboBox<BankingWorkspaceQuery.AccountView>)panel.root().lookup("#bankingWorkspaceAccount");
    }
    @SuppressWarnings("unchecked") TableView<BankingWorkspaceQuery.AccountView> accounts()
    {
        return (TableView<BankingWorkspaceQuery.AccountView>)panel.root().lookup("#bankingWorkspaceAccounts");
    }
    @SuppressWarnings("unchecked") TableView<BankingWorkspaceQuery.BankItem> journal()
    {
        return (TableView<BankingWorkspaceQuery.BankItem>)panel.root().lookup("#bankingWorkspaceJournal");
    }
    @SuppressWarnings("unchecked") TableView<BankingWorkspaceQuery.BankItem> candidates()
    {
        return (TableView<BankingWorkspaceQuery.BankItem>)panel.root().lookup("#bankingWorkspaceCandidates");
    }
    @SuppressWarnings("unchecked") TableView<BankReconciliationWorkspaceService.StatementEntryView> statements()
    {
        return (TableView<BankReconciliationWorkspaceService.StatementEntryView>)
               panel.root().lookup("#bankingWorkspaceStatements");
    }
    TabPane pages()
    {
        return (TabPane)panel.root().lookup("#bankingWorkspacePages");
    }
    TabPane reconciliationPages()
    {
        return (TabPane)panel.root().lookup("#bankingWorkspaceReconciliationPages");
    }
    Button button(String id)
    {
        return (Button)panel.root().lookup("#" + id);
    }
    TextField text(String id)
    {
        return (TextField)panel.root().lookup("#" + id);
    }
    private static void await(BooleanSupplier condition)
    {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(25);
        while (!FxTestSupport.onFx(condition::getAsBoolean))
        {
            assertTrue(System.nanoTime() < deadline, "Banking operation did not complete");
            try
            {
                Thread.sleep(30);
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                throw new AssertionError(ex);
            }
        }
    }
    private static final class StubPanel implements AppPanel
    {
        public String title()
        {
            return "Secondary test tools";
        }
        public javafx.scene.Node root()
        {
            return new Label("Secondary test tools");
        }
    }
}
