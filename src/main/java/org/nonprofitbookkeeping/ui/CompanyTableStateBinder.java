package org.nonprofitbookkeeping.ui;

import javafx.animation.PauseTransition;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Duration;
import org.nonprofitbookkeeping.service.CompanyUiPreferencesService;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Applies the production table contract and persists table layout in company-owned H2 state.
 */
final class CompanyTableStateBinder
{
    static final String COMPANY_STATE_OWNER_PROPERTY = "sca.companyTableStateOwner";
    private static final String BINDING_PROPERTY = "sca.companyTableStateBinding";

    private CompanyTableStateBinder()
    {
    }

    static void applyProductionPanel(Node root, AppPanelId panelId)
    {
        List<TableView<?>> tables = findTables(root);
        if (tables.stream().allMatch(CompanyTableStateBinder::isCompanyStateOwned))
        {
            return;
        }

        String companyCode = activeCompanyCode();
        CompanyUiPreferencesService service = UiServiceRegistry.companyUiPreferences();
        apply(root, panelId, service, companyCode);
    }

    static void apply(Node root,
                      AppPanelId panelId,
                      CompanyUiPreferencesService service,
                      String companyCode)
    {
        List<TableView<?>> tables = findTables(root);
        String panelKey = AppPanelId.canonical(panelId).name().toLowerCase(Locale.ROOT);
        int anonymousIndex = 0;
        for (TableView<?> table : tables)
        {
            if (isCompanyStateOwned(table))
            {
                continue;
            }
            String tableKey = table.getId() == null || table.getId().isBlank()
                    ? "table-" + anonymousIndex++
                    : safeKey(table.getId());
            new Binding(table, service, companyCode, "ui.table." + panelKey + "." + tableKey + ".").install();
        }
    }

    static void markCompanyStateOwned(TableView<?> table)
    {
        table.getProperties().put(COMPANY_STATE_OWNER_PROPERTY, Boolean.TRUE);
    }

    static boolean isCompanyStateOwned(TableView<?> table)
    {
        return Boolean.TRUE.equals(table.getProperties().get(COMPANY_STATE_OWNER_PROPERTY));
    }

    static void saveNow(TableView<?> table)
    {
        Object binding = table.getProperties().get(BINDING_PROPERTY);
        if (binding instanceof Binding value)
        {
            value.save();
        }
    }

    static List<TableView<?>> findTables(Node root)
    {
        List<TableView<?>> result = new ArrayList<>();
        collectTables(root, result);
        return result;
    }

    private static void collectTables(Node node, List<TableView<?>> result)
    {
        if (node == null)
        {
            return;
        }
        if (node instanceof TableView<?> table)
        {
            result.add(table);
            return;
        }
        if (node instanceof ScrollPane scroll)
        {
            collectTables(scroll.getContent(), result);
            return;
        }
        if (node instanceof SplitPane split)
        {
            split.getItems().forEach(item -> collectTables(item, result));
            return;
        }
        if (node instanceof TabPane tabs)
        {
            tabs.getTabs().forEach(tab -> collectTables(tab.getContent(), result));
            return;
        }
        if (node instanceof Parent parent)
        {
            parent.getChildrenUnmodifiable().forEach(child -> collectTables(child, result));
        }
    }

    private static String activeCompanyCode()
    {
        String company = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
        return company == null || company.isBlank() ? "DEFAULT" : company.trim().toUpperCase(Locale.ROOT);
    }

    private static String safeKey(String value)
    {
        return value.replaceAll("[^A-Za-z0-9_-]", "-");
    }

    private static final class Binding
    {
        private final TableView<?> table;
        private final CompanyUiPreferencesService service;
        private final String companyCode;
        private final String prefix;
        private final PauseTransition saveDelay = new PauseTransition(Duration.millis(400));
        private final Map<TableColumn<?, ?>, String> columnKeys = new IdentityHashMap<>();
        private boolean restoring;

        private Binding(TableView<?> table,
                        CompanyUiPreferencesService service,
                        String companyCode,
                        String prefix)
        {
            this.table = table;
            this.service = service;
            this.companyCode = companyCode;
            this.prefix = prefix;
        }

