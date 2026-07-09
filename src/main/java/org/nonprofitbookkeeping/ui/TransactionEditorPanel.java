package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.CorrectionMethod;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.service.AccountingJournalProjection;
import org.nonprofitbookkeeping.service.TransactionCommand;
import org.nonprofitbookkeeping.service.TransactionCommandValidator;
import org.nonprofitbookkeeping.service.TransactionEntryService;
import org.nonprofitbookkeeping.service.TransactionLineCommand;
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
import java.util.Set;
import java.util.prefs.Preferences;

/** Transaction entry workspace backed by canonical transaction services. */
public class TransactionEditorPanel implements AppPanel
{
    private static final String SPLIT_TABLE_ID = "transactionEditorSplitTable";
    private static final Preferences TABLE_STATE = Preferences.userNodeForPackage(TransactionEditorPanel.class)
            .node("company-table-state")
            .node(SPLIT_TABLE_ID);
    private static final Preferences SUPPLEMENTAL_TABLE_STATE = Preferences.userNodeForPackage(TransactionEditorPanel.class)
            .node("company-table-state")
            .node("transactionEditorSupplementalTables");

    private final BorderPane root = new BorderPane();
    private final TabPane workspaceTabs = new TabPane();
    private final TableView<SplitRow> splitTable = new TableView<>();
    private final Label status = new Label("Prepare split lines, then save to the canonical ledger.");
    private final TransactionLineEditorModel lineEditorModel = new TransactionLineEditorModel(new TransactionCommandValidator());
    private final Label totals = new Label("Debits=0.00 Credits=0.00 Difference=0.00");
    private final Label debitTotal = new Label("$0.00");
    private final Label creditTotal = new Label("$0.00");
    private final Label differenceTotal = new Label("$0.00");
    private final Label statusBadge = new Label("Needs attention");
    private final Label validationMessage = new Label("Enter at least two split lines and balance debits and credits.");
    private final TextField dateField = new TextField();
    private final TextArea memoField = new TextArea();
    private final TextField payeeField = new TextField();
    private final TextField checkNumberField = new TextField();
    private final TextField bankField = new TextField();
    private final TextField clearingBankField = new TextField();
    private final CheckBox reconciledCheckBox = new CheckBox("Reconciled");
    private final TextField budgetTrackingField = new TextField();
    private final ComboBox<String> fundNameField = new ComboBox<>();
    private final CheckBox donationSchedule = new CheckBox("Donation schedule");
    private final TextField donationIdField = new TextField();
    private final TextField donorIdField = new TextField();
    private final TextField donorNameField = new TextField();
    private final Button editDonor = new Button("Edit Selected Donor");
    private final Button openSavedInLedger = new Button("Open Saved in Ledger");
    private final Button deleteTransaction = new Button("Delete");
    private final Map<SupplementalKind, CheckBox> supplementalSelections = new EnumMap<>(SupplementalKind.class);
    private final Map<SupplementalKind, Tab> supplementalTabs = new EnumMap<>(SupplementalKind.class);
    private final Map<SupplementalKind, TableView<SupplementalRow>> supplementalTables = new EnumMap<>(SupplementalKind.class);
    private ValidationResult lastValidationResult;
    private EditorMode editorMode = EditorMode.NEW;
    private Long editTransactionId;
    private Long lastSavedTransactionId;
    private boolean dirty;
    private boolean restoringTableState;

    public TransactionEditorPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Transaction Editor — New/Edit Journal Entry");
        title.setId("transactionEditorTitleLabel");
        title.getStyleClass().add("panel-title");

        Button save = new Button("Save");
        save.setId("transactionEditorSaveButton");
        Button validate = new Button("Validate");
        validate.setId("transactionEditorValidateButton");
        Button journal = new Button("Journal View");
        journal.setId("transactionEditorJournalViewButton");
        openSavedInLedger.setId("transactionEditorOpenSavedInLedgerButton");
        openSavedInLedger.setDisable(true);
        deleteTransaction.setId("transactionEditorDeleteButton");
        deleteTransaction.setText(deleteActionLabel(MainWindow.sharedSessionState().preferences().correctionMethod()));
        deleteTransaction.setDisable(true);
        HBox actions = new HBox(8, save, validate, journal, openSavedInLedger, deleteTransaction);

        status.setId("transactionEditorStatusLabel");
        totals.setId("transactionEditorTotalsLabel");
        dateField.setId("transactionEditorDateField");
        payeeField.setId("transactionEditorPayeeField");
        memoField.setId("transactionEditorMemoField");
        bankField.setId("transactionEditorBankField");
        splitTable.setId(SPLIT_TABLE_ID);

        configureHeaderFields();
        buildSplitTable();
        configureSupplementalFields();
        workspaceTabs.getTabs().setAll(
                tab("1. Header", headerPage()),
                tab("2. Entry Lines", entryLinesPage()),
                tab("3. Additional Details", additionalDetailsPage()),
                tab("4. Donation Subschedule", donationPage()),
                tab("5. Supplemental Details", supplementalDetailsPage()));

        root.setTop(new VBox(6, title, actions, status, new Separator()));
        root.setCenter(workspaceTabs);

