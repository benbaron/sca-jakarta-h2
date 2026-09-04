package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.service.ApplicationPermission;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.nonprofitbookkeeping.model.CompanyUiPreferences;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.service.CompanyUiPreferencesService;
import org.nonprofitbookkeeping.service.FundCommand;
import org.nonprofitbookkeeping.service.FundUsage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Stable-ID Fund administration workspace with protected delete/deactivate behavior. */
public class FundsPanel implements AppPanel
{
    private static final String STATE_PREFIX = "funds.";

    private final BorderPane root = new BorderPane();
    private final TableView<Fund> table = new TableView<>();
    private final Label status = new Label("Ready.");
    private final Label editMode = new Label("New fund");
    private final TextField codeField = new TextField();
    private final TextField nameField = new TextField();
    private final ComboBox<FundType> typeField = new ComboBox<>();
    private final ComboBox<ParentOption> parentField = new ComboBox<>();
    private final DatePicker effectiveFromField = new DatePicker();
    private final DatePicker effectiveToField = new DatePicker();
    private final TextArea restrictionField = new TextArea();
    private final CheckBox activeField = new CheckBox("Active");
    private final Button deleteUnused = new Button("Delete Unused");
    private final Button refresh = new Button("Refresh");
    private final SplitPane split = new SplitPane();
    private final PauseTransition stateSaveDelay = new PauseTransition(Duration.millis(350));

    private final CompanyUiPreferencesService preferencesService = UiServiceRegistry.companyUiPreferences();
    private final String companyCode = activeCompanyCode();
    private final CompanyUiFormat companyFormat;
    private final Map<String, String> savedState = new LinkedHashMap<>();
    private final Map<String, TableColumn<Fund, String>> columnsByKey = new LinkedHashMap<>();

    private Long editingFundId;
    private boolean populating;
    private boolean dirty;
    private boolean restoringState;
    private boolean suppressSelection;
    private String pendingDrillContext = "";

    public FundsPanel()
    {
        CompanyUiPreferences preferences = preferencesService.load(companyCode);
        companyFormat = new CompanyUiFormat(preferences);
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

        Label title = new Label("Funds");
        title.getStyleClass().add("panel-title");
        Label help = new Label("Edit funds by stable database identity. Referenced funds remain in history and are deactivated; only unused funds can be physically deleted.");
        help.setWrapText(true);

        Button add = new Button("New");
        add.setOnAction(event -> onNew());
        Button save = new Button("Save");
        save.setOnAction(event -> saveForm());
        UiPermissionGate.gate(add, ApplicationPermission.BOOKKEEPING_WRITE, "Create a fund");
        UiPermissionGate.gate(save, ApplicationPermission.BOOKKEEPING_WRITE, "Save a fund");
        UiPermissionGate.gate(deleteUnused, ApplicationPermission.BOOKKEEPING_WRITE, "Delete an unused fund");
        deleteUnused.setDisable(true);
        deleteUnused.setOnAction(event -> deleteUnusedFund());
        refresh.setOnAction(event -> reload(editingFundId));

        HBox actions = new HBox(8, add, save, deleteUnused, refresh);
        root.setTop(new VBox(6, title, help, actions, status));

        configureTable();
        Node editor = buildEditor();

        VBox tableRegion = new VBox(6, new Label("Fund list"), table);
        tableRegion.setPadding(new Insets(8, 8, 8, 0));
        tableRegion.setMinHeight(0.0);
        VBox.setVgrow(table, Priority.ALWAYS);

        ScrollPane editorScroll = new ScrollPane(editor);
        editorScroll.setId("fundsEditorScroll");
        editorScroll.setFitToWidth(true);
        editorScroll.setMinHeight(0.0);
        editorScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        editorScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        split.setId("fundsWorkspaceSplit");
        split.setOrientation(Orientation.VERTICAL);
        split.getItems().setAll(tableRegion, editorScroll);
        split.setDividerPositions(0.56);
        root.setCenter(split);

        installDirtyListeners();
    }

