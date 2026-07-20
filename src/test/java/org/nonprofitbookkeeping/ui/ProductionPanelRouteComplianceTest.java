package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock("ui-service-registry")
class ProductionPanelRouteComplianceTest
{
    @Test
    void everyCanonicalProductionRouteOpensAndOwnsCompliantTables(@TempDir Path tempDir)
    {
        Path database = tempDir.resolve("production-panel-routes");
        UiSessionState session = MainWindow.sharedSessionState();
        DatabaseSelectionState originalDatabase = session.databaseSelection();
        MultiCompanyState originalCompany = session.multiCompany();

        session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        UiServiceRegistry.reconnectToDatabase(database);
        FileAppStateStore stateStore = new FileAppStateStore(tempDir.resolve("production-ui-state.properties"));
        stateStore.saveDatabaseSelection(session.databaseSelection());
        stateStore.saveMultiCompany(session.multiCompany());

        try
        {
            FxTestSupport.onFx(() ->
            {
                ProductionWorkspaceWindow window = new ProductionWorkspaceWindow(
                        stateStore,
                        UiServiceRegistry::reconnectToDatabase);
                PanelFactory routeInventory = new PanelFactory();
                Set<AppPanelId> canonicalRoutes = new LinkedHashSet<>();
                routeInventory.supportedPanelIds().stream()
                        .map(AppPanelId::canonical)
                        .forEach(canonicalRoutes::add);

                assertFalse(canonicalRoutes.isEmpty());
                for (AppPanelId panelId : canonicalRoutes)
                {
                    window.openPanel(panelId);
                    javafx.scene.Node root = window.panelHost().activeRoot();
                    assertNotNull(root, panelId + " must create a production root.");
                    for (javafx.scene.control.TableView<?> table
                            : CompanyTableStateBinder.findTables(root))
                    {
                        assertTrue(CompanyTableStateBinder.isCompanyStateOwned(table),
                                panelId + " / " + table.getId() + " must use company-owned H2 state.");
                        assertSame(javafx.scene.control.TableView.UNCONSTRAINED_RESIZE_POLICY,
                                table.getColumnResizePolicy(),
                                panelId + " / " + table.getId() + " must be unconstrained.");
                        table.getColumns().forEach(column ->
                        {
                            assertTrue(column.isSortable(), panelId + " / " + column.getText() + " must sort.");
                            assertTrue(column.isResizable(), panelId + " / " + column.getText() + " must resize.");
                            assertTrue(column.isReorderable(), panelId + " / " + column.getText() + " must reorder.");
                        });
                    }
                }
                assertTrue(canonicalRoutes.containsAll(EnumSet.of(
                        AppPanelId.DASHBOARD,
                        AppPanelId.JOURNAL_PANE,
                        AppPanelId.SETTINGS,
                        AppPanelId.HELP)));
                return null;
            });
        }
        finally
        {
            session.setDatabaseSelection(originalDatabase);
            session.setMultiCompany(originalCompany);
            UiServiceRegistry.reconnectToDatabase(Path.of(originalDatabase.activeDatabasePath()));
        }
    }
}
