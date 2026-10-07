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
import javafx.scene.control.ComboBox;
import org.nonprofitbookkeeping.model.CounterpartyKind;
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
import org.nonprofitbookkeeping.service.CounterpartyCommand;
import org.nonprofitbookkeeping.service.CounterpartyView;

import javafx.collections.transformation.SortedList;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Company-owned, stable-ID Counterparty maintenance. */
public final class CounterpartiesPanel implements AppPanel
{
    private final VBox root = new VBox(8);
    private final TableView<CounterpartyView> table = new TableView<>();
    private final ObservableList<CounterpartyView> parties = FXCollections.observableArrayList();
    private final FilteredList<CounterpartyView> filtered = new FilteredList<>(parties);
    private final TextField search = new TextField();
    private final TextField name = new TextField();
    private final ComboBox<CounterpartyKind> kind = new ComboBox<>(FXCollections.observableArrayList(CounterpartyKind.values()));
    private final TextField email = new TextField();
    private final TextField phone = new TextField();
    private final TextArea notes = new TextArea();
    private final CheckBox active = new CheckBox("Active");
    private final Label mode = new Label("New Counterparty");
    private final Label status = new Label("Ready.");
    private final Label lifecycle = new Label();
    private final Button save = new Button("Save");
    private final Button refresh = new Button("Refresh");
    private final BooleanSupplier discardConfirmation;
    private final SplitPane workspace = new SplitPane();
    private final SplitPane mainSplit = new SplitPane();
    private final PauseTransition layoutDelay = new PauseTransition(Duration.millis(250));
    private Long editingId;
    private boolean populating;
    private boolean dirty;
    private boolean suppressSelection;

    public CounterpartiesPanel()
    {
        this(CounterpartiesPanel::showDiscardConfirmation);
    }

    CounterpartiesPanel(BooleanSupplier discardConfirmation)
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
        Label title = new Label("Payees / Counterparties");
        title.getStyleClass().add("panel-title");
        Label help = new Label("Maintain company-owned payees used by Journal. Counterparties are retained for history; clear Active and save to retire one.");
        help.setWrapText(true);

        Button newCounterparty = new Button("New Payee");
        newCounterparty.setId("counterpartiesNew");
        newCounterparty.setOnAction(event -> onNew());
        save.setId("counterpartiesSave");
        save.setOnAction(event -> saveForm());
        refresh.setId("counterpartiesRefresh");
        refresh.setOnAction(event -> refreshForm());
        Button journal = new Button("Return to Journal");
        journal.setId("counterpartiesJournal");
        journal.setOnAction(event -> DrillThroughCoordinator.openPanelWithContext(AppPanelId.JOURNAL_PANE, ""));
        UiPermissionGate.gate(newCounterparty, ApplicationPermission.BOOKKEEPING_WRITE, "Create a Counterparty");
        UiPermissionGate.gate(save, ApplicationPermission.BOOKKEEPING_WRITE, "Save a Counterparty");
        UiPermissionGate.gate(name, ApplicationPermission.BOOKKEEPING_WRITE, "Edit a Counterparty name");
        UiPermissionGate.gate(active, ApplicationPermission.BOOKKEEPING_WRITE, "Change Counterparty lifecycle state");
        UiPermissionGate.gate(kind, ApplicationPermission.BOOKKEEPING_WRITE, "Edit Counterparty kind");
        UiPermissionGate.gate(email, ApplicationPermission.BOOKKEEPING_WRITE, "Edit Counterparty email");
        UiPermissionGate.gate(phone, ApplicationPermission.BOOKKEEPING_WRITE, "Edit Counterparty phone");
        UiPermissionGate.gate(notes, ApplicationPermission.BOOKKEEPING_WRITE, "Edit Counterparty notes");
        search.setId("counterpartiesSearch");
        search.setPromptText("Search name, email or phone");
        search.textProperty().addListener((observable, oldValue, newValue) -> applyFilter());
        Button edit = new Button("Edit Selected");
        edit.setId("counterpartiesEdit");
        edit.setOnAction(event ->
        {
            CounterpartyView row = table.getSelectionModel().getSelectedItem();
            if (row != null && (!dirty || confirmDiscard()))
            {
                loadForm(row);
            }
        });
        VBox header = new VBox(6, title, help, new javafx.scene.layout.FlowPane(8, 6, newCounterparty, edit, save, refresh, journal), search, status, lifecycle);
        ScrollPane headerScroll = new ScrollPane(header);
        headerScroll.setId("counterpartiesHeaderScroll");
        headerScroll.setFitToWidth(true);
        headerScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        headerScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        headerScroll.setPrefHeight(175);
        headerScroll.setMinSize(0, 0);

