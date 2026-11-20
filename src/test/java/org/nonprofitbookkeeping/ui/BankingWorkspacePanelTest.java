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
            stage = new Stage(); stage.setScene(new Scene((Parent)panel.root(), 1000, 740)); stage.show();
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
            text("bankingWorkspaceFirstOpening").setText("0.00");
            ((CheckBox)panel.root().lookup("#bankingWorkspaceOpeningReviewed")).setSelected(true);
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
        FxTestSupport.onFx(()-> {snapshot("accounts"); text("bankingWorkspaceNickname").setText("Checking draft"); account().getSelectionModel().select(1); return null;});
        await(()->!account().isDisabled() && account().getValue().id() == 3002);
        FxTestSupport.onFx(()->
        {
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
                pages()
                .getSelectionModel().select(page);
                ((Parent)panel.root()).applyCss();
                ((Parent)panel.root()).layout();
                var split = (SplitPane)panel.root().lookup(page == 0 ? "#banking-workspace-accounts" : page == 1 ?
                        "#banking-workspace-journal" : "#banking-workspace-reconciliation");
                assertNotNull(split);
                assertEquals(Orientation.VERTICAL, split.getOrientation());
                split.setDividerPositions(.5);
                assertFalse(split.lookupAll(".split-pane-divider").isEmpty());
                for (var node : panel.root().lookupAll(".table-view"))
                {
                    assertEquals(TableView.UNCONSTRAINED_RESIZE_POLICY, ((TableView<?>)node).getColumnResizePolicy());
                }
            }
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
