package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.ApplicationPermission;
import org.nonprofitbookkeeping.service.BudgetCategoryCommand;
import org.nonprofitbookkeeping.service.BudgetCategoryView;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Company-owned, stable-ID Budget Category maintenance. */
public final class BudgetCategoriesPanel implements AppPanel
{
    private final VBox root = new VBox(8);
    private final TableView<BudgetCategoryView> table = new TableView<>();
    private final ObservableList<BudgetCategoryView> categories = FXCollections.observableArrayList();
    private final FilteredList<BudgetCategoryView> filtered = new FilteredList<>(categories);
    private final TextField search = new TextField();
    private final TextField code = new TextField();
    private final TextField name = new TextField();
    private final DatePicker effectiveFrom = new DatePicker();
    private final DatePicker effectiveTo = new DatePicker();
    private final TextArea description = new TextArea();
    private final CheckBox active = new CheckBox("Active");
    private final Label mode = new Label("New Budget Category");
    private final Label status = new Label("Ready.");
    private final Label lifecycle = new Label();
    private final Button save = new Button("Save");
    private final Button refresh = new Button("Refresh");
    private final BooleanSupplier discardConfirmation;
    private Long editingId;
    private boolean populating;
    private boolean dirty;
    private boolean suppressSelection;

    public BudgetCategoriesPanel()
    {
        this(BudgetCategoriesPanel::showDiscardConfirmation);
    }

    BudgetCategoriesPanel(BooleanSupplier discardConfirmation)
    {
        this.discardConfirmation = Objects.requireNonNull(discardConfirmation, "discardConfirmation");
        root.setPadding(new Insets(8));
        root.setMinSize(0, 0);
        build();
        reload(null);
        clearForm(false);
    }

    private void build()
    {
        Label title = new Label("Budget Categories");
        title.getStyleClass().add("panel-title");
        Label help = new Label("Maintain company-owned budget buckets used by Budget, Journal, Inventory and Event cost reporting. Categories are retained for history; clear Active and save to retire one.");
        help.setWrapText(true);

        Button newCategory = new Button("New Category");
        newCategory.setId("budgetCategoriesNew");
        newCategory.setOnAction(event -> onNew());
        save.setId("budgetCategoriesSave");
        save.setOnAction(event -> saveForm());
        refresh.setId("budgetCategoriesRefresh");
        refresh.setOnAction(event -> refreshForm());
        Button journal = new Button("Return to Journal");
        journal.setId("budgetCategoriesJournal");
        journal.setOnAction(event -> DrillThroughCoordinator.openPanelWithContext(AppPanelId.JOURNAL_PANE, ""));
        UiPermissionGate.gate(newCategory, ApplicationPermission.BOOKKEEPING_WRITE, "Create a Budget Category");
        UiPermissionGate.gate(save, ApplicationPermission.BOOKKEEPING_WRITE, "Save a Budget Category");
        UiPermissionGate.gate(code, ApplicationPermission.BOOKKEEPING_WRITE, "Edit a Budget Category code");
        UiPermissionGate.gate(name, ApplicationPermission.BOOKKEEPING_WRITE, "Edit a Budget Category name");
        UiPermissionGate.gate(active, ApplicationPermission.BOOKKEEPING_WRITE, "Change Budget Category lifecycle state");
        UiPermissionGate.gate(effectiveFrom, ApplicationPermission.BOOKKEEPING_WRITE, "Edit Budget Category dates");
        UiPermissionGate.gate(effectiveTo, ApplicationPermission.BOOKKEEPING_WRITE, "Edit Budget Category dates");
        UiPermissionGate.gate(description, ApplicationPermission.BOOKKEEPING_WRITE, "Edit Budget Category description");
        search.setId("budgetCategoriesSearch");
        search.setPromptText("Search code or name");
        search.textProperty().addListener((observable, oldValue, newValue) -> applyFilter());
        VBox header = new VBox(6, title, help, new javafx.scene.layout.FlowPane(8, 6, newCategory, save, refresh, journal), search, status, lifecycle);
        ScrollPane headerScroll = new ScrollPane(header);
        headerScroll.setId("budgetCategoriesHeaderScroll");
        headerScroll.setFitToWidth(true);
        headerScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        headerScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        headerScroll.setPrefHeight(175);
        headerScroll.setMinSize(0, 0);

        configureTable();
        Node editor = buildEditor();
        VBox tableRegion = new VBox(6, new Label("Categories — including inactive"), table);
        tableRegion.setMinSize(0, 0);
        VBox.setVgrow(table, Priority.ALWAYS);
        ScrollPane editorScroll = new ScrollPane(editor);
        editorScroll.setId("budgetCategoriesEditorScroll");
        editorScroll.setFitToWidth(true);
        editorScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        editorScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        editorScroll.setMinSize(0, 0);
        SplitPane workspace = new SplitPane(tableRegion, editorScroll);
        workspace.setId("budgetCategoriesWorkspaceSplit");
        workspace.setOrientation(Orientation.VERTICAL);
        workspace.setDividerPositions(0.58);
        workspace.setMinSize(0, 0);
        VBox.setVgrow(workspace, Priority.ALWAYS);
        root.getChildren().setAll(headerScroll, workspace);
        installDirtyListeners();
    }

