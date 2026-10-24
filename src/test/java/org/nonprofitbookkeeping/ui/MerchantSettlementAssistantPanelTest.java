package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.ExpenseFunction;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.AccountLookupService;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;
import org.nonprofitbookkeeping.service.MerchantSettlementAssistantService;
import org.nonprofitbookkeeping.service.MerchantSettlementTestFixture;
import org.nonprofitbookkeeping.service.TransactionEntryService;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nonprofitbookkeeping.service.NmrItemTestFixture.COMPANY;
import static org.nonprofitbookkeeping.service.NmrItemTestFixture.DATE;

@ResourceLock("ui-service-registry")
class MerchantSettlementAssistantPanelTest
{
    @TempDir Path directory;
    private Jpa jpa;
    private Stage stage;
    private MerchantSettlementAssistantPanel panel;
    private Optional<AuthenticatedUserSession> previousUser;
    private LocalDate previousPeriod;

    @BeforeEach
    void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        previousUser = MainWindow.sharedSessionState().authenticatedUser();
        previousPeriod = ActivePeriodContext.get();
        jpa = new Jpa(directory.resolve("merchant-settlement-ui"));
        MerchantSettlementTestFixture fixture = MerchantSettlementTestFixture.seed(jpa);
        fixture.nmr().base().entry(jpa).enter(fixture.receipt());
        TransactionEntryService entry = new TransactionEntryService(jpa, () -> COMPANY);
        MerchantSettlementAssistantService service = new MerchantSettlementAssistantService(jpa, () -> COMPANY, entry);
        AccountLookupService lookup = new AccountLookupService(jpa, () -> COMPANY);
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            DrillThroughCoordinator.configureOpener(null);
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.manager());
            ActivePeriodContext.set(DATE.plusDays(1));
            panel = new MerchantSettlementAssistantPanel(() -> service, lookup::listActivePostingAccounts);
            stage = new Stage();
            stage.setScene(new Scene((VBox) panel.root(), 980, 720));
            stage.show();
            return null;
        });
    }

    @AfterEach
    void cleanup()
    {
        FxTestSupport.onFx(() ->
        {
            if (stage != null) stage.close();
            DrillThroughCoordinator.configureOpener(null);
            previousUser.ifPresentOrElse(
                    MainWindow.sharedSessionState()::setAuthenticatedUser,
                    MainWindow.sharedSessionState()::clearAuthenticatedUser);
            ActivePeriodContext.set(previousPeriod);
            return null;
        });
        if (jpa != null) jpa.close();
    }

    @Test
    void actualPreviewSharedSaveLivePermissionAndCorrectionRouteUseCanonicalTransaction()
    {
        FxTestSupport.onFx(() ->
        {
            ((DatePicker) panel.root().lookup("#merchantSettlementDate")).setValue(DATE.plusDays(1));
            receivables().getSelectionModel().selectFirst();
            selectAccount("merchantSettlementBank", "1000");
            selectAccount("merchantSettlementFeeAccount", "6000");
            text("merchantSettlementPayout").setText("950");
            text("merchantSettlementFee").setText("20");
            function().setValue(ExpenseFunction.AR);
            text("merchantSettlementWithheld").setText("30");
            ((TextArea) panel.root().lookup("#merchantSettlementEvidence")).setText("processor/ui/001");
            button("merchantSettlementPreview").fire();

            assertTrue(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            assertTrue(panel.hasUnsavedChanges());
            assertEquals("Save Settlement", panel.commandLabel(AppCommand.SAVE_ACTIVE));
            assertTrue(((javafx.scene.control.Label) panel.root().lookup("#merchantSettlementPreviewSummary"))
                    .getText().contains("remaining"));

            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertFalse(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            assertTrue(button("merchantSettlementSave").isDisabled());
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.manager());
            assertTrue(panel.supportsCommand(AppCommand.SAVE_ACTIVE));
            assertTrue(panel.executeCommand(AppCommand.SAVE_ACTIVE).handled());
            assertNotNull(panel.savedTransactionId());
            assertFalse(panel.hasUnsavedChanges());
            assertTrue(panel.commandResultMessage(AppCommand.SAVE_ACTIVE).contains("reconciliation"));

            button("merchantSettlementCorrectionRoute").fire();
            assertTrue(DrillThroughCoordinator.consumeContext(AppPanelId.JOURNAL_PANE).contains("original merchant"));
            return null;
        });
        assertEquals(2, new TransactionEntryService(jpa, () -> COMPANY).search(null, null, null, 20).size());
    }

    @Test
    void layoutKeepsBothTablesResizableAndFormScrollable()
    {
        FxTestSupport.onFx(() ->
        {
            SplitPane workspace = (SplitPane) panel.root().lookup("#merchantSettlementWorkspaceSplit");
            SplitPane detail = (SplitPane) panel.root().lookup("#merchantSettlementDetailSplit");
            assertEquals(Orientation.VERTICAL, workspace.getOrientation());
            assertEquals(2, workspace.getItems().size());
            assertEquals(2, detail.getItems().size());
            workspace.setDividerPositions(.28);
            detail.setDividerPositions(.35);
            stage.setWidth(540);
            panel.root().setStyle("-fx-font-size: 17px;");
            panel.root().applyCss();
            ((VBox) panel.root()).layout();
            assertNotNull(panel.root().lookup("#merchantSettlementFormScroll"));
            assertNotNull(panel.root().lookup("#merchantSettlementCorrectionRoute"));
            assertEquals(TableView.UNCONSTRAINED_RESIZE_POLICY, receivables().getColumnResizePolicy());
            assertEquals(TableView.UNCONSTRAINED_RESIZE_POLICY, nmrItems().getColumnResizePolicy());
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private TableView<?> receivables()
    {
        return (TableView<?>) panel.root().lookup("#merchantSettlementReceivables");
    }

    @SuppressWarnings("unchecked")
    private TableView<?> nmrItems()
    {
        return (TableView<?>) panel.root().lookup("#merchantSettlementNmrItems");
    }

    @SuppressWarnings("unchecked")
    private void selectAccount(String id, String code)
    {
        ComboBox<Account> box = (ComboBox<Account>) panel.root().lookup("#" + id);
        box.getSelectionModel().select(box.getItems().stream()
                .filter(value -> code.equals(value.getCode())).findFirst().orElseThrow());
    }

    @SuppressWarnings("unchecked")
    private ComboBox<ExpenseFunction> function()
    {
        return (ComboBox<ExpenseFunction>) panel.root().lookup("#merchantSettlementFeeFunction");
    }

    private TextField text(String id) { return (TextField) panel.root().lookup("#" + id); }
    private Button button(String id) { return (Button) panel.root().lookup("#" + id); }
}