        configureTable();
        Node editor = buildEditor();
        VBox tableRegion = new VBox(6, new Label("Counterparties — including inactive"), table);
        tableRegion.setMinSize(0, 0);
        VBox.setVgrow(table, Priority.ALWAYS);
        ScrollPane editorScroll = new ScrollPane(editor);
        editorScroll.setId("counterpartiesEditorScroll");
        editorScroll.setFitToWidth(true);
        editorScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        editorScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        editorScroll.setMinSize(0, 0);
        workspace.getItems().setAll(tableRegion, editorScroll);
        workspace.setId("counterpartiesWorkspaceSplit");
        workspace.setOrientation(Orientation.VERTICAL);
        workspace.setDividerPositions(0.58);
        workspace.setMinSize(0, 0);
        VBox.setVgrow(workspace, Priority.ALWAYS);
        mainSplit.getItems().setAll(headerScroll, workspace);
        mainSplit.setOrientation(Orientation.VERTICAL);
        mainSplit.setId("counterpartiesMainSplit");
        mainSplit.setMinSize(0, 0);
        mainSplit.setDividerPositions(0.25);
        VBox.setVgrow(mainSplit, Priority.ALWAYS);
        root.getChildren().setAll(mainSplit);
        String company = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
        if (company == null || company.isBlank())
        {
            company = "DEFAULT";
        }
        final String layoutCompany = company;
        var preferences = UiServiceRegistry.companyUiPreferences();
        Map<String, String> state = preferences.loadState(layoutCompany, "ui.counterparties.divider.");
        restoreDivider(workspace, state.get("ui.counterparties.divider.workspace"), 0.58);
        restoreDivider(mainSplit, state.get("ui.counterparties.divider.main"), 0.25);
        layoutDelay.setOnFinished(event ->
        {
            try
            {
                preferences.saveState(layoutCompany, Map.of(
                        "ui.counterparties.divider.workspace", Double.toString(workspace.getDividerPositions()[0]),
                        "ui.counterparties.divider.main", Double.toString(mainSplit.getDividerPositions()[0])));
            }
            catch (RuntimeException ex)
            {
                status.setText("Could not save layout: " + UiErrors.safeMessage(ex));
            }
        });
        workspace.getDividers().get(0).positionProperty().addListener((obs, oldValue, value) -> layoutDelay.playFromStart());
        mainSplit.getDividers().get(0).positionProperty().addListener((obs, oldValue, value) -> layoutDelay.playFromStart());
        installDirtyListeners();
        FullTextTooltipInstaller.install(root);
    }

    private void configureTable()
    {
        table.setId("counterpartiesTable");
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        SortedList<CounterpartyView> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setMinSize(0, 0);
        table.setPlaceholder(new Label("No matching Payees / Counterparties."));
        addColumn("name", "Name", row -> row.name(), 280);
        addColumn("active", "Active", row -> row.active() ? "Yes" : "No", 90);
        addColumn("kind", "Kind", row -> row.kind().name(), 100);
        addColumn("email", "Email", row -> row.email(), 230);
        addColumn("phone", "Phone", row -> row.phone(), 150);
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldRow, row) ->
        {
            if (suppressSelection || row == null)
            {
                return;
            }
            if (dirty && !confirmDiscard())
            {
                suppressSelection = true;
                table.getSelectionModel().select(oldRow);
                suppressSelection = false;
                status.setText("Selection cancelled; unsaved party changes remain.");
                return;
            }
            loadForm(row);
        });
    }

    private void addColumn(String id, String title, java.util.function.Function<CounterpartyView, String> value, double width)
    {
        TableColumn<CounterpartyView, String> column = new TableColumn<>(title);
        column.setId("counterparty" + id);
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
        name.setId("counterpartiesName");
        kind.setId("counterpartiesKind");
        email.setId("counterpartiesEmail");
        phone.setId("counterpartiesPhone");
        notes.setId("counterpartiesNotes");
        active.setId("counterpartiesActive");
        notes.setPrefRowCount(4);
        GridPane fields = new GridPane();
        fields.setHgap(10);
        fields.setVgap(8);
        fields.add(new Label("Mode"), 0, 0);
        fields.add(mode, 1, 0);
        fields.add(new Label("Name"), 0, 1);
        fields.add(name, 1, 1);
        fields.add(new Label("Kind"), 0, 2);
        fields.add(kind, 1, 2);
        fields.add(new Label("Email"), 0, 3);
        fields.add(email, 1, 3);
        fields.add(new Label("Phone"), 0, 4);
        fields.add(phone, 1, 4);
        fields.add(new Label("Notes"), 0, 5);
        fields.add(notes, 1, 5);
        fields.add(active, 1, 6);
        GridPane.setHgrow(name, Priority.ALWAYS);
        GridPane.setHgrow(email, Priority.ALWAYS);
        GridPane.setHgrow(phone, Priority.ALWAYS);
        GridPane.setHgrow(notes, Priority.ALWAYS);
        Label lifecycleHelp = new Label("Counterparties are retained to preserve Journal, contact, audit and import history. Clear Active and save to preserve those references while removing the party from new choices.");
        lifecycleHelp.setWrapText(true);
        VBox editor = new VBox(8, new Label("Counterparty editor"), fields, lifecycleHelp);
        editor.setPadding(new Insets(8));
        editor.setMinSize(javafx.scene.layout.Region.USE_COMPUTED_SIZE, javafx.scene.layout.Region.USE_COMPUTED_SIZE);
        return editor;
    }

    private void installDirtyListeners()
    {
        name.textProperty().addListener((observable, oldValue, newValue) -> markDirty());
        kind.valueProperty().addListener((observable, oldValue, newValue) -> markDirty());
        email.textProperty().addListener((observable, oldValue, newValue) -> markDirty());
        phone.textProperty().addListener((observable, oldValue, newValue) -> markDirty());
        notes.textProperty().addListener((observable, oldValue, newValue) -> markDirty());
        active.selectedProperty().addListener((observable, oldValue, newValue) -> markDirty());
    }

    private void markDirty()
    {
        if (!populating)
        {
            dirty = true;
        }
    }

    private void loadForm(CounterpartyView row)
    {
        populating = true;
        try
        {
            editingId = row.id();
            name.setText(row.name());
            active.setSelected(row.active());
            kind.setValue(row.kind());
            email.setText(row.email());
            phone.setText(row.phone());
            notes.setText(row.notes() == null ? "" : row.notes());
            mode.setText("Editing " + row.name());
            dirty = false;
            lifecycle.setText(row.active() ? "Active: available for new Journal choices." : "Inactive: retained history, omitted from new choices.");
            status.setText("Loaded " + row.name() + ".");
        }
        finally
        {
            populating = false;
        }
    }

    private void clearForm(boolean announce)
    {
        populating = true;
        try
        {
            editingId = null;
            table.getSelectionModel().clearSelection();
            name.clear();
            kind.setValue(CounterpartyKind.OTHER);
            email.clear();
            phone.clear();
            notes.clear();
            active.setSelected(true);
            mode.setText("New Counterparty");
            dirty = false;
            lifecycle.setText("New parties have no history until used by a canonical transaction.");
            if (announce)
            {
                status.setText("Enter a distinct name, then choose Save.");
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
            CounterpartyView saved = UiServiceRegistry.counterpartyAdmin().save(new CounterpartyCommand(
                    editingId, name.getText(), kind.getValue(), email.getText(), phone.getText(), notes.getText(),
                    active.isSelected()));
            loadForm(saved);
            reload(saved.id());
            status.setText("Saved Counterparty " + saved.name() + ".");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save Counterparty: " + UiErrors.safeMessage(ex));
        }
    }

    private void reload(Long selectId)
    {
        try
        {
            List<CounterpartyView> values = UiServiceRegistry.counterpartyAdmin().listForMaintenance();
            suppressSelection = true;
            try
            {
                parties.setAll(values);
            }
            finally
            {
                suppressSelection = false;
            }
            applyFilter();
            if (selectId != null)
            {
                values.stream().filter(row -> selectId.equals(row.id())).findFirst().ifPresent(row ->
                {
                    table.getSelectionModel().select(row);
                    loadForm(row);
                });
            }
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not load Payees / Counterparties: " + UiErrors.safeMessage(ex));
        }
    }

    private void refreshForm()
    {
        if (dirty)
        {
            status.setText("Unsaved party changes remain; save or choose New Payee before refreshing.");
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
        confirmation.setTitle("Discard Counterparty edits");
        confirmation.setHeaderText("Discard unsaved Counterparty changes?");
        confirmation.setContentText("Choose Cancel to remain in the current editor.");
        return confirmation.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void applyFilter()
    {
        String needle = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        suppressSelection = true;
        try
        {
            filtered.setPredicate(row -> needle.isBlank() || matches(row.name(), needle)
                    || matches(row.email(), needle) || matches(row.phone(), needle));
        }
        finally
        {
            suppressSelection = false;
        }
    }

    private static boolean matches(String value, String needle)
    {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static void restoreDivider(SplitPane split, String saved, double fallback)
    {
        double value = fallback;
        if (saved != null)
        {
            try
            {
                value = Double.parseDouble(saved);
            }
            catch (NumberFormatException invalidState)
            {
                value = fallback;
            }
        }
        split.setDividerPositions(Double.isFinite(value) ? Math.max(0.1, Math.min(0.9, value)) : fallback);
    }

    @Override
    public String title()
    {
        return "Payees / Counterparties";
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
            clearForm(true);
        }
        else
        {
            status.setText("New Counterparty cancelled; unsaved changes remain.");
        }
    }
    @Override
    public void onSave()
    {
        saveForm();
    }
    @Override
    public void onPanelShown()
    {
        refreshForm();
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
