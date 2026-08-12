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
import static org.junit.jupiter.api.Assertions.assertEquals;
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
                        UiServiceRegistry::prepareDatabaseConnection);
                javafx.scene.Scene scene = new javafx.scene.Scene(window, 1280, 800);
                window.applyCss();
                window.layout();
                assertEquals(1280.0, scene.getWidth(),
                        "Production smoke must exercise the canonical laptop-width contract.");
                PanelFactory routeInventory = new PanelFactory();
                Set<AppPanelId> canonicalRoutes = new LinkedHashSet<>();
                routeInventory.supportedPanelIds().stream()
                        .map(AppPanelId::canonical)
                        .forEach(canonicalRoutes::add);

                EnumSet<AppPanelId> expectedRoutes = EnumSet.allOf(AppPanelId.class);
                expectedRoutes.remove(AppPanelId.LEDGER_REGISTER);
                expectedRoutes.remove(AppPanelId.TXN_EDITOR);
                expectedRoutes.remove(AppPanelId.SCHEDULES);
                assertEquals(expectedRoutes, canonicalRoutes,
                        "Production smoke must enumerate every canonical destination exactly once.");
                assertFalse(routeInventory.supportedPanelIds().contains(AppPanelId.SCHEDULES));
                for (AppPanelId panelId : canonicalRoutes)
                {
                    window.openPanel(panelId);
                    window.applyCss();
                    window.layout();
                    javafx.scene.Node root = window.panelHost().activeRoot();
                    assertNotNull(root, panelId + " must create a production root.");
                    Set<AppCommand> capabilities = window.panelHost().activeCommandCapabilities();
                    assertNotNull(capabilities, panelId + " must declare factual command capabilities.");
                    for (AppCommand command : EnumSet.of(
                            AppCommand.NEW_ACTIVE,
                            AppCommand.SAVE_ACTIVE,
                            AppCommand.POST_VALIDATE))
                    {
                        if (!capabilities.contains(command))
                        {
                            AppPanel.RunCommandResult unavailable =
                                    window.panelHost().executeActive(command);
                            assertFalse(unavailable.handled(),
                                    panelId + " must not claim an unsupported command.");
                            assertTrue(unavailable.message().contains("not available"),
                                    panelId + " must explain why " + command + " is unavailable.");
                        }
                    }
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

                window.openPanel(AppPanelId.JOURNAL_PANE);
                javafx.scene.Node journalRoot = window.panelHost().activeRoot();
                int openCount = window.panelHost().openPanelCount();
                window.openPanel(AppPanelId.LEDGER_REGISTER);
                assertSame(journalRoot, window.panelHost().activeRoot());
                assertEquals(openCount, window.panelHost().openPanelCount());
                window.openPanel(AppPanelId.TXN_EDITOR);
                assertSame(journalRoot, window.panelHost().activeRoot());
                assertEquals(openCount, window.panelHost().openPanelCount());
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
