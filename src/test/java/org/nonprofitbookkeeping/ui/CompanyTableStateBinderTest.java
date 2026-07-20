package org.nonprofitbookkeeping.ui;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.CompanyUiPreferences;
import org.nonprofitbookkeeping.repository.CompanyUiPreferenceRepository;
import org.nonprofitbookkeeping.service.CompanyUiPreferencesService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyTableStateBinderTest
{
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
