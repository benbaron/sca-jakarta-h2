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
import org.nonprofitbookkeeping.service.BudgetCategoryCommand;
import org.nonprofitbookkeeping.service.BudgetCategoryView;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class BudgetCategoriesPanelTest
{
    @TempDir Path directory;
    private DatabaseSelectionState previousDatabase;
    private MultiCompanyState previousCompany;
    private Optional<AuthenticatedUserSession> previousUser;
    private Stage stage;
    private BudgetCategoriesPanel panel;

    @BeforeEach
    void setup()
    {
        FxTestSupport.initToolkitOrSkip();
        UiSessionState session = MainWindow.sharedSessionState();
        previousDatabase = session.databaseSelection();
        previousCompany = session.multiCompany();
        previousUser = session.authenticatedUser();
        Path database = directory.resolve("budget-categories");
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
            DrillThroughCoordinator.configureOpener(null);
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
    void stableIdEditDeactivationRefreshAndLayoutRemainUsable()
    {
        BudgetCategoryView initial = UiServiceRegistry.budgetCategoryAdmin().save(
                new BudgetCategoryCommand(null, "OPS", "Operations", true, null, null, ""));
        FxTestSupport.onFx(() ->
        {
            panel = new BudgetCategoriesPanel();
            stage = new Stage();
            stage.setScene(new Scene((VBox) panel.root(), 900, 650));
            stage.show();
            return null;
        });
        FxTestSupport.onFx(() ->
        {
            TableView<?> table = (TableView<?>) panel.root().lookup("#budgetCategoriesTable");
            assertEquals(1, table.getItems().size());
            table.getSelectionModel().select(0);
            TextField name = (TextField) panel.root().lookup("#budgetCategoriesName");
            name.setText("Retired Operations");
            CheckBox active = (CheckBox) panel.root().lookup("#budgetCategoriesActive");
            active.setSelected(false);
            assertTrue(panel.hasUnsavedChanges());
            ((Button) panel.root().lookup("#budgetCategoriesSave")).fire();
            return null;
        });
        FxTestSupport.onFx(() ->
        {
            BudgetCategoryView saved = UiServiceRegistry.budgetCategoryLookup().listForMaintenance().stream()
                    .filter(row -> row.id().equals(initial.id())).findFirst().orElseThrow();
            assertEquals("Retired Operations", saved.name());
            assertFalse(saved.active());
            ((Button) panel.root().lookup("#budgetCategoriesNew")).fire();
            ((TextField) panel.root().lookup("#budgetCategoriesCode")).setText("NEW");
            ((TextField) panel.root().lookup("#budgetCategoriesName")).setText("New Category");
            assertTrue(panel.hasUnsavedChanges());
            ((TextField) panel.root().lookup("#budgetCategoriesSearch")).setText("new");
            assertTrue(panel.hasUnsavedChanges(), "Search must not discard a draft");
            SplitPane split = (SplitPane) panel.root().lookup("#budgetCategoriesWorkspaceSplit");
            ScrollPane editor = (ScrollPane) panel.root().lookup("#budgetCategoriesEditorScroll");
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, editor.getVbarPolicy());
            split.setDividerPositions(0.25);
            panel.root().applyCss();
            ((javafx.scene.Parent) panel.root()).layout();
            assertTrue(editor.getViewportBounds().getHeight() > 0);
            MainWindow.sharedSessionState().setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(((Button) panel.root().lookup("#budgetCategoriesSave")).isDisabled());
            assertTrue(((TextField) panel.root().lookup("#budgetCategoriesName")).isDisabled());
            return null;
        });
    }

    @Test
    void reopeningAndShowAllRevealEveryCompanyCategoryWhileKeepingTheDraft()
    {
        seed("ACTIVE", "Active category", true);
        seed("RETIRED", "Retired category", false);
        try (var jpa = new org.nonprofitbookkeeping.persistence.Jpa(Path.of(
                MainWindow.sharedSessionState().databaseSelection().activeDatabasePath())); var em = jpa.em())
        {
            em.getTransaction().begin();
            var other = new org.nonprofitbookkeeping.model.Company();
            other.setCode("OTHER"); other.setDisplayName("Other company"); em.persist(other);
            var foreign = new org.nonprofitbookkeeping.model.BudgetCategory();
            foreign.setCompany(other); foreign.setCode("FOREIGN"); foreign.setName("Other company's category");
            em.persist(foreign); em.getTransaction().commit();
        }
        FxTestSupport.onFx(() ->
        {
            PanelHost host = hostWithSource(AppPanelId.IMPORT_PREVIEW, "Import");
            panel = new BudgetCategoriesPanel(() -> false);
            host.showReplacement(AppPanelId.BUDGET_CATEGORIES, panel);
            settle();
            assertEquals(2, categories().getItems().size());
            search().setText("no match");
            assertTrue(categories().getItems().isEmpty());
            field("budgetCategoriesCode").setText("DRAFT");
            field("budgetCategoriesName").setText("Keep unsaved category");
            host.show(AppPanelId.IMPORT_PREVIEW);
            seed("ADDED", "Added while away", true);
            host.show(AppPanelId.BUDGET_CATEGORIES);
            assertEquals("", search().getText(), "Activation shows all, not the prior hidden filter");
            assertEquals(3, categories().getItems().size());
            assertTrue(categories().getItems().stream().noneMatch(row -> "FOREIGN".equals(row.code())));
            assertTrue(categories().getItems().stream().anyMatch(row -> !row.active()));
            assertEquals("Keep unsaved category", field("budgetCategoriesName").getText());
            assertTrue(panel.hasUnsavedChanges());
            search().setText("no match");
            ((Button) panel.root().lookup("#budgetCategoriesShowAll")).fire();
            assertEquals(3, categories().getItems().size());
            assertEquals("Keep unsaved category", field("budgetCategoriesName").getText());
            seed("REFRESH", "Refresh while dirty", true);
            ((Button) panel.root().lookup("#budgetCategoriesRefresh")).fire();
            assertEquals(4, categories().getItems().size());
            assertEquals("Keep unsaved category", field("budgetCategoriesName").getText());
            return null;
        });
    }

    @Test
    void saveRevealsNewCategoryAndEmptyListIsDistinctFromSearchMiss()
    {
        FxTestSupport.onFx(() ->
        {
            panel = new BudgetCategoriesPanel();
            stage = new Stage(); stage.setScene(new Scene((VBox) panel.root(), 900, 650)); stage.show();
            String empty = ((javafx.scene.control.Label) categories().getPlaceholder()).getText();
            assertTrue(empty.contains("No Budget Categories have been created"), empty);
            field("budgetCategoriesCode").setText("NEW");
            field("budgetCategoriesName").setText("New category");
            search().setText("does not match new category");
            ((Button) panel.root().lookup("#budgetCategoriesSave")).fire();
            assertEquals("", search().getText());
            assertEquals(1, categories().getItems().size());
            assertEquals("NEW", categories().getSelectionModel().getSelectedItem().code());
            search().setText("no match");
            String filtered = ((javafx.scene.control.Label) categories().getPlaceholder()).getText();
            assertTrue(filtered.contains("No matching"), filtered);
            assertFalse(filtered.equals(empty));
            return null;
        });
    }

    @Test
    void returnTracksActualCallerAndReselectionKeepsItsDestination()
    {
        FxTestSupport.onFx(() ->
        {
            PanelHost host = hostWithSource(AppPanelId.IMPORT_PREVIEW, "Import");
            panel = new BudgetCategoriesPanel();
            host.showReplacement(AppPanelId.BUDGET_CATEGORIES, panel);
            settle();
            DrillThroughCoordinator.configureOpener(host::show);
            Button back = (Button) panel.root().lookup("#budgetCategoriesJournal");
            assertEquals("Return to Import", back.getText());
            host.show(AppPanelId.BUDGET_CATEGORIES);
            assertEquals("Return to Import", back.getText());
            back.fire();
            assertEquals(AppPanelId.IMPORT_PREVIEW, host.activePanelId());
            host.showReplacement(AppPanelId.JOURNAL_PANE, source("Journal"));
            host.show(AppPanelId.BUDGET_CATEGORIES);
            assertEquals("Return to Journal", back.getText());
            back.fire();
            assertEquals(AppPanelId.JOURNAL_PANE, host.activePanelId());
            host.showReplacement(AppPanelId.DASHBOARD, source("Dashboard"));
            host.show(AppPanelId.BUDGET_CATEGORIES);
            assertEquals("Return to Dashboard", back.getText());
            back.fire();
            assertEquals(AppPanelId.DASHBOARD, host.activePanelId());
            return null;
        });
    }

    @Test
    void loadFailureDoesNotMasqueradeAsAnEmptyOrFilteredList()
    {
        FxTestSupport.onFx(() ->
        {
            panel = new BudgetCategoriesPanel();
            stage = new Stage(); stage.setScene(new Scene((VBox) panel.root(), 900, 650)); stage.show();
            field("budgetCategoriesCode").setText("PENDING");
            field("budgetCategoriesName").setText("Retain category draft");
            MainWindow.sharedSessionState().setMultiCompany(new MultiCompanyState("MISSING", List.of("DEFAULT", "MISSING")));
            panel.onPanelShown();
            String failed = ((javafx.scene.control.Label) categories().getPlaceholder()).getText();
            assertTrue(failed.contains("Could not load"), failed);
            assertEquals("Retain category draft", field("budgetCategoriesName").getText());
            MainWindow.sharedSessionState().setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
            panel.onPanelShown();
            assertTrue(((javafx.scene.control.Label) categories().getPlaceholder()).getText()
                    .contains("No Budget Categories have been created"));
            assertEquals("Retain category draft", field("budgetCategoriesName").getText());
            assertTrue(panel.hasUnsavedChanges());
            return null;
        });
        assertTrue(UiServiceRegistry.budgetCategoryLookup().listForMaintenance().isEmpty());
    }

    private BudgetCategoryView seed(String code, String name, boolean active)
    {
        return UiServiceRegistry.budgetCategoryAdmin().save(new BudgetCategoryCommand(null, code, name, active, null, null, ""));
    }

    private PanelHost hostWithSource(AppPanelId id, String title)
    {
        PanelHost host = new PanelHost();
        host.showReplacement(id, source(title));
        stage = new Stage(); stage.setScene(new Scene(host, 950, 750)); stage.show();
        return host;
    }

    private static AppPanel source(String title)
    {
        return new AppPanel()
        {
            private final VBox root = new VBox();
            @Override public String title() { return title; }
            @Override public javafx.scene.Node root() { return root; }
        };
    }

    @SuppressWarnings("unchecked")
    private TableView<BudgetCategoryView> categories()
    {
        return (TableView<BudgetCategoryView>) panel.root().lookup("#budgetCategoriesTable");
    }

    private void settle()
    {
        for (int pass = 0; pass < 3; pass++)
        {
            stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
        }
    }

    private TextField field(String id) { return (TextField) panel.root().lookup("#" + id); }
    private TextField search() { return field("budgetCategoriesSearch"); }
}
