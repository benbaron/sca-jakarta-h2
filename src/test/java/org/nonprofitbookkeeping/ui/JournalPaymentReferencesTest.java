package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.service.ActivityCommand;
import org.nonprofitbookkeeping.service.ActivityView;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class JournalPaymentReferencesTest
{
    @TempDir Path directory;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private Optional<AuthenticatedUserSession> previousUser;
    private Stage stage;
    private JournalWorkspaceCompliancePanel panel;
    private org.nonprofitbookkeeping.service.FundTransferTestFixture fixture;

    @BeforeEach
    void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        UiSessionState session = MainWindow.sharedSessionState();
        previousDatabase = session.databaseSelection();
        previousCompany = session.multiCompany();
        previousUser = session.authenticatedUser();
        Path database = directory.resolve("journal-tags");
        session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        UiServiceRegistry.reconnectToDatabase(database);
        try (var jpa = new org.nonprofitbookkeeping.persistence.Jpa(database))
        {
            fixture = org.nonprofitbookkeeping.service.FundTransferTestFixture.seed(jpa);
        }
        FxTestSupport.onFx(() ->
        {
            Platform.setImplicitExit(false);
            session.setAuthenticatedUser(UiPermissionTestSessions.manager());
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
            if (stage != null)
            {
                stage.close();
            }
            return null;
        });
        // Let the existing debounced layout saves finish before restoring authentication.
        Thread.sleep(600);
        FxTestSupport.onFx(() ->
        {
            DrillThroughCoordinator.configureOpener(null);
            UiSessionState session = MainWindow.sharedSessionState();
            session.setDatabaseSelection(previousDatabase);
            session.setMultiCompany(previousCompany);
            if (previousUser.isPresent())
            {
                session.setAuthenticatedUser(previousUser.get());
            }
            else
            {
                session.clearAuthenticatedUser();
            }
            return null;
        });
        UiServiceRegistry.reconnectToDatabase(Path.of(previousDatabase.activeDatabasePath()));
    }

    @Test
    void bankLineFactsSaveReloadAndProtectAnUnsavedPaymentOnlyChange() throws Exception
    {
        FxTestSupport.onFx(() ->
        {
            panel = new JournalWorkspaceCompliancePanel();
            stage = new Stage(); stage.setScene(new Scene(new VBox(panel.root()), 950, 700)); stage.show();
            panel.onPanelShown(); return null;
        });
        await(() -> choices("journalSelectedFund").getItems().size() >= 2);
        FxTestSupport.onFx(() ->
        {
            var expense = table().getItems().get(0); var bank = table().getItems().get(1);
            expense.setAccount(new TransactionLineEditorModel.Option(fixture.expense(), "5000", "Expense"));
            expense.setFund(new TransactionLineEditorModel.Option(fixture.source(), "GENERAL", "General")); expense.setDebit("10");
            bank.setAccount(new TransactionLineEditorModel.Option(fixture.bank(), "1000", "Bank"));
            bank.setFund(expense.getFund()); bank.setCredit("10");
            bank.paymentMethod.set("CHECK"); bank.paymentReference.set("000123");
            bank.paymentIssued.set("2026-02-01"); bank.paymentDelivered.set("2026-02-02");
            button("journalWorkspaceSaveButton").fire(); return null;
        });
        await(() -> !panel.hasUnsavedChanges());
        var saved = UiServiceRegistry.transactionEntry().search(null, null, "000123", 20).get(0);
        assertEquals("000123", saved.lines().get(1).payment().reference());
        FxTestSupport.onFx(() ->
        {
            var method = (TableColumn<JournalWorkspacePanel.EditorLine, String>) table().getColumns().stream()
                    .filter(c -> "Payment method".equals(c.getText())).findFirst().orElseThrow();
            table().scrollToColumn(method); table().applyCss(); table().layout(); table().edit(1, method);
            var cell = table().lookupAll(".table-cell").stream().map(TableCell.class::cast)
                    .filter(c -> c.getIndex() == 1 && c.getTableColumn() == method).findFirst().orElseThrow();
            assertTrue(cell.isEditing(), "Initialization must not commit the method cell");
            ((ComboBox<String>) cell.getGraphic()).getSelectionModel().select("EFT");
            assertEquals("EFT", table().getItems().get(1).paymentMethod.get());
            assertTrue(panel.hasUnsavedChanges());
            table().getItems().get(1).paymentReference.set("000124");
            assertTrue(panel.hasUnsavedChanges(), "Reference-only changes must protect the draft");
            panel.onPanelShown(); return null;
        });
        await(() -> choices("journalSelectedFund").getItems().size() >= 2);
        FxTestSupport.onFx(() ->
        {
            assertEquals("000124", table().getItems().get(1).paymentReference.get());
            assertTrue(panel.hasUnsavedChanges());
            assertNotNull(field("journalPaymentBankFilter"));
            assertTrue(table().getColumns().stream().anyMatch(c -> "Check / Reference".equals(c.getText())));
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(button("journalWorkspaceSaveButton").isDisabled());
            return null;
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void explicitNmrRecipientUsesRealJournalSaveReloadAndProtectsRecipientOnlyDraft() throws Exception
    {
        var session = MainWindow.sharedSessionState();
        org.nonprofitbookkeeping.service.NmrItemTestFixture nmr;
        try (var jpa = new org.nonprofitbookkeeping.persistence.Jpa(Path.of(session.databaseSelection().activeDatabasePath())))
        {
            nmr = org.nonprofitbookkeeping.service.NmrItemTestFixture.seed(jpa);
        }
        FxTestSupport.onFx(() ->
        {
            session.setMultiCompany(new MultiCompanyState("NMR_DETAIL", List.of("DEFAULT", "NMR_DETAIL")));
            var now = java.time.Instant.now();
            session.setAuthenticatedUser(new AuthenticatedUserSession(9001L, "operator", "Operator", "NMR_DETAIL",
                    java.util.Set.of(org.nonprofitbookkeeping.service.ReservedSecurityRole.ACCOUNTANT), now, now));
            panel = new JournalWorkspaceCompliancePanel();
            stage = new Stage(); stage.setScene(new Scene(new VBox(panel.root()), 950, 700)); stage.show();
            panel.onPanelShown(); return null;
        });
        await(() -> choices("journalSelectedFund").getItems().stream().anyMatch(o -> o.id().equals(nmr.fund())));
        FxTestSupport.onFx(() ->
        {
            ((DatePicker)panel.root().lookup("#journalEntryDate")).setValue(org.nonprofitbookkeeping.service.NmrItemTestFixture.DATE);
            var cash = table().getItems().get(0); var liability = table().getItems().get(1);
            cash.setAccount(new TransactionLineEditorModel.Option(nmr.cash(), "1000", "Cash"));
            cash.setFund(new TransactionLineEditorModel.Option(nmr.fund(), "GENERAL", "General")); cash.setDebit("20");
            liability.setAccount(new TransactionLineEditorModel.Option(nmr.liability(), "2300", "Liability"));
            liability.setFund(cash.getFund()); liability.setCredit("20");
            liability.setActivity(new TransactionLineEditorModel.Option(nmr.event(), "EVENT", "Synthetic event"));
            ((CheckBox)panel.root().lookup("#journalSupplementalOTHER_LIABILITY")).fire();
            TabPane tabs = (TabPane)panel.root().lookup("#journalSupplementalTabs");
            tabs.getSelectionModel().select(tabs.getTabs().stream().filter(t -> t.getText().equals("Other Liabilities")).findFirst().orElseThrow());
            stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
            var details = (TableView<JournalWorkspacePanel.SupplementalRow>)panel.root().lookup("#journalWorkspaceSupplementalOTHER_LIABILITYTable");
            var row = new JournalWorkspacePanel.SupplementalRow();
            row.setItemId(java.util.UUID.randomUUID().toString()); row.setItemEffect("INCREASE"); row.setTransactionLine("2");
            row.setDescription("Explicit NMR item"); row.setAmount("20"); details.getItems().add(row);
            var column = (TableColumn<JournalWorkspacePanel.SupplementalRow, TransactionLineEditorModel.Option>)details.getColumns().stream()
                    .filter(c -> "NMR recipient".equals(c.getText())).findFirst().orElseThrow();
            details.scrollToColumn(column); details.applyCss(); details.layout(); details.edit(0, column);
            var cell = details.lookupAll(".table-cell").stream().map(TableCell.class::cast)
                    .filter(c -> c.getIndex() == 0 && c.getTableColumn() == column).findFirst().orElseThrow();
            assertTrue(cell.isEditing());
            var combo = (ComboBox<TransactionLineEditorModel.Option>)cell.getGraphic();
            combo.getSelectionModel().select(combo.getItems().stream().filter(o -> o != null && o.id().equals(nmr.recipient())).findFirst().orElseThrow());
            assertEquals(nmr.recipient(), row.nmrRecipient.get().id());
            var movementColumn = (TableColumn<JournalWorkspacePanel.SupplementalRow, String>)details.getColumns().stream()
                    .filter(c -> "NMR movement".equals(c.getText())).findFirst().orElseThrow();
            details.scrollToColumn(movementColumn); details.applyCss(); details.layout(); details.edit(0, movementColumn);
            var movementCell = details.lookupAll(".table-cell").stream().map(TableCell.class::cast)
                    .filter(c -> c.getIndex() == 0 && c.getTableColumn() == movementColumn).findFirst().orElseThrow();
            assertTrue(movementCell.isEditing());
            ((ComboBox<String>)movementCell.getGraphic()).getSelectionModel().select("COLLECTION");
            assertEquals("COLLECTION", row.nmrMovement.get());
            editNmrEvidence(details, "external/collection/001");
            button("journalWorkspaceSaveButton").fire(); return null;
        });
        await(() -> !panel.hasUnsavedChanges());
        var saved = UiServiceRegistry.transactionEntry().search(null, null, "", 20).get(0);
        assertEquals(nmr.recipient(), saved.supplementalLines().get(0).nmrRecipientId());
        assertEquals("COLLECTION", saved.supplementalLines().get(0).nmrMovement());
        assertEquals("external/collection/001", saved.supplementalLines().get(0).nmrEvidenceReference());
        FxTestSupport.onFx(() ->
        {
            var details = (TableView<JournalWorkspacePanel.SupplementalRow>)panel.root().lookup("#journalWorkspaceSupplementalOTHER_LIABILITYTable");
            editNmrEvidence(details, "external/collection/002");
            assertTrue(panel.hasUnsavedChanges(), "Evidence-only cell commits protect the shared Journal draft");
            button("journalWorkspaceSaveButton").fire(); return null;
        });
        await(() -> !panel.hasUnsavedChanges());
        assertEquals("external/collection/002", UiServiceRegistry.transactionEntry().load(saved.id()).supplementalLines().get(0).nmrEvidenceReference());
        assertEquals("20.0000", UiServiceRegistry.supplementalOpenItems().queryNmr(org.nonprofitbookkeeping.service.NmrItemTestFixture.DATE)
                .items().get(0).openBalance().toPlainString());
        FxTestSupport.onFx(() ->
        {
            var details = (TableView<JournalWorkspacePanel.SupplementalRow>)panel.root().lookup("#journalWorkspaceSupplementalOTHER_LIABILITYTable");
            assertEquals(nmr.recipient(), details.getItems().get(0).nmrRecipient.get().id(), "Save reload retains stable recipient");
            var column = (TableColumn<JournalWorkspacePanel.SupplementalRow, TransactionLineEditorModel.Option>)details.getColumns().stream()
                    .filter(c -> "NMR recipient".equals(c.getText())).findFirst().orElseThrow();
            details.scrollToColumn(column); details.applyCss(); details.layout(); details.edit(0, column);
            var cell = details.lookupAll(".table-cell").stream().map(TableCell.class::cast)
                    .filter(c -> c.getIndex() == 0 && c.getTableColumn() == column).findFirst().orElseThrow();
            ((ComboBox<?>)cell.getGraphic()).getSelectionModel().select(0);
            assertTrue(panel.hasUnsavedChanges(), "Recipient-only edits protect the transaction draft");
            for (int index = 0; index < 60; index++) details.getItems().add(new JournalWorkspacePanel.SupplementalRow());
            panel.root().setStyle("-fx-font-size: 20px;");
            ((ScrollPane)panel.root().lookup("#journalWorkspaceEditorScroll")).setVvalue(1);
            ((SplitPane)panel.root().lookup("#journalWorkspaceEditorSplit")).setDividerPositions(0.12, 0.30);
            ((SplitPane)panel.root().lookup("#journalWorkspaceDetailSplit")).setDividerPositions(0.65);
            var split = (SplitPane)panel.root().lookup("#journalNmrDetailSplit");
            split.setDividerPositions(0.3);
            stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
            assertEquals(1, split.getDividers().size(), "Controls/table expose a real draggable divider");
            double before = details.getHeight();
            split.setDividerPositions(0.55); stage.getScene().getRoot().layout();
            assertTrue(details.getHeight() < before, "Divider movement resizes the actual table viewport");
            assertEquals(0, details.getMinWidth()); assertEquals(0, details.getMinHeight());
            return null;
        });
        // Let JavaFX pulse the nested VirtualFlow after font and divider changes.
        await(() ->
        {
            var details = (TableView<?>)panel.root().lookup("#journalWorkspaceSupplementalOTHER_LIABILITYTable");
            details.applyCss(); details.layout();
            return details.lookupAll(".scroll-bar").stream().map(ScrollBar.class::cast).filter(ScrollBar::isVisible).count() >= 2;
        });
        FxTestSupport.onFx(() ->
        {
            var details = (TableView<?>)panel.root().lookup("#journalWorkspaceSupplementalOTHER_LIABILITYTable");
            for (var orientation : javafx.geometry.Orientation.values())
            {
                assertTrue(details.lookupAll(".scroll-bar").stream().map(ScrollBar.class::cast)
                        .anyMatch(bar -> bar.getOrientation() == orientation && bar.isVisible()), "Supplemental table overflows in " + orientation);
            }
            var controls = (ScrollPane)panel.root().lookup("#journalNmrDetailControlsScroll");
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, controls.getHbarPolicy());
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, controls.getVbarPolicy());
            assertTrue(controls.getContent().getBoundsInLocal().getWidth() > controls.getViewportBounds().getWidth(), "Controls overflow horizontally without a forced panel minimum");
            assertTrue(controls.getContent().getBoundsInLocal().getHeight() > controls.getViewportBounds().getHeight(), "Scaled help scrolls vertically within its region");
            var now = java.time.Instant.now();
            session.setAuthenticatedUser(new AuthenticatedUserSession(9001L, "operator", "Operator", "NMR_DETAIL",
                    java.util.Set.of(org.nonprofitbookkeeping.service.ReservedSecurityRole.VIEWER), now, now));
            assertTrue(button("journalWorkspaceSaveButton").isDisabled());
            return null;
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void merchantFactsUseSharedJournalSavePreservePrecisionAndExposeRealScrolling() throws Exception
    {
        var session = MainWindow.sharedSessionState();
        org.nonprofitbookkeeping.service.MerchantSettlementTestFixture merchant;
        long transaction;
        try (var jpa = new org.nonprofitbookkeeping.persistence.Jpa(Path.of(session.databaseSelection().activeDatabasePath())))
        {
            merchant = org.nonprofitbookkeeping.service.MerchantSettlementTestFixture.seed(jpa);
            var entry = merchant.nmr().base().entry(jpa);
            entry.enter(merchant.receipt());
            transaction = entry.enter(merchant.payout(null)).id();
        }
        FxTestSupport.onFx(() ->
        {
            session.setMultiCompany(new MultiCompanyState("NMR_DETAIL", List.of("DEFAULT", "NMR_DETAIL")));
            var now = java.time.Instant.now();
            session.setAuthenticatedUser(new AuthenticatedUserSession(9001L, "operator", "Operator", "NMR_DETAIL",
                    java.util.Set.of(org.nonprofitbookkeeping.service.ReservedSecurityRole.ACCOUNTANT), now, now));
            panel = new JournalWorkspaceCompliancePanel();
            stage = new Stage(); stage.setScene(new Scene(new VBox(panel.root()), 950, 700)); stage.show();
            DrillThroughCoordinator.openTransactionEditorWithContext("Edit transaction Txn #" + transaction);
            panel.onPanelShown(); return null;
        });
        await(() ->
        {
            var details = (TableView<?>)panel.root().lookup("#journalWorkspaceSupplementalRECEIVABLETable");
            return details != null && details.getItems().size() == 1;
        });
        FxTestSupport.onFx(() ->
        {
            var tabs = (TabPane)panel.root().lookup("#journalSupplementalTabs");
            tabs.getSelectionModel().select(tabs.getTabs().stream().filter(t -> t.getText().equals("Receivables")).findFirst().orElseThrow());
            stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
            var details = (TableView<JournalWorkspacePanel.SupplementalRow>)panel.root().lookup("#journalWorkspaceSupplementalRECEIVABLETable");
            var column = (TableColumn<JournalWorkspacePanel.SupplementalRow, String>)details.getColumns().stream().filter(c -> "Merchant role".equals(c.getText())).findFirst().orElseThrow();
            details.scrollToColumn(column); details.applyCss(); details.layout(); details.edit(0, column);
            var cell = details.lookupAll(".table-cell").stream().map(TableCell.class::cast)
                    .filter(c -> c.getIndex() == 0 && c.getTableColumn() == column).findFirst().orElseThrow();
            assertTrue(cell.isEditing()); ((ComboBox<String>)cell.getGraphic()).getSelectionModel().select("SETTLEMENT");
            editMerchantText(details, "Observed withholding", "30.1234");
            editMerchantText(details, "Observation / settlement evidence", "processor/observed/001");
            editMerchantText(details, "Payout lines", "1"); editMerchantText(details, "Fee lines", "2");
            assertTrue(panel.hasUnsavedChanges()); button("journalWorkspaceSaveButton").fire(); return null;
        });
        await(() -> !panel.hasUnsavedChanges());
        var facts = UiServiceRegistry.transactionEntry().load(transaction).supplementalLines().get(0).merchantSettlement();
        assertNotNull(facts); assertEquals("SETTLEMENT", facts.role()); assertEquals("30.1234", facts.withheldUnconfirmed().toPlainString()); assertEquals(2, facts.components().size());
        FxTestSupport.onFx(() ->
        {
            var details = (TableView<JournalWorkspacePanel.SupplementalRow>)panel.root().lookup("#journalWorkspaceSupplementalRECEIVABLETable");
            editMerchantText(details, "Observation / settlement evidence", "processor/observed/002");
            assertTrue(panel.hasUnsavedChanges(), "Evidence-only commits protect the shared draft");
            panel.onSave(); return null;
        });
        await(() -> !panel.hasUnsavedChanges());
        facts = UiServiceRegistry.transactionEntry().load(transaction).supplementalLines().get(0).merchantSettlement();
        assertEquals("30.1234", facts.withheldUnconfirmed().toPlainString(), "Display formatting must not round unchanged stored precision");
        assertEquals("processor/observed/002", facts.evidenceReference());
        FxTestSupport.onFx(() ->
        {
            var details = (TableView<JournalWorkspacePanel.SupplementalRow>)panel.root().lookup("#journalWorkspaceSupplementalRECEIVABLETable");
            editMerchantText(details, "Observed withholding", ""); panel.onSave(); return null;
        });
        await(() -> !panel.hasUnsavedChanges());
        assertNull(UiServiceRegistry.transactionEntry().load(transaction).supplementalLines().get(0).merchantSettlement().withheldUnconfirmed(), "Blank remains unknown rather than an invented zero");
        FxTestSupport.onFx(() ->
        {
            var details = (TableView<JournalWorkspacePanel.SupplementalRow>)panel.root().lookup("#journalWorkspaceSupplementalRECEIVABLETable");
            for (int index = 0; index < 60; index++) details.getItems().add(new JournalWorkspacePanel.SupplementalRow());
            panel.root().setStyle("-fx-font-size: 20px;");
            ((ScrollPane)panel.root().lookup("#journalWorkspaceEditorScroll")).setVvalue(1);
            ((SplitPane)panel.root().lookup("#journalWorkspaceEditorSplit")).setDividerPositions(0.12, 0.30);
            ((SplitPane)panel.root().lookup("#journalWorkspaceDetailSplit")).setDividerPositions(0.65);
            var split = (SplitPane)panel.root().lookup("#journalMerchantDetailSplit"); split.setDividerPositions(0.3);
            stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
            assertEquals(1, split.getDividers().size()); double before = details.getHeight();
            split.setDividerPositions(0.55); stage.getScene().getRoot().layout(); assertTrue(details.getHeight() < before);
            assertEquals(0, details.getMinWidth()); assertEquals(0, details.getMinHeight()); return null;
        });
        await(() ->
        {
            var details = (TableView<?>)panel.root().lookup("#journalWorkspaceSupplementalRECEIVABLETable"); details.applyCss(); details.layout();
            return details.lookupAll(".scroll-bar").stream().map(ScrollBar.class::cast).filter(ScrollBar::isVisible).count() >= 2;
        });
        FxTestSupport.onFx(() ->
        {
            var details = (TableView<?>)panel.root().lookup("#journalWorkspaceSupplementalRECEIVABLETable");
            for (var orientation : javafx.geometry.Orientation.values())
            {
                assertTrue(details.lookupAll(".scroll-bar").stream().map(ScrollBar.class::cast).anyMatch(bar -> bar.getOrientation() == orientation && bar.isVisible()));
            }
            var controls = (ScrollPane)panel.root().lookup("#journalMerchantDetailControlsScroll");
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, controls.getHbarPolicy()); assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, controls.getVbarPolicy());
            assertTrue(controls.getContent().getBoundsInLocal().getWidth() > controls.getViewportBounds().getWidth());
            assertTrue(controls.getContent().getBoundsInLocal().getHeight() > controls.getViewportBounds().getHeight());
            var now = java.time.Instant.now();
            session.setAuthenticatedUser(new AuthenticatedUserSession(9001L, "operator", "Operator", "NMR_DETAIL",
                    java.util.Set.of(org.nonprofitbookkeeping.service.ReservedSecurityRole.VIEWER), now, now));
            assertTrue(button("journalWorkspaceSaveButton").isDisabled()); return null;
        });
    }

    @SuppressWarnings("unchecked")
    private static void editMerchantText(TableView<JournalWorkspacePanel.SupplementalRow> table, String title, String value)
    {
        var column = (TableColumn<JournalWorkspacePanel.SupplementalRow, String>)table.getColumns().stream().filter(c -> title.equals(c.getText())).findFirst().orElseThrow();
        table.scrollToColumn(column); table.applyCss(); table.layout(); table.edit(0, column);
        var cell = table.lookupAll(".table-cell").stream().map(TableCell.class::cast).filter(c -> c.getIndex() == 0 && c.getTableColumn() == column).findFirst().orElseThrow();
        assertTrue(cell.isEditing()); var editor = (TextField)cell.getGraphic(); editor.setText(value); editor.fireEvent(new javafx.event.ActionEvent());
    }

    @SuppressWarnings("unchecked")
    private static void editNmrEvidence(TableView<JournalWorkspacePanel.SupplementalRow> table, String value)
    {
        var column = (TableColumn<JournalWorkspacePanel.SupplementalRow, String>)table.getColumns().stream()
                .filter(c -> "NMR evidence".equals(c.getText())).findFirst().orElseThrow();
        table.scrollToColumn(column); table.applyCss(); table.layout(); table.edit(0, column);
        var cell = table.lookupAll(".table-cell").stream().map(TableCell.class::cast)
                .filter(c -> c.getIndex() == 0 && c.getTableColumn() == column).findFirst().orElseThrow();
        assertTrue(cell.isEditing());
        var editor = (TextField)cell.getGraphic(); editor.setText(value); editor.fireEvent(new javafx.event.ActionEvent());
    }

    private static void await(BooleanSupplier condition) throws Exception
    {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (!FxTestSupport.onFx(condition::getAsBoolean))
        {
            assertTrue(System.nanoTime() < deadline, "Journal choices did not finish loading");
            Thread.sleep(25);
        }
    }

    @SuppressWarnings("unchecked")
    private TableView<JournalWorkspacePanel.EditorLine> table()
    {
        return (TableView<JournalWorkspacePanel.EditorLine>) panel.root().lookup("#journalWorkspaceEntryLineTable");
    }

    @SuppressWarnings("unchecked")
    private ComboBox<TransactionLineEditorModel.Option> choices(String id)
    {
        return (ComboBox<TransactionLineEditorModel.Option>) panel.root().lookup("#" + id);
    }

    private TextField field(String id)
    {
        return (TextField) panel.root().lookup("#" + id);
    }

    private Button button(String id)
    {
        return (Button) panel.root().lookup("#" + id);
    }
}
