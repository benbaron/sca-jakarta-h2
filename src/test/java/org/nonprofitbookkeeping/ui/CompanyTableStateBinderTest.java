package org.nonprofitbookkeeping.ui;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.CompanyUiPreferences;
import org.nonprofitbookkeeping.repository.CompanyUiPreferenceRepository;
import org.nonprofitbookkeeping.service.CompanyUiPreferencesService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock("ui-service-registry")
class CompanyTableStateBinderTest
{
    @Test
    void delayedDividerSaveCannotOutliveItsSessionOrCompany()
    {
        FxTestSupport.onFx(() ->
        {
            UiSessionState session = new UiSessionState();
            MemoryRepository repository = new MemoryRepository();
            CompanyUiPreferencesService service = new CompanyUiPreferencesService(repository);
            var split = new javafx.scene.control.SplitPane(new BorderPane(), new BorderPane());
            try
            {
                session.setMultiCompany(new org.nonprofitbookkeeping.model.MultiCompanyState("DEFAULT", java.util.List.of("DEFAULT")));
                session.setAuthenticatedUser(UiPermissionTestSessions.manager());
                CompanySplitPaneStateBinder.bind(split, "session-test", 0.5, service, session);
                var timer = (javafx.animation.PauseTransition) split.getProperties().get("sca.companySplitPaneStateBinding");
                split.setDividerPositions(0.6);
                timer.stop();
                timer.getOnFinished().handle(new javafx.event.ActionEvent());
                assertEquals("0.6", service.loadState("DEFAULT", "ui.split.session-test").get("ui.split.session-test.divider"));
                session.clearAuthenticatedUser();
                split.setDividerPositions(0.7);
                timer.stop();
                timer.getOnFinished().handle(new javafx.event.ActionEvent());
                assertEquals("0.6", service.loadState("DEFAULT", "ui.split.session-test").get("ui.split.session-test.divider"));
                session.setAuthenticatedUser(UiPermissionTestSessions.manager());
                session.setMultiCompany(new org.nonprofitbookkeeping.model.MultiCompanyState("OTHER", java.util.List.of("DEFAULT", "OTHER")));
                timer.getOnFinished().handle(new javafx.event.ActionEvent());
                assertEquals("0.6", service.loadState("DEFAULT", "ui.split.session-test").get("ui.split.session-test.divider"));
                session.setMultiCompany(new org.nonprofitbookkeeping.model.MultiCompanyState("DEFAULT", java.util.List.of("DEFAULT")));
                session.setDatabaseSelection(new org.nonprofitbookkeeping.model.DatabaseSelectionState(
                        "/different/company.mv.db", java.util.List.of("/different/company.mv.db")));
                timer.getOnFinished().handle(new javafx.event.ActionEvent());
                assertEquals("0.6", service.loadState("DEFAULT", "ui.split.session-test").get("ui.split.session-test.divider"));
            }
            finally
            {
                var timer = (javafx.animation.PauseTransition) split.getProperties().get("sca.companySplitPaneStateBinding");
                if (timer != null) timer.stop();
            }
            return null;
        });
    }

    @Test
    void enforcesTableContractAndRestoresCompanyOwnedOrderAndSort()
    {
        MemoryRepository repository = new MemoryRepository();
        CompanyUiPreferencesService service = new CompanyUiPreferencesService(repository);

        FxTestSupport.onFx(() ->
        {
            TableView<String> first = table();
            CompanyTableStateBinder.apply(new BorderPane(first), AppPanelId.BANKING, service, "SCA");

            assertSame(TableView.UNCONSTRAINED_RESIZE_POLICY, first.getColumnResizePolicy());
            assertTrue(CompanyTableStateBinder.isCompanyStateOwned(first));
            first.getColumns().forEach(column ->
            {
                assertTrue(column.isSortable());
                assertTrue(column.isResizable());
                assertTrue(column.isReorderable());
            });

            TableColumn<String, ?> code = first.getColumns().get(0);
            TableColumn<String, ?> name = first.getColumns().get(1);
            name.setSortType(TableColumn.SortType.DESCENDING);
            first.getColumns().setAll(name, code);
            first.getSortOrder().setAll(name);
            CompanyTableStateBinder.saveNow(first);

            TableView<String> restored = table();
            CompanyTableStateBinder.apply(new BorderPane(restored), AppPanelId.BANKING, service, "SCA");

            assertEquals("name", restored.getColumns().get(0).getUserData());
            assertEquals("code", restored.getColumns().get(1).getUserData());
            assertEquals(1, restored.getSortOrder().size());
            assertEquals("name", restored.getSortOrder().get(0).getUserData());
            assertEquals(TableColumn.SortType.DESCENDING, restored.getSortOrder().get(0).getSortType());
            return null;
        });
    }

    @Test
    void leavesAnExistingCompanyStateOwnerUntouched()
    {
        FxTestSupport.onFx(() ->
        {
            TableView<String> table = table();
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            CompanyTableStateBinder.markCompanyStateOwned(table);
            CompanyTableStateBinder.apply(
                    new BorderPane(table),
                    AppPanelId.FUNDS,
                    new CompanyUiPreferencesService(new MemoryRepository()),
                    "SCA");

            assertTrue(CompanyTableStateBinder.isCompanyStateOwned(table));
            assertSame(TableView.CONSTRAINED_RESIZE_POLICY, table.getColumnResizePolicy());
            assertTrue(table.getColumns().stream().noneMatch(TableColumn::isSortable));
            return null;
        });
    }

    private static TableView<String> table()
    {
        TableView<String> table = new TableView<>();
        table.setId("records");
        TableColumn<String, String> code = new TableColumn<>("Code");
        code.setUserData("code");
        code.setSortable(false);
        code.setResizable(false);
        code.setReorderable(false);
        TableColumn<String, String> name = new TableColumn<>("Name");
        name.setUserData("name");
        name.setSortable(false);
        name.setResizable(false);
        name.setReorderable(false);
        table.getColumns().setAll(code, name);
        return table;
    }

    private static final class MemoryRepository implements CompanyUiPreferenceRepository
    {
        private final Map<String, String> state = new LinkedHashMap<>();

        @Override
        public Optional<CompanyUiPreferences> findPreferences(String companyCode)
        {
            return Optional.empty();
        }

        @Override
        public void savePreferences(String companyCode, CompanyUiPreferences preferences)
        {
        }

        @Override
        public Map<String, String> findStateByPrefix(String companyCode, String keyPrefix)
        {
            Map<String, String> result = new LinkedHashMap<>();
            state.forEach((key, value) ->
            {
                if (key.startsWith(companyCode + ":" + keyPrefix))
                {
                    result.put(key.substring(companyCode.length() + 1), value);
                }
            });
            return result;
        }

        @Override
        public void saveState(String companyCode, Map<String, String> values)
        {
            values.forEach((key, value) -> state.put(companyCode + ":" + key, value));
        }
    }
}