        save.setOnAction(e -> onSave());
        validate.setOnAction(e -> validateOrPost());
        journal.setOnAction(e -> showJournal());
        openSavedInLedger.setOnAction(e -> openSavedTransactionInLedger());
        deleteTransaction.setOnAction(e -> deleteCurrentTransaction());
        refreshTotals();
    }

    private Tab tab(String title, Node content)
    {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private void configureHeaderFields()
    {
        dateField.setPromptText("YYYY-MM-DD");
        memoField.setPromptText("Memo");
        memoField.setPrefRowCount(3);
        memoField.setWrapText(true);
        fundNameField.setPromptText("Select fund");
        List.of(dateField, payeeField, checkNumberField, bankField, clearingBankField, budgetTrackingField, donationIdField, donorIdField, donorNameField)
                .forEach(field -> field.textProperty().addListener((observable, oldValue, newValue) -> dirty = true));
        memoField.textProperty().addListener((observable, oldValue, newValue) -> dirty = true);
        reconciledCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> dirty = true);
        donationSchedule.selectedProperty().addListener((observable, oldValue, enabled) -> {
            dirty = true;
            updateDonationFields();
        });
        editDonor.setDisable(true);
        editDonor.setTooltip(new Tooltip("Donor editing is not yet backed by a P03 H2 donor service in this application."));
    }

    private Node headerPage()
    {
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.setPadding(new Insets(8));
        form.add(new Label("Date"), 0, 0);
        form.add(dateField, 1, 0);
        form.add(new Label("Memo"), 0, 1);
        form.add(memoField, 1, 1, 3, 1);
        form.getColumnConstraints().addAll(new ColumnConstraints(80), new ColumnConstraints(220), new ColumnConstraints(80), new ColumnConstraints(400));
        form.getColumnConstraints().get(1).setHgrow(Priority.SOMETIMES);
        form.getColumnConstraints().get(3).setHgrow(Priority.ALWAYS);

        HBox summary = new HBox(18,
                labelledValue("Debit", debitTotal),
                labelledValue("Credit", creditTotal),
                labelledValue("Difference", differenceTotal),
                statusBadge);
        validationMessage.setWrapText(true);
        VBox page = new VBox(12, new Label("New / Edit Journal Entry"), form, summary, validationMessage);
        page.setPadding(new Insets(8));
        return page;
    }

    private Node entryLinesPage()
    {
        Button addLine = new Button("Add Line");
        Button duplicate = new Button("Duplicate");
        Button removeLine = new Button("Remove");
        ToolBar toolbar = new ToolBar(addLine, duplicate, removeLine);
        addLine.setOnAction(e -> addEmptySplitRow());
        duplicate.setOnAction(e -> duplicateSelectedSplitRow());
        removeLine.setOnAction(e -> removeSelectedSplitRow());

        VBox totalsPane = new VBox(6, totals);
        totalsPane.setPadding(new Insets(4));
        SplitPane splitPane = new SplitPane(splitTable, totalsPane);
        splitPane.setId("transactionEditorEntryLinesSplitPane");
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.setDividerPositions(0.88);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        VBox tablePane = new VBox(8, new Label("Entry Lines"), toolbar, splitPane);
        tablePane.setPadding(new Insets(8));
        return tablePane;
    }

    private Node additionalDetailsPage()
    {
        HBox cards = new HBox(12,
                card("Party / Document", partyDocumentGrid()),
                card("Bank / Reconciliation", bankGrid()),
                card("Budget / Fund", budgetGrid()));
        cards.setPadding(new Insets(8));
        cards.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));
        VBox page = new VBox(8, new Label("Additional Details"), cards,
                new Label("These fields preserve the current transaction-editor metadata surface. Only fields currently supported by TransactionEntryService are authoritative on save."));
        page.setPadding(new Insets(8));
        return page;
    }

    private GridPane partyDocumentGrid()
    {
        GridPane grid = detailGrid();
        grid.add(new Label("To / From"), 0, 0);
        grid.add(payeeField, 1, 0);
        grid.add(new Label("Check #"), 0, 1);
        grid.add(checkNumberField, 1, 1);
        return grid;
    }

    private GridPane bankGrid()
    {
        GridPane grid = detailGrid();
        grid.add(new Label("Bank"), 0, 0);
        grid.add(bankField, 1, 0);
        grid.add(new Label("Clearing Bank"), 0, 1);
        grid.add(clearingBankField, 1, 1);
        grid.add(new Label("Reconciliation"), 0, 2);
        grid.add(reconciledCheckBox, 1, 2);
        return grid;
    }

    private GridPane budgetGrid()
    {
        GridPane grid = detailGrid();
        grid.add(new Label("Budget Tracking"), 0, 0);
        grid.add(budgetTrackingField, 1, 0);
        grid.add(new Label("Fund Name"), 0, 1);
        grid.add(fundNameField, 1, 1);
        return grid;
    }

    private Node donationPage()
    {
        GridPane grid = detailGrid();
        grid.add(new Label("Use Donation Schedule"), 0, 0);
        grid.add(donationSchedule, 1, 0);
        grid.add(new Label("Donation ID"), 0, 1);
        grid.add(donationIdField, 1, 1);
        grid.add(new Label("Donor ID"), 0, 2);
        grid.add(donorIdField, 1, 2);
        grid.add(new Label("Donor Name"), 0, 3);
        grid.add(donorNameField, 1, 3);
        grid.add(new Label("Donor"), 0, 4);
        grid.add(editDonor, 1, 4);
        updateDonationFields();
        VBox page = new VBox(8, new Label("Donation Subschedule"), grid,
                new Label("Donation fields are transaction-local detail fields until a donor/donation H2 service is introduced."));
        page.setPadding(new Insets(8));
        return page;
    }

    private Node supplementalDetailsPage()
    {
        TabPane tabs = new TabPane();
        FlowPane toggles = new FlowPane(8, 8);
        toggles.setId("transactionEditorSupplementalToggles");
        for (SupplementalKind kind : SupplementalKind.values())
        {
            CheckBox checkBox = new CheckBox(kind.toggleLabel());
            checkBox.setOnAction(event -> {
                dirty = true;
                updateSupplementalTabAvailability();
            });
            supplementalSelections.put(kind, checkBox);
            toggles.getChildren().add(checkBox);
            Tab tab = supplementalTab(kind);
            supplementalTabs.put(kind, tab);
            tabs.getTabs().add(tab);
        }
        updateSupplementalTabAvailability();
        VBox page = new VBox(8, new Label("Supplemental Schedule Details"), toggles,
                new Label("These editable transaction-local detail panels follow the NonprofitAccounting supplemental schedule design reference. They are not the eliminated generic Schedules module."), tabs);
        page.setPadding(new Insets(8));
        VBox.setVgrow(tabs, Priority.ALWAYS);
        return page;
    }

    private Tab supplementalTab(SupplementalKind kind)
    {
        TableView<SupplementalRow> table = new TableView<>();
        table.setId("transactionEditorSupplemental" + kind.name() + "Table");
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setItems(FXCollections.observableArrayList());
        table.getColumns().add(supplementalColumn("Link to Entry", "entry", SupplementalRow::linkToEntry, SupplementalRow::setLinkToEntry, 150));
        table.getColumns().add(supplementalColumn("Counterparty", "counterparty", SupplementalRow::counterparty, SupplementalRow::setCounterparty, 180));
        table.getColumns().add(supplementalColumn("Description", "description", SupplementalRow::description, SupplementalRow::setDescription, 260));
        table.getColumns().add(supplementalColumn("Reference", "reference", SupplementalRow::reference, SupplementalRow::setReference, 160));
        table.getColumns().add(supplementalColumn("Amount", "amount", SupplementalRow::amount, (row, value) -> row.setAmount(formatAmountInput(value)), 120));
        if (kind.showDueDate())
        {
            table.getColumns().add(supplementalColumn("Due Date", "dueDate", SupplementalRow::dueDate, (row, value) -> row.setDueDate(formatDateInput(value)), 120));
        }
        if (kind.showStartEnd())
        {
            table.getColumns().add(supplementalColumn("Start Date", "startDate", SupplementalRow::startDate, (row, value) -> row.setStartDate(formatDateInput(value)), 120));
            table.getColumns().add(supplementalColumn("End Date", "endDate", SupplementalRow::endDate, (row, value) -> row.setEndDate(formatDateInput(value)), 120));
        }
        table.getColumns().add(supplementalColumn("Notes", "notes", SupplementalRow::notes, SupplementalRow::setNotes, 260));
        table.setPlaceholder(new Label("No " + kind.tabTitle().toLowerCase(Locale.ROOT) + " supplemental details for this transaction."));
        restoreSupplementalTableState(kind, table);
        installSupplementalTableStatePersistence(kind, table);
        supplementalTables.put(kind, table);

        Button add = new Button("Add");
        Button remove = new Button("Remove");
        add.setOnAction(event -> addSupplementalRow(kind));
        remove.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(event -> removeSupplementalRow(kind));
        Label validation = new Label("Description and non-negative amount are required for rows that contain supplemental detail data.");
        validation.setWrapText(true);
        VBox validationPane = new VBox(6, validation);
        validationPane.setPadding(new Insets(4));
        SplitPane splitPane = new SplitPane(table, validationPane);
        splitPane.setId("transactionEditorSupplemental" + kind.name() + "SplitPane");
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.setDividerPositions(0.86);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        VBox content = new VBox(8, new HBox(8, add, remove), splitPane);
        VBox.setVgrow(content, Priority.ALWAYS);
        Tab tab = new Tab(kind.tabTitle(), content);
        tab.setClosable(false);
        return tab;
    }

    private TableColumn<SupplementalRow, String> supplementalColumn(String title,
                                                                    String key,
                                                                    java.util.function.Function<SupplementalRow, String> getter,
                                                                    java.util.function.BiConsumer<SupplementalRow, String> setter,
                                                                    double width)
    {
        TableColumn<SupplementalRow, String> column = new TableColumn<>(title);
        column.setId(key);
        column.setUserData(key);
        column.setPrefWidth(width);
        column.setMinWidth(80);
        column.setResizable(true);
        column.setReorderable(true);
        column.setSortable(true);
        column.setCellValueFactory(value -> new SimpleStringProperty(getter.apply(value.getValue())));
        column.setCellFactory(c -> new SupplementalFocusCommitTextCell());
        column.setOnEditCommit(event -> {
            setter.accept(event.getRowValue(), event.getNewValue());
            dirty = true;
            validateSupplementalRows();
            event.getTableView().refresh();
        });
        return column;
    }

    private void addSupplementalRow(SupplementalKind kind)
    {
        TableView<SupplementalRow> table = supplementalTables.get(kind);
        CheckBox selection = supplementalSelections.get(kind);
        if (selection != null && !selection.isSelected())
        {
            selection.setSelected(true);
            updateSupplementalTabAvailability();
        }
        SupplementalRow row = new SupplementalRow();
        table.getItems().add(row);
        table.getSelectionModel().select(row);
        table.scrollTo(row);
        if (!table.getColumns().isEmpty())
        {
            table.edit(table.getItems().indexOf(row), table.getColumns().get(0));
        }
        dirty = true;
        status.setText("Added " + kind.tabTitle() + " supplemental detail row.");
    }

    private void removeSupplementalRow(SupplementalKind kind)
    {
        TableView<SupplementalRow> table = supplementalTables.get(kind);
        SupplementalRow selected = table.getSelectionModel().getSelectedItem();
        if (selected != null)
        {
            table.getItems().remove(selected);
            dirty = true;
            status.setText("Removed " + kind.tabTitle() + " supplemental detail row.");
            validateSupplementalRows();
        }
    }

    private void updateSupplementalTabAvailability()
    {
        for (SupplementalKind kind : SupplementalKind.values())
        {
            Tab tab = supplementalTabs.get(kind);
            CheckBox checkBox = supplementalSelections.get(kind);
            if (tab != null && checkBox != null)
            {
                tab.setDisable(!checkBox.isSelected());
            }
        }
    }

    private List<String> validateSupplementalRows()
    {
        List<String> errors = new ArrayList<>();
        for (SupplementalKind kind : SupplementalKind.values())
        {
            CheckBox enabled = supplementalSelections.get(kind);
            if (enabled == null || !enabled.isSelected())
            {
                continue;
            }
            TableView<SupplementalRow> table = supplementalTables.get(kind);
            if (table == null)
            {
                continue;
            }
            int rowNo = 0;
            for (SupplementalRow row : table.getItems())
            {
                rowNo++;
                if (!row.hasAnyInput())
                {
                    continue;
                }
                if (row.description().isBlank())
                {
                    errors.add(kind.tabTitle() + " row " + rowNo + ": Description is required.");
                }
                BigDecimal amount = parseOptionalAmount(row.amount());
                if (amount == null)
                {
                    errors.add(kind.tabTitle() + " row " + rowNo + ": Amount must be numeric.");
                }
                else if (amount.signum() < 0)
                {
                    errors.add(kind.tabTitle() + " row " + rowNo + ": Amount must be >= 0.");
                }
                if (kind.showStartEnd())
                {
                    LocalDate start = parseDateOrNull(row.startDate());
                    LocalDate end = parseDateOrNull(row.endDate());
                    if ((!row.startDate().isBlank() && start == null) || (!row.endDate().isBlank() && end == null))
                    {
                        errors.add(kind.tabTitle() + " row " + rowNo + ": Start and End dates must be YYYY-MM-DD.");
                    }
                    else if ((start == null) != (end == null))
                    {
                        errors.add(kind.tabTitle() + " row " + rowNo + ": Start and End date must both be set.");
                    }
                    else if (start != null && start.isAfter(end))
                    {
                        errors.add(kind.tabTitle() + " row " + rowNo + ": Start date must be <= End date.");
                    }
                }
                if (kind.showDueDate() && !row.dueDate().isBlank() && parseDateOrNull(row.dueDate()) == null)
                {
                    errors.add(kind.tabTitle() + " row " + rowNo + ": Due Date must be YYYY-MM-DD.");
                }
            }
        }
        if (!errors.isEmpty())
        {
            status.setText(String.join(" ", errors));
        }
        return errors;
    }

    private GridPane detailGrid()
    {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.getColumnConstraints().addAll(new ColumnConstraints(120), new ColumnConstraints(260));
        grid.getColumnConstraints().get(1).setHgrow(Priority.ALWAYS);
        return grid;
    }

    private Node card(String title, Node content)
    {
        VBox card = new VBox(8, new Label(title), content);
        card.getStyleClass().add("dashboard-card");
        card.setPadding(new Insets(8));
        return card;
    }

    private Node labelledValue(String label, Label value)
    {
        return new HBox(5, new Label(label + ":"), value);
    }

    private void configureSupplementalFields()
    {
        fundNameField.setEditable(true);
    }

    private void updateDonationFields()
    {
        boolean enabled = donationSchedule.isSelected();
        donationIdField.setDisable(!enabled);
        donorIdField.setDisable(!enabled);
        donorNameField.setDisable(!enabled);
        if (enabled && donationIdField.getText().isBlank())
        {
            donationIdField.setText("pending-save");
        }
    }

    private void buildSplitTable()
    {
        splitTable.setEditable(true);
        splitTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        splitTable.getColumns().add(optionCol("Account", "account", 260, TransactionLineEditorModel.ReferenceData::accounts, SplitRow::account, SplitRow::setAccount));
        splitTable.getColumns().add(optionCol("Fund", "fund", 180, TransactionLineEditorModel.ReferenceData::funds, SplitRow::fund, SplitRow::setFund));
        splitTable.getColumns().add(optionCol("Budget", "budget", 160, TransactionLineEditorModel.ReferenceData::budgetCategories, SplitRow::budgetCategory, SplitRow::setBudgetCategory));
        splitTable.getColumns().add(editableCol("Debit", "debit", 120, SplitRow::debit, (row, value) -> {
            row.setDebit(formatAmountInput(value));
            if (!isBlank(value) && parseOptionalAmount(value) != null && parseOptionalAmount(value).signum() > 0)
            {
                row.setCredit("");
            }
        }));
        splitTable.getColumns().add(editableCol("Credit", "credit", 120, SplitRow::credit, (row, value) -> {
            row.setCredit(formatAmountInput(value));
            if (!isBlank(value) && parseOptionalAmount(value) != null && parseOptionalAmount(value).signum() > 0)
            {
                row.setDebit("");
            }
        }));
        splitTable.getColumns().add(optionCol("Activity", "activity", 150, TransactionLineEditorModel.ReferenceData::activities, SplitRow::activity, SplitRow::setActivity));
        splitTable.getColumns().add(optionCol("Merchant", "merchant", 150, TransactionLineEditorModel.ReferenceData::merchants, SplitRow::merchant, SplitRow::setMerchant));
        splitTable.getColumns().add(optionCol("Counterparty", "counterparty", 160, TransactionLineEditorModel.ReferenceData::counterparties, SplitRow::counterparty, SplitRow::setCounterparty));
        splitTable.getColumns().add(editableCol("NMR", "nmr", 90, SplitRow::nmr, SplitRow::setNmr));
        splitTable.getColumns().add(editableCol("Notes", "notes", 260, SplitRow::notes, SplitRow::setNotes));
        splitTable.getItems().addAll(
                new SplitRow("", "", "", "", "", "", "", "", "", ""),
                new SplitRow("", "", "", "", "", "", "", "", "", ""));
        restoreTableState();
        installTableStatePersistence();
        UiAsync.run("txn-editor-reference-data", () -> UiServiceRegistry.transactionReferenceData().loadActiveReferenceData(),
                referenceData -> {
                    lineEditorModel.replaceOptions(referenceData);
                    splitTable.getProperties().put("referenceData", referenceData);
                    splitTable.refresh();
                },
                ex -> status.setText("Reference choices unavailable: " + UiErrors.safeMessage(ex)));
        splitTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.INSERT)
            {
                addEmptySplitRow();
                event.consume();
            }
        });
    }

    private TableColumn<SplitRow, String> optionCol(String name,
                                                     String key,
                                                     double prefWidth,
                                                     java.util.function.Function<TransactionLineEditorModel.ReferenceData, List<TransactionLineEditorModel.Option>> options,
                                                     java.util.function.Function<SplitRow, String> labelGetter,
                                                     RowOptionSetter setter)
    {
        TableColumn<SplitRow, String> column = configuredColumn(name, key, prefWidth);
        column.setCellValueFactory(value -> new SimpleStringProperty(labelGetter.apply(value.getValue())));
        column.setCellFactory(c -> new OptionCommitCell(options));
        column.setOnEditCommit(event -> {
            TransactionLineEditorModel.Option option = optionByLabel(options, event.getNewValue());
            setter.accept(event.getRowValue(), option);
            syncModelRow(event.getTablePosition().getRow(), event.getRowValue());
            dirty = true;
            refreshTotals();
        });
        return column;
    }

    private TableColumn<SplitRow, String> editableCol(String name,
                                                       String key,
                                                       double prefWidth,
                                                       java.util.function.Function<SplitRow, String> getter,
                                                       java.util.function.BiConsumer<SplitRow, String> setter)
    {
        TableColumn<SplitRow, String> column = configuredColumn(name, key, prefWidth);
        column.setCellValueFactory(value -> new SimpleStringProperty(getter.apply(value.getValue())));
        column.setCellFactory(c -> new FocusCommitTextCell());
        column.setOnEditCommit(event -> {
            setter.accept(event.getRowValue(), event.getNewValue());
            syncModelRow(event.getTablePosition().getRow(), event.getRowValue());
            dirty = true;
            refreshTotals();
            splitTable.refresh();
        });
        return column;
    }

    private TableColumn<SplitRow, String> configuredColumn(String name, String key, double prefWidth)
    {
        TableColumn<SplitRow, String> column = new TableColumn<>(name);
        column.setId(key);
        column.setUserData(key);
        column.setPrefWidth(prefWidth);
        column.setMinWidth(72);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        return column;
    }

    private void installTableStatePersistence()
    {
        splitTable.getColumns().addListener((ListChangeListener<TableColumn<SplitRow, ?>>) change -> saveTableState());
        splitTable.getSortOrder().addListener((ListChangeListener<TableColumn<SplitRow, ?>>) change -> saveTableState());
        for (TableColumn<SplitRow, ?> column : splitTable.getColumns())
        {
            column.widthProperty().addListener((obs, oldWidth, newWidth) -> saveTableState());
            column.sortTypeProperty().addListener((obs, oldType, newType) -> saveTableState());
        }
    }

    private void restoreTableState()
    {
        restoringTableState = true;
        try
        {
            String prefix = tableStatePrefix();
            for (TableColumn<SplitRow, ?> column : splitTable.getColumns())
            {
                column.setPrefWidth(TABLE_STATE.getDouble(prefix + columnKey(column) + ".width", column.getPrefWidth()));
                String sort = TABLE_STATE.get(prefix + columnKey(column) + ".sort", "");
                if ("ASCENDING".equals(sort)) column.setSortType(TableColumn.SortType.ASCENDING);
                else if ("DESCENDING".equals(sort)) column.setSortType(TableColumn.SortType.DESCENDING);
            }
            restoreColumnOrder(prefix, splitTable);
            restoreSortOrder(prefix, splitTable);
        }
        finally
        {
            restoringTableState = false;
        }
    }

    private void saveTableState()
    {
        if (restoringTableState) return;
        String prefix = tableStatePrefix();
        TABLE_STATE.put(prefix + "order", String.join(",", splitTable.getColumns().stream().map(TransactionEditorPanel::columnKey).toList()));
        TABLE_STATE.put(prefix + "sortOrder", String.join(",", splitTable.getSortOrder().stream().map(TransactionEditorPanel::columnKey).toList()));
        for (TableColumn<SplitRow, ?> column : splitTable.getColumns())
        {
            TABLE_STATE.putDouble(prefix + columnKey(column) + ".width", column.getWidth() > 0 ? column.getWidth() : column.getPrefWidth());
            TABLE_STATE.put(prefix + columnKey(column) + ".sort", column.getSortType() == null ? "" : column.getSortType().name());
        }
    }

    private void restoreColumnOrder(String prefix, TableView<?> table)
    {
        String order = TABLE_STATE.get(prefix + "order", "");
        if (order.isBlank()) return;
        List<String> keys = List.of(order.split(","));
        List<TableColumn<?, ?>> columns = new ArrayList<>(table.getColumns());
        columns.sort(Comparator.comparingInt(column -> {
            int index = keys.indexOf(columnKey(column));
            return index < 0 ? Integer.MAX_VALUE : index;
        }));
        table.getColumns().setAll(columns);
    }

    private void restoreSortOrder(String prefix, TableView<?> table)
    {
        String sortOrder = TABLE_STATE.get(prefix + "sortOrder", "");
        if (sortOrder.isBlank()) return;
        List<String> keys = List.of(sortOrder.split(","));
        List<TableColumn<?, ?>> restored = new ArrayList<>();
        for (String key : keys)
        {
            table.getColumns().stream().filter(column -> Objects.equals(columnKey(column), key)).findFirst().ifPresent(restored::add);
        }
        table.getSortOrder().setAll(restored);
    }

    private void installSupplementalTableStatePersistence(SupplementalKind kind, TableView<SupplementalRow> table)
    {
        table.getColumns().addListener((ListChangeListener<TableColumn<SupplementalRow, ?>>) change -> saveSupplementalTableState(kind, table));
        table.getSortOrder().addListener((ListChangeListener<TableColumn<SupplementalRow, ?>>) change -> saveSupplementalTableState(kind, table));
        for (TableColumn<SupplementalRow, ?> column : table.getColumns())
        {
            column.widthProperty().addListener((obs, oldWidth, newWidth) -> saveSupplementalTableState(kind, table));
            column.sortTypeProperty().addListener((obs, oldType, newType) -> saveSupplementalTableState(kind, table));
        }
    }

    private void restoreSupplementalTableState(SupplementalKind kind, TableView<SupplementalRow> table)
    {
        String prefix = tableStatePrefix() + kind.name() + ".";
        for (TableColumn<SupplementalRow, ?> column : table.getColumns())
        {
            column.setPrefWidth(SUPPLEMENTAL_TABLE_STATE.getDouble(prefix + columnKey(column) + ".width", column.getPrefWidth()));
        }
    }

    private void saveSupplementalTableState(SupplementalKind kind, TableView<SupplementalRow> table)
    {
        String prefix = tableStatePrefix() + kind.name() + ".";
        SUPPLEMENTAL_TABLE_STATE.put(prefix + "order", String.join(",", table.getColumns().stream().map(TransactionEditorPanel::columnKey).toList()));
        SUPPLEMENTAL_TABLE_STATE.put(prefix + "sortOrder", String.join(",", table.getSortOrder().stream().map(TransactionEditorPanel::columnKey).toList()));
        for (TableColumn<SupplementalRow, ?> column : table.getColumns())
        {
            SUPPLEMENTAL_TABLE_STATE.putDouble(prefix + columnKey(column) + ".width", column.getWidth() > 0 ? column.getWidth() : column.getPrefWidth());
            SUPPLEMENTAL_TABLE_STATE.put(prefix + columnKey(column) + ".sort", column.getSortType() == null ? "" : column.getSortType().name());
        }
    }

    private String tableStatePrefix()
    {
        String company = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
        String value = company == null || company.isBlank() ? "DEFAULT" : company.trim().toUpperCase(Locale.ROOT);
        return value.replaceAll("[^A-Z0-9_-]", "_") + ".";
    }

    private static String columnKey(TableColumn<?, ?> column)
    {
        Object key = column.getUserData();
        return key == null ? column.getText() : String.valueOf(key);
    }

    private TransactionLineEditorModel.Option optionByLabel(java.util.function.Function<TransactionLineEditorModel.ReferenceData, List<TransactionLineEditorModel.Option>> options, String label)
    {
        TransactionLineEditorModel.ReferenceData data = currentReferenceData();
        if (data == null || label == null || label.isBlank()) return null;
        return options.apply(data).stream().filter(option -> option.label().equals(label)).findFirst().orElse(null);
    }

    private TransactionLineEditorModel.ReferenceData currentReferenceData()
    {
        Object data = splitTable.getProperties().get("referenceData");
        return data instanceof TransactionLineEditorModel.ReferenceData referenceData ? referenceData : null;
    }

    private void addEmptySplitRow()
    {
        SplitRow row = new SplitRow("", "", "", "", "", "", "", "", "", "");
        splitTable.getItems().add(row);
        lineEditorModel.addRow();
        splitTable.getSelectionModel().select(row);
        splitTable.scrollTo(row);
        splitTable.edit(splitTable.getItems().indexOf(row), splitTable.getColumns().get(0));
        status.setText("Added split row. Enter an account code or choose the Account cell to begin editing.");
        dirty = true;
        refreshTotals();
    }

    private void duplicateSelectedSplitRow()
    {
        SplitRow selected = splitTable.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select an entry line before duplicating.");
            return;
        }
        SplitRow copy = selected.copy();
        int index = splitTable.getSelectionModel().getSelectedIndex() + 1;
        splitTable.getItems().add(index, copy);
        lineEditorModel.addRow();
        for (int i = 0; i < splitTable.getItems().size(); i++) syncModelRow(i, splitTable.getItems().get(i));
        splitTable.getSelectionModel().select(copy);
        dirty = true;
        refreshTotals();
    }

    private void removeSelectedSplitRow()
    {
        int selectedIndex = splitTable.getSelectionModel().getSelectedIndex();
        SplitRow selected = splitTable.getSelectionModel().getSelectedItem();
        if (selected != null && lineEditorModel.removeRow(selectedIndex))
        {
            splitTable.getItems().remove(selected);
            dirty = true;
            refreshTotals();
        }
        else
        {
            status.setText("At least two split rows are required for a balanced transaction.");
        }
    }

    private void syncModelRow(int index, SplitRow splitRow)
    {
        if (index < 0 || index >= lineEditorModel.rows().size()) return;
        TransactionLineEditorModel.Row row = lineEditorModel.rows().get(index);
        row.setAccountId(splitRow.accountId());
        row.setFundId(splitRow.fundId());
        row.setBudgetCategoryId(splitRow.budgetCategoryId());
        row.setActivityId(splitRow.activityId());
        row.setMerchantId(splitRow.merchantId());
        row.setCounterpartyId(splitRow.counterpartyId());
        row.setDebit(parseOptionalAmount(splitRow.debit()));
        row.setCredit(parseOptionalAmount(splitRow.credit()));
        row.setNmr(Boolean.parseBoolean(splitRow.nmr()));
        row.setNotes(splitRow.notes());
    }

    private void validateOrPost()
    {
        for (int i = 0; i < splitTable.getItems().size(); i++) syncModelRow(i, splitTable.getItems().get(i));
        TransactionValidationResult result = lineEditorModel.validate(parseDateOrNull(dateField.getText()), null, memoField.getText(), null);
        List<String> supplementalErrors = validateSupplementalRows();
        int errorCount = result.valid() ? supplementalErrors.size() : result.errors().size() + supplementalErrors.size();
        String message = result.valid() && supplementalErrors.isEmpty()
                ? "Validation result: ready to save through the transaction service."
                : "Validation result: " + String.join(" ", result.errors()) + " " + String.join(" ", supplementalErrors);
        lastValidationResult = new ValidationResult(message.trim(),
                lineEditorModel.toCommand(parseDateOrNull(dateField.getText()), null, memoField.getText(), null).lines().size(),
                errorCount == 0 ? lineEditorModel.toCommand(parseDateOrNull(dateField.getText()), null, memoField.getText(), null).lines().size() : 0,
                errorCount,
                lineEditorModel.totals().difference());
        status.setText(lastValidationResult.message());
        validationMessage.setText(lastValidationResult.message());
        UiDebug.log("transaction-editor", lastValidationResult.message());
    }

    private void refreshTotals()
    {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (SplitRow row : splitTable.getItems())
        {
            BigDecimal rowDebit = parseOptionalAmount(row.debit());
            BigDecimal rowCredit = parseOptionalAmount(row.credit());
            if (rowDebit != null) debit = debit.add(rowDebit);
            if (rowCredit != null) credit = credit.add(rowCredit);
        }
        BigDecimal difference = debit.subtract(credit);
        totals.setText("Debits=" + debit.toPlainString() + " Credits=" + credit.toPlainString() + " Difference=" + difference.toPlainString());
        debitTotal.setText(money(debit));
        creditTotal.setText(money(credit));
        differenceTotal.setText(money(difference.abs()));
        if (difference.compareTo(BigDecimal.ZERO) == 0 && debit.signum() > 0)
        {
            statusBadge.setText("Balanced");
            validationMessage.setText("Transaction is balanced.");
        }
        else
        {
            statusBadge.setText("Needs attention");
            validationMessage.setText("Transaction is not balanced.");
        }
    }

    static String postValidateStatusFor(ValidationResult result)
    {
        if (result == null) return "Validate completed: run validation first to review row readiness.";
        if (result.errorCount() > 0) return "Validate blocked: fix validation errors before posting.";
        if (result.netAmount().compareTo(BigDecimal.ZERO) != 0) return "Validate blocked: split rows are not balanced (net=" + result.netAmount().toPlainString() + ").";
        return "Validate accepted: transaction is balanced and ready to save.";
    }

    private void showJournal()
    {
        if (lastSavedTransactionId == null)
        {
            status.setText("Journal preview unavailable until the transaction has been saved through the transaction service.");
            UiDebug.log("transaction-editor", "Journal View requested without a saved transaction id.");
            return;
        }
        status.setText("Loading journal preview for saved transaction #" + lastSavedTransactionId + "...");
        UiAsync.run("txn-editor-journal-preview", () -> UiServiceRegistry.transactionEntry().journalView(lastSavedTransactionId),
                preview -> status.setText(renderJournalPreview(preview)),
                ex -> status.setText("Journal preview failed: " + UiErrors.safeMessage(ex)));
    }

    static String renderJournalPreview(AccountingJournalProjection projection)
    {
        StringBuilder body = new StringBuilder();
        body.append("Journal preview: Txn #").append(projection.transactionId()).append(" on ").append(projection.date()).append(" (lines: ").append(projection.lines().size()).append(")");
        if (!projection.lines().isEmpty())
        {
            AccountingJournalProjection.Line first = projection.lines().get(0);
            body.append(" | first line ").append(first.accountCode()).append("/").append(first.fundCode() == null ? "" : first.fundCode()).append(" DR=").append(first.debit().toPlainString()).append(" CR=").append(first.credit().toPlainString());
        }
        return body.toString();
    }

    @Override public String title() { return "Transaction Editor"; }
    @Override public Node root() { return root; }

    @Override
    public void onSave()
    {
        LocalDate date = parseDateOrNull(dateField.getText());
        if (date == null)
        {
            status.setText("Save blocked: enter a transaction date as YYYY-MM-DD.");
            return;
        }
        List<String> supplementalErrors = validateSupplementalRows();
        if (!supplementalErrors.isEmpty())
        {
            status.setText("Save blocked: " + String.join(" ", supplementalErrors));
            return;
        }
        for (int i = 0; i < splitTable.getItems().size(); i++) syncModelRow(i, splitTable.getItems().get(i));
        TransactionEntryService service = UiServiceRegistry.transactionEntry();
        UiAsync.<TransactionView>run("txn-editor-save", () -> {
                    TransactionCommand command = lineEditorModel.toCommand(date, null, memoField.getText(), null);
                    return editorMode == EditorMode.EDIT ? service.update(editTransactionId, command) : service.enter(command);
                },
                view -> {
                    boolean wasNew = editorMode == EditorMode.NEW;
                    lastSavedTransactionId = view.id();
                    editorMode = EditorMode.EDIT;
                    editTransactionId = view.id();
                    applySavedView(view);
                    dirty = false;
                    lineEditorModel.markClean();
                    openSavedInLedger.setDisable(false);
                    deleteTransaction.setDisable(false);
                    status.setText((wasNew ? "Saved new transaction #" : "Updated transaction #") + view.id() + " through TransactionEntryService with " + view.lines().size() + " split line(s). Supplemental detail rows remain editable transaction-local fields for this editor session until their H2 service is implemented.");
                },
                ex -> status.setText("Save failed: " + UiErrors.safeMessage(ex)));
    }

    private void resetForNewEntry()
    {
        editorMode = EditorMode.NEW;
        editTransactionId = null;
        deleteTransaction.setDisable(true);
        dateField.clear();
        payeeField.clear();
        checkNumberField.clear();
        memoField.clear();
        bankField.clear();
        clearingBankField.clear();
        budgetTrackingField.clear();
        fundNameField.setValue(null);
        donationSchedule.setSelected(false);
        donationIdField.clear();
        donorIdField.clear();
        donorNameField.clear();
        clearSupplementalRows();
        splitTable.getItems().setAll(new SplitRow("", "", "", "", "", "", "", "", "", ""), new SplitRow("", "", "", "", "", "", "", "", "", ""));
        lineEditorModel.rows().clear();
        lineEditorModel.addRow();
        lineEditorModel.addRow();
        refreshTotals();
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
        updateSupplementalTabAvailability();
    }

    private void applySavedView(TransactionView view)
    {
        dateField.setText(view.date() == null ? "" : view.date().toString());
        memoField.setText(view.memo() == null ? "" : view.memo());
        payeeField.setText(view.payeeName() == null ? "" : view.payeeName());
        bankField.setText(view.bankAccountName() == null ? "" : view.bankAccountName());
        splitTable.getItems().setAll(view.lines().stream().map(SplitRow::fromViewLine).toList());
        while (lineEditorModel.rows().size() < splitTable.getItems().size()) lineEditorModel.addRow();
        for (int i = 0; i < splitTable.getItems().size(); i++) syncModelRow(i, splitTable.getItems().get(i));
        for (int i = splitTable.getItems().size(); i < lineEditorModel.rows().size(); i++)
        {
            TransactionLineEditorModel.Row row = lineEditorModel.rows().get(i);
            row.setAccountId(null); row.setFundId(null); row.setBudgetCategoryId(null); row.setActivityId(null); row.setMerchantId(null); row.setCounterpartyId(null); row.setDebit(BigDecimal.ZERO); row.setCredit(BigDecimal.ZERO); row.setNmr(false); row.setNotes("");
        }
        refreshTotals();
    }

    private void deleteCurrentTransaction()
    {
        Long transactionId = editTransactionId == null ? lastSavedTransactionId : editTransactionId;
        if (transactionId == null)
        {
            status.setText("Delete unavailable until an existing transaction is loaded or saved.");
            return;
        }
        CorrectionMethod method = MainWindow.sharedSessionState().preferences().correctionMethod();
        if (method == CorrectionMethod.DIRECT_EDIT)
        {
            if (!confirmDeleteAction(deleteConfirmationHeader(transactionId), directDeleteConfirmationBody()))
            {
                status.setText("Delete cancelled for Txn #" + transactionId + ".");
                return;
            }
            UiAsync.run("txn-editor-delete-" + transactionId, () -> {
                        UiServiceRegistry.transactionCorrection().delete(transactionId, "ui", "Deleted from Transaction Editor");
                        return transactionId;
                    },
                    deletedId -> {
                        resetForNewEntry();
                        lastSavedTransactionId = null;
                        openSavedInLedger.setDisable(true);
                        dirty = false;
                        lineEditorModel.markClean();
                        status.setText("Deleted Txn #" + deletedId + ". The editor is ready for a new transaction.");
                    },
                    ex -> status.setText("Delete failed for Txn #" + transactionId + ": " + UiErrors.safeMessage(ex)));
        }
        else
        {
            if (!confirmDeleteAction(reversalConfirmationHeader(transactionId), reversalConfirmationBody()))
            {
                status.setText("Reversal cancelled for Txn #" + transactionId + ".");
                return;
            }
            UiAsync.run("txn-editor-reverse-" + transactionId,
                    () -> UiServiceRegistry.transactionCorrection().reverse(transactionId, ActivePeriodContext.get(), "ui", "Reversed from Transaction Editor delete action", false),
                    result -> {
                        resetForNewEntry();
                        lastSavedTransactionId = result.reversalTransactionId();
                        openSavedInLedger.setDisable(false);
                        dirty = false;
                        lineEditorModel.markClean();
                        status.setText("Created reversing Txn #" + result.reversalTransactionId() + " for original Txn #" + transactionId + ".");
                    },
                    ex -> status.setText("Reversal failed for Txn #" + transactionId + ": " + UiErrors.safeMessage(ex)));
        }
    }

    private boolean confirmDeleteAction(String header, String content)
    {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, content, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle("Confirm transaction delete");
        alert.setHeaderText(header);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    static String deleteActionLabel(CorrectionMethod method) { return method == CorrectionMethod.DIRECT_EDIT ? "Delete" : "Reverse instead of deleting"; }
    static String deleteConfirmationHeader(long transactionId) { return "Delete transaction #" + transactionId + "?"; }
    static String directDeleteConfirmationBody() { return "This removes the entered transaction after period and reconciliation checks and writes an audit snapshot."; }
    static String reversalConfirmationHeader(long transactionId) { return "Reverse transaction #" + transactionId + " instead of deleting?"; }
    static String reversalConfirmationBody() { return "Current correction settings do not allow hard deletion. A reversing entry will be created using the active period date."; }

    private void openSavedTransactionInLedger()
    {
        if (lastSavedTransactionId == null)
        {
            status.setText("Save a transaction before opening it in the ledger register.");
            return;
        }
        DrillThroughCoordinator.openLedgerWithContext(savedLedgerContext(lastSavedTransactionId));
        status.setText("Opened saved transaction #" + lastSavedTransactionId + " in Ledger Register.");
    }

    static String savedLedgerContext(long transactionId) { return "Saved transaction Txn #" + transactionId; }

    private void consumeLedgerRegisterContext()
    {
        String context = DrillThroughCoordinator.consumeContext(AppPanelId.TXN_EDITOR);
        Long transactionId = transactionIdFromContext(context);
        if (transactionId == null) return;
        status.setText("Loading transaction #" + transactionId + " from the ledger register...");
        UiAsync.run("txn-editor-load-" + transactionId,
                () -> UiServiceRegistry.transactionEntry().load(transactionId),
                view -> {
                    lastSavedTransactionId = view.id();
                    editorMode = EditorMode.EDIT;
                    editTransactionId = view.id();
                    deleteTransaction.setDisable(false);
                    applySavedView(view);
                    dirty = false;
                    lineEditorModel.markClean();
                    openSavedInLedger.setDisable(false);
                    status.setText("Loaded transaction #" + view.id() + " in Edit mode. Save updates this transaction by ID when policy allows.");
                },
                ex -> status.setText("Could not load transaction #" + transactionId + ": " + UiErrors.safeMessage(ex)));
    }

    static Long transactionIdFromContext(String context)
    {
        if (context == null || context.isBlank()) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("Txn #(\\d+)").matcher(context);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    @Override public boolean hasUnsavedChanges() { return dirty; }
    @Override public void onPanelShown() { consumeLedgerRegisterContext(); }

    @Override
    public RunCommandResult onRunCommand(AppCommand command)
    {
        if (command != AppCommand.POST_VALIDATE) return new RunCommandResult(false, "Unsupported run command: " + command);
        validateOrPost();
        return new RunCommandResult(true, "Validate command delegated to Transaction Editor validation.");
    }

    static ValidationResult validateSplits(List<SplitRow> rows, Set<String> accountCodes, Set<String> fundCodes)
    {
        int nonEmpty = 0;
        int valid = 0;
        int errors = 0;
        BigDecimal net = BigDecimal.ZERO;
        for (SplitRow row : rows)
        {
            boolean hasData = !(isBlank(row.account()) && isBlank(row.fund()) && isBlank(row.debit()) && isBlank(row.credit()));
            if (!hasData) continue;
            nonEmpty++;
            boolean rowValid = true;
            String accountToken = row.account().contains(" — ") ? row.account().substring(0, row.account().indexOf(" — ")) : row.account();
            String fundToken = row.fund().contains(" — ") ? row.fund().substring(0, row.fund().indexOf(" — ")) : row.fund();
            if (isBlank(accountToken) || !accountCodes.contains(accountToken.trim())) rowValid = false;
            if (isBlank(fundToken) || !fundCodes.contains(fundToken.trim())) rowValid = false;
            BigDecimal debit = parseOptionalAmount(row.debit());
            BigDecimal credit = parseOptionalAmount(row.credit());
            if (debit == null || credit == null) rowValid = false;
            else if (debit.signum() < 0 || credit.signum() < 0 || (debit.signum() > 0 && credit.signum() > 0)) rowValid = false;
            else if (debit.signum() == 0 && credit.signum() == 0) rowValid = false;
            else net = net.add(debit).subtract(credit);
            if (rowValid) valid++;
            else errors++;
        }
        if (nonEmpty == 0) return new ValidationResult("Validation result: no split rows entered.", 0, 0, 0, BigDecimal.ZERO);
        String message = "Validation result: rows=" + nonEmpty + ", valid=" + valid + ", errors=" + errors + ", debit-credit difference=" + net.toPlainString();
        if (errors == 0 && net.compareTo(BigDecimal.ZERO) == 0) message += " (ready to save)";
        else if (errors == 0) message += " (warning: not balanced)";
        return new ValidationResult(message, nonEmpty, valid, errors, net);
    }

    static TransactionCommand toTransactionCommand(String date, String memo, List<SplitRow> rows, List<Account> accounts, List<Fund> funds)
    {
        List<TransactionLineCommand> lines = rows.stream()
                .filter(row -> !(isBlank(row.account()) && isBlank(row.fund()) && isBlank(row.debit()) && isBlank(row.credit())))
                .map(row -> new TransactionLineCommand(resolveAccountId(row, accounts), resolveFundId(row, funds), row.budgetCategoryId(), row.activityId(), row.merchantId(), parseOptionalAmount(row.debit()), parseOptionalAmount(row.credit()), Boolean.parseBoolean(row.nmr()), row.notes()))
                .toList();
        return new TransactionCommand(parseDateOrNull(date), null, memo, null, lines);
    }

    private static Long resolveAccountId(SplitRow row, List<Account> accounts)
    {
        if (row.accountId() != null) return row.accountId();
        String code = codeToken(row.account());
        return accounts.stream().filter(account -> code.equals(account.getCode())).map(Account::getId).findFirst().orElse(null);
    }

    private static Long resolveFundId(SplitRow row, List<Fund> funds)
    {
        if (row.fundId() != null) return row.fundId();
        String code = codeToken(row.fund());
        return funds.stream().filter(fund -> code.equals(fund.getCode())).map(Fund::getId).findFirst().orElse(null);
    }

    private static String codeToken(String label)
    {
        if (label == null) return "";
        int separator = label.indexOf(" — ");
        return (separator < 0 ? label : label.substring(0, separator)).trim();
    }

    private static LocalDate parseDateOrNull(String value)
    {
        if (isBlank(value)) return null;
        try { return LocalDate.parse(value.trim()); }
        catch (RuntimeException ex) { return null; }
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }

    private static BigDecimal parseOptionalAmount(String value)
    {
        if (isBlank(value)) return BigDecimal.ZERO;
        try { return new BigDecimal(value.trim().replace("$", "").replace(",", "")); }
        catch (NumberFormatException ex) { return null; }
    }

    private static String formatAmountInput(String value)
    {
        BigDecimal amount = parseOptionalAmount(value);
        return amount == null ? (value == null ? "" : value.trim()) : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatDateInput(String value)
    {
        LocalDate date = parseDateOrNull(value);
        return date == null ? (value == null ? "" : value.trim()) : date.toString();
    }

    private static String money(BigDecimal value)
    {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private final class OptionCommitCell extends TableCell<SplitRow, String>
    {
        private final java.util.function.Function<TransactionLineEditorModel.ReferenceData, List<TransactionLineEditorModel.Option>> options;
        private ComboBox<String> editor;
        private OptionCommitCell(java.util.function.Function<TransactionLineEditorModel.ReferenceData, List<TransactionLineEditorModel.Option>> options) { this.options = options; }
        @Override public void startEdit()
        {
            if (!isEditable() || !getTableView().isEditable() || !getTableColumn().isEditable()) return;
            super.startEdit();
            if (editor == null)
            {
                editor = new ComboBox<>();
                editor.setMaxWidth(Double.MAX_VALUE);
                editor.setOnAction(event -> commitEdit(editor.getValue()));
                editor.focusedProperty().addListener((observable, wasFocused, isFocused) -> { if (!isFocused) commitEdit(editor.getValue()); });
            }
            TransactionLineEditorModel.ReferenceData data = currentReferenceData();
            editor.getItems().setAll(data == null ? List.of() : options.apply(data).stream().map(TransactionLineEditorModel.Option::label).toList());
            editor.setValue(getItem() == null ? "" : getItem());
            setText(null);
            setGraphic(editor);
            editor.show();
        }
        @Override public void updateItem(String item, boolean empty)
        {
            super.updateItem(item, empty);
            if (empty) { setText(null); setGraphic(null); }
            else if (isEditing()) { setText(null); setGraphic(editor); }
            else { setText(item == null ? "" : item); setGraphic(null); }
        }
    }

    private static class FocusCommitTextCell extends TableCell<SplitRow, String>
    {
        private TextField editor;
        @Override public void startEdit()
        {
            if (!isEditable() || !getTableView().isEditable() || !getTableColumn().isEditable()) return;
            super.startEdit();
            if (editor == null)
            {
                editor = new TextField();
                editor.setOnAction(event -> commitEditorValue());
                editor.focusedProperty().addListener((observable, wasFocused, isFocused) -> { if (!isFocused) commitEditorValue(); });
            }
            editor.setText(getItem() == null ? "" : getItem());
            setText(null);
            setGraphic(editor);
            editor.selectAll();
            editor.requestFocus();
        }
        @Override public void cancelEdit()
        {
            if (editor != null && !Objects.equals(getItem(), editor.getText())) { commitEditorValue(); return; }
            super.cancelEdit();
            setText(getItem() == null ? "" : getItem());
            setGraphic(null);
        }
        @Override public void updateItem(String item, boolean empty)
        {
            super.updateItem(item, empty);
            if (empty) { setText(null); setGraphic(null); }
            else if (isEditing() && editor != null) { editor.setText(item == null ? "" : item); setText(null); setGraphic(editor); }
            else { setText(item == null ? "" : item); setGraphic(null); }
        }
        private void commitEditorValue()
        {
            if (editor == null) return;
            String value = editor.getText();
            if (isEditing()) commitEdit(value);
            else commitValueWhenFocusLossAlreadyCancelled(value);
        }
        @Override public void commitEdit(String newValue)
        {
            super.commitEdit(newValue);
            setText(newValue == null ? "" : newValue);
            setGraphic(null);
        }
        private void commitValueWhenFocusLossAlreadyCancelled(String value)
        {
            TableView<SplitRow> table = getTableView();
            TableColumn<SplitRow, String> column = getTableColumn();
            if (table == null || column == null || getIndex() < 0 || getIndex() >= table.getItems().size()) return;
            CellEditEvent<SplitRow, String> event = new CellEditEvent<>(table, new TablePosition<>(table, getIndex(), column), TableColumn.editCommitEvent(), value);
            Event.fireEvent(column, event);
            updateItem(value, false);
        }
    }

    private static class SupplementalFocusCommitTextCell extends TableCell<SupplementalRow, String>
    {
        private TextField editor;
        @Override public void startEdit()
        {
            if (!isEditable() || !getTableView().isEditable() || !getTableColumn().isEditable()) return;
            super.startEdit();
            if (editor == null)
            {
                editor = new TextField();
                editor.setOnAction(event -> commitEditorValue());
                editor.focusedProperty().addListener((observable, wasFocused, isFocused) -> { if (!isFocused) commitEditorValue(); });
            }
            editor.setText(getItem() == null ? "" : getItem());
            setText(null);
            setGraphic(editor);
            editor.selectAll();
            editor.requestFocus();
        }
        @Override public void updateItem(String item, boolean empty)
        {
            super.updateItem(item, empty);
            if (empty) { setText(null); setGraphic(null); }
            else if (isEditing() && editor != null) { editor.setText(item == null ? "" : item); setText(null); setGraphic(editor); }
            else { setText(item == null ? "" : item); setGraphic(null); }
        }
        private void commitEditorValue()
        {
            if (editor == null) return;
            String value = editor.getText();
            if (isEditing()) commitEdit(value);
            else commitValueWhenFocusLossAlreadyCancelled(value);
        }
        @Override public void commitEdit(String newValue)
        {
            super.commitEdit(newValue);
            setText(newValue == null ? "" : newValue);
            setGraphic(null);
        }
        private void commitValueWhenFocusLossAlreadyCancelled(String value)
        {
            TableView<SupplementalRow> table = getTableView();
            TableColumn<SupplementalRow, String> column = getTableColumn();
            if (table == null || column == null || getIndex() < 0 || getIndex() >= table.getItems().size()) return;
            CellEditEvent<SupplementalRow, String> event = new CellEditEvent<>(table, new TablePosition<>(table, getIndex(), column), TableColumn.editCommitEvent(), value);
            Event.fireEvent(column, event);
            updateItem(value, false);
        }
    }

    @FunctionalInterface
    private interface RowOptionSetter { void accept(SplitRow row, TransactionLineEditorModel.Option option); }

    static final class ValidationResult
    {
        private final String message;
        private final int rowCount;
        private final int validCount;
        private final int errorCount;
        private final BigDecimal netAmount;
        ValidationResult(String message, int rowCount, int validCount, int errorCount, BigDecimal netAmount)
        {
            this.message = message;
            this.rowCount = rowCount;
            this.validCount = validCount;
            this.errorCount = errorCount;
            this.netAmount = netAmount == null ? BigDecimal.ZERO : netAmount;
        }
        String message() { return message; }
        int rowCount() { return rowCount; }
        int validCount() { return validCount; }
        int errorCount() { return errorCount; }
        BigDecimal netAmount() { return netAmount; }
    }

    private enum EditorMode { NEW, EDIT }

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
        private final boolean showDueDate;
        private final boolean showStartEnd;

        SupplementalKind(String toggleLabel, String tabTitle, boolean showDueDate, boolean showStartEnd)
        {
            this.toggleLabel = toggleLabel;
            this.tabTitle = tabTitle;
            this.showDueDate = showDueDate;
            this.showStartEnd = showStartEnd;
        }

        String toggleLabel() { return toggleLabel; }
        String tabTitle() { return tabTitle; }
        boolean showDueDate() { return showDueDate; }
        boolean showStartEnd() { return showStartEnd; }
    }

    public static final class SupplementalRow
    {
        private String linkToEntry = "";
        private String counterparty = "";
        private String description = "";
        private String reference = "";
        private String amount = "0.00";
        private String dueDate = "";
        private String startDate = "";
        private String endDate = "";
        private String notes = "";

        public String linkToEntry() { return linkToEntry; }
        public void setLinkToEntry(String value) { linkToEntry = value(value); }
        public String counterparty() { return counterparty; }
        public void setCounterparty(String value) { counterparty = value(value); }
        public String description() { return description; }
        public void setDescription(String value) { description = value(value); }
        public String reference() { return reference; }
        public void setReference(String value) { reference = value(value); }
        public String amount() { return amount; }
        public void setAmount(String value) { amount = value(value); }
        public String dueDate() { return dueDate; }
        public void setDueDate(String value) { dueDate = value(value); }
        public String startDate() { return startDate; }
        public void setStartDate(String value) { startDate = value(value); }
        public String endDate() { return endDate; }
        public void setEndDate(String value) { endDate = value(value); }
        public String notes() { return notes; }
        public void setNotes(String value) { notes = value(value); }

        boolean hasAnyInput()
        {
            return !(linkToEntry.isBlank() && counterparty.isBlank() && description.isBlank() && reference.isBlank()
                    && (amount.isBlank() || "0.00".equals(amount) || "0".equals(amount))
                    && dueDate.isBlank() && startDate.isBlank() && endDate.isBlank() && notes.isBlank());
        }
    }

    public static class SplitRow
    {
        private Long accountId;
        private Long fundId;
        private Long budgetCategoryId;
        private Long activityId;
        private Long merchantId;
        private Long counterpartyId;
        private String account;
        private String fund;
        private String budgetCategory;
        private String debit;
        private String credit;
        private String activity;
        private String merchant;
        private String counterparty;
        private String nmr;
        private String notes;
        public SplitRow(String account, String fund, String amount, String activity, String merchant, String nmr, String notes)
        {
            this(account, fund, "", amount == null || amount.startsWith("-") ? "" : amount, amount != null && amount.startsWith("-") ? amount.substring(1) : "", activity, merchant, "", nmr, notes);
        }
        public SplitRow(String account, String fund, String budgetCategory, String debit, String credit, String activity, String merchant, String counterparty, String nmr, String notes)
        {
            this.account = value(account); this.fund = value(fund); this.budgetCategory = value(budgetCategory); this.debit = value(debit); this.credit = value(credit); this.activity = value(activity); this.merchant = value(merchant); this.counterparty = value(counterparty); this.nmr = value(nmr); this.notes = value(notes);
        }
        static SplitRow fromViewLine(TransactionView.Line line)
        {
            SplitRow row = new SplitRow(label(line.accountCode(), line.accountName()), label(line.fundCode(), line.fundName()), "", line.debit().signum() == 0 ? "" : line.debit().toPlainString(), line.credit().signum() == 0 ? "" : line.credit().toPlainString(), "", "", "", Boolean.toString(line.nmr()), line.notes());
            row.accountId = line.accountId(); row.fundId = line.fundId(); row.budgetCategoryId = line.budgetCategoryId(); row.activityId = line.activityId(); row.merchantId = line.merchantId(); return row;
        }
        SplitRow copy()
        {
            SplitRow row = new SplitRow(account, fund, budgetCategory, debit, credit, activity, merchant, counterparty, nmr, notes);
            row.accountId = accountId; row.fundId = fundId; row.budgetCategoryId = budgetCategoryId; row.activityId = activityId; row.merchantId = merchantId; row.counterpartyId = counterpartyId; return row;
        }
        public Long accountId() { return accountId; }
        public Long fundId() { return fundId; }
        public Long budgetCategoryId() { return budgetCategoryId; }
        public Long activityId() { return activityId; }
        public Long merchantId() { return merchantId; }
        public Long counterpartyId() { return counterpartyId; }
        public String account() { return account; }
        public void setAccount(String account) { this.account = value(account); }
        public void setAccount(TransactionLineEditorModel.Option option) { this.accountId = id(option); this.account = label(option); }
        public String fund() { return fund; }
        public void setFund(String fund) { this.fund = value(fund); }
        public void setFund(TransactionLineEditorModel.Option option) { this.fundId = id(option); this.fund = label(option); }
        public String budgetCategory() { return budgetCategory; }
        public void setBudgetCategory(String budgetCategory) { this.budgetCategory = value(budgetCategory); }
        public void setBudgetCategory(TransactionLineEditorModel.Option option) { this.budgetCategoryId = id(option); this.budgetCategory = label(option); }
        public String debit() { return debit; }
        public void setDebit(String debit) { this.debit = value(debit); }
        public String credit() { return credit; }
        public void setCredit(String credit) { this.credit = value(credit); }
        public String amount() { return debit.isBlank() ? credit : debit; }
        public String activity() { return activity; }
        public void setActivity(String activity) { this.activity = value(activity); }
        public void setActivity(TransactionLineEditorModel.Option option) { this.activityId = id(option); this.activity = label(option); }
        public String merchant() { return merchant; }
        public void setMerchant(String merchant) { this.merchant = value(merchant); }
        public void setMerchant(TransactionLineEditorModel.Option option) { this.merchantId = id(option); this.merchant = label(option); }
        public String counterparty() { return counterparty; }
        public void setCounterparty(String counterparty) { this.counterparty = value(counterparty); }
        public void setCounterparty(TransactionLineEditorModel.Option option) { this.counterpartyId = id(option); this.counterparty = label(option); }
        public String nmr() { return nmr; }
        public void setNmr(String nmr) { this.nmr = value(nmr); }
        public String notes() { return notes; }
        public void setNotes(String notes) { this.notes = value(notes); }
        private static Long id(TransactionLineEditorModel.Option option) { return option == null ? null : option.id(); }
        private static String label(TransactionLineEditorModel.Option option) { return option == null ? "" : option.label(); }
        private static String label(String code, String name) { String safeCode = code == null ? "" : code; String safeName = name == null ? "" : name; return (safeCode + " — " + safeName).trim(); }
        private static String value(String value) { return value == null ? "" : value; }
    }

    private static String value(String value)
    {
        return value == null ? "" : value;
    }
}
