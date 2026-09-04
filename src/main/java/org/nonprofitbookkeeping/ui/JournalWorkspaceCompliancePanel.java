package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.service.ApplicationPermission;
import javafx.animation.PauseTransition;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.event.Event;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.nonprofitbookkeeping.model.CompanyUiPreferences;
import org.nonprofitbookkeeping.service.CompanyUiPreferencesService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Applies the P03-C7 scrolling, table, formatting, and company-state contract to
 * the service-backed Journal workspace without duplicating its accounting logic.
 */
public final class JournalWorkspaceCompliancePanel implements AppPanel
{
    private static final String STATE_PREFIX = "journal.";

    private final JournalWorkspacePanel delegate = new JournalWorkspacePanel();
    private final CompanyUiPreferencesService preferencesService = UiServiceRegistry.companyUiPreferences();
    private final CompanyUiFormat format;
    private final String companyCode;
    private final Map<String, String> state = new LinkedHashMap<>();
    private final Map<String, String> pendingState = new LinkedHashMap<>();
    private final PauseTransition stateFlushDelay = new PauseTransition(Duration.millis(350));
    private boolean restoringState;

    public JournalWorkspaceCompliancePanel()
    {
        companyCode = activeCompanyCode();
        CompanyUiPreferences preferences = preferencesService.load(companyCode);
        format = new CompanyUiFormat(preferences);
        state.putAll(preferencesService.loadState(companyCode, STATE_PREFIX));
        stateFlushDelay.setOnFinished(event -> flushState());

        Node workspaceRoot = delegate.root();
        installOverallEditorScroll(workspaceRoot);
        installCompanyDateEditors(workspaceRoot);
        installTableCompliance(workspaceRoot);
        installTotalFormatting(workspaceRoot);
    }

    @Override
    public String title()
    {
        return delegate.title();
    }

    @Override
    public Node root()
    {
        return delegate.root();
    }

    @Override
    public java.util.Set<AppCommand> commandCapabilities()
    {
        return delegate.commandCapabilities();
    }

    @Override
    public RunCommandResult executeCommand(AppCommand command)
    {
        return delegate.executeCommand(command);
    }

    @Override
    public void onSave()
    {
        delegate.onSave();
    }

    @Override
    public java.util.Optional<ApplicationPermission> requiredPermission(AppCommand command)
    {
        return delegate.requiredPermission(command);
    }

    @Override
    public void onNew()
    {
        delegate.onNew();
    }

    @Override
    public void onPanelShown()
    {
        delegate.onPanelShown();
    }

    @Override
    public boolean hasUnsavedChanges()
    {
        return delegate.hasUnsavedChanges();
    }

    @Override
    public RunCommandResult onRunCommand(AppCommand command)
    {
        return delegate.onRunCommand(command);
    }

    @Override
    public Optional<JournalSelection> activeJournalSelection()
    {
        return delegate.activeJournalSelection();
    }

    private void installOverallEditorScroll(Node workspaceRoot)
    {
        SplitPane outer = findById(workspaceRoot, "journalWorkspaceOuterSplit", SplitPane.class);
        SplitPane editor = findById(workspaceRoot, "journalWorkspaceEditorSplit", SplitPane.class);
        if (outer == null || editor == null)
        {
            throw new IllegalStateException("Journal workspace split regions are unavailable.");
        }

        unwrapLegacyScroll(workspaceRoot, "journalWorkspaceEntryHeaderScroll");
        unwrapLegacyScroll(workspaceRoot, "journalWorkspaceAdditionalDetailsScroll");

        int index = outer.getItems().indexOf(editor);
        if (index < 0)
        {
            return;
        }
        outer.getItems().remove(index);
        editor.setMinHeight(720);
        editor.setPrefHeight(980);

        ScrollPane scroll = new ScrollPane(editor);
        scroll.setId("journalWorkspaceEditorScroll");
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(false);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPannable(true);
        scroll.setMinSize(0, 0);
        outer.getItems().add(index, scroll);

        installDividerState(outer, "outer");
        installDividerState(editor, "editor");
        SplitPane detail = findById(workspaceRoot, "journalWorkspaceDetailSplit", SplitPane.class);
        if (detail != null)
        {
            installDividerState(detail, "detail");
        }
    }

