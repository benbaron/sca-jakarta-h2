package org.nonprofitbookkeeping.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.nonprofitbookkeeping.model.CorrectionMethod;
import org.nonprofitbookkeeping.service.TransactionCommand;
import org.nonprofitbookkeeping.service.TransactionCommandValidator;
import org.nonprofitbookkeeping.service.TransactionEntryService;
import org.nonprofitbookkeeping.service.TransactionLineCommand;
import org.nonprofitbookkeeping.service.TransactionSupplementalLineCommand;
import org.nonprofitbookkeeping.service.TransactionSupplementalLineView;
import org.nonprofitbookkeeping.service.TransactionValidationResult;
import org.nonprofitbookkeeping.service.TransactionView;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

/**
 * Unified Journal workspace derived from the donor JournalPanelFX and
 * JournalEntryWorkspaceFX interaction model, backed only by current H2 services.
 */
public final class JournalWorkspacePanel implements AppPanel
{
    private static final String JOURNAL_TABLE_ID = "journalWorkspaceJournalTable";
    private static final String LINE_TABLE_ID = "journalWorkspaceEntryLineTable";
    private static final Preferences VIEW_STATE = Preferences.userNodeForPackage(JournalWorkspacePanel.class)
            .node("company-journal-workspace");

    private final BorderPane root = new BorderPane();
    private final TableView<JournalTransactionRow> journalTable = new TableView<>();
    private final TableView<EditorLine> lineTable = new TableView<>();
    private final DatePicker fromDate = new DatePicker();
    private final DatePicker toDate = new DatePicker();
    private final TextField searchText = new TextField();
    private final DatePicker entryDate = new DatePicker();
    private final TextArea memoArea = new TextArea();
    private final ComboBox<TransactionLineEditorModel.Option> payeeBox = new ComboBox<>();
    private final ComboBox<TransactionLineEditorModel.Option> bankAccountBox = new ComboBox<>();
    private final Label status = new Label("Journal is ready.");
    private final Label editorMode = new Label("New journal entry");
    private final Label transactionIdentity = new Label("Unsaved");
    private final Label transactionStatus = new Label("ENTERED after save");
    private final Label debitTotal = new Label("$0.00");
    private final Label creditTotal = new Label("$0.00");
    private final Label differenceTotal = new Label("$0.00");
    private final Label balanceStatus = new Label("Needs attention");
    private final Label validationMessage = new Label("Enter at least two meaningful, balanced lines.");
    private final Button editButton = new Button("Edit Selected");
    private final Button saveButton = new Button("Save Entry");
    private final Button deleteButton = new Button("Delete");
    private final Button removeLineButton = new Button("Remove Line");
    private final Button drillReconciliationButton = new Button("Open Selected Line Reconciliation");
    private final SplitPane outerSplit = new SplitPane();
    private final SplitPane editorSplit = new SplitPane();
    private final SplitPane detailSplit = new SplitPane();
    private final TabPane supplementalTabs = new TabPane();
    private final Map<SupplementalKind, CheckBox> supplementalSelections = new EnumMap<>(SupplementalKind.class);
    private final Map<SupplementalKind, TableView<SupplementalRow>> supplementalTables = new EnumMap<>(SupplementalKind.class);
    private final Map<SupplementalKind, Tab> supplementalTabIndex = new EnumMap<>(SupplementalKind.class);
    private final TransactionCommandValidator commandValidator = new TransactionCommandValidator();

    private TransactionLineEditorModel.ReferenceData referenceData = emptyReferenceData();
    private Long editTransactionId;
    private Long requestedTransactionId;
    private boolean dirty;
    private boolean loading;
    private boolean restoringTableState;

    public JournalWorkspacePanel()
    {
        root.setPadding(new Insets(8));
        root.getStyleClass().add("journal-workspace");
        root.setTop(buildWorkspaceHeader());

        buildJournalTable();
        buildLineTable();

        Node journalRegion = buildJournalRegion();
        Node entryHeaderRegion = buildEntryHeaderRegion();
        Node lineRegion = buildLineRegion();
        Node additionalDetails = buildAdditionalDetailsRegion();
        Node supplementalDetails = buildSupplementalRegion();

        detailSplit.setId("journalWorkspaceDetailSplit");
        detailSplit.setOrientation(Orientation.HORIZONTAL);
        detailSplit.getItems().setAll(additionalDetails, supplementalDetails);
        detailSplit.setDividerPositions(0.36);
        detailSplit.setMinSize(0, 0);

        editorSplit.setId("journalWorkspaceEditorSplit");
        editorSplit.setOrientation(Orientation.VERTICAL);
        editorSplit.getItems().setAll(entryHeaderRegion, lineRegion, detailSplit);
        editorSplit.setDividerPositions(0.22, 0.64);
        editorSplit.setMinSize(0, 0);

        outerSplit.setId("journalWorkspaceOuterSplit");
        outerSplit.setOrientation(Orientation.VERTICAL);
        outerSplit.getItems().setAll(journalRegion, editorSplit);
        outerSplit.setDividerPositions(0.43);
        outerSplit.setMinSize(0, 0);
        root.setCenter(outerSplit);

        installDividerState(outerSplit, "outer", 0.43);
        installDividerState(editorSplit, "editor", 0.22, 0.64);
        installDividerState(detailSplit, "detail", 0.36);
        configureEditorListeners();
        loadReferenceData();
        startNew(false);
    }

