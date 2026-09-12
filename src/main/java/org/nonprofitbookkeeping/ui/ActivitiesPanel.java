package org.nonprofitbookkeeping.ui;

import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.nonprofitbookkeeping.service.ActivityCommand;
import org.nonprofitbookkeeping.service.ActivityUsage;
import org.nonprofitbookkeeping.service.ActivityView;
import org.nonprofitbookkeeping.service.ApplicationPermission;
import org.nonprofitbookkeeping.service.CompanyUiPreferencesService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Stable-ID Activity master-data maintenance over the canonical H2 Activity authority. */
public class ActivitiesPanel implements AppPanel
{
    private static final String STATE_PREFIX = "activities.";

    private final VBox root = new VBox(6);
    private final TableView<ActivityView> table = new TableView<>();
    private final Label status = new Label("Ready.");
    private final Label lifecycleStatus = new Label("Select an existing Activity to review deletion eligibility.");
    private final Label editMode = new Label("New Activity");
    private final TextField codeField = new TextField();
    private final TextField nameField = new TextField();
    private final CheckBox activeField = new CheckBox("Active");
    private final Button deleteUnused = new Button("Delete Unused");
    private final Button refresh = new Button("Refresh");
    private final SplitPane split = new SplitPane();
    private final PauseTransition stateSaveDelay = new PauseTransition(Duration.millis(350));

    private final CompanyUiPreferencesService preferencesService = UiServiceRegistry.companyUiPreferences();
    private final String companyCode = activeCompanyCode();
    private final Map<String, String> savedState = new LinkedHashMap<>();
    private final Map<String, TableColumn<ActivityView, String>> columnsByKey = new LinkedHashMap<>();

    private Long editingActivityId;
    private boolean populating;
    private boolean dirty;
    private boolean restoringState;
    private boolean suppressSelection;

    public ActivitiesPanel()
    {
        savedState.putAll(preferencesService.loadState(companyCode, STATE_PREFIX));
        build();
        restoreLayoutState();
        installLayoutStateListeners();
        clearFormForNew(false);
        reload(null);
    }

    private void build()
    {
        root.setPadding(new Insets(8));
        root.setMinWidth(0.0);
        root.setMinHeight(0.0);

        Label title = new Label("Activities");
        title.getStyleClass().add("panel-title");
        Label help = new Label(
                "Activities identify events, projects, or occasions on Journal lines. Edit by stable database identity; code and name are business data and may change without changing existing Journal links.");
        help.setWrapText(true);
        lifecycleStatus.setWrapText(true);

        Button add = new Button("New");
        add.setOnAction(event -> onNew());
        Button save = new Button("Save");
        save.setOnAction(event -> saveForm());
        deleteUnused.setDisable(true);
        deleteUnused.setOnAction(event -> deleteUnusedActivity());
        refresh.setOnAction(event -> reload(editingActivityId));

        UiPermissionGate.gate(add, ApplicationPermission.BOOKKEEPING_WRITE, "Create an Activity");
        UiPermissionGate.gate(save, ApplicationPermission.BOOKKEEPING_WRITE, "Save an Activity");
        UiPermissionGate.gate(deleteUnused, ApplicationPermission.BOOKKEEPING_WRITE, "Delete an unused Activity");
        UiPermissionGate.gate(codeField, ApplicationPermission.BOOKKEEPING_WRITE, "Edit an Activity code");
        UiPermissionGate.gate(nameField, ApplicationPermission.BOOKKEEPING_WRITE, "Edit an Activity name");
        UiPermissionGate.gate(activeField, ApplicationPermission.BOOKKEEPING_WRITE, "Change Activity lifecycle state");

        HBox actions = new HBox(8, add, save, deleteUnused, refresh);
        VBox header = new VBox(6, title, help, actions, status, lifecycleStatus);

        configureTable();
        Node editor = buildEditor();

        VBox tableRegion = new VBox(6, new Label("Activity list"), table);
        tableRegion.setPadding(new Insets(8, 8, 8, 0));
        tableRegion.setMinHeight(0.0);
        VBox.setVgrow(table, Priority.ALWAYS);

        ScrollPane editorScroll = new ScrollPane(editor);
        editorScroll.setId("activitiesEditorScroll");
        editorScroll.setFitToWidth(true);
        editorScroll.setMinHeight(0.0);
        editorScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        editorScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        split.setId("activitiesWorkspaceSplit");
        split.setOrientation(Orientation.VERTICAL);
        split.getItems().setAll(tableRegion, editorScroll);
        split.setDividerPositions(0.58);
        VBox.setVgrow(split, Priority.ALWAYS);

        root.getChildren().addAll(header, split);
        installDirtyListeners();
    }