    private void unwrapLegacyScroll(Node workspaceRoot, String id)
    {
        ScrollPane scroll = findById(workspaceRoot, id, ScrollPane.class);
        if (scroll == null || scroll.getContent() == null)
        {
            return;
        }
        Node content = scroll.getContent();
        Parent parent = scroll.getParent();
        if (parent instanceof SplitPane splitPane)
        {
            int index = splitPane.getItems().indexOf(scroll);
            splitPane.getItems().remove(index);
            scroll.setContent(null);
            splitPane.getItems().add(index, content);
        }
    }

    private void installCompanyDateEditors(Node workspaceRoot)
    {
        for (DatePicker picker : findAll(workspaceRoot, DatePicker.class))
        {
            format.install(picker);
        }
    }

    private void installTableCompliance(Node workspaceRoot)
    {
        for (TableView<?> table : findAll(workspaceRoot, TableView.class))
        {
            CompanyTableStateBinder.markCompanyStateOwned(table);
            String tableKey = table.getId() == null || table.getId().isBlank()
                    ? "anonymous-" + Integer.toHexString(System.identityHashCode(table))
                    : table.getId();
            table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
            for (TableColumn<?, ?> column : table.getColumns())
            {
                column.setSortable(true);
                column.setResizable(true);
                column.setReorderable(true);
                column.setMinWidth(Math.max(70, column.getMinWidth()));
            }
            installCompanyCellFormatting(table);
            restoreTableState(table, tableKey);
            installTableStatePersistence(table, tableKey);
            wrapTableInSplitRegion(table, tableKey);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void installCompanyCellFormatting(TableView<?> table)
    {
        String tableId = safe(table.getId());
        for (TableColumn<?, ?> rawColumn : table.getColumns())
        {
            String key = columnKey(rawColumn);
            TableColumn column = rawColumn;
            if ("journalWorkspaceJournalTable".equals(tableId))
            {
                if ("date".equals(key))
                {
                    column.setCellFactory(ignored -> new ReadOnlyDateCell(format));
                }
                else if ("debits".equals(key) || "credits".equals(key))
                {
                    column.setCellFactory(ignored -> new ReadOnlyMultilineMoneyCell(format));
                }
            }
            else if ("journalWorkspaceEntryLineTable".equals(tableId)
                    && ("debit".equals(key) || "credit".equals(key)))
            {
                column.setCellFactory(ignored -> new MoneyEditCell(format));
            }
            else if ("journalWorkspaceEntryLineTable".equals(tableId) && "clearedOn".equals(key))
            {
                column.setCellFactory(ignored -> new ReadOnlyDateCell(format));
            }
            else if (tableId.startsWith("journalWorkspaceSupplemental"))
            {
                if ("amount".equals(key))
                {
                    column.setCellFactory(ignored -> new MoneyEditCell(format));
                }
                else if ("dueDate".equals(key) || "startDate".equals(key) || "endDate".equals(key))
                {
                    column.setCellFactory(ignored -> new DateEditCell(format));
                }
            }
        }
    }

    private void wrapTableInSplitRegion(TableView<?> table, String tableKey)
    {
        Parent parent = table.getParent();
        if (!(parent instanceof VBox box))
        {
            return;
        }
        int index = box.getChildren().indexOf(table);
        if (index < 0)
        {
            return;
        }
        box.getChildren().remove(index);

        SplitPane tableSplit = new SplitPane(table);
        tableSplit.setId(tableKey + "ComplianceSplit");
        tableSplit.setOrientation(Orientation.VERTICAL);
        tableSplit.setMinSize(0, 0);
        VBox.setVgrow(tableSplit, Priority.ALWAYS);
        box.getChildren().add(index, tableSplit);
    }

    private void installTotalFormatting(Node workspaceRoot)
    {
        for (Label label : findAll(workspaceRoot, Label.class))
        {
            Parent parent = label.getParent();
            if (!(parent instanceof javafx.scene.layout.HBox box))
            {
                continue;
            }
            List<Node> children = box.getChildren();
            int index = children.indexOf(label);
            if (index < 0 || index + 1 >= children.size() || !(children.get(index + 1) instanceof Label value))
            {
                continue;
            }
            String heading = safe(label.getText());
            if ("Debit:".equals(heading) || "Credit:".equals(heading) || "Difference:".equals(heading))
            {
                normalizeMoneyLabel(value);
                ChangeListener<String> listener = (obs, oldValue, newValue) -> normalizeMoneyLabel(value);
                value.textProperty().addListener(listener);
            }
        }
    }

    private void normalizeMoneyLabel(Label label)
    {
        BigDecimal amount = CompanyUiFormat.parseMoneyLenient(label.getText());
        if (amount == null)
        {
            return;
        }
        String formatted = format.formatMoney(amount);
        if (!Objects.equals(formatted, label.getText()))
        {
            label.setText(formatted);
        }
    }

    private void restoreTableState(TableView<?> table, String tableKey)
    {
        restoringState = true;
        try
        {
            String prefix = STATE_PREFIX + "table." + tableKey + ".";
            for (TableColumn<?, ?> column : table.getColumns())
            {
                column.setPrefWidth(stateDouble(prefix + columnKey(column) + ".width", column.getPrefWidth()));
                String sort = state.getOrDefault(prefix + columnKey(column) + ".sort", "");
                if ("ASCENDING".equals(sort))
                {
                    column.setSortType(TableColumn.SortType.ASCENDING);
                }
                else if ("DESCENDING".equals(sort))
                {
                    column.setSortType(TableColumn.SortType.DESCENDING);
                }
            }
            restoreColumnOrder(table, state.getOrDefault(prefix + "order", ""));
            restoreSortOrder(table, state.getOrDefault(prefix + "sortOrder", ""));
        }
        finally
        {
            restoringState = false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restoreColumnOrder(TableView table, String order)
    {
        if (order == null || order.isBlank())
        {
            return;
        }
        List<String> keys = List.of(order.split(","));
        List<TableColumn> columns = new ArrayList<>(table.getColumns());
        columns.sort(Comparator.comparingInt(column -> {
            int index = keys.indexOf(columnKey(column));
            return index < 0 ? Integer.MAX_VALUE : index;
        }));
        table.getColumns().setAll(columns);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restoreSortOrder(TableView table, String sortOrder)
    {
        if (sortOrder == null || sortOrder.isBlank())
        {
            return;
        }
        List<String> keys = List.of(sortOrder.split(","));
        List<TableColumn> restored = new ArrayList<>();
        for (String key : keys)
        {
            table.getColumns().stream()
                    .filter(column -> Objects.equals(columnKey((TableColumn<?, ?>) column), key))
                    .findFirst()
                    .ifPresent(column -> restored.add((TableColumn) column));
        }
        table.getSortOrder().setAll(restored);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void installTableStatePersistence(TableView table, String tableKey)
    {
        table.getColumns().addListener((ListChangeListener<TableColumn>) change -> saveTableState(table, tableKey));
        table.getSortOrder().addListener((ListChangeListener<TableColumn>) change -> saveTableState(table, tableKey));
        for (Object item : table.getColumns())
        {
            TableColumn column = (TableColumn) item;
            column.widthProperty().addListener((obs, oldValue, newValue) -> saveTableState(table, tableKey));
            column.sortTypeProperty().addListener((obs, oldValue, newValue) -> saveTableState(table, tableKey));
        }
    }

    private void saveTableState(TableView<?> table, String tableKey)
    {
        if (restoringState)
        {
            return;
        }
        String prefix = STATE_PREFIX + "table." + tableKey + ".";
        queueState(prefix + "order", String.join(",", table.getColumns().stream()
                .map(JournalWorkspaceCompliancePanel::columnKey)
                .toList()));
        queueState(prefix + "sortOrder", String.join(",", table.getSortOrder().stream()
                .map(JournalWorkspaceCompliancePanel::columnKey)
                .toList()));
        for (TableColumn<?, ?> column : table.getColumns())
        {
            double width = column.getWidth() > 0 ? column.getWidth() : column.getPrefWidth();
            queueState(prefix + columnKey(column) + ".width", Double.toString(width));
            queueState(prefix + columnKey(column) + ".sort",
                    column.getSortType() == null ? "" : column.getSortType().name());
        }
    }

    private void installDividerState(SplitPane split, String key)
    {
        for (int index = 0; index < split.getDividers().size(); index++)
        {
            String stateKey = STATE_PREFIX + "divider." + key + "." + index;
            double fallback = split.getDividers().get(index).getPosition();
            split.getDividers().get(index).setPosition(stateDouble(stateKey, fallback));
            int dividerIndex = index;
            split.getDividers().get(index).positionProperty().addListener((obs, oldValue, newValue) ->
                    queueState(STATE_PREFIX + "divider." + key + "." + dividerIndex,
                            Double.toString(newValue.doubleValue())));
        }
    }

    private void queueState(String key, String value)
    {
        state.put(key, value == null ? "" : value);
        pendingState.put(key, value == null ? "" : value);
        stateFlushDelay.playFromStart();
    }

    private void flushState()
    {
        if (pendingState.isEmpty())
        {
            return;
        }
        Map<String, String> snapshot = Map.copyOf(pendingState);
        pendingState.clear();
        UiAsync.run("journal-compliance-state-save",
                () -> {
                    preferencesService.saveState(companyCode, snapshot);
                    return Boolean.TRUE;
                },
                ignored -> { },
                ex -> pendingState.putAll(snapshot));
    }

    private double stateDouble(String key, double fallback)
    {
        try
        {
            return Double.parseDouble(state.getOrDefault(key, Double.toString(fallback)));
        }
        catch (NumberFormatException ex)
        {
            return fallback;
        }
    }

    private static String columnKey(TableColumn<?, ?> column)
    {
        Object key = column.getUserData();
        if (key != null)
        {
            return String.valueOf(key);
        }
        if (column.getId() != null && !column.getId().isBlank())
        {
            return column.getId();
        }
        return safe(column.getText()).replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static String activeCompanyCode()
    {
        String company = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
        String value = company == null || company.isBlank() ? "DEFAULT" : company.trim().toUpperCase(Locale.ROOT);
        return value.replaceAll("[^A-Z0-9_-]", "_");
    }

    private static String safe(String value)
    {
        return value == null ? "" : value;
    }

    private static <T extends Node> T findById(Node root, String id, Class<T> type)
    {
        for (Node node : walk(root))
        {
            if (id.equals(node.getId()) && type.isInstance(node))
            {
                return type.cast(node);
            }
        }
        return null;
    }

    private static <T extends Node> List<T> findAll(Node root, Class<T> type)
    {
        List<T> result = new ArrayList<>();
        for (Node node : walk(root))
        {
            if (type.isInstance(node))
            {
                result.add(type.cast(node));
            }
        }
        return result;
    }

    private static List<Node> walk(Node root)
    {
        List<Node> result = new ArrayList<>();
        appendNodes(root, result);
        return result;
    }

    private static void appendNodes(Node node, List<Node> result)
    {
        if (node == null || result.contains(node))
        {
            return;
        }
        result.add(node);
        if (node instanceof ScrollPane scroll)
        {
            appendNodes(scroll.getContent(), result);
        }
        if (node instanceof SplitPane split)
        {
            split.getItems().forEach(child -> appendNodes(child, result));
        }
        if (node instanceof TabPane tabs)
        {
            tabs.getTabs().forEach(tab -> appendNodes(tab.getContent(), result));
        }
        if (node instanceof Parent parent)
        {
            parent.getChildrenUnmodifiable().forEach(child -> appendNodes(child, result));
        }
    }

    private static final class ReadOnlyDateCell extends TableCell<Object, String>
    {
        private final CompanyUiFormat format;

        private ReadOnlyDateCell(CompanyUiFormat format)
        {
            this.format = format;
        }

        @Override
        protected void updateItem(String item, boolean empty)
        {
            super.updateItem(item, empty);
            if (empty || item == null || item.isBlank())
            {
                setText(null);
                return;
            }
            LocalDate date = format.parseDate(item);
            setText(date == null ? item : format.formatDate(date));
        }
    }

    private static final class ReadOnlyMultilineMoneyCell extends TableCell<Object, String>
    {
        private final CompanyUiFormat format;
        private final Label label = new Label();

        private ReadOnlyMultilineMoneyCell(CompanyUiFormat format)
        {
            this.format = format;
            label.setAlignment(Pos.TOP_RIGHT);
        }

        @Override
        protected void updateItem(String item, boolean empty)
        {
            super.updateItem(item, empty);
            if (empty)
            {
                setGraphic(null);
                return;
            }
            String[] lines = safe(item).split("\\n", -1);
            List<String> formatted = new ArrayList<>();
            for (String line : lines)
            {
                if (line.isBlank())
                {
                    formatted.add("");
                    continue;
                }
                BigDecimal amount = CompanyUiFormat.parseMoneyLenient(line);
                formatted.add(amount == null ? line : format.formatMoney(amount));
            }
            label.setText(String.join("\n", formatted));
            setGraphic(label);
            setText(null);
        }
    }

    private static final class MoneyEditCell extends TableCell<Object, String>
    {
        private final CompanyUiFormat format;
        private TextField editor;

        private MoneyEditCell(CompanyUiFormat format)
        {
            this.format = format;
        }

        @Override
        public void startEdit()
        {
            if (!isEditable() || !getTableView().isEditable() || !getTableColumn().isEditable())
            {
                return;
            }
            super.startEdit();
            if (editor == null)
            {
                editor = new TextField();
                editor.setOnAction(event -> commitEditor());
                editor.focusedProperty().addListener((obs, oldValue, focused) -> {
                    if (!focused)
                    {
                        commitEditor();
                    }
                });
            }
            editor.setText(display(getItem()));
            setText(null);
            setGraphic(editor);
            editor.selectAll();
            editor.requestFocus();
        }

        @Override
        protected void updateItem(String item, boolean empty)
        {
            super.updateItem(item, empty);
            if (empty)
            {
                setText(null);
                setGraphic(null);
            }
            else if (isEditing() && editor != null)
            {
                editor.setText(display(item));
                setText(null);
                setGraphic(editor);
            }
            else
            {
                setText(display(item));
                setGraphic(null);
            }
        }

        private String display(String value)
        {
            BigDecimal amount = format.parseMoney(value);
            return amount == null ? safe(value) : format.formatMoney(amount);
        }

        private void commitEditor()
        {
            if (editor == null)
            {
                return;
            }
            BigDecimal amount = format.parseMoney(editor.getText());
            String value = amount == null
                    ? editor.getText().trim()
                    : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
            commitValue(value);
        }

        private void commitValue(String value)
        {
            if (isEditing())
            {
                commitEdit(value);
                return;
            }
            fireCommitEvent(this, value);
            updateItem(value, false);
        }
    }

    private static final class DateEditCell extends TableCell<Object, String>
    {
        private final CompanyUiFormat format;
        private TextField editor;

        private DateEditCell(CompanyUiFormat format)
        {
            this.format = format;
        }

        @Override
        public void startEdit()
        {
            if (!isEditable() || !getTableView().isEditable() || !getTableColumn().isEditable())
            {
                return;
            }
            super.startEdit();
            if (editor == null)
            {
                editor = new TextField();
                editor.setPromptText(format.preferences().dateDisplayFormat().pattern().replace("uuuu", "yyyy"));
                editor.setOnAction(event -> commitEditor());
                editor.focusedProperty().addListener((obs, oldValue, focused) -> {
                    if (!focused)
                    {
                        commitEditor();
                    }
                });
            }
            editor.setText(display(getItem()));
            setText(null);
            setGraphic(editor);
            editor.selectAll();
            editor.requestFocus();
        }

        @Override
        protected void updateItem(String item, boolean empty)
        {
            super.updateItem(item, empty);
            if (empty)
            {
                setText(null);
                setGraphic(null);
            }
            else if (isEditing() && editor != null)
            {
                editor.setText(display(item));
                setText(null);
                setGraphic(editor);
            }
            else
            {
                setText(display(item));
                setGraphic(null);
            }
        }

        private String display(String value)
        {
            LocalDate date = format.parseDate(value);
            return date == null ? safe(value) : format.formatDate(date);
        }

        private void commitEditor()
        {
            if (editor == null)
            {
                return;
            }
            LocalDate date = format.parseDate(editor.getText());
            String value = date == null ? editor.getText().trim() : date.toString();
            if (isEditing())
            {
                commitEdit(value);
            }
            else
            {
                fireCommitEvent(this, value);
                updateItem(value, false);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void fireCommitEvent(TableCell cell, String value)
    {
        TableView table = cell.getTableView();
        TableColumn column = cell.getTableColumn();
        if (table == null || column == null || cell.getIndex() < 0 || cell.getIndex() >= table.getItems().size())
        {
            return;
        }
        CellEditEvent event = new CellEditEvent(
                table,
                new TablePosition(table, cell.getIndex(), column),
                TableColumn.editCommitEvent(),
                value);
        Event.fireEvent(column, event);
    }
}