        private void install()
        {
            table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
            List<TableColumn<?, ?>> columns = allColumns(table.getColumns());
            for (int i = 0; i < columns.size(); i++)
            {
                TableColumn<?, ?> column = columns.get(i);
                column.setSortable(true);
                column.setResizable(true);
                column.setReorderable(true);
                columnKeys.put(column, columnKey(column, i));
            }

            restore(service.loadState(companyCode, prefix));
            saveDelay.setOnFinished(event -> save());
            installTableListeners();
            columns.forEach(column ->
            {
                column.widthProperty().addListener((obs, oldValue, newValue) -> queueSave());
                column.sortTypeProperty().addListener((obs, oldValue, newValue) -> queueSave());
            });
            markCompanyStateOwned(table);
            table.getProperties().put(BINDING_PROPERTY, this);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void installTableListeners()
        {
            table.getColumns().addListener((ListChangeListener) change -> queueSave());
            table.getSortOrder().addListener((ListChangeListener) change -> queueSave());
        }

        private void restore(Map<String, String> state)
        {
            restoring = true;
            try
            {
                for (Map.Entry<TableColumn<?, ?>, String> entry : columnKeys.entrySet())
                {
                    double width = parseDouble(state.get(prefix + "width." + entry.getValue()), -1);
                    if (width > 0)
                    {
                        entry.getKey().setPrefWidth(width);
                    }
                }
                restoreOrder(state.get(prefix + "order"));
                restoreSort(state.get(prefix + "sort"));
            }
            finally
            {
                restoring = false;
            }
        }

        private void restoreOrder(String value)
        {
            if (value == null || value.isBlank())
            {
                return;
            }
            List<TableColumn<?, ?>> ordered = new ArrayList<>();
            for (String key : value.split(","))
            {
                columnKeys.entrySet().stream()
                        .filter(entry -> entry.getValue().equals(key))
                        .map(Map.Entry::getKey)
                        .filter(table.getColumns()::contains)
                        .findFirst()
                        .ifPresent(column ->
                        {
                            if (!ordered.contains(column))
                            {
                                ordered.add(column);
                            }
                        });
            }
            table.getColumns().stream().filter(column -> !ordered.contains(column)).forEach(ordered::add);
            if (ordered.size() == table.getColumns().size())
            {
                setColumns(ordered);
            }
        }

        private void restoreSort(String value)
        {
            table.getSortOrder().clear();
            if (value == null || value.isBlank())
            {
                return;
            }
            for (String part : value.split(","))
            {
                String[] pieces = part.split(":", 2);
                columnKeys.entrySet().stream()
                        .filter(entry -> entry.getValue().equals(pieces[0]))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .ifPresent(column ->
                        {
                            column.setSortType(pieces.length > 1 && "DESC".equals(pieces[1])
                                    ? TableColumn.SortType.DESCENDING
                                    : TableColumn.SortType.ASCENDING);
                            table.getSortOrder().add(column);
                        });
            }
        }

        private void queueSave()
        {
            if (!restoring)
            {
                saveDelay.playFromStart();
            }
        }

        private void save()
        {
            Map<String, String> state = new LinkedHashMap<>();
            state.put(prefix + "order", table.getColumns().stream()
                    .map(column -> columnKeys.getOrDefault(column, "column"))
                    .reduce((left, right) -> left + "," + right)
                    .orElse(""));
            columnKeys.forEach((column, key) ->
                    state.put(prefix + "width." + key, Double.toString(column.getWidth())));
            state.put(prefix + "sort", table.getSortOrder().stream()
                    .map(column -> columnKeys.getOrDefault(column, "column") + ":"
                            + (column.getSortType() == TableColumn.SortType.DESCENDING ? "DESC" : "ASC"))
                    .reduce((left, right) -> left + "," + right)
                    .orElse(""));
            service.saveState(companyCode, state);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void setColumns(List<TableColumn<?, ?>> ordered)
        {
            ((TableView) table).getColumns().setAll((List) ordered);
        }

        private static List<TableColumn<?, ?>> allColumns(List<? extends TableColumn<?, ?>> roots)
        {
            List<TableColumn<?, ?>> result = new ArrayList<>();
            for (TableColumn<?, ?> column : roots)
            {
                result.add(column);
                result.addAll(allColumns(column.getColumns()));
            }
            return result;
        }

        private static String columnKey(TableColumn<?, ?> column, int index)
        {
            Object userData = column.getUserData();
            if (userData != null && !userData.toString().isBlank())
            {
                return safeKey(userData.toString());
            }
            if (column.getId() != null && !column.getId().isBlank())
            {
                return safeKey(column.getId());
            }
            return "column-" + index;
        }

        private static double parseDouble(String value, double fallback)
        {
            try
            {
                return value == null || value.isBlank() ? fallback : Double.parseDouble(value);
            }
            catch (NumberFormatException ex)
            {
                return fallback;
            }
        }
    }
}