    private void configureTable()
    {
        table.setId("activitiesTable");
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        CompanyTableStateBinder.markCompanyStateOwned(table);
        table.setPlaceholder(new Label("No Activities found. Choose New to create one."));

        addColumn("code", "Code", ActivityView::code, 150);
        addColumn("name", "Name", ActivityView::name, 300);
        addColumn("active", "Active", row -> row.active() ? "Yes" : "No", 95);

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) ->
        {
            if (suppressSelection || newRow == null)
            {
                return;
            }
            if (dirty && editingActivityId != null
                    && !Objects.equals(editingActivityId, newRow.id())
                    && !confirmDiscard())
            {
                suppressSelection = true;
                table.getSelectionModel().select(oldRow);
                suppressSelection = false;
                return;
            }
            loadRowIntoForm(newRow);
            inspectUsage(newRow.id());
        });
    }

    private void addColumn(
            String key,
            String title,
            java.util.function.Function<ActivityView, String> extractor,
            double preferredWidth)
    {
        TableColumn<ActivityView, String> column = new TableColumn<>(title);
        column.setUserData(key);
        column.setCellValueFactory(data -> new SimpleStringProperty(nullToBlank(extractor.apply(data.getValue()))));
        column.setMinWidth(70);
        column.setPrefWidth(preferredWidth);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        columnsByKey.put(key, column);
        table.getColumns().add(column);
    }

    private Node buildEditor()
    {
        activeField.setSelected(true);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setPadding(new Insets(8));
        form.add(new Label("Mode"), 0, 0);
        form.add(editMode, 1, 0);
        form.add(new Label("Code"), 0, 1);
        form.add(codeField, 1, 1);
        form.add(new Label("Name"), 0, 2);
        form.add(nameField, 1, 2);
        form.add(activeField, 1, 3);
        GridPane.setHgrow(codeField, Priority.ALWAYS);
        GridPane.setHgrow(nameField, Priority.ALWAYS);

        Label lifecycle = new Label(
                "Clearing Active and saving deactivates the Activity without changing historical Journal lines. Inactive Activities remain visible here but are omitted from new Journal Activity choices. Delete Unused is available only when there are no Journal transaction-split references and no durable interchange identity; otherwise deactivate the Activity instead.");
        lifecycle.setWrapText(true);
        VBox editor = new VBox(8, new Label("Activity editor"), form, lifecycle);
        editor.setPadding(new Insets(8));
        return editor;
    }

    private void installDirtyListeners()
    {
        codeField.textProperty().addListener((obs, oldValue, newValue) -> markDirty());
        nameField.textProperty().addListener((obs, oldValue, newValue) -> markDirty());
        activeField.selectedProperty().addListener((obs, oldValue, newValue) -> markDirty());
    }

    private void markDirty()
    {
        if (!populating)
        {
            dirty = true;
        }
    }

    private void loadRowIntoForm(ActivityView row)
    {
        populating = true;
        try
        {
            editingActivityId = row.id();
            codeField.setText(row.code());
            nameField.setText(row.name());
            activeField.setSelected(row.active());
            editMode.setText("Editing Activity ID " + row.id());
            dirty = false;
            deleteUnused.setDisable(true);
            status.setText("Editing Activity " + row.code()
                    + ". Save preserves stable ID " + row.id() + " even when the code changes.");
            lifecycleStatus.setText("Checking Activity references...");
        }
        finally
        {
            populating = false;
        }
    }

    private void clearFormForNew(boolean announce)
    {
        populating = true;
        try
        {
            editingActivityId = null;
            suppressSelection = true;
            table.getSelectionModel().clearSelection();
            suppressSelection = false;
            codeField.clear();
            nameField.clear();
            activeField.setSelected(true);
            editMode.setText("New Activity");
            deleteUnused.setDisable(true);
            dirty = false;
            lifecycleStatus.setText("New Activities have no history until referenced by Journal or interchange data.");
            if (announce)
            {
                status.setText("New Activity: enter a code and name, then choose Save.");
            }
        }
        finally
        {
            populating = false;
        }
    }

    private void saveForm()
    {
        try
        {
            ActivityView saved = UiServiceRegistry.activityAdmin().save(new ActivityCommand(
                    editingActivityId,
                    codeField.getText(),
                    nameField.getText(),
                    activeField.isSelected()));
            editingActivityId = saved.id();
            dirty = false;
            status.setText("Saved Activity " + saved.code() + ".");
            reload(saved.id());
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save Activity: " + UiErrors.safeMessage(ex));
        }
    }

    private void inspectUsage(long activityId)
    {
        deleteUnused.setDisable(true);
        UiAsync.run("activity-usage",
                () -> UiServiceRegistry.activityAdmin().usage(activityId),
                usage -> applyUsage(activityId, usage),
                ex -> {
                    if (Objects.equals(editingActivityId, activityId))
                    {
                        lifecycleStatus.setText("Could not determine deletion eligibility: " + UiErrors.safeMessage(ex));
                    }
                });
    }

    private void applyUsage(long activityId, ActivityUsage usage)
    {
        if (!Objects.equals(editingActivityId, activityId))
        {
            return;
        }
        deleteUnused.setDisable(!usage.canDelete());
        if (usage.canDelete())
        {
            lifecycleStatus.setText(
                    "This Activity has no Journal or interchange references and may be permanently deleted.");
        }
        else
        {
            lifecycleStatus.setText("This Activity is referenced by " + usage.describeReferences()
                    + ". Clear Active and Save to preserve history; permanent deletion is unavailable.");
        }
    }

    private void deleteUnusedActivity()
    {
        if (editingActivityId == null)
        {
            status.setText("Select an existing Activity before deleting.");
            return;
        }

        try
        {
            ActivityUsage usage = UiServiceRegistry.activityAdmin().usage(editingActivityId);
            if (!usage.canDelete())
            {
                applyUsage(editingActivityId, usage);
                status.setText("Activity deletion is unavailable because historical references exist.");
                return;
            }

            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Delete unused Activity");
            confirmation.setHeaderText("Delete " + codeField.getText().trim() + " permanently?");
            confirmation.setContentText(
                    "The service found no Journal transaction-split or interchange-identity references. This operation cannot be undone.");
            if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty())
            {
                status.setText("Activity deletion cancelled.");
                return;
            }

            String deletedCode = codeField.getText().trim();
            UiServiceRegistry.activityAdmin().deleteUnused(editingActivityId);
            clearFormForNew(false);
            status.setText("Deleted unused Activity " + deletedCode + ".");
            reload(null);
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not delete Activity: " + UiErrors.safeMessage(ex));
        }
    }

    private void reload(Long reselectId)
    {
        refresh.setDisable(true);
        status.setText("Loading Activities...");
        UiAsync.run("activity-load",
                () -> UiServiceRegistry.activityLookup().listAllActivities(),
                rows -> {
                    table.setItems(FXCollections.observableArrayList(rows));
                    if (reselectId != null)
                    {
                        rows.stream()
                                .filter(row -> Objects.equals(row.id(), reselectId))
                                .findFirst()
                                .ifPresent(table.getSelectionModel()::select);
                    }
                    else if (editingActivityId == null)
                    {
                        dirty = false;
                    }
                    status.setText("Loaded " + rows.size() + " Activity record(s), including inactive Activities.");
                    refresh.setDisable(false);
                },
                ex -> {
                    status.setText("Failed to load Activities: " + UiErrors.safeMessage(ex));
                    refresh.setDisable(false);
                });
    }

    private boolean confirmDiscard()
    {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Discard Activity edits");
        confirmation.setHeaderText("Discard unsaved Activity changes?");
        confirmation.setContentText("Choose Cancel to remain in the current editor.");
        return confirmation.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void restoreLayoutState()
    {
        restoringState = true;
        try
        {
            double divider = parseDouble(savedState.get(STATE_PREFIX + "divider"), 0.58);
            split.setDividerPositions(Math.max(0.20, Math.min(0.80, divider)));

            for (Map.Entry<String, TableColumn<ActivityView, String>> entry : columnsByKey.entrySet())
            {
                double width = parseDouble(
                        savedState.get(STATE_PREFIX + "table.width." + entry.getKey()),
                        entry.getValue().getPrefWidth());
                entry.getValue().setPrefWidth(Math.max(entry.getValue().getMinWidth(), width));
            }

            String order = savedState.get(STATE_PREFIX + "table.order");
            if (order != null && !order.isBlank())
            {
                List<TableColumn<ActivityView, ?>> ordered = new ArrayList<>();
                for (String key : order.split(","))
                {
                    TableColumn<ActivityView, String> column = columnsByKey.get(key);
                    if (column != null && !ordered.contains(column))
                    {
                        ordered.add(column);
                    }
                }
                for (TableColumn<ActivityView, String> column : columnsByKey.values())
                {
                    if (!ordered.contains(column))
                    {
                        ordered.add(column);
                    }
                }
                table.getColumns().setAll(ordered);
            }

            String sort = savedState.get(STATE_PREFIX + "table.sort");
            if (sort != null && !sort.isBlank())
            {
                table.getSortOrder().clear();
                for (String part : sort.split(","))
                {
                    String[] pieces = part.split(":", 2);
                    TableColumn<ActivityView, String> column = columnsByKey.get(pieces[0]);
                    if (column != null)
                    {
                        column.setSortType(pieces.length > 1 && "DESC".equals(pieces[1])
                                ? TableColumn.SortType.DESCENDING
                                : TableColumn.SortType.ASCENDING);
                        table.getSortOrder().add(column);
                    }
                }
            }
        }
        finally
        {
            restoringState = false;
        }
    }

    private void installLayoutStateListeners()
    {
        stateSaveDelay.setOnFinished(event -> saveLayoutState());
        split.getDividers().get(0).positionProperty().addListener((obs, oldValue, newValue) -> queueLayoutStateSave());
        table.getColumns().addListener((ListChangeListener<TableColumn<ActivityView, ?>>) change -> queueLayoutStateSave());
        table.getSortOrder().addListener((ListChangeListener<TableColumn<ActivityView, ?>>) change -> queueLayoutStateSave());
        for (TableColumn<ActivityView, String> column : columnsByKey.values())
        {
            column.widthProperty().addListener((obs, oldValue, newValue) -> queueLayoutStateSave());
            column.sortTypeProperty().addListener((obs, oldValue, newValue) -> queueLayoutStateSave());
        }
    }

    private void queueLayoutStateSave()
    {
        if (!restoringState)
        {
            stateSaveDelay.playFromStart();
        }
    }

    private void saveLayoutState()
    {
        Map<String, String> state = new LinkedHashMap<>();
        state.put(STATE_PREFIX + "divider", Double.toString(split.getDividers().get(0).getPosition()));
        state.put(STATE_PREFIX + "table.order", table.getColumns().stream()
                .map(column -> String.valueOf(column.getUserData()))
                .reduce((left, right) -> left + "," + right)
                .orElse(""));
        for (Map.Entry<String, TableColumn<ActivityView, String>> entry : columnsByKey.entrySet())
        {
            state.put(STATE_PREFIX + "table.width." + entry.getKey(), Double.toString(entry.getValue().getWidth()));
        }
        state.put(STATE_PREFIX + "table.sort", table.getSortOrder().stream()
                .map(column -> String.valueOf(column.getUserData()) + ":"
                        + (column.getSortType() == TableColumn.SortType.DESCENDING ? "DESC" : "ASC"))
                .reduce((left, right) -> left + "," + right)
                .orElse(""));
        UiAsync.run("activity-layout-state-save",
                () -> {
                    preferencesService.saveState(companyCode, state);
                    return Boolean.TRUE;
                },
                ignored -> { },
                ex -> status.setText("Could not save Activity workspace layout: " + UiErrors.safeMessage(ex)));
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

    private static String activeCompanyCode()
    {
        String company = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
        String value = company == null || company.isBlank() ? "DEFAULT" : company.trim().toUpperCase(Locale.ROOT);
        return value.replaceAll("[^A-Z0-9_-]", "_");
    }

    private static String nullToBlank(String value)
    {
        return value == null ? "" : value;
    }

    @Override
    public String title()
    {
        return "Activities";
    }

    @Override
    public Node root()
    {
        return root;
    }

    @Override
    public java.util.Optional<ApplicationPermission> requiredPermission(AppCommand command)
    {
        return switch (command)
        {
            case NEW_ACTIVE, SAVE_ACTIVE -> java.util.Optional.of(ApplicationPermission.BOOKKEEPING_WRITE);
            default -> java.util.Optional.empty();
        };
    }

    @Override
    public java.util.Set<AppCommand> commandCapabilities()
    {
        return AppPanel.capabilities(AppCommand.NEW_ACTIVE, AppCommand.SAVE_ACTIVE);
    }

    @Override
    public void onNew()
    {
        if (!dirty || confirmDiscard())
        {
            clearFormForNew(true);
        }
        else
        {
            status.setText("New Activity cancelled; unsaved changes remain.");
        }
    }

    @Override
    public void onSave()
    {
        saveForm();
    }

    @Override
    public String commandResultMessage(AppCommand command)
    {
        return status.getText();
    }

    @Override
    public boolean hasUnsavedChanges()
    {
        return dirty;
    }
}
