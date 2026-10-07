package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;
import org.nonprofitbookkeeping.service.MerchantCommand;
import org.nonprofitbookkeeping.service.MerchantView;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class MerchantsPanelTest
{
    @TempDir Path directory;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private Optional<AuthenticatedUserSession> previousUser;
    private Stage stage;
    private MerchantsPanel panel;

    @BeforeEach
    void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        UiSessionState session = MainWindow.sharedSessionState();
        previousDatabase = session.databaseSelection();
        previousCompany = session.multiCompany();
        previousUser = session.authenticatedUser();
        Path database = directory.resolve("merchants");
        session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        UiServiceRegistry.reconnectToDatabase(database);
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
            if (stage != null) stage.close();
            return null;
        });
        Thread.sleep(500);
        FxTestSupport.onFx(() ->
        {
            UiSessionState session = MainWindow.sharedSessionState();
            session.setDatabaseSelection(previousDatabase);
            session.setMultiCompany(previousCompany);
            previousUser.ifPresentOrElse(session::setAuthenticatedUser, session::clearAuthenticatedUser);
            return null;
        });
        UiServiceRegistry.reconnectToDatabase(Path.of(previousDatabase.activeDatabasePath()));
    }

    @Test
    void returningToJournalRefreshesMixedMerchantsWithoutLosingIdsOrDraft() throws Exception
    {
        final JournalWorkspaceCompliancePanel[] journal = new JournalWorkspaceCompliancePanel[1];
        MerchantView original = UiServiceRegistry.merchantAdmin().save(new MerchantCommand(null, "Original", null, true));
        MerchantView second = UiServiceRegistry.merchantAdmin().save(new MerchantCommand(null, "Second", null, true));
        FxTestSupport.onFx(() ->
        {
            journal[0] = new JournalWorkspaceCompliancePanel();
            stage = new Stage();
            stage.setScene(new Scene((javafx.scene.Parent) journal[0].root(), 900, 650));
            stage.show();
            lines(journal[0]).getItems().get(0).setMerchant(TransactionLineEditorModel.option(original.id(), "", original.name()));
            lines(journal[0]).getItems().get(1).setMerchant(TransactionLineEditorModel.option(second.id(), "", second.name()));
            assertTrue(journal[0].hasUnsavedChanges(), "Merchant assignment must mark the draft dirty");
            lines(journal[0]).getItems().get(0).setDebit("25.00");
            lines(journal[0]).getItems().get(1).setCredit("25.00");
            lines(journal[0]).getItems().get(0).setNotes("Keep draft notes");
            assertTrue(journal[0].hasUnsavedChanges());
            assertNotNull(journal[0].root().lookup("#journalMerchants"));
            return null;
        });
        UiServiceRegistry.merchantAdmin().save(new MerchantCommand(original.id(), "Renamed inactive", null, false));
        UiServiceRegistry.merchantAdmin().save(new MerchantCommand(second.id(), "Second renamed", null, true));
        FxTestSupport.onFx(() -> { journal[0].onPanelShown(); return null; });
        await(() -> "Renamed inactive".equals(lines(journal[0]).getItems().get(0).getMerchant().name())
                && "Second renamed".equals(lines(journal[0]).getItems().get(1).getMerchant().name()));
        FxTestSupport.onFx(() ->
        {
            assertEquals("25.00", lines(journal[0]).getItems().get(0).getDebit());
            assertEquals("25.00", lines(journal[0]).getItems().get(1).getCredit());
            assertEquals("Keep draft notes", lines(journal[0]).getItems().get(0).getNotes());
            assertEquals(original.id(), lines(journal[0]).getItems().get(0).getMerchant().id());
            assertEquals(second.id(), lines(journal[0]).getItems().get(1).getMerchant().id());
            assertTrue(journal[0].hasUnsavedChanges());
            assertEquals(List.of(second.id()), UiServiceRegistry.transactionReferenceData().loadActiveReferenceData()
                    .merchants().stream().map(o -> o.id()).toList());
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private TableView<JournalWorkspacePanel.EditorLine> lines(AppPanel journal)
    {
        return (TableView<JournalWorkspacePanel.EditorLine>) journal.root().lookup("#journalWorkspaceEntryLineTable");
    }

    private void await(java.util.function.BooleanSupplier condition) throws Exception
    {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline)
        {
            if (FxTestSupport.onFx(condition::getAsBoolean)) return;
            Thread.sleep(30);
        }
        fail("Timed out waiting for UI refresh or layout");
    }

    @Test
    void stableIdEditDeactivationRefreshAndLayoutRemainUsable() throws Exception
    {
        MerchantView initial = UiServiceRegistry.merchantAdmin().save(
                new MerchantCommand(null, "Operations", "", true));
        FxTestSupport.onFx(() ->
        {
            panel = new MerchantsPanel(() -> false);
            CompanyTableStateBinder.applyProductionPanel(panel.root(), AppPanelId.MERCHANTS);
            stage = new Stage();
            stage.setScene(new Scene((VBox) panel.root(), 900, 650));
            stage.show();
            return null;
        });
        FxTestSupport.onFx(() ->
        {
            TableView<?> table = (TableView<?>) panel.root().lookup("#merchantsTable");
            assertEquals(1, table.getItems().size());
            table.getSelectionModel().select(0);
            TextField name = (TextField) panel.root().lookup("#merchantsName");
            name.setText("Retired Operations");
            CheckBox active = (CheckBox) panel.root().lookup("#merchantsActive");
            active.setSelected(false);
            assertTrue(panel.hasUnsavedChanges());
            ((Button) panel.root().lookup("#merchantsSave")).fire();
            return null;
        });
        FxTestSupport.onFx(() ->
        {
            MerchantView saved = UiServiceRegistry.merchantAdmin().listForMaintenance().stream()
                    .filter(row -> row.id().equals(initial.id())).findFirst().orElseThrow();
            assertEquals("Retired Operations", saved.name());
            assertFalse(saved.active());
            ((Button) panel.root().lookup("#merchantsNew")).fire();
            ((TextField) panel.root().lookup("#merchantsName")).setText("New Merchant");
            assertTrue(panel.hasUnsavedChanges());
            ((TextField) panel.root().lookup("#merchantsSearch")).setText("new");
            assertTrue(panel.hasUnsavedChanges(), "Search must not discard a draft");
            ((TextField) panel.root().lookup("#merchantsSearch")).clear();
            ((TableView<?>) panel.root().lookup("#merchantsTable")).getSelectionModel().select(0);
            assertEquals("New Merchant", ((TextField) panel.root().lookup("#merchantsName")).getText(), "Rejected discard retains new draft");
            ((Button) panel.root().lookup("#merchantsRefresh")).fire();
            assertTrue(panel.hasUnsavedChanges());
            ((Button) panel.root().lookup("#merchantsNew")).fire();
            assertEquals("New Merchant", ((TextField) panel.root().lookup("#merchantsName")).getText());
            SplitPane split = (SplitPane) panel.root().lookup("#merchantsWorkspaceSplit");
            ScrollPane editor = (ScrollPane) panel.root().lookup("#merchantsEditorScroll");
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, editor.getVbarPolicy());
            split.setDividerPositions(0.25);
            panel.root().applyCss();
            ((javafx.scene.Parent) panel.root()).layout();
            assertTrue(editor.getViewportBounds().getHeight() > 0);
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, editor.getHbarPolicy());
            stage.setWidth(600);
            panel.root().setStyle("-fx-font-size: 17px;");
            panel.root().applyCss();
            ((javafx.scene.Parent) panel.root()).layout();
            assertTrue(editor.getViewportBounds().getWidth() > 0);
            split.setDividerPositions(0.90);
            ((javafx.scene.layout.Region) editor.getContent()).setMinWidth(editor.getViewportBounds().getWidth() + 300);
            panel.root().applyCss();
            ((javafx.scene.Parent) panel.root()).layout();
            return null;
        });
        await(() ->
        {
            ScrollPane editor = (ScrollPane) panel.root().lookup("#merchantsEditorScroll");
            return editor.getContent().getLayoutBounds().getHeight() > editor.getViewportBounds().getHeight()
                    && editor.getContent().getLayoutBounds().getWidth() > editor.getViewportBounds().getWidth()
                    && editor.lookupAll(".scroll-bar").stream().filter(node ->
                            node instanceof javafx.scene.control.ScrollBar bar && bar.isVisible()).count() == 2;
        });
        FxTestSupport.onFx(() ->
        {
            ScrollPane editor = (ScrollPane) panel.root().lookup("#merchantsEditorScroll");
            assertTrue(editor.getContent().getLayoutBounds().getHeight() > editor.getViewportBounds().getHeight(),
                    "Shrinking the editor must expose vertical overflow");
            assertTrue(editor.getContent().getLayoutBounds().getWidth() > editor.getViewportBounds().getWidth(),
                    "Content minimum sizes must overflow horizontally instead of clipping controls");
            assertTrue(editor.lookupAll(".scroll-bar").stream().anyMatch(node ->
                    node instanceof javafx.scene.control.ScrollBar bar && bar.isVisible()
                            && bar.getOrientation() == javafx.geometry.Orientation.VERTICAL));
            assertTrue(editor.lookupAll(".scroll-bar").stream().anyMatch(node ->
                    node instanceof javafx.scene.control.ScrollBar bar && bar.isVisible()
                            && bar.getOrientation() == javafx.geometry.Orientation.HORIZONTAL));
            TableView<?> maintainedTable = (TableView<?>) panel.root().lookup("#merchantsTable");
            maintainedTable.getColumns().get(0).setPrefWidth(1100);
            panel.root().applyCss();
            ((javafx.scene.Parent) panel.root()).layout();
            assertTrue(CompanyTableStateBinder.isCompanyStateOwned(maintainedTable));
            assertTrue(maintainedTable.lookupAll(".scroll-bar").stream().anyMatch(node ->
                    node instanceof javafx.scene.control.ScrollBar bar && bar.isVisible()
                            && bar.getOrientation() == javafx.geometry.Orientation.HORIZONTAL));
            CompanyTableStateBinder.saveNow(maintainedTable);
            assertFalse(UiServiceRegistry.companyUiPreferences().loadState("DEFAULT", "ui.table.merchants.").isEmpty());
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(((Button) panel.root().lookup("#merchantsSave")).isDisabled());
            assertTrue(((TextField) panel.root().lookup("#merchantsName")).isDisabled());
            return null;
        });
    }
}