    private void configureTable()
    {
        table.setId("fundsTable");
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        CompanyTableStateBinder.markCompanyStateOwned(table);
        table.setPlaceholder(new Label("No funds found. Choose New to create a fund."));

        addColumn("code", "Code", Fund::getCode, 125);
        addColumn("name", "Name", Fund::getName, 210);
        addColumn("type", "Type", fund -> String.valueOf(fund.getFundType()), 145);
        addColumn("parent", "Parent", fund -> fund.getParent() == null ? "" : fund.getParent().getCode(), 125);
        addColumn("effectiveFrom", "Effective From", fund -> companyFormat.formatDate(fund.getEffectiveFrom()), 125);
        addColumn("effectiveTo", "Effective Through", fund -> companyFormat.formatDate(fund.getEffectiveTo()), 135);
        addColumn("active", "Active", fund -> fund.isActive() ? "Yes" : "No", 90);

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> {
            if (suppressSelection || newRow == null)
            {
                return;
            }
            if (dirty && editingFundId != null && !Objects.equals(editingFundId, newRow.getId()) && !confirmDiscard())
            {
                suppressSelection = true;
                table.getSelectionModel().select(oldRow);
                suppressSelection = false;
                return;
            }
            rebuildParentOptions(table.getItems(), newRow.getId());
            loadRowIntoForm(newRow);
        });
    }

    private void addColumn(String key,
                           String title,
                           java.util.function.Function<Fund, String> extractor,
                           double preferredWidth)
    {
        TableColumn<Fund, String> column = new TableColumn<>(title);
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
        typeField.getItems().setAll(FundType.values());
        parentField.setConverter(new StringConverter<>()
        {
            @Override
            public String toString(ParentOption option)
            {
                return option == null ? "" : option.label();
            }

            @Override
            public ParentOption fromString(String value)
            {
                return null;
            }
        });
        companyFormat.install(effectiveFromField);
        companyFormat.install(effectiveToField);
        restrictionField.setWrapText(true);
        restrictionField.setPrefRowCount(6);
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
        form.add(new Label("Type"), 0, 3);
        form.add(typeField, 1, 3);
        form.add(new Label("Parent fund"), 0, 4);
        form.add(parentField, 1, 4);
        form.add(new Label("Effective from"), 0, 5);
        form.add(effectiveFromField, 1, 5);
        form.add(new Label("Effective through"), 0, 6);
        form.add(effectiveToField, 1, 6);
        form.add(activeField, 1, 7);
        form.add(new Label("Restriction / purpose"), 0, 8);
        form.add(restrictionField, 1, 8);
        GridPane.setHgrow(codeField, Priority.ALWAYS);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(parentField, Priority.ALWAYS);
        GridPane.setHgrow(restrictionField, Priority.ALWAYS);

        Label lifecycle = new Label("Clearing Active and saving deactivates a referenced fund without removing historical transactions, budgets, assets, inventory, aliases, transfers, or child-fund relationships. Active child funds require an active parent hierarchy: deactivate or reparent active children before a parent, and reactivate parents before children.");
        lifecycle.setWrapText(true);
        VBox editor = new VBox(8, new Label("Fund editor"), form, lifecycle);
        editor.setPadding(new Insets(8));
        return editor;
    }

    private void installDirtyListeners()
    {
        codeField.textProperty().addListener((obs, oldValue, newValue) -> markDirty());
        nameField.textProperty().addListener((obs, oldValue, newValue) -> markDirty());
        typeField.valueProperty().addListener((obs, oldValue, newValue) -> markDirty());
        parentField.valueProperty().addListener((obs, oldValue, newValue) -> markDirty());
        effectiveFromField.valueProperty().addListener((obs, oldValue, newValue) -> markDirty());
        effectiveToField.valueProperty().addListener((obs, oldValue, newValue) -> markDirty());
        activeField.selectedProperty().addListener((obs, oldValue, newValue) -> markDirty());
        restrictionField.textProperty().addListener((obs, oldValue, newValue) -> markDirty());
    }

    private void markDirty()
    {
        if (!populating)
        {
            dirty = true;
        }
    }

    private void loadRowIntoForm(Fund row)
    {
        if (row == null)
        {
            return;
        }
        populating = true;
        try
        {
            editingFundId = row.getId();
            codeField.setText(row.getCode());
            nameField.setText(row.getName());
            typeField.setValue(row.getFundType());
            selectParent(row.getParent() == null ? null : row.getParent().getId());
            effectiveFromField.setValue(row.getEffectiveFrom());
            effectiveToField.setValue(row.getEffectiveTo());
            activeField.setSelected(row.isActive());
            restrictionField.setText(nullToBlank(row.getRestrictionText()));
            editMode.setText("Editing fund ID " + row.getId());
            deleteUnused.setDisable(false);
            dirty = false;
            status.setText("Editing fund " + row.getCode() + ". Save preserves its stable identity even when the code changes.");
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
            editingFundId = null;
            suppressSelection = true;
            table.getSelectionModel().clearSelection();
            suppressSelection = false;
            rebuildParentOptions(table.getItems(), null);
            codeField.clear();
            nameField.clear();
            typeField.getSelectionModel().clearSelection();
            selectParent(null);
            effectiveFromField.setValue(null);
            effectiveToField.setValue(null);
            activeField.setSelected(true);
            restrictionField.clear();
            editMode.setText("New fund");
            deleteUnused.setDisable(true);
            dirty = false;
            if (announce)
            {
                status.setText("New fund: enter details and choose Save.");
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
            ParentOption parent = parentField.getValue();
            FundCommand command = new FundCommand(
                    editingFundId,
                    codeField.getText(),
                    nameField.getText(),
                    typeField.getValue(),
                    activeField.isSelected(),
                    parent == null ? null : parent.id(),
                    effectiveFromField.getValue(),
                    effectiveToField.getValue(),
                    restrictionField.getText());
            Fund saved = UiServiceRegistry.fundAdmin().save(command);
            editingFundId = saved.getId();
            dirty = false;
            status.setText("Saved fund " + saved.getCode() + ".");
            reload(saved.getId());
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save fund: " + UiErrors.safeMessage(ex));
        }
    }

    private void deleteUnusedFund()
    {
        if (editingFundId == null)
        {
            status.setText("Select an existing fund before deleting.");
            return;
        }
        try
        {
            FundUsage usage = UiServiceRegistry.fundAdmin().usage(editingFundId);
            if (!usage.canDelete())
            {
                status.setText("This fund is referenced by " + usage.describeReferences()
                        + ". Clear Active and Save to preserve history.");
                return;
            }

            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Delete unused fund");
            confirmation.setHeaderText("Delete " + codeField.getText().trim() + " permanently?");
            confirmation.setContentText("The service found no ledger, budget, asset, inventory, alias, transfer, or child-fund references. This operation cannot be undone.");
            if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty())
            {
                status.setText("Fund deletion cancelled.");
                return;
            }

            UiServiceRegistry.fundAdmin().deleteUnused(editingFundId);
            status.setText("Deleted unused fund " + codeField.getText().trim() + ".");
            clearFormForNew(false);
            reload(null);
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not delete fund: " + UiErrors.safeMessage(ex));
        }
    }

    private void reload(Long reselectId)
    {
        refresh.setDisable(true);
        String incomingContext = DrillThroughCoordinator.consumeContext(AppPanelId.FUNDS);
        if (!incomingContext.isBlank())
        {
            pendingDrillContext = incomingContext;
        }
        status.setText(formatStatus("Loading funds..."));

        UiAsync.run("fund-load",
                () -> UiServiceRegistry.fundLookup().listAllFunds(),
                rows -> {
                    table.setItems(FXCollections.observableArrayList(rows));
                    rebuildParentOptions(rows, reselectId);
                    if (reselectId != null)
                    {
                        rows.stream()
                                .filter(row -> Objects.equals(row.getId(), reselectId))
                                .findFirst()
                                .ifPresent(table.getSelectionModel()::select);
                    }
                    else if (editingFundId == null)
                    {
                        dirty = false;
                    }
                    status.setText(formatStatus("Loaded " + rows.size() + " fund(s), including inactive funds."));
                    refresh.setDisable(false);
                },
                ex -> {
                    status.setText(formatStatus("Failed to load funds: " + UiErrors.safeMessage(ex)));
                    refresh.setDisable(false);
                });
    }

    private void rebuildParentOptions(List<Fund> rows, Long selectedFundId)
    {
        boolean previousPopulating = populating;
        populating = true;
        try
        {
            Long currentParent = parentField.getValue() == null ? null : parentField.getValue().id();
            List<ParentOption> options = new ArrayList<>();
            options.add(new ParentOption(null, "(no parent)"));
            rows.stream()
                    .filter(fund -> selectedFundId == null || !Objects.equals(fund.getId(), selectedFundId))
                    .sorted(Comparator.comparing(Fund::getCode, String.CASE_INSENSITIVE_ORDER))
                    .map(fund -> new ParentOption(fund.getId(), fund.getCode() + " — " + fund.getName()))
                    .forEach(options::add);
            parentField.getItems().setAll(options);
            selectParent(currentParent);
        }
        finally
        {
            populating = previousPopulating;
        }
    }

    private void selectParent(Long parentId)
    {
        parentField.getItems().stream()
                .filter(option -> Objects.equals(option.id(), parentId))
                .findFirst()
                .ifPresentOrElse(parentField::setValue, () -> parentField.setValue(
                        parentField.getItems().isEmpty() ? null : parentField.getItems().get(0)));
    }

    private boolean confirmDiscard()
    {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Discard fund edits");
        confirmation.setHeaderText("Discard unsaved Fund changes?");
        confirmation.setContentText("Choose Cancel to remain in the current editor.");
        return confirmation.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void restoreLayoutState()
    {
        restoringState = true;
        try
        {
            double divider = parseDouble(savedState.get(STATE_PREFIX + "divider"), 0.56);
            split.setDividerPositions(Math.max(0.20, Math.min(0.80, divider)));

            for (Map.Entry<String, TableColumn<Fund, String>> entry : columnsByKey.entrySet())
            {
                double width = parseDouble(savedState.get(STATE_PREFIX + "table.width." + entry.getKey()),
                        entry.getValue().getPrefWidth());
                entry.getValue().setPrefWidth(Math.max(entry.getValue().getMinWidth(), width));
            }

            String order = savedState.get(STATE_PREFIX + "table.order");
            if (order != null && !order.isBlank())
            {
                List<TableColumn<Fund, ?>> ordered = new ArrayList<>();
                for (String key : order.split(","))
                {
                    TableColumn<Fund, String> column = columnsByKey.get(key);
                    if (column != null && !ordered.contains(column))
                    {
                        ordered.add(column);
                    }
                }
                for (TableColumn<Fund, String> column : columnsByKey.values())
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
                    TableColumn<Fund, String> column = columnsByKey.get(pieces[0]);
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
        table.getColumns().addListener((ListChangeListener<TableColumn<Fund, ?>>) change -> queueLayoutStateSave());
        table.getSortOrder().addListener((ListChangeListener<TableColumn<Fund, ?>>) change -> queueLayoutStateSave());
        for (TableColumn<Fund, String> column : columnsByKey.values())
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
        for (Map.Entry<String, TableColumn<Fund, String>> entry : columnsByKey.entrySet())
        {
            state.put(STATE_PREFIX + "table.width." + entry.getKey(), Double.toString(entry.getValue().getWidth()));
        }
        state.put(STATE_PREFIX + "table.sort", table.getSortOrder().stream()
                .map(column -> String.valueOf(column.getUserData()) + ":"
                        + (column.getSortType() == TableColumn.SortType.DESCENDING ? "DESC" : "ASC"))
                .reduce((left, right) -> left + "," + right)
                .orElse(""));
        UiAsync.run("fund-layout-state-save",
                () -> {
                    preferencesService.saveState(companyCode, state);
                    return Boolean.TRUE;
                },
                ignored -> { },
                ex -> status.setText("Could not save Fund workspace layout: " + UiErrors.safeMessage(ex)));
    }

    private String formatStatus(String message)
    {
        if (pendingDrillContext == null || pendingDrillContext.isBlank())
        {
            return message;
        }
        String combined = message + " | " + pendingDrillContext;
        pendingDrillContext = "";
        return combined;
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
        return "Funds";
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
            status.setText("New fund cancelled; unsaved changes remain.");
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

    private record ParentOption(Long id, String label)
    {
    }
}
