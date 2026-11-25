package org.nonprofitbookkeeping.ui;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;
import org.nonprofitbookkeeping.service.CompanyChartView;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class ChartOfAccountsSelectionTest
{
    private Optional<AuthenticatedUserSession> previous;
    private Stage stage;

    @BeforeEach
    void prepare()
    {
        FxTestSupport.onFx(() ->
        {
            javafx.application.Platform.setImplicitExit(false);
            previous = MainWindow.sharedSessionState().authenticatedUser();
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.manager());
            return null;
        });
    }

    @AfterEach
    void cleanup()
    {
        if (previous != null)
        {
            FxTestSupport.onFx(() ->
            {
                if (stage != null)
                {
                    stage.close();
                }
                if (previous.isPresent())
                {
                    MainWindow.sharedSessionState().setAuthenticatedUser(previous.get());
                }
                else
                {
                    MainWindow.sharedSessionState().clearAuthenticatedUser();
                }
                return null;
            });
        }
    }

    @Test
    void importPrefillViewsTheAlreadyActiveChartAndStartsOnlyALiteralNameDraft() throws Exception
    {
        Actions actions = new Actions();
        ChartOfAccountsPanel panel = open(actions, new AtomicBoolean(true));
        await(() -> !selector(panel).isDisabled());
        FxTestSupport.onFx(() ->
        {
            selector(panel).getSelectionModel().select(1);
            assertFalse(selector(panel).getValue().activeForCompany());
            table(panel).getSelectionModel().select(actions.newAccount);
            String oldName = actions.newAccount.getName();
            String literal = "Literal / MiXeD account name";
            panel.onImportAddSuggestion(ImportAddSuggestion.capture(literal));
            assertEquals(1L, selector(panel).getValue().id());
            assertTrue(selector(panel).getValue().activeForCompany());
            assertEquals(0, actions.activations, "Prefill browses the active chart; it never activates another chart");
            assertNull(panel.readFormStateForTests().accountId());
            assertEquals("", panel.readFormStateForTests().code());
            assertEquals(literal, panel.readFormStateForTests().name());
            assertNull(panel.readFormStateForTests().accountType());
            assertNull(panel.readFormStateForTests().normalBalance());
            assertTrue(panel.hasUnsavedChanges());
            assertEquals(oldName, actions.newAccount.getName(), "The selected saved record is unchanged");
            return null;
        });
    }

    @Test
    void browsingSameCodesFiltersByChartAndActivationRefreshesCurrentWithoutMovingRows() throws Exception
    {
        Actions actions = new Actions();
        ChartOfAccountsPanel panel = open(actions, new AtomicBoolean(true));
        await(() -> !selector(panel).isDisabled());
        FxTestSupport.onFx(() ->
        {
            assertEquals(1L, selector(panel).getValue().id());
            assertEquals(List.of(actions.oldAccount), table(panel).getItems());
            selector(panel).getSelectionModel().select(1);
            assertEquals(List.of(actions.newAccount), table(panel).getItems());
            assertEquals(0, actions.activations);
            assertTrue(panel.commandCapabilities().contains(AppCommand.SAVE_ACTIVE), "Owned non-retired charts are now editable without activation");
            assertTrue(panel.root().lookup("#chartOfAccountsSelection").getBoundsInParent().getWidth() > 0);
            activate(panel).fire();
            return null;
        });
        await(() -> !selector(panel).isDisabled() && selector(panel).getValue().activeForCompany());
        FxTestSupport.onFx(() ->
        {
            assertEquals(1, actions.activations);
            assertEquals(2L, actions.active);
            assertEquals(1L, actions.oldAccount.getChart().getId());
            assertEquals(List.of(actions.newAccount), table(panel).getItems());
            assertTrue(panel.commandCapabilities().contains(AppCommand.SAVE_ACTIVE));
            assertTrue(activate(panel).isDisabled());
            return null;
        });
    }

    @Test
    void cancelledDirtyNavigationAndActivationPreserveContextAndRestrictedRolesSeeExplanation() throws Exception
    {
        Actions actions = new Actions();
        AtomicBoolean confirm = new AtomicBoolean(false);
        ChartOfAccountsPanel panel = open(actions, confirm);
        await(() -> !selector(panel).isDisabled());
        FxTestSupport.onFx(() ->
        {
            table(panel).getSelectionModel().select(actions.oldAccount);
            var current = panel.readFormStateForTests();
            panel.setFormStateForTests(new ChartOfAccountsPanel.FormState(current.accountId(), "1000", "unsaved",
                    current.accountType(), current.accountFunction(), current.normalBalance(), current.subtype(), "", true));
            // Change an actual field and require cancellation to preserve both the draft and selected row.
            var field = (javafx.scene.control.TextField) ((Pane) panel.root()).lookupAll(".text-field").stream()
                    .filter(node -> node instanceof javafx.scene.control.TextField text && text.getText().equals("unsaved"))
                    .findFirst().orElseThrow();
            field.setText("keep this edit");
            panel.setDiscardConfirmationForTests(() -> false);
            selector(panel).getSelectionModel().select(1);
            assertEquals(1L, selector(panel).getValue().id());
            assertEquals("keep this edit", panel.readFormStateForTests().name());
            assertEquals(actions.oldAccount, table(panel).getSelectionModel().getSelectedItem());
            panel.setDiscardConfirmationForTests(() -> true);
            selector(panel).getSelectionModel().select(1);
            activate(panel).fire();
            assertEquals(0, actions.activations);
            assertEquals(1L, actions.active);
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(activate(panel).isDisabled());
            assertTrue(panel.root().lookupAll(".label").stream().filter(Label.class::isInstance)
                    .map(Label.class::cast).anyMatch(label -> label.getText().contains("requires a MANAGER or ADMIN")));
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.manager());
            assertFalse(activate(panel).isDisabled());
            selector(panel).getSelectionModel().select(2);
            assertTrue(activate(panel).isDisabled());
            return null;
        });
    }

    @Test
    void namedChartAndActivationControlFitNarrowWindow() throws Exception
    {
        ChartOfAccountsPanel panel = open(new Actions(), new AtomicBoolean(false));
        await(() -> !selector(panel).isDisabled());
        FxTestSupport.onFx(() -> { stage.setWidth(430); return null; });
        await(() -> stage.getScene().getWidth() < 450);
        FxTestSupport.onFx(() ->
        {
            panel.root().applyCss();
            ((javafx.scene.Parent) panel.root()).layout();
            var viewport = panel.root().localToScene(panel.root().getLayoutBounds());
            for (var control : List.of(selector(panel), activate(panel)))
            {
                var bounds = control.localToScene(control.getLayoutBounds());
                assertTrue(bounds.getWidth() > 50);
                assertTrue(bounds.getMinX() >= viewport.getMinX() - 1);
                assertTrue(bounds.getMaxX() <= viewport.getMaxX() + 1, "Chart control must fit the actual pane width");
                assertTrue(bounds.getMinY() >= viewport.getMinY() - 1);
                assertTrue(bounds.getMaxY() <= viewport.getMaxY() + 1);
            }
            return null;
        });
    }

    @Test
    void rejectedActivationLeavesSelectedAndActiveChartUnchanged() throws Exception
    {
        Actions actions = new Actions();
        actions.reject = true;
        ChartOfAccountsPanel panel = open(actions, new AtomicBoolean(true));
        await(() -> !selector(panel).isDisabled());
        FxTestSupport.onFx(() -> { selector(panel).getSelectionModel().select(1); activate(panel).fire(); return null; });
        await(() -> !selector(panel).isDisabled());
        FxTestSupport.onFx(() ->
        {
            assertEquals(1L, actions.active);
            assertEquals(2L, selector(panel).getValue().id());
            assertFalse(selector(panel).getValue().activeForCompany());
            assertTrue(panel.commandResultMessage(AppCommand.SAVE_ACTIVE).contains("Injected denial"));
            assertEquals(List.of(actions.newAccount), table(panel).getItems());
            return null;
        });
    }

    private ChartOfAccountsPanel open(Actions actions, AtomicBoolean confirm)
    {
        return FxTestSupport.onFx(() ->
        {
            ChartOfAccountsPanel panel = new ChartOfAccountsPanel(actions, chart -> confirm.get());
            stage = new Stage();
            stage.setScene(new Scene((javafx.scene.Parent) panel.root(), 760, 650));
            stage.show();
            panel.root().applyCss();
            panel.root().autosize();
            return panel;
        });
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<CompanyChartView> selector(ChartOfAccountsPanel panel)
    {
        return (ComboBox<CompanyChartView>) panel.root().lookup("#chartOfAccountsSelection");
    }

    private static Button activate(ChartOfAccountsPanel panel)
    {
        return (Button) panel.root().lookup("#makeActiveChart");
    }

    @SuppressWarnings("unchecked")
    private static TableView<Account> table(ChartOfAccountsPanel panel)
    {
        return (TableView<Account>) panel.root().lookup(".table-view");
    }

    private static void await(BooleanSupplier condition) throws Exception
    {
        long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!FxTestSupport.onFx(condition::getAsBoolean) && System.nanoTime() < limit)
        {
            Thread.sleep(20);
        }
        assertTrue(FxTestSupport.onFx(condition::getAsBoolean));
    }

    private static class Actions implements ChartOfAccountsPanel.ChartActions
    {
        volatile long active = 1;
        volatile int activations;
        boolean reject;
        final Account oldAccount = account(1, "Old cash");
        final Account newAccount = account(2, "New cash");

        @Override public ChartOfAccountsPanel.ChartData load()
        {
            return new ChartOfAccountsPanel.ChartData(10L, List.of(
                    new CompanyChartView(1L, "Original", "1", ChartStatus.ACTIVE, active == 1),
                    new CompanyChartView(2L, "Imported", "2", active == 2 ? ChartStatus.ACTIVE : ChartStatus.DRAFT, active == 2),
                    new CompanyChartView(3L, "Retired", "0", ChartStatus.RETIRED, false)), List.of(oldAccount, newAccount));
        }

        @Override public void activate(long companyId, long chartId)
        {
            assertEquals(10L, companyId);
            if (reject)
            {
                throw new IllegalStateException("Injected denial");
            }
            activations++;
            active = chartId;
        }

        private static Account account(long chartId, String name)
        {
            ChartOfAccounts chart = new ChartOfAccounts()
            {
                @Override public Long getId() { return chartId; }
            };
            Account account = new Account()
            {
                @Override public Long getId() { return chartId * 10; }
            };
            account.setChart(chart);
            account.setCode("1000");
            account.setName(name);
            account.setAccountType(org.nonprofitbookkeeping.model.AccountType.ASSET);
            account.setNormalBalance(org.nonprofitbookkeeping.model.NormalBalance.DEBIT);
            return account;
        }
    }
}