    private Node buildWorkspaceHeader()
    {
        Label title = new Label("Journal");
        title.getStyleClass().add("panel-title");
        Label caption = new Label("Review complete transactions and create or edit the selected journal entry in one workspace.");

        searchText.setPromptText("Memo or payee");
        searchText.setPrefColumnCount(18);
        Button applyFilters = new Button("Apply Filters");
        Button clearFilters = new Button("Clear Filters");
        Button newButton = new Button("New Entry");
        Button refreshButton = new Button("Refresh");

        newButton.setId("journalWorkspaceNewButton");
        editButton.setId("journalWorkspaceEditButton");
        saveButton.setId("journalWorkspaceSaveButton");
        deleteButton.setId("journalWorkspaceDeleteButton");
        refreshButton.setId("journalWorkspaceRefreshButton");
        status.setId("journalWorkspaceStatusLabel");

        HBox filters = new HBox(8,
                new Label("From"), fromDate,
                new Label("To"), toDate,
                new Label("Search"), searchText,
                applyFilters,
                clearFilters);
        filters.setAlignment(Pos.CENTER_LEFT);

        deleteButton.setText(deleteActionLabel());
        HBox actions = new HBox(8, newButton, editButton, saveButton, deleteButton, refreshButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        applyFilters.setOnAction(event -> reloadJournal());
        clearFilters.setOnAction(event -> {
            fromDate.setValue(null);
            toDate.setValue(null);
            searchText.clear();
            reloadJournal();
        });
        newButton.setOnAction(event -> startNew(true));
        editButton.setOnAction(event -> editSelectedTransaction());
        saveButton.setOnAction(event -> saveCurrentEntry());
        deleteButton.setOnAction(event -> deleteOrReverseCurrentTransaction());
        refreshButton.setOnAction(event -> reloadJournal());
        searchText.setOnAction(event -> reloadJournal());

        VBox header = new VBox(6, title, caption, filters, actions, status, new Separator());
        return header;
    }

    private Node buildJournalRegion()
    {
        Label heading = sectionHeading("Journal Transactions");
        Label help = new Label("One row represents one canonical transaction. Double-click a row to load it into the editor below.");
        VBox region = new VBox(6, heading, help, journalTable);
        region.setPadding(new Insets(4, 0, 4, 0));
        region.setMinSize(0, 0);
        VBox.setVgrow(journalTable, Priority.ALWAYS);
        return region;
    }

    private void buildJournalTable()
    {
        journalTable.setId(JOURNAL_TABLE_ID);
        journalTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        journalTable.setFixedCellSize(-1);
        journalTable.setPlaceholder(new Label("No journal transactions match the current filters."));
        journalTable.getColumns().add(journalColumn("Date", "date", 110, JournalTransactionRow::date));
        journalTable.getColumns().add(multilineJournalColumn("Account Title and Description", "accounts", 340, JournalTransactionRow::accounts));
        journalTable.getColumns().add(multilineJournalColumn("Fund", "funds", 180, JournalTransactionRow::funds));
        journalTable.getColumns().add(journalColumn("Cleared", "cleared", 120, JournalTransactionRow::cleared));
        journalTable.getColumns().add(multilineJournalColumn("Debit", "debits", 120, JournalTransactionRow::debits));
        journalTable.getColumns().add(multilineJournalColumn("Credit", "credits", 120, JournalTransactionRow::credits));
        journalTable.getColumns().add(journalColumn("Transaction ID", "transactionId", 110, row -> String.valueOf(row.transactionId())));
        journalTable.getColumns().add(journalColumn("Supplemental", "supplemental", 140, JournalTransactionRow::supplemental));
        journalTable.getColumns().add(multilineJournalColumn("Memo / Details", "details", 320, JournalTransactionRow::details));
        restoreTableState(journalTable, JOURNAL_TABLE_ID);
        installTableStatePersistence(journalTable, JOURNAL_TABLE_ID);

        journalTable.setRowFactory(table -> {
            TableRow<JournalTransactionRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty())
                {
                    loadTransactionIntoEditor(row.getItem().transactionId(), true);
                }
            });
            return row;
        });
        journalTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> updateActionState());
    }

    private TableColumn<JournalTransactionRow, String> journalColumn(
            String title,
            String key,
            double width,
            Function<JournalTransactionRow, String> getter)
    {
        TableColumn<JournalTransactionRow, String> column = configuredColumn(title, key, width);
        column.setCellValueFactory(value -> new SimpleStringProperty(getter.apply(value.getValue())));
        return column;
    }

    private TableColumn<JournalTransactionRow, String> multilineJournalColumn(
            String title,
            String key,
            double width,
            Function<JournalTransactionRow, String> getter)
    {
        TableColumn<JournalTransactionRow, String> column = journalColumn(title, key, width, getter);
        column.setCellFactory(ignored -> new TableCell<>()
        {
            private final Label label = new Label();

            {
                label.setWrapText(false);
                label.setMaxWidth(Double.MAX_VALUE);
                label.setAlignment(Pos.TOP_LEFT);
            }

            @Override
            protected void updateItem(String item, boolean empty)
            {
                super.updateItem(item, empty);
                if (empty)
                {
                    setGraphic(null);
                    setText(null);
                }
                else
                {
                    label.setText(item == null ? "" : item);
                    setGraphic(label);
                    setText(null);
                }
            }
        });
        return column;
    }

    private Node buildEntryHeaderRegion()
    {
        editorMode.getStyleClass().add("panel-title");
        memoArea.setWrapText(true);
        memoArea.setPrefRowCount(2);
        memoArea.setMaxWidth(Double.MAX_VALUE);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.setPadding(new Insets(6));
        ColumnConstraints labelColumn = new ColumnConstraints(70);
        ColumnConstraints dateColumn = new ColumnConstraints(180);
        ColumnConstraints memoLabelColumn = new ColumnConstraints(55);
        ColumnConstraints memoColumn = new ColumnConstraints();
        memoColumn.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().setAll(labelColumn, dateColumn, memoLabelColumn, memoColumn);
        form.add(new Label("Date"), 0, 0);
        form.add(entryDate, 1, 0);
        form.add(new Label("Memo"), 2, 0);
        form.add(memoArea, 3, 0);
        GridPane.setHgrow(memoArea, Priority.ALWAYS);

        HBox totals = new HBox(18,
                labelledValue("Debit", debitTotal),
                labelledValue("Credit", creditTotal),
                labelledValue("Difference", differenceTotal),
                balanceStatus);
        totals.setAlignment(Pos.CENTER_LEFT);
        validationMessage.setWrapText(true);

        VBox content = new VBox(8, editorMode, form, totals, validationMessage);
        content.setPadding(new Insets(6));
        content.setMinSize(0, 0);
        ScrollPane scroll = scrollable(content, true);
        scroll.setId("journalWorkspaceEntryHeaderScroll");
        return scroll;
    }

    private Node buildLineRegion()
    {
        Button addLine = new Button("Add Line");
        Button duplicateLine = new Button("Duplicate Line");
        addLine.setOnAction(event -> addEditorLine(null));
        duplicateLine.setOnAction(event -> duplicateSelectedLine());
        drillReconciliationButton.setOnAction(event -> openSelectedLineReconciliation());
        removeLineButton.setOnAction(event -> removeSelectedLine());
        ToolBar tools = new ToolBar(addLine, duplicateLine, removeLineButton, drillReconciliationButton);
        VBox region = new VBox(6, sectionHeading("Entry Lines"), tools, lineTable);
        region.setPadding(new Insets(4));
        region.setMinSize(0, 0);
        VBox.setVgrow(lineTable, Priority.ALWAYS);
        return region;
    }

    private void buildLineTable()
    {
        lineTable.setId(LINE_TABLE_ID);
        lineTable.setEditable(true);
        lineTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        lineTable.setPlaceholder(new Label("Use Add Line to create accounting lines."));

        lineTable.getColumns().add(optionColumn("Account", "account", 250,
                EditorLine::accountProperty,
                () -> referenceData.accounts()));
        lineTable.getColumns().add(optionColumn("Fund", "fund", 185,
                EditorLine::fundProperty,
                () -> referenceData.funds()));
        lineTable.getColumns().add(readOnlyTextColumn("Bank state", "bankState", 115,
                EditorLine::bankStateProperty));
        lineTable.getColumns().add(readOnlyTextColumn("Cleared on", "clearedOn", 120,
                EditorLine::clearedOnProperty));
        lineTable.getColumns().add(optionColumn("Budget", "budget", 170,
                EditorLine::budgetProperty,
                () -> referenceData.budgetCategories()));
        lineTable.getColumns().add(moneyColumn("Debit", "debit", 120,
                EditorLine::debitProperty,
                (row, value) -> {
                    row.setDebit(normalizeMoney(value));
                    BigDecimal amount = parseMoney(row.getDebit());
                    if (amount != null && amount.signum() > 0)
                    {
                        row.setCredit("");
                    }
                }));
        lineTable.getColumns().add(moneyColumn("Credit", "credit", 120,
                EditorLine::creditProperty,
                (row, value) -> {
                    row.setCredit(normalizeMoney(value));
                    BigDecimal amount = parseMoney(row.getCredit());
                    if (amount != null && amount.signum() > 0)
                    {
                        row.setDebit("");
                    }
                }));
        lineTable.getColumns().add(optionColumn("Activity", "activity", 165,
                EditorLine::activityProperty,
                () -> referenceData.activities()));
        lineTable.getColumns().add(optionColumn("Merchant", "merchant", 165,
                EditorLine::merchantProperty,
                () -> referenceData.merchants()));

        TableColumn<EditorLine, Boolean> nmr = new TableColumn<>("NMR");
        configureColumn(nmr, "nmr", 80);
        nmr.setCellValueFactory(value -> value.getValue().nmrProperty());
        nmr.setCellFactory(CheckBoxTableCell.forTableColumn(nmr));
        lineTable.getColumns().add(nmr);

        lineTable.getColumns().add(textColumn("Notes", "notes", 260,
                EditorLine::notesProperty,
                EditorLine::setNotes));

        restoreTableState(lineTable, LINE_TABLE_ID);
        installTableStatePersistence(lineTable, LINE_TABLE_ID);
        lineTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> updateActionState());
        lineTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.INSERT)
            {
                addEditorLine(null);
                event.consume();
            }
        });
    }

    private TableColumn<EditorLine, TransactionLineEditorModel.Option> optionColumn(
            String title,
            String key,
            double width,
            Function<EditorLine, ObjectProperty<TransactionLineEditorModel.Option>> property,
            Supplier<List<TransactionLineEditorModel.Option>> options)
    {
        TableColumn<EditorLine, TransactionLineEditorModel.Option> column = new TableColumn<>(title);
        configureColumn(column, key, width);
        column.setCellValueFactory(value -> property.apply(value.getValue()));
        column.setCellFactory(ignored -> new OptionTableCell(options));
        column.setOnEditCommit(event -> {
            property.apply(event.getRowValue()).set(event.getNewValue());
            markDirty();
            recalculateTotals();
        });
        return column;
    }

    private TableColumn<EditorLine, String> moneyColumn(
            String title,
            String key,
            double width,
            Function<EditorLine, StringProperty> property,
            BiConsumer<EditorLine, String> setter)
    {
        return textColumn(title, key, width, property, (row, value) -> {
            setter.accept(row, value);
            recalculateTotals();
            lineTable.refresh();
        });
    }

    private TableColumn<EditorLine, String> readOnlyTextColumn(
            String title,
            String key,
            double width,
            Function<EditorLine, StringProperty> property)
    {
        TableColumn<EditorLine, String> column = new TableColumn<>(title);
        configureColumn(column, key, width);
        column.setEditable(false);
        column.setCellValueFactory(value -> property.apply(value.getValue()));
        return column;
    }

    private TableColumn<EditorLine, String> textColumn(
            String title,
            String key,
            double width,
            Function<EditorLine, StringProperty> property,
            BiConsumer<EditorLine, String> setter)
    {
        TableColumn<EditorLine, String> column = new TableColumn<>(title);
        configureColumn(column, key, width);
        column.setCellValueFactory(value -> property.apply(value.getValue()));
        column.setCellFactory(ignored -> new FocusCommitTextCell<>());
        column.setOnEditCommit(event -> {
            setter.accept(event.getRowValue(), safe(event.getNewValue()));
            markDirty();
        });
        return column;
    }

    private Node buildAdditionalDetailsRegion()
    {
        payeeBox.setConverter(new OptionConverter());
        bankAccountBox.setConverter(new OptionConverter());
        payeeBox.setMaxWidth(Double.MAX_VALUE);
        bankAccountBox.setMaxWidth(Double.MAX_VALUE);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(9);
        grid.setPadding(new Insets(8));
        ColumnConstraints labels = new ColumnConstraints(125);
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().setAll(labels, values);
        addDetailField(grid, 0, "Transaction", transactionIdentity);
        addDetailField(grid, 1, "Status", transactionStatus);
        addDetailField(grid, 2, "Payee", payeeBox);
        addDetailField(grid, 3, "Bank account", bankAccountBox);

        Label scope = new Label("Only H2-backed transaction fields are editable here. Check/reference and other legacy-only fields are not presented as fake saveable data.");
        scope.setWrapText(true);
        VBox content = new VBox(8, sectionHeading("Additional Details"), grid, scope);
        content.setPadding(new Insets(4));
        content.setMinSize(0, 0);
        ScrollPane scroll = scrollable(content, true);
        scroll.setId("journalWorkspaceAdditionalDetailsScroll");
        return scroll;
    }

    private Node buildSupplementalRegion()
    {
        FlowPane toggles = new FlowPane(8, 8);
        toggles.setAlignment(Pos.CENTER_LEFT);
        for (SupplementalKind kind : SupplementalKind.values())
        {
            CheckBox checkBox = new CheckBox(kind.toggleLabel());
            checkBox.setOnAction(event -> {
                markDirty();
                updateSupplementalAvailability();
            });
            supplementalSelections.put(kind, checkBox);
            toggles.getChildren().add(checkBox);

            Tab tab = buildSupplementalTab(kind);
            supplementalTabIndex.put(kind, tab);
            supplementalTabs.getTabs().add(tab);
        }
        supplementalTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        updateSupplementalAvailability();

        VBox content = new VBox(8,
                sectionHeading("Supplemental Details"),
                toggles,
                supplementalTabs);
        content.setPadding(new Insets(4));
        content.setMinSize(0, 0);
        VBox.setVgrow(supplementalTabs, Priority.ALWAYS);
        return content;
    }

    private Tab buildSupplementalTab(SupplementalKind kind)
    {
        TableView<SupplementalRow> table = new TableView<>();
        table.setId("journalWorkspaceSupplemental" + kind.name() + "Table");
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No " + kind.tabTitle().toLowerCase(Locale.ROOT) + " details."));
        table.getColumns().add(supplementalTextColumn("Link to Entry", "entryRef", 140, SupplementalRow::entryRefProperty, SupplementalRow::setEntryRef));
        table.getColumns().add(supplementalTextColumn("Counterparty", "counterparty", 170, SupplementalRow::counterpartyProperty, SupplementalRow::setCounterparty));
        table.getColumns().add(supplementalTextColumn("Description", "description", 240, SupplementalRow::descriptionProperty, SupplementalRow::setDescription));
        table.getColumns().add(supplementalTextColumn("Reference", "reference", 150, SupplementalRow::referenceProperty, SupplementalRow::setReference));
        table.getColumns().add(supplementalTextColumn("Amount", "amount", 120, SupplementalRow::amountProperty,
                (row, value) -> row.setAmount(normalizeMoney(value))));
        if (kind.dueDate())
        {
            table.getColumns().add(supplementalTextColumn("Due Date", "dueDate", 120, SupplementalRow::dueDateProperty, SupplementalRow::setDueDate));
        }
        if (kind.dateRange())
        {
            table.getColumns().add(supplementalTextColumn("Start Date", "startDate", 120, SupplementalRow::startDateProperty, SupplementalRow::setStartDate));
            table.getColumns().add(supplementalTextColumn("End Date", "endDate", 120, SupplementalRow::endDateProperty, SupplementalRow::setEndDate));
        }
        table.getColumns().add(supplementalTextColumn("Notes", "notes", 240, SupplementalRow::notesProperty, SupplementalRow::setNotes));
        restoreTableState(table, "supplemental-" + kind.name());
        installTableStatePersistence(table, "supplemental-" + kind.name());
        supplementalTables.put(kind, table);

        Button add = new Button("Add Detail");
        Button remove = new Button("Remove Detail");
        add.setOnAction(event -> addSupplementalRow(kind));
        remove.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(event -> {
            SupplementalRow selected = table.getSelectionModel().getSelectedItem();
            if (selected != null)
            {
                table.getItems().remove(selected);
                markDirty();
            }
        });
        VBox panel = new VBox(6, new HBox(8, add, remove), table);
        VBox.setVgrow(table, Priority.ALWAYS);
        panel.setMinSize(0, 0);
        Tab tab = new Tab(kind.tabTitle(), panel);
        tab.setClosable(false);
        return tab;
    }

    private TableColumn<SupplementalRow, String> supplementalTextColumn(
            String title,
            String key,
            double width,
            Function<SupplementalRow, StringProperty> property,
            BiConsumer<SupplementalRow, String> setter)
    {
        TableColumn<SupplementalRow, String> column = new TableColumn<>(title);
        configureColumn(column, key, width);
        column.setCellValueFactory(value -> property.apply(value.getValue()));
        column.setCellFactory(ignored -> new FocusCommitTextCell<>());
        column.setOnEditCommit(event -> {
            setter.accept(event.getRowValue(), safe(event.getNewValue()));
            markDirty();
            event.getTableView().refresh();
        });
        return column;
    }

    private void configureEditorListeners()
    {
        entryDate.valueProperty().addListener((obs, oldValue, newValue) -> markDirty());
        memoArea.textProperty().addListener((obs, oldValue, newValue) -> markDirty());
        payeeBox.valueProperty().addListener((obs, oldValue, newValue) -> markDirty());
        bankAccountBox.valueProperty().addListener((obs, oldValue, newValue) -> markDirty());
        updateActionState();
    }

    private void loadReferenceData()
    {
        UiAsync.run("journal-workspace-reference-data",
                () -> UiServiceRegistry.transactionReferenceData().loadActiveReferenceData(),
                data -> {
                    referenceData = data;
                    payeeBox.getItems().setAll(data.counterparties());
                    bankAccountBox.getItems().setAll(data.accounts());
                    resolveLoadedOptions();
                    lineTable.refresh();
                    status.setText("Loaded journal reference choices.");
                },
                ex -> status.setText("Reference choices unavailable: " + UiErrors.safeMessage(ex)));
    }

    private void resolveLoadedOptions()
    {
        payeeBox.setValue(resolveOption(payeeBox.getValue(), referenceData.counterparties()));
        bankAccountBox.setValue(resolveOption(bankAccountBox.getValue(), referenceData.accounts()));
        for (EditorLine row : lineTable.getItems())
        {
            row.setAccount(resolveOption(row.getAccount(), referenceData.accounts()));
            row.setFund(resolveOption(row.getFund(), referenceData.funds()));
            row.setBudget(resolveOption(row.getBudget(), referenceData.budgetCategories()));
            row.setActivity(resolveOption(row.getActivity(), referenceData.activities()));
            row.setMerchant(resolveOption(row.getMerchant(), referenceData.merchants()));
        }
    }

    private TransactionLineEditorModel.Option resolveOption(
            TransactionLineEditorModel.Option current,
            List<TransactionLineEditorModel.Option> options)
    {
        if (current == null || current.id() == null)
        {
            return current;
        }
        return options.stream().filter(option -> Objects.equals(option.id(), current.id())).findFirst().orElse(current);
    }

    private void reloadJournal()
    {
        status.setText("Loading journal transactions...");
        Long centerId = requestedTransactionId != null ? requestedTransactionId : selectedTransactionId();
        UiAsync.run("journal-workspace-load",
                () -> UiServiceRegistry.transactionEntry().search(fromDate.getValue(), toDate.getValue(), searchText.getText(), 500),
                views -> {
                    List<JournalTransactionRow> rows = views.stream().map(JournalTransactionRow::from).toList();
                    journalTable.getItems().setAll(rows);
                    centerJournalSelection(centerId);
                    requestedTransactionId = null;
                    status.setText("Loaded " + rows.size() + " journal transaction(s).");
                    updateActionState();
                },
                ex -> status.setText("Failed to load journal transactions: " + UiErrors.safeMessage(ex)));
    }

    private void centerJournalSelection(Long transactionId)
    {
        if (transactionId == null)
        {
            return;
        }
        for (int index = 0; index < journalTable.getItems().size(); index++)
        {
            if (Objects.equals(transactionId, journalTable.getItems().get(index).transactionId()))
            {
                journalTable.getSelectionModel().select(index);
                journalTable.scrollTo(index);
                return;
            }
        }
    }

    private void startNew(boolean confirmDiscard)
    {
        if (confirmDiscard && !confirmDiscardIfDirty())
        {
            return;
        }
        loading = true;
        try
        {
            editTransactionId = null;
            entryDate.setValue(ActivePeriodContext.get() == null ? LocalDate.now() : ActivePeriodContext.get());
            memoArea.clear();
            payeeBox.setValue(null);
            bankAccountBox.setValue(null);
            lineTable.getItems().clear();
            addEditorLine(null);
            addEditorLine(null);
            clearSupplementalRows();
            editorMode.setText("New journal entry");
            transactionIdentity.setText("Unsaved");
            transactionStatus.setText("ENTERED after save");
            validationMessage.setText("Enter at least two meaningful, balanced lines.");
            journalTable.getSelectionModel().clearSelection();
        }
        finally
        {
            loading = false;
        }
        dirty = false;
        recalculateTotals();
        updateActionState();
        status.setText("New journal entry ready.");
    }

    private void editSelectedTransaction()
    {
        JournalTransactionRow selected = journalTable.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select a journal transaction before editing.");
            return;
        }
        loadTransactionIntoEditor(selected.transactionId(), true);
    }

    private void loadTransactionIntoEditor(long transactionId, boolean confirmDiscard)
    {
        if (confirmDiscard && !confirmDiscardIfDirty())
        {
            return;
        }
        status.setText("Loading transaction #" + transactionId + "...");
        UiAsync.run("journal-workspace-edit-" + transactionId,
                () -> UiServiceRegistry.transactionEntry().load(transactionId),
                view -> {
                    applyTransactionView(view);
                    centerJournalSelection(view.id());
                    status.setText("Loaded transaction #" + view.id() + " for editing.");
                },
                ex -> status.setText("Could not load transaction #" + transactionId + ": " + UiErrors.safeMessage(ex)));
    }

    private void applyTransactionView(TransactionView view)
    {
        loading = true;
        try
        {
            editTransactionId = view.id();
            entryDate.setValue(view.date());
            memoArea.setText(safe(view.memo()));
            payeeBox.setValue(option(view.payeeId(), "", view.payeeName()));
            bankAccountBox.setValue(option(view.bankAccountId(), "", view.bankAccountName()));
            lineTable.getItems().setAll(view.lines().stream().map(this::editorLineFromView).toList());
            while (lineTable.getItems().size() < 2)
            {
                addEditorLine(null);
            }
            applySupplementalViews(view.supplementalLines());
            editorMode.setText("Edit journal entry");
            transactionIdentity.setText("Txn #" + view.id());
            transactionStatus.setText(view.status());
            validationMessage.setText("Loaded from H2. Save updates this transaction by stable ID when policy allows.");
            resolveLoadedOptions();
        }
        finally
        {
            loading = false;
        }
        dirty = false;
        recalculateTotals();
        updateActionState();
    }

    private EditorLine editorLineFromView(TransactionView.Line line)
    {
        EditorLine row = new EditorLine();
        row.setAccount(option(line.accountId(), line.accountCode(), line.accountName()));
        row.setFund(option(line.fundId(), line.fundCode(), line.fundName()));
        row.setBudget(option(line.budgetCategoryId(), "", ""));
        row.setActivity(option(line.activityId(), "", ""));
        row.setMerchant(option(line.merchantId(), "", ""));
        row.setDebit(line.debit().signum() == 0 ? "" : normalizeMoney(line.debit().toPlainString()));
        row.setCredit(line.credit().signum() == 0 ? "" : normalizeMoney(line.credit().toPlainString()));
        row.setNmr(line.nmr());
        row.setNotes(safe(line.notes()));
        row.setBankState(line.clearedDisplay());
        row.setClearedOn(line.bankClearedOn() == null ? "" : line.bankClearedOn().toString());
        row.setReconciliationSessionId(line.reconciliationSessionId());
        attachLineListeners(row);
        return row;
    }

    private void openSelectedLineReconciliation()
    {
        EditorLine selected = lineTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getReconciliationSessionId() == null)
        {
            status.setText("Select a persisted bank line with a durable reconciliation match.");
            return;
        }
        long sessionId = selected.getReconciliationSessionId();
        DrillThroughCoordinator.openPanelWithContext(
                AppPanelId.RECONCILIATION_RUNS,
                BankImportNavigationContext.forReconciliationSession(sessionId));
        status.setText("Opening reconciliation session #" + sessionId + ".");
    }

    private void saveCurrentEntry()
    {
        TransactionCommand command;
        try
        {
            command = buildCommand();
        }
        catch (IllegalArgumentException ex)
        {
            validationMessage.setText(ex.getMessage());
            status.setText("Save blocked: " + ex.getMessage());
            return;
        }

        TransactionValidationResult result = commandValidator.validate(command);
        if (!result.valid())
        {
            String message = String.join(" ", result.errors());
            validationMessage.setText(message);
            status.setText("Save blocked: " + message);
            return;
        }

        TransactionEntryService service = UiServiceRegistry.transactionEntry();
        Long targetId = editTransactionId;
        status.setText(targetId == null ? "Saving new journal entry..." : "Updating transaction #" + targetId + "...");
        UiAsync.run("journal-workspace-save",
                () -> targetId == null ? service.enter(command) : service.update(targetId, command),
                view -> {
                    applyTransactionView(view);
                    requestedTransactionId = view.id();
                    reloadJournal();
                    status.setText((targetId == null ? "Saved new transaction #" : "Updated transaction #")
                            + view.id() + " with " + view.lines().size() + " accounting line(s) and "
                            + view.supplementalLines().size() + " supplemental detail row(s).");
                },
                ex -> status.setText("Save failed: " + UiErrors.safeMessage(ex)));
    }

    private TransactionCommand buildCommand()
    {
        if (entryDate.getValue() == null)
        {
            throw new IllegalArgumentException("Transaction date is required.");
        }
        List<TransactionLineCommand> lineCommands = new ArrayList<>();
        int rowNumber = 0;
        for (EditorLine row : lineTable.getItems())
        {
            rowNumber++;
            if (!row.hasAnyInput())
            {
                continue;
            }
            if (row.getAccount() == null || row.getAccount().id() == null)
            {
                throw new IllegalArgumentException("Entry line " + rowNumber + " requires an account.");
            }
            if (row.getFund() == null || row.getFund().id() == null)
            {
                throw new IllegalArgumentException("Entry line " + rowNumber + " requires a fund.");
            }
            BigDecimal debit = parseMoney(row.getDebit());
            BigDecimal credit = parseMoney(row.getCredit());
            if (debit == null || credit == null)
            {
                throw new IllegalArgumentException("Entry line " + rowNumber + " contains an invalid amount.");
            }
            if (debit.signum() < 0 || credit.signum() < 0 || (debit.signum() > 0 && credit.signum() > 0))
            {
                throw new IllegalArgumentException("Entry line " + rowNumber + " must contain one non-negative debit or credit amount, not both.");
            }
            if (debit.signum() == 0 && credit.signum() == 0)
            {
                throw new IllegalArgumentException("Entry line " + rowNumber + " has no accounting amount.");
            }
            lineCommands.add(new TransactionLineCommand(
                    row.getAccount().id(),
                    row.getFund().id(),
                    id(row.getBudget()),
                    id(row.getActivity()),
                    id(row.getMerchant()),
                    debit,
                    credit,
                    row.isNmr(),
                    blankToNull(row.getNotes())));
        }
        List<TransactionSupplementalLineCommand> supplemental = supplementalCommands();
        return new TransactionCommand(
                entryDate.getValue(),
                id(payeeBox.getValue()),
                blankToNull(memoArea.getText()),
                id(bankAccountBox.getValue()),
                lineCommands,
                supplemental);
    }

    private List<TransactionSupplementalLineCommand> supplementalCommands()
    {
        List<TransactionSupplementalLineCommand> commands = new ArrayList<>();
        for (SupplementalKind kind : SupplementalKind.values())
        {
            CheckBox enabled = supplementalSelections.get(kind);
            if (enabled == null || !enabled.isSelected())
            {
                continue;
            }
            TableView<SupplementalRow> table = supplementalTables.get(kind);
            int rowNumber = 0;
            for (SupplementalRow row : table.getItems())
            {
                rowNumber++;
                if (!row.hasAnyInput())
                {
                    continue;
                }
                if (row.getDescription().isBlank())
                {
                    throw new IllegalArgumentException(kind.tabTitle() + " detail row " + rowNumber + " requires a description.");
                }
                BigDecimal amount = parseMoney(row.getAmount());
                if (amount == null || amount.signum() < 0)
                {
                    throw new IllegalArgumentException(kind.tabTitle() + " detail row " + rowNumber + " requires a non-negative amount.");
                }
                LocalDate dueDate = parseDate(row.getDueDate(), kind.tabTitle(), rowNumber, "due date");
                LocalDate startDate = parseDate(row.getStartDate(), kind.tabTitle(), rowNumber, "start date");
                LocalDate endDate = parseDate(row.getEndDate(), kind.tabTitle(), rowNumber, "end date");
                if ((startDate == null) != (endDate == null))
                {
                    throw new IllegalArgumentException(kind.tabTitle() + " detail row " + rowNumber + " requires both start and end dates or neither.");
                }
                if (startDate != null && startDate.isAfter(endDate))
                {
                    throw new IllegalArgumentException(kind.tabTitle() + " detail row " + rowNumber + " start date must be on or before end date.");
                }
                commands.add(new TransactionSupplementalLineCommand(
                        kind.name(),
                        blankToNull(row.getEntryRef()),
                        blankToNull(row.getCounterparty()),
                        row.getDescription().trim(),
                        blankToNull(row.getReference()),
                        amount,
                        dueDate,
                        startDate,
                        endDate,
                        blankToNull(row.getNotes())));
            }
        }
        return commands;
    }

    private LocalDate parseDate(String value, String kind, int row, String field)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        try
        {
            return LocalDate.parse(value.trim());
        }
        catch (RuntimeException ex)
        {
            throw new IllegalArgumentException(kind + " detail row " + row + " has an invalid " + field + "; use YYYY-MM-DD.");
        }
    }

    private void deleteOrReverseCurrentTransaction()
    {
        Long targetId = editTransactionId != null ? editTransactionId : selectedTransactionId();
        if (targetId == null)
        {
            status.setText("Select or load a saved transaction before deleting or reversing.");
            return;
        }
        CorrectionMethod method = MainWindow.sharedSessionState().preferences().correctionMethod();
        if (method == CorrectionMethod.DIRECT_EDIT)
        {
            if (!confirm("Delete transaction #" + targetId + "?",
                    "This removes the entered transaction after period and reconciliation checks and writes an audit snapshot."))
            {
                status.setText("Delete cancelled for transaction #" + targetId + ".");
                return;
            }
            UiAsync.run("journal-workspace-delete-" + targetId,
                    () -> {
                        UiServiceRegistry.transactionCorrection().delete(targetId, "ui", "Deleted from unified Journal workspace");
                        return targetId;
                    },
                    deletedId -> {
                        startNew(false);
                        reloadJournal();
                        status.setText("Deleted transaction #" + deletedId + ".");
                    },
                    ex -> status.setText("Delete failed for transaction #" + targetId + ": " + UiErrors.safeMessage(ex)));
        }
        else
        {
            if (!confirm("Reverse transaction #" + targetId + " instead of deleting?",
                    "Current correction settings require a reversing entry. The reversal date will use the active period date."))
            {
                status.setText("Reversal cancelled for transaction #" + targetId + ".");
                return;
            }
            UiAsync.run("journal-workspace-reverse-" + targetId,
                    () -> UiServiceRegistry.transactionCorrection().reverse(
                            targetId,
                            ActivePeriodContext.get(),
                            "ui",
                            "Reversed from unified Journal workspace",
                            false),
                    result -> {
                        startNew(false);
                        requestedTransactionId = result.reversalTransactionId();
                        reloadJournal();
                        status.setText("Created reversing transaction #" + result.reversalTransactionId()
                                + " for original transaction #" + targetId + ".");
                    },
                    ex -> status.setText("Reversal failed for transaction #" + targetId + ": " + UiErrors.safeMessage(ex)));
        }
    }

    private boolean confirm(String header, String content)
    {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, content, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle("Confirm Journal action");
        alert.setHeaderText(header);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private boolean confirmDiscardIfDirty()
    {
        if (!dirty)
        {
            return true;
        }
        return confirm("Discard unsaved journal-entry changes?",
                "The current editor contains unsaved changes. Continue only if those changes should be discarded.");
    }

    private void addEditorLine(EditorLine source)
    {
        EditorLine row = source == null ? new EditorLine() : source.copy();
        attachLineListeners(row);
        lineTable.getItems().add(row);
        lineTable.getSelectionModel().select(row);
        lineTable.scrollTo(row);
        if (!loading)
        {
            markDirty();
        }
        recalculateTotals();
        updateActionState();
    }

    private void attachLineListeners(EditorLine row)
    {
        row.nmrProperty().addListener((obs, oldValue, newValue) -> markDirty());
    }

    private void duplicateSelectedLine()
    {
        EditorLine selected = lineTable.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select an entry line before duplicating it.");
            return;
        }
        int index = lineTable.getSelectionModel().getSelectedIndex() + 1;
        EditorLine copy = selected.copy();
        attachLineListeners(copy);
        lineTable.getItems().add(index, copy);
        lineTable.getSelectionModel().select(copy);
        markDirty();
        recalculateTotals();
    }

    private void removeSelectedLine()
    {
        EditorLine selected = lineTable.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            return;
        }
        if (lineTable.getItems().size() <= 2)
        {
            status.setText("At least two entry-line rows must remain available.");
            return;
        }
        lineTable.getItems().remove(selected);
        markDirty();
        recalculateTotals();
        updateActionState();
    }

    private void addSupplementalRow(SupplementalKind kind)
    {
        CheckBox selection = supplementalSelections.get(kind);
        if (selection != null)
        {
            selection.setSelected(true);
        }
        updateSupplementalAvailability();
        TableView<SupplementalRow> table = supplementalTables.get(kind);
        SupplementalRow row = new SupplementalRow();
        table.getItems().add(row);
        table.getSelectionModel().select(row);
        table.scrollTo(row);
        markDirty();
    }

    private void clearSupplementalRows()
    {
        for (TableView<SupplementalRow> table : supplementalTables.values())
        {
            table.getItems().clear();
        }
        for (CheckBox checkBox : supplementalSelections.values())
        {
            checkBox.setSelected(false);
        }
        updateSupplementalAvailability();
    }

    private void applySupplementalViews(List<TransactionSupplementalLineView> views)
    {
        clearSupplementalRows();
        for (TransactionSupplementalLineView view : views)
        {
            SupplementalKind kind = SupplementalKind.from(view.kind());
            if (kind == null)
            {
                continue;
            }
            supplementalSelections.get(kind).setSelected(true);
            supplementalTables.get(kind).getItems().add(SupplementalRow.from(view));
        }
        updateSupplementalAvailability();
    }

    private void updateSupplementalAvailability()
    {
        for (SupplementalKind kind : SupplementalKind.values())
        {
            CheckBox selection = supplementalSelections.get(kind);
            Tab tab = supplementalTabIndex.get(kind);
            if (selection != null && tab != null)
            {
                tab.setDisable(!selection.isSelected());
            }
        }
    }

    private void recalculateTotals()
    {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (EditorLine row : lineTable.getItems())
        {
            BigDecimal rowDebit = parseMoney(row.getDebit());
            BigDecimal rowCredit = parseMoney(row.getCredit());
            if (rowDebit != null)
            {
                debit = debit.add(rowDebit);
            }
            if (rowCredit != null)
            {
                credit = credit.add(rowCredit);
            }
        }
        BigDecimal difference = debit.subtract(credit);
        debitTotal.setText(money(debit));
        creditTotal.setText(money(credit));
        differenceTotal.setText(money(difference.abs()));
        if (debit.signum() > 0 && difference.compareTo(BigDecimal.ZERO) == 0)
        {
            balanceStatus.setText("Balanced");
            validationMessage.setText("Transaction is balanced and ready for validation.");
        }
        else
        {
            balanceStatus.setText("Needs attention");
            validationMessage.setText("Transaction is not balanced.");
        }
    }

    private void markDirty()
    {
        if (!loading)
        {
            dirty = true;
        }
    }

    private void updateActionState()
    {
        editButton.setDisable(journalTable.getSelectionModel().getSelectedItem() == null);
        deleteButton.setDisable(editTransactionId == null && journalTable.getSelectionModel().getSelectedItem() == null);
        removeLineButton.setDisable(lineTable.getSelectionModel().getSelectedItem() == null || lineTable.getItems().size() <= 2);
        EditorLine selectedLine = lineTable.getSelectionModel().getSelectedItem();
        drillReconciliationButton.setDisable(
                selectedLine == null || selectedLine.getReconciliationSessionId() == null);
        deleteButton.setText(deleteActionLabel());
    }

    private String deleteActionLabel()
    {
        return MainWindow.sharedSessionState().preferences().correctionMethod() == CorrectionMethod.DIRECT_EDIT
                ? "Delete"
                : "Reverse";
    }

    private Long selectedTransactionId()
    {
        JournalTransactionRow selected = journalTable.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.transactionId();
    }

    private void validateCurrentEntry()
    {
        try
        {
            TransactionCommand command = buildCommand();
            TransactionValidationResult result = commandValidator.validate(command);
            if (result.valid())
            {
                validationMessage.setText("Validation accepted: transaction is balanced and ready to save.");
                status.setText("Validation accepted.");
            }
            else
            {
                String message = String.join(" ", result.errors());
                validationMessage.setText(message);
                status.setText("Validation blocked: " + message);
            }
        }
        catch (IllegalArgumentException ex)
        {
            validationMessage.setText(ex.getMessage());
            status.setText("Validation blocked: " + ex.getMessage());
        }
    }

    @Override
    public String title()
    {
        return "Journal";
    }

    @Override
    public Node root()
    {
        return root;
    }

    @Override
    public void onNew()
    {
        startNew(true);
    }

    @Override
    public void onSave()
    {
        saveCurrentEntry();
    }

    @Override
    public boolean hasUnsavedChanges()
    {
        return dirty;
    }

    @Override
    public void onPanelShown()
    {
        String context = DrillThroughCoordinator.consumeContext(AppPanelId.JOURNAL_PANE);
        Long transactionId = transactionIdFromContext(context);
        if (transactionId != null)
        {
            requestedTransactionId = transactionId;
            loadTransactionIntoEditor(transactionId, false);
        }
        else if (context != null && context.toLowerCase(Locale.ROOT).contains("new"))
        {
            startNew(false);
        }
        reloadJournal();
    }

    @Override
    public RunCommandResult onRunCommand(AppCommand command)
    {
        if (command == AppCommand.POST_VALIDATE)
        {
            validateCurrentEntry();
            return new RunCommandResult(true, validationMessage.getText());
        }
        return new RunCommandResult(false, "Unsupported Journal command: " + command);
    }

    @Override
    public Optional<JournalSelection> activeJournalSelection()
    {
        Long transactionId = selectedTransactionId();
        if (transactionId == null)
        {
            transactionId = editTransactionId;
        }
        return transactionId == null
                ? Optional.empty()
                : Optional.of(new JournalSelection(transactionId, "Journal"));
    }

    static Long transactionIdFromContext(String context)
    {
        if (context == null || context.isBlank())
        {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("Txn #(\\d+)").matcher(context);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private static TransactionLineEditorModel.ReferenceData emptyReferenceData()
    {
        return new TransactionLineEditorModel.ReferenceData(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static TransactionLineEditorModel.Option option(Long id, String code, String name)
    {
        return id == null ? null : TransactionLineEditorModel.option(id, safe(code), safe(name));
    }

    private static Long id(TransactionLineEditorModel.Option option)
    {
        return option == null ? null : option.id();
    }

    private static String safe(String value)
    {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal parseMoney(String value)
    {
        if (value == null || value.isBlank())
        {
            return BigDecimal.ZERO;
        }
        try
        {
            return new BigDecimal(value.trim().replace("$", "").replace(",", ""));
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
    }

    private static String normalizeMoney(String value)
    {
        BigDecimal amount = parseMoney(value);
        return amount == null
                ? safe(value).trim()
                : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String money(BigDecimal value)
    {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static Label sectionHeading(String text)
    {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private static Node labelledValue(String label, Label value)
    {
        return new HBox(5, new Label(label + ":"), value);
    }

    private static void addDetailField(GridPane grid, int row, String label, Node value)
    {
        grid.add(new Label(label), 0, row);
        grid.add(value, 1, row);
        GridPane.setHgrow(value, Priority.ALWAYS);
        if (value instanceof Region region)
        {
            region.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private static ScrollPane scrollable(Node content, boolean fitToWidth)
    {
        ScrollPane pane = new ScrollPane(content);
        pane.setFitToWidth(fitToWidth);
        pane.setFitToHeight(false);
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pane.setPannable(true);
        pane.setMinSize(0, 0);
        return pane;
    }

    private <S> TableColumn<S, String> configuredColumn(String title, String key, double width)
    {
        TableColumn<S, String> column = new TableColumn<>(title);
        configureColumn(column, key, width);
        return column;
    }

    private static void configureColumn(TableColumn<?, ?> column, String key, double width)
    {
        column.setId(key);
        column.setUserData(key);
        column.setPrefWidth(width);
        column.setMinWidth(70);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
    }

    private <S> void restoreTableState(TableView<S> table, String tableKey)
    {
        restoringTableState = true;
        try
        {
            Preferences state = tableState(tableKey);
            for (TableColumn<S, ?> column : table.getColumns())
            {
                column.setPrefWidth(state.getDouble(columnKey(column) + ".width", column.getPrefWidth()));
                String sort = state.get(columnKey(column) + ".sort", "");
                if ("ASCENDING".equals(sort))
                {
                    column.setSortType(TableColumn.SortType.ASCENDING);
                }
                else if ("DESCENDING".equals(sort))
                {
                    column.setSortType(TableColumn.SortType.DESCENDING);
                }
            }
            String order = state.get("order", "");
            if (!order.isBlank())
            {
                List<String> keys = List.of(order.split(","));
                List<TableColumn<S, ?>> columns = new ArrayList<>(table.getColumns());
                columns.sort(Comparator.comparingInt(column -> {
                    int index = keys.indexOf(columnKey(column));
                    return index < 0 ? Integer.MAX_VALUE : index;
                }));
                table.getColumns().setAll(columns);
            }
            String sortOrder = state.get("sortOrder", "");
            if (!sortOrder.isBlank())
            {
                List<TableColumn<S, ?>> restored = new ArrayList<>();
                for (String key : sortOrder.split(","))
                {
                    table.getColumns().stream()
                            .filter(column -> Objects.equals(columnKey(column), key))
                            .findFirst()
                            .ifPresent(restored::add);
                }
                table.getSortOrder().setAll(restored);
            }
        }
        finally
        {
            restoringTableState = false;
        }
    }

    private <S> void installTableStatePersistence(TableView<S> table, String tableKey)
    {
        table.getColumns().addListener((ListChangeListener<TableColumn<S, ?>>) change -> saveTableState(table, tableKey));
        table.getSortOrder().addListener((ListChangeListener<TableColumn<S, ?>>) change -> saveTableState(table, tableKey));
        for (TableColumn<S, ?> column : table.getColumns())
        {
            column.widthProperty().addListener((obs, oldValue, newValue) -> saveTableState(table, tableKey));
            column.sortTypeProperty().addListener((obs, oldValue, newValue) -> saveTableState(table, tableKey));
        }
    }

    private <S> void saveTableState(TableView<S> table, String tableKey)
    {
        if (restoringTableState)
        {
            return;
        }
        Preferences state = tableState(tableKey);
        state.put("order", String.join(",", table.getColumns().stream().map(JournalWorkspacePanel::columnKey).toList()));
        state.put("sortOrder", String.join(",", table.getSortOrder().stream().map(JournalWorkspacePanel::columnKey).toList()));
        for (TableColumn<S, ?> column : table.getColumns())
        {
            state.putDouble(columnKey(column) + ".width", column.getWidth() > 0 ? column.getWidth() : column.getPrefWidth());
            state.put(columnKey(column) + ".sort", column.getSortType() == null ? "" : column.getSortType().name());
        }
    }

    private Preferences tableState(String tableKey)
    {
        return VIEW_STATE.node(companyKey()).node(tableKey);
    }

    private static String columnKey(TableColumn<?, ?> column)
    {
        Object key = column.getUserData();
        return key == null ? column.getText() : String.valueOf(key);
    }

    private void installDividerState(SplitPane splitPane, String key, double... defaults)
    {
        Preferences state = VIEW_STATE.node(companyKey()).node("dividers");
        for (int index = 0; index < splitPane.getDividers().size(); index++)
        {
            double fallback = index < defaults.length ? defaults[index] : splitPane.getDividers().get(index).getPosition();
            splitPane.getDividers().get(index).setPosition(state.getDouble(key + "." + index, fallback));
            int dividerIndex = index;
            splitPane.getDividers().get(index).positionProperty().addListener((obs, oldValue, newValue) ->
                    state.putDouble(key + "." + dividerIndex, newValue.doubleValue()));
        }
    }

    private static String companyKey()
    {
        String company = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
        String value = company == null || company.isBlank() ? "DEFAULT" : company.trim().toUpperCase(Locale.ROOT);
        return value.replaceAll("[^A-Z0-9_-]", "_");
    }

    private final class OptionTableCell extends TableCell<EditorLine, TransactionLineEditorModel.Option>
    {
        private final Supplier<List<TransactionLineEditorModel.Option>> options;
        private final ComboBox<TransactionLineEditorModel.Option> editor = new ComboBox<>();

        private OptionTableCell(Supplier<List<TransactionLineEditorModel.Option>> options)
        {
            this.options = options;
            editor.setConverter(new OptionConverter());
            editor.setMaxWidth(Double.MAX_VALUE);
            editor.setOnAction(event -> {
                if (isEditing())
                {
                    commitEdit(editor.getValue());
                }
            });
            editor.focusedProperty().addListener((obs, oldValue, focused) -> {
                if (!focused && isEditing())
                {
                    commitEdit(editor.getValue());
                }
            });
        }

        @Override
        public void startEdit()
        {
            if (!isEditable() || !getTableView().isEditable() || !getTableColumn().isEditable())
            {
                return;
            }
            super.startEdit();
            editor.getItems().setAll(options.get());
            editor.setValue(getItem());
            setText(null);
            setGraphic(editor);
            editor.show();
        }

        @Override
        protected void updateItem(TransactionLineEditorModel.Option item, boolean empty)
        {
            super.updateItem(item, empty);
            if (empty)
            {
                setText(null);
                setGraphic(null);
            }
            else if (isEditing())
            {
                editor.setValue(item);
                setText(null);
                setGraphic(editor);
            }
            else
            {
                setText(item == null ? "" : item.label());
                setGraphic(null);
            }
        }
    }

    private static final class OptionConverter extends StringConverter<TransactionLineEditorModel.Option>
    {
        @Override
        public String toString(TransactionLineEditorModel.Option option)
        {
            return option == null ? "" : option.label();
        }

        @Override
        public TransactionLineEditorModel.Option fromString(String value)
        {
            return null;
        }
    }

    private static final class FocusCommitTextCell<S> extends TableCell<S, String>
    {
        private TextField editor;

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
                editor.setOnAction(event -> commitEditorValue());
                editor.focusedProperty().addListener((obs, oldValue, focused) -> {
                    if (!focused)
                    {
                        commitEditorValue();
                    }
                });
            }
            editor.setText(getItem() == null ? "" : getItem());
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
                editor.setText(item == null ? "" : item);
                setText(null);
                setGraphic(editor);
            }
            else
            {
                setText(item == null ? "" : item);
                setGraphic(null);
            }
        }

        private void commitEditorValue()
        {
            if (editor == null)
            {
                return;
            }
            String value = editor.getText();
            if (isEditing())
            {
                commitEdit(value);
            }
            else
            {
                TableView<S> table = getTableView();
                TableColumn<S, String> column = getTableColumn();
                if (table == null || column == null || getIndex() < 0 || getIndex() >= table.getItems().size())
                {
                    return;
                }
                CellEditEvent<S, String> event = new CellEditEvent<>(
                        table,
                        new TablePosition<>(table, getIndex(), column),
                        TableColumn.editCommitEvent(),
                        value);
                Event.fireEvent(column, event);
                updateItem(value, false);
            }
        }
    }

    private enum SupplementalKind
    {
        RECEIVABLE("Receivable", "Receivables", true, false),
        PAYABLE("Payable", "Payables", true, false),
        PREPAID_EXPENSE("Prepaid Expense", "Prepaid Expenses", false, true),
        DEFERRED_REVENUE("Deferred Revenue", "Deferred Revenue", false, true),
        OTHER_ASSET("Other Asset", "Other Assets", false, false),
        OTHER_LIABILITY("Other Liability", "Other Liabilities", true, false);

        private final String toggleLabel;
        private final String tabTitle;
        private final boolean dueDate;
        private final boolean dateRange;

        SupplementalKind(String toggleLabel, String tabTitle, boolean dueDate, boolean dateRange)
        {
            this.toggleLabel = toggleLabel;
            this.tabTitle = tabTitle;
            this.dueDate = dueDate;
            this.dateRange = dateRange;
        }

        String toggleLabel()
        {
            return toggleLabel;
        }

        String tabTitle()
        {
            return tabTitle;
        }

        boolean dueDate()
        {
            return dueDate;
        }

        boolean dateRange()
        {
            return dateRange;
        }

        static SupplementalKind from(String value)
        {
            if (value == null)
            {
                return null;
            }
            try
            {
                return valueOf(value);
            }
            catch (IllegalArgumentException ex)
            {
                return null;
            }
        }
    }

    static final class JournalTransactionRow
    {
        private final TransactionView view;
        private final String accounts;
        private final String funds;
        private final String debits;
        private final String credits;
        private final String details;

        private JournalTransactionRow(TransactionView view)
        {
            this.view = view;
            StringBuilder accountBuilder = new StringBuilder();
            StringBuilder fundBuilder = new StringBuilder();
            StringBuilder debitBuilder = new StringBuilder();
            StringBuilder creditBuilder = new StringBuilder();
            StringBuilder detailBuilder = new StringBuilder();
            int lineNumber = 0;
            for (TransactionView.Line line : view.lines())
            {
                lineNumber++;
                append(accountBuilder, (line.credit().signum() > 0 ? "    " : "") + safe(line.accountCode()) + " " + safe(line.accountName()));
                append(fundBuilder, safe(line.fundCode()) + " " + safe(line.fundName()));
                append(debitBuilder, line.debit().signum() == 0 ? "" : money(line.debit()));
                append(creditBuilder, line.credit().signum() == 0 ? "" : money(line.credit()));
                if (line.notes() != null && !line.notes().isBlank())
                {
                    append(detailBuilder, "Line " + lineNumber + ": " + line.notes());
                }
            }
            if (view.memo() != null && !view.memo().isBlank())
            {
                append(detailBuilder, "Memo: " + view.memo());
            }
            if (view.payeeName() != null && !view.payeeName().isBlank())
            {
                append(detailBuilder, "Payee: " + view.payeeName());
            }
            if (view.bankAccountName() != null && !view.bankAccountName().isBlank())
            {
                append(detailBuilder, "Bank: " + view.bankAccountName());
            }
            accounts = accountBuilder.toString();
            funds = fundBuilder.toString();
            debits = debitBuilder.toString();
            credits = creditBuilder.toString();
            details = detailBuilder.length() == 0 ? "" : detailBuilder.toString();
        }

        static JournalTransactionRow from(TransactionView view)
        {
            return new JournalTransactionRow(view);
        }

        Long transactionId()
        {
            return view.id();
        }

        String date()
        {
            return view.date() == null ? "" : view.date().toString();
        }

        String accounts()
        {
            return accounts;
        }

        String funds()
        {
            return funds;
        }

        String cleared()
        {
            return view.clearedState().displayText();
        }

        String debits()
        {
            return debits;
        }

        String credits()
        {
            return credits;
        }

        String supplemental()
        {
            int count = view.supplementalLines().size();
            return count == 0 ? "" : "Details (" + count + ")";
        }

        String details()
        {
            return details;
        }

        private static void append(StringBuilder builder, String value)
        {
            if (builder.length() > 0)
            {
                builder.append('\n');
            }
            builder.append(value == null ? "" : value.trim());
        }
    }

    static final class EditorLine
    {
        private final ObjectProperty<TransactionLineEditorModel.Option> account = new SimpleObjectProperty<>();
        private final ObjectProperty<TransactionLineEditorModel.Option> fund = new SimpleObjectProperty<>();
        private final ObjectProperty<TransactionLineEditorModel.Option> budget = new SimpleObjectProperty<>();
        private final ObjectProperty<TransactionLineEditorModel.Option> activity = new SimpleObjectProperty<>();
        private final ObjectProperty<TransactionLineEditorModel.Option> merchant = new SimpleObjectProperty<>();
        private final StringProperty debit = new SimpleStringProperty("");
        private final StringProperty credit = new SimpleStringProperty("");
        private final BooleanProperty nmr = new SimpleBooleanProperty(false);
        private final StringProperty notes = new SimpleStringProperty("");
        private final StringProperty bankState = new SimpleStringProperty("");
        private final StringProperty clearedOn = new SimpleStringProperty("");
        private Long reconciliationSessionId;

        ObjectProperty<TransactionLineEditorModel.Option> accountProperty() { return account; }
        ObjectProperty<TransactionLineEditorModel.Option> fundProperty() { return fund; }
        ObjectProperty<TransactionLineEditorModel.Option> budgetProperty() { return budget; }
        ObjectProperty<TransactionLineEditorModel.Option> activityProperty() { return activity; }
        ObjectProperty<TransactionLineEditorModel.Option> merchantProperty() { return merchant; }
        StringProperty debitProperty() { return debit; }
        StringProperty creditProperty() { return credit; }
        BooleanProperty nmrProperty() { return nmr; }
        StringProperty notesProperty() { return notes; }
        StringProperty bankStateProperty() { return bankState; }
        StringProperty clearedOnProperty() { return clearedOn; }

        TransactionLineEditorModel.Option getAccount() { return account.get(); }
        void setAccount(TransactionLineEditorModel.Option value) { account.set(value); }
        TransactionLineEditorModel.Option getFund() { return fund.get(); }
        void setFund(TransactionLineEditorModel.Option value) { fund.set(value); }
        TransactionLineEditorModel.Option getBudget() { return budget.get(); }
        void setBudget(TransactionLineEditorModel.Option value) { budget.set(value); }
        TransactionLineEditorModel.Option getActivity() { return activity.get(); }
        void setActivity(TransactionLineEditorModel.Option value) { activity.set(value); }
        TransactionLineEditorModel.Option getMerchant() { return merchant.get(); }
        void setMerchant(TransactionLineEditorModel.Option value) { merchant.set(value); }
        String getDebit() { return debit.get(); }
        void setDebit(String value) { debit.set(safe(value)); }
        String getCredit() { return credit.get(); }
        void setCredit(String value) { credit.set(safe(value)); }
        boolean isNmr() { return nmr.get(); }
        void setNmr(boolean value) { nmr.set(value); }
        String getNotes() { return notes.get(); }
        void setNotes(String value) { notes.set(safe(value)); }
        void setBankState(String value) { bankState.set(safe(value)); }
        void setClearedOn(String value) { clearedOn.set(safe(value)); }
        Long getReconciliationSessionId() { return reconciliationSessionId; }
        void setReconciliationSessionId(Long value) { reconciliationSessionId = value; }

        boolean hasAnyInput()
        {
            return getAccount() != null || getFund() != null || getBudget() != null || getActivity() != null
                    || getMerchant() != null || parseMoney(getDebit()) != null && parseMoney(getDebit()).signum() != 0
                    || parseMoney(getCredit()) != null && parseMoney(getCredit()).signum() != 0
                    || isNmr() || !getNotes().isBlank();
        }

        EditorLine copy()
        {
            EditorLine copy = new EditorLine();
            copy.setAccount(getAccount());
            copy.setFund(getFund());
            copy.setBudget(getBudget());
            copy.setActivity(getActivity());
            copy.setMerchant(getMerchant());
            copy.setDebit(getDebit());
            copy.setCredit(getCredit());
            copy.setNmr(isNmr());
            copy.setNotes(getNotes());
            return copy;
        }
    }

    static final class SupplementalRow
    {
        private final StringProperty entryRef = new SimpleStringProperty("");
        private final StringProperty counterparty = new SimpleStringProperty("");
        private final StringProperty description = new SimpleStringProperty("");
        private final StringProperty reference = new SimpleStringProperty("");
        private final StringProperty amount = new SimpleStringProperty("0.00");
        private final StringProperty dueDate = new SimpleStringProperty("");
        private final StringProperty startDate = new SimpleStringProperty("");
        private final StringProperty endDate = new SimpleStringProperty("");
        private final StringProperty notes = new SimpleStringProperty("");

        static SupplementalRow from(TransactionSupplementalLineView view)
        {
            SupplementalRow row = new SupplementalRow();
            row.setEntryRef(view.entryRef());
            row.setCounterparty(view.counterparty());
            row.setDescription(view.description());
            row.setReference(view.reference());
            row.setAmount(normalizeMoney(view.amount().toPlainString()));
            row.setDueDate(view.dueDate() == null ? "" : view.dueDate().toString());
            row.setStartDate(view.startDate() == null ? "" : view.startDate().toString());
            row.setEndDate(view.endDate() == null ? "" : view.endDate().toString());
            row.setNotes(view.notes());
            return row;
        }

        StringProperty entryRefProperty() { return entryRef; }
        StringProperty counterpartyProperty() { return counterparty; }
        StringProperty descriptionProperty() { return description; }
        StringProperty referenceProperty() { return reference; }
        StringProperty amountProperty() { return amount; }
        StringProperty dueDateProperty() { return dueDate; }
        StringProperty startDateProperty() { return startDate; }
        StringProperty endDateProperty() { return endDate; }
        StringProperty notesProperty() { return notes; }

        String getEntryRef() { return entryRef.get(); }
        void setEntryRef(String value) { entryRef.set(safe(value)); }
        String getCounterparty() { return counterparty.get(); }
        void setCounterparty(String value) { counterparty.set(safe(value)); }
        String getDescription() { return description.get(); }
        void setDescription(String value) { description.set(safe(value)); }
        String getReference() { return reference.get(); }
        void setReference(String value) { reference.set(safe(value)); }
        String getAmount() { return amount.get(); }
        void setAmount(String value) { amount.set(safe(value)); }
        String getDueDate() { return dueDate.get(); }
        void setDueDate(String value) { dueDate.set(safe(value)); }
        String getStartDate() { return startDate.get(); }
        void setStartDate(String value) { startDate.set(safe(value)); }
        String getEndDate() { return endDate.get(); }
        void setEndDate(String value) { endDate.set(safe(value)); }
        String getNotes() { return notes.get(); }
        void setNotes(String value) { notes.set(safe(value)); }

        boolean hasAnyInput()
        {
            BigDecimal parsedAmount = parseMoney(getAmount());
            return !getEntryRef().isBlank() || !getCounterparty().isBlank() || !getDescription().isBlank()
                    || !getReference().isBlank() || parsedAmount == null || parsedAmount.signum() != 0
                    || !getDueDate().isBlank() || !getStartDate().isBlank() || !getEndDate().isBlank()
                    || !getNotes().isBlank();
        }
    }
}
