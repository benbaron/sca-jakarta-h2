package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import org.nonprofitbookkeeping.model.CounterpartyKind;
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
import org.nonprofitbookkeeping.service.CounterpartyCommand;
import org.nonprofitbookkeeping.service.CounterpartyView;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class CounterpartiesPanelTest
{
    @TempDir Path directory;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private Optional<AuthenticatedUserSession> previousUser;
    private Stage stage;
    private CounterpartiesPanel panel;

    @BeforeEach
    void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        UiSessionState session = MainWindow.sharedSessionState();
        previousDatabase = session.databaseSelection();
        previousCompany = session.multiCompany();
        previousUser = session.authenticatedUser();
        Path database = directory.resolve("counterparties");
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
    void returningToJournalRefreshesPayeesWithoutLosingSelectedIdOrDraft() throws Exception
    {
        final JournalWorkspaceCompliancePanel[] journal = new JournalWorkspaceCompliancePanel[1];
        CounterpartyView original = UiServiceRegistry.counterpartyAdmin().save(new CounterpartyCommand(
                null, "Original Payee", CounterpartyKind.PERSON, null, null, null, true));
        FxTestSupport.onFx(() ->
        {
            journal[0] = new JournalWorkspaceCompliancePanel();
            stage = new Stage();
            stage.setScene(new Scene((javafx.scene.Parent) journal[0].root(), 900, 650));
            stage.show();
            return null;
        });
        await(() -> payees(journal[0]).getItems().size() == 1);
        FxTestSupport.onFx(() ->
        {
            payees(journal[0]).getSelectionModel().select(0);
            assertTrue(journal[0].hasUnsavedChanges());
            assertNotNull(journal[0].root().lookup("#journalCounterparties"));
            UiServiceRegistry.counterpartyAdmin().save(new CounterpartyCommand(original.id(), "Renamed Payee",
                    CounterpartyKind.PERSON, null, null, null, true));
            UiServiceRegistry.counterpartyAdmin().save(new CounterpartyCommand(null, "New Payee",
                    CounterpartyKind.ORG, null, null, null, true));
            journal[0].onPanelShown();
            return null;
        });
        await(() -> payees(journal[0]).getItems().size() == 2);
        FxTestSupport.onFx(() ->
        {
            assertEquals(original.id(), payees(journal[0]).getValue().id());
            assertEquals("Renamed Payee", payees(journal[0]).getValue().name());
            assertTrue(journal[0].hasUnsavedChanges());
            UiServiceRegistry.counterpartyAdmin().save(new CounterpartyCommand(original.id(), "Renamed Payee",
                    CounterpartyKind.PERSON, null, null, null, false));
            journal[0].onPanelShown();
            return null;
        });
        await(() -> payees(journal[0]).getItems().size() == 1);
        FxTestSupport.onFx(() ->
        {
            assertEquals(original.id(), payees(journal[0]).getValue().id());
            assertTrue(journal[0].hasUnsavedChanges());
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private javafx.scene.control.ComboBox<TransactionLineEditorModel.Option> payees(AppPanel journal)
    {
        return (javafx.scene.control.ComboBox<TransactionLineEditorModel.Option>) journal.root().lookup("#journalPayee");
    }

    private void await(java.util.function.BooleanSupplier condition) throws Exception
    {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline)
        {
            if (FxTestSupport.onFx(condition::getAsBoolean)) return;
            Thread.sleep(30);
        }
        fail("Timed out waiting for Journal reference refresh");
    }

    @Test
    void stableIdEditDeactivationRefreshAndLayoutRemainUsable()
    {
        CounterpartyView initial = UiServiceRegistry.counterpartyAdmin().save(
                new CounterpartyCommand(null, "Operations", CounterpartyKind.ORG, null, null, "", true));
        FxTestSupport.onFx(() ->
        {
            panel = new CounterpartiesPanel(() -> false);
            CompanyTableStateBinder.applyProductionPanel(panel.root(), AppPanelId.COUNTERPARTIES);
            stage = new Stage();
            stage.setScene(new Scene((VBox) panel.root(), 900, 650));
            stage.show();
            return null;
        });
        FxTestSupport.onFx(() ->
        {
            TableView<?> table = (TableView<?>) panel.root().lookup("#counterpartiesTable");
            assertEquals(1, table.getItems().size());
            table.getSelectionModel().select(0);
            TextField name = (TextField) panel.root().lookup("#counterpartiesName");
            name.setText("Retired Operations");
            CheckBox active = (CheckBox) panel.root().lookup("#counterpartiesActive");
            active.setSelected(false);
            assertTrue(panel.hasUnsavedChanges());
            ((Button) panel.root().lookup("#counterpartiesSave")).fire();
            return null;
        });
        FxTestSupport.onFx(() ->
        {
            CounterpartyView saved = UiServiceRegistry.counterpartyAdmin().listForMaintenance().stream()
                    .filter(row -> row.id().equals(initial.id())).findFirst().orElseThrow();
            assertEquals("Retired Operations", saved.name());
            assertFalse(saved.active());
            ((Button) panel.root().lookup("#counterpartiesNew")).fire();
            ((TextField) panel.root().lookup("#counterpartiesName")).setText("New Payee");
            assertTrue(panel.hasUnsavedChanges());
            ((TextField) panel.root().lookup("#counterpartiesSearch")).setText("new");
            assertTrue(panel.hasUnsavedChanges(), "Search must not discard a draft");
            ((TextField) panel.root().lookup("#counterpartiesSearch")).clear();
            ((TableView<?>) panel.root().lookup("#counterpartiesTable")).getSelectionModel().select(0);
            assertEquals("New Payee", ((TextField) panel.root().lookup("#counterpartiesName")).getText(), "Rejected discard retains new draft");
            ((Button) panel.root().lookup("#counterpartiesRefresh")).fire();
            assertTrue(panel.hasUnsavedChanges());
            ((Button) panel.root().lookup("#counterpartiesNew")).fire();
            assertEquals("New Payee", ((TextField) panel.root().lookup("#counterpartiesName")).getText());
            SplitPane split = (SplitPane) panel.root().lookup("#counterpartiesWorkspaceSplit");
            ScrollPane editor = (ScrollPane) panel.root().lookup("#counterpartiesEditorScroll");
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
            TableView<?> maintainedTable = (TableView<?>) panel.root().lookup("#counterpartiesTable");
            maintainedTable.getColumns().get(0).setPrefWidth(1100);
            panel.root().applyCss();
            ((javafx.scene.Parent) panel.root()).layout();
            assertTrue(CompanyTableStateBinder.isCompanyStateOwned(maintainedTable));
            assertTrue(maintainedTable.lookupAll(".scroll-bar").stream().anyMatch(node ->
                    node instanceof javafx.scene.control.ScrollBar bar && bar.isVisible()
                            && bar.getOrientation() == javafx.geometry.Orientation.HORIZONTAL));
            CompanyTableStateBinder.saveNow(maintainedTable);
            assertFalse(UiServiceRegistry.companyUiPreferences().loadState("DEFAULT", "ui.table.counterparties.").isEmpty());
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(((Button) panel.root().lookup("#counterpartiesSave")).isDisabled());
            assertTrue(((TextField) panel.root().lookup("#counterpartiesName")).isDisabled());
            return null;
        });
    }
}