    private void configureTable()
    {
        table.setId("budgetCategoriesTable");
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setItems(filtered);
        table.setPlaceholder(new Label("No matching Budget Categories."));
        addColumn("code", "Code", row -> row.code(), 150);
        addColumn("name", "Name", row -> row.name(), 280);
        addColumn("active", "Active", row -> row.active() ? "Yes" : "No", 90);
        addColumn("from", "Effective from", row -> date(row.effectiveFrom()), 130);
        addColumn("to", "Effective to", row -> date(row.effectiveTo()), 130);
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldRow, row) ->
        {
            if (suppressSelection || row == null)
            {
                return;
            }
            if (dirty && !Objects.equals(editingId, row.id()) && !confirmDiscard())
            {
                suppressSelection = true;
                table.getSelectionModel().select(oldRow);
                suppressSelection = false;
                status.setText("Selection cancelled; unsaved category changes remain.");
                return;
            }
            loadForm(row);
        });
    }

    private void addColumn(String id, String title, java.util.function.Function<BudgetCategoryView, String> value, double width)
    {
        TableColumn<BudgetCategoryView, String> column = new TableColumn<>(title);
        column.setId("budgetCategory" + id);
        column.setUserData(id);
        column.setCellValueFactory(row -> new SimpleStringProperty(value.apply(row.getValue())));
        column.setPrefWidth(width);
        column.setMinWidth(72);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        table.getColumns().add(column);
    }

    private Node buildEditor()
    {
        code.setId("budgetCategoriesCode");
        name.setId("budgetCategoriesName");
        effectiveFrom.setId("budgetCategoriesEffectiveFrom");
        effectiveTo.setId("budgetCategoriesEffectiveTo");
        description.setId("budgetCategoriesDescription");
        active.setId("budgetCategoriesActive");
        CompanyUiFormat format = CompanyUiFormat.activeCompany();
        format.install(effectiveFrom);
        format.install(effectiveTo);
        description.setPrefRowCount(4);
        GridPane fields = new GridPane();
        fields.setHgap(10);
        fields.setVgap(8);
        fields.add(new Label("Mode"), 0, 0); fields.add(mode, 1, 0);
        fields.add(new Label("Code"), 0, 1); fields.add(code, 1, 1);
        fields.add(new Label("Name"), 0, 2); fields.add(name, 1, 2);
        fields.add(new Label("Effective from"), 0, 3); fields.add(effectiveFrom, 1, 3);
        fields.add(new Label("Effective to"), 0, 4); fields.add(effectiveTo, 1, 4);
        fields.add(new Label("Description"), 0, 5); fields.add(description, 1, 5);
        fields.add(active, 1, 6);
        GridPane.setHgrow(code, Priority.ALWAYS); GridPane.setHgrow(name, Priority.ALWAYS); GridPane.setHgrow(description, Priority.ALWAYS);
        Label lifecycleHelp = new Label("Categories with Journal, Budget, alias or import history are never physically deleted. Clear Active and save to preserve those references while removing the category from new choices.");
        lifecycleHelp.setWrapText(true);
        VBox editor = new VBox(8, new Label("Budget Category editor"), fields, lifecycleHelp);
        editor.setPadding(new Insets(8));
        editor.setMinSize(0, 0);
        return editor;
    }

    private void installDirtyListeners()
    {
        code.textProperty().addListener((observable, oldValue, newValue) -> markDirty());
        name.textProperty().addListener((observable, oldValue, newValue) -> markDirty());
        effectiveFrom.valueProperty().addListener((observable, oldValue, newValue) -> markDirty());
        effectiveTo.valueProperty().addListener((observable, oldValue, newValue) -> markDirty());
        description.textProperty().addListener((observable, oldValue, newValue) -> markDirty());
        active.selectedProperty().addListener((observable, oldValue, newValue) -> markDirty());
    }

    private void markDirty()
    {
        if (!populating)
        {
            dirty = true;
        }
    }

    private void loadForm(BudgetCategoryView row)
    {
        populating = true;
        try
        {
            editingId = row.id(); code.setText(row.code()); name.setText(row.name()); active.setSelected(row.active());
            effectiveFrom.setValue(row.effectiveFrom()); effectiveTo.setValue(row.effectiveTo()); description.setText(row.description() == null ? "" : row.description());
            mode.setText("Editing " + row.code() + " — " + row.name()); dirty = false;
            lifecycle.setText(row.active() ? "Active: available for new Journal, Budget and Inventory choices." : "Inactive: retained history, omitted from new choices.");
            status.setText("Loaded " + row.code() + ".");
        }
        finally { populating = false; }
    }

    private void clearForm(boolean announce)
    {
        populating = true;
        try
        {
            editingId = null; table.getSelectionModel().clearSelection(); code.clear(); name.clear(); effectiveFrom.setValue(null); effectiveTo.setValue(null); description.clear(); active.setSelected(true); mode.setText("New Budget Category"); dirty = false;
            lifecycle.setText("New categories have no history until used by a canonical Budget or transaction split.");
            if (announce) status.setText("Enter a distinct code and name, then choose Save.");
        }
        finally { populating = false; }
    }

    private void saveForm()
    {
        try
        {
            BudgetCategoryView saved = UiServiceRegistry.budgetCategoryAdmin().save(new BudgetCategoryCommand(editingId, code.getText(), name.getText(), active.isSelected(), effectiveFrom.getValue(), effectiveTo.getValue(), description.getText()));
            dirty = false; editingId = saved.id(); status.setText("Saved Budget Category " + saved.code() + "."); reload(saved.id());
        }
        catch (RuntimeException ex) { status.setText("Could not save Budget Category: " + UiErrors.safeMessage(ex)); }
    }

    private void reload(Long selectId)
    {
        try
        {
            List<BudgetCategoryView> values = UiServiceRegistry.budgetCategoryLookup().listForMaintenance();
            categories.setAll(values); applyFilter();
            if (selectId != null) filtered.stream().filter(row -> selectId.equals(row.id())).findFirst().ifPresent(row -> { table.getSelectionModel().select(row); loadForm(row); });
        }
        catch (RuntimeException ex) { status.setText("Could not load Budget Categories: " + UiErrors.safeMessage(ex)); }
    }

    private void refreshForm()
    {
        if (dirty)
        {
            status.setText("Unsaved category changes remain; save or choose New Category before refreshing.");
            return;
        }
        reload(editingId);
    }

    private boolean confirmDiscard()
    {
        return discardConfirmation.getAsBoolean();
    }

    private static boolean showDiscardConfirmation()
    {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Discard Budget Category edits");
        confirmation.setHeaderText("Discard unsaved Budget Category changes?");
        confirmation.setContentText("Choose Cancel to remain in the current editor.");
        return confirmation.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void applyFilter()
    {
        String needle = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        filtered.setPredicate(row -> needle.isBlank() || row.code().toLowerCase(Locale.ROOT).contains(needle) || row.name().toLowerCase(Locale.ROOT).contains(needle));
    }

    private static String date(LocalDate value) { return value == null ? "" : value.toString(); }

    @Override public String title() { return "Budget Categories"; }
    @Override public Node root() { return root; }
    @Override public java.util.Optional<ApplicationPermission> requiredPermission(AppCommand command)
    {
        return switch (command) { case NEW_ACTIVE, SAVE_ACTIVE -> java.util.Optional.of(ApplicationPermission.BOOKKEEPING_WRITE); default -> java.util.Optional.empty(); };
    }
    @Override public java.util.Set<AppCommand> commandCapabilities() { return AppPanel.capabilities(AppCommand.NEW_ACTIVE, AppCommand.SAVE_ACTIVE); }
    @Override public void onNew()
    {
        if (!dirty || confirmDiscard())
        {
            clearForm(true);
        }
        else
        {
            status.setText("New Budget Category cancelled; unsaved changes remain.");
        }
    }
    @Override public void onSave() { saveForm(); }
    @Override public void onPanelShown() { refreshForm(); }
    @Override public String commandResultMessage(AppCommand command) { return status.getText(); }
    @Override public boolean hasUnsavedChanges() { return dirty; }
}
