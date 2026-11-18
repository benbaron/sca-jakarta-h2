package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import org.nonprofitbookkeeping.interchange.exchequer.ExchequerReportCsvDocument;
import org.nonprofitbookkeeping.model.ExpenseFunction;
import org.nonprofitbookkeeping.service.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;

/** One transient import draft shared by proposed-entry, edit and source work pages. */
final class ExchequerImportReviewPane extends BorderPane
{
    private final ExchequerImportService service;
    private final Supplier<TransactionLineEditorModel.ReferenceData> references;
    private final BooleanSupplier scopeMatches;
    private final CompanyUiFormat format;
    private final InterchangeTaskController tasks = new InterchangeTaskController();
    private final TableView<EntryDraft> entries = new TableView<>();
    private final TableView<LineDraft> lines = new TableView<>();
    private final TextArea source = new TextArea();
    private final TextArea memo = new TextArea();
    private final TextField date = new TextField();
    private final ComboBox<TransactionLineEditorModel.Option> payee = new ComboBox<>();
    private final ComboBox<TransactionLineEditorModel.Option> account = new ComboBox<>();
    private final ComboBox<TransactionLineEditorModel.Option> fund = new ComboBox<>();
    private final ComboBox<TransactionLineEditorModel.Option> budget = new ComboBox<>();
    private final ComboBox<TransactionLineEditorModel.Option> activity = new ComboBox<>();
    private final ComboBox<TransactionLineEditorModel.Option> merchant = new ComboBox<>();
    private final ComboBox<ExpenseFunction> function = new ComboBox<>();
    private final TextField debit = new TextField();
    private final TextField credit = new TextField();
    private final TextField notes = new TextField();
    private final CheckBox reviewed = new CheckBox("I reviewed the entries and mappings");
    private final CheckBox similar = new CheckBox("Accept similar Journal entries as distinct");
    private final Label status = new Label("Choose an Exchequer CSV to review.");
    private final Label totals = new Label();
    private final Label selectedValidation = new Label();
    private final Button commit = new Button("Commit Reviewed Entries…");
    private final WorkPaneTabs pages = new WorkPaneTabs("exchequerReviewPages");
    private ExchequerImportService.Preview preview;
    private EntryDraft selected;
    private LineDraft selectedLine;
    private boolean loading;
    private boolean accepted;
    private final javafx.beans.value.ChangeListener<Boolean> permissionListener = (o, a, b) -> updateAvailability();
    private Runnable capabilitiesChanged = () -> { };
    private Consumer<AppPanel.RunCommandResult> resultListener = ignored -> { };

    ExchequerImportReviewPane(ExchequerImportService service,
                             Supplier<TransactionLineEditorModel.ReferenceData> references,
                             CompanyUiFormat format, BooleanSupplier scopeMatches)
    {
        this.service = service;
        this.references = references;
        this.format = format;
        this.scopeMatches = scopeMatches;
        setId("exchequerImportReview");
        setMinSize(0, 0);
        status.setId("exchequerReviewStatus");
        status.setWrapText(true);
        commit.setId("commitExchequerEntries");
        commit.setTooltip(new Tooltip("Save all remaining reviewed entries together to Journal. No partial import."));
        commit.setOnAction(event -> confirmCommit());
        UiPermissionGate.gate(commit, ApplicationPermission.BOOKKEEPING_WRITE, "Commit reviewed Exchequer entries");
        reviewed.setId("confirmExchequerReview");
        reviewed.selectedProperty().addListener((o, a, b) -> updateAvailability());
        UiPermissionGate.deniedProperty(ApplicationPermission.BOOKKEEPING_WRITE).addListener(
                new javafx.beans.value.WeakChangeListener<>(permissionListener));
        tasks.busyProperty().addListener((o, a, b) ->
        {
            pages.setDisable(b || accepted);
            reviewed.setDisable(b || accepted);
            similar.setDisable(b || accepted);
            updateAvailability();
        });
        reviewed.setMaxWidth(Double.MAX_VALUE);
        similar.setMaxWidth(Double.MAX_VALUE);
        similar.setTooltip(new Tooltip("Select only after reviewing existing Journal entries with the same date and amount. Exact source duplicates remain blocked."));
        VBox header = new VBox(6, new FlowPane(8, 6, commit,
                button("Refresh Choices", "refreshExchequerChoices", this::refreshChoices)), reviewed, similar, status);
        ScrollPane headerScroll = scroll(header);
        buildEntryTable();
        buildLineTable();
        Button edit = button("Edit Selected", "editExchequerEntry", () -> pages.getSelectionModel().select(1));
        Button remove = button("Delete Proposed Entry", "deleteExchequerEntry", this::deleteSelected);
        entries.setRowFactory(table ->
        {
            TableRow<EntryDraft> row = new TableRow<>();
            row.setOnMouseClicked(event ->
            {
                if (event.getClickCount() == 2 && !row.isEmpty())
                {
                    pages.getSelectionModel().select(1);
                }
            });
            MenuItem item = new MenuItem("Edit proposed entry");
            item.setOnAction(event ->
            {
                entries.getSelectionModel().select(row.getItem());
                pages.getSelectionModel().select(1);
            });
            row.setContextMenu(new ContextMenu(item));
            return row;
        });
        entries.setOnKeyPressed(event ->
        {
            if (event.getCode() == javafx.scene.input.KeyCode.DELETE)
            {
                deleteSelected();
                event.consume();
            }
        });
        entries.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> selectEntry(b));
        lines.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> selectLine(b));
        VBox list = new VBox(6, new FlowPane(8, 6, edit, remove), entries);
        VBox.setVgrow(entries, Priority.ALWAYS);
        pages.addPane("Proposed Entries", list);
        pages.addPane("Edit Entry", buildEditor());
        source.setId("exchequerSourceFacts");
        source.setEditable(false);
        source.setWrapText(false);
        pages.addPane("Original Source and Diagnostics", source);
        SplitPane workspace = new SplitPane(headerScroll, pages);
        workspace.setId("exchequerReviewWorkspaceSplit");
        workspace.setOrientation(Orientation.VERTICAL);
        workspace.setDividerPositions(0.22);
        CompanySplitPaneStateBinder.bind(workspace, "exchequer-review-workspace", 0.22);
        setCenter(workspace);
        PanelViewport.prepare(this, "exchequer-review");
        FullTextTooltipInstaller.install(this);
        updateAvailability();
    }

    void open(Path path)
    {
        if (tasks.busyProperty().get())
        {
            return;
        }
        status.setText("Reading Exchequer CSV…");
        tasks.runPreview("exchequer-preview", "Read Exchequer CSV", () ->
        {
            var proposal = service.preview(path);
            return new Loaded(proposal, references.get(), service.importedSourceLines(proposal));
        }, loaded ->
        {
            if (!scopeMatches.getAsBoolean())
            {
                status.setText("Company or database changed. Open the CSV again in the intended company.");
                return;
            }
            preview = loaded.preview();
            accepted = false;
            replaceChoices(loaded.references());
            entries.getItems().setAll(preview.entries().stream().map(item ->
            {
                var row = preview.source().rows().stream().filter(r -> r.sourceLine() == item.sourceLine()).findFirst().orElseThrow();
                return new EntryDraft(item, row, loaded.imported().contains(item.sourceLine()));
            }).toList());
            reviewed.setSelected(false);
            similar.setSelected(false);
            entries.getSelectionModel().selectFirst();
            source.setText(sourceText(preview));
            status.setText(entries.getItems().size() + " proposed entries. Map accounts and funds, edit or delete, then review and commit."
                    + (loaded.imported().isEmpty() ? "" : " " + loaded.imported().size()
                    + " source records were already imported; remove repeated proposals."));
            CompanyTableStateBinder.applyProductionPanel(this, AppPanelId.IMPORT_PREVIEW);
            updateAvailability();
        }, this::failed, () -> status.setText("Preview cancelled; no entries saved."));
    }

    private record Loaded(ExchequerImportService.Preview preview,
                          TransactionLineEditorModel.ReferenceData references, Set<Integer> imported)
    {
    }

    private void buildEntryTable()
    {
        entries.setId("exchequerProposedEntries");
        entries.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        entries.setMinSize(0, 0);
        typedColumn(entries, "Source line", row -> row.sourceLine, Object::toString, 100);
        typedColumn(entries, "Worksheet row", row -> row.source.worksheetRow(), value -> Objects.toString(value, ""), 110);
        typedColumn(entries, "Date", row -> format.parseDate(row.date), format::formatDate, 120);
        column(entries, "Memo / Payee", row -> row.memo, 310);
        typedColumn(entries, "Source net", row -> row.source.netAmount(), format::formatMoney, 120);
        column(entries, "Fund label", row -> row.source.fund(), 150);
        column(entries, "Validation", this::validation, 430);
        entries.setPlaceholder(new Label("No proposed monetary entries."));
    }

    private void buildLineTable()
    {
        lines.setId("exchequerProposedLines");
        lines.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        lines.setMinSize(0, 0);
        column(lines, "Source label", row -> row.hint, 220);
        column(lines, "Account", row -> label(row.account), 250);
        column(lines, "Fund", row -> label(row.fund), 200);
        typedColumn(lines, "Debit", row -> format.parseMoney(row.debit), format::formatMoney, 120);
        typedColumn(lines, "Credit", row -> format.parseMoney(row.credit), format::formatMoney, 120);
        column(lines, "Budget", row -> label(row.budget), 180);
        column(lines, "Event", row -> label(row.activity), 180);
        column(lines, "Merchant", row -> label(row.merchant), 180);
        column(lines, "Expense function", row -> Objects.toString(row.function, ""), 150);
        column(lines, "Notes", row -> row.notes, 220);
    }

    private Node buildEditor()
    {
        date.setId("exchequerEntryDate");
        memo.setId("exchequerEntryMemo");
        memo.setPrefRowCount(2);
        memo.setWrapText(true);
        for (var combo : List.of(account, fund, budget, activity, merchant, payee))
        {
            combo.setConverter(new StringConverter<>()
            {
                public String toString(TransactionLineEditorModel.Option option)
                {
                    return label(option);
                }
                public TransactionLineEditorModel.Option fromString(String text)
                {
                    throw new UnsupportedOperationException("Select an ID-backed choice.");
                }
            });
            combo.setMaxWidth(Double.MAX_VALUE);
        }
        account.setId("exchequerLineAccount");
        fund.setId("exchequerLineFund");
        debit.setId("exchequerLineDebit");
        credit.setId("exchequerLineCredit");
        function.getItems().add(null);
        function.getItems().addAll(ExpenseFunction.values());
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(6);
        form.addRow(0, new Label("Date"), date, new Label("Payee (optional)"), payee);
        form.addRow(1, new Label("Memo"), memo);
        GridPane.setColumnSpan(memo, 3);
        form.addRow(2, new Label("Account"), account, new Label("Fund"), fund);
        form.addRow(3, new Label("Debit"), debit, new Label("Credit"), credit);
        form.addRow(4, new Label("Budget (optional)"), budget, new Label("Event (optional)"), activity);
        form.addRow(5, new Label("Merchant (optional)"), merchant, new Label("Expense function"), function);
        form.addRow(6, new Label("Line notes"), notes);
        GridPane.setColumnSpan(notes, 3);
        form.setMinWidth(Region.USE_PREF_SIZE);
        for (var field : List.of(date, memo, notes))
        {
            field.textProperty().addListener((o, a, b) -> capture());
        }
        debit.textProperty().addListener((o, a, b) ->
        {
            if (!loading && selectedLine != null)
            {
                selectedLine.debit = b;
                capture();
            }
        });
        credit.textProperty().addListener((o, a, b) ->
        {
            if (!loading && selectedLine != null)
            {
                selectedLine.credit = b;
                capture();
            }
        });
        for (var combo : List.of(account, fund, budget, activity, merchant, payee, function))
        {
            combo.valueProperty().addListener((o, a, b) -> capture());
        }
        for (var field : List.of(debit, credit))
        {
            field.focusedProperty().addListener((o, a, b) ->
            {
                if (!b && format.parseMoney(field.getText()) != null)
                {
                    loading = true;
                    field.setText(format.formatMoney(format.parseMoney(field.getText())));
                    loading = false;
                }
            });
        }
        date.focusedProperty().addListener((o, a, b) ->
        {
            if (!b && format.parseDate(date.getText()) != null)
            {
                loading = true;
                date.setText(format.formatDate(format.parseDate(date.getText())));
                loading = false;
            }
        });
        Button add = button("Add Line", "addExchequerLine", () ->
        {
            if (selected != null)
            {
                LineDraft line = new LineDraft();
                selected.lines.add(line);
                lines.getItems().add(line);
                lines.getSelectionModel().select(line);
                changed();
            }
        });
        Button remove = button("Remove Line", "removeExchequerLine", () ->
        {
            if (selected != null && selectedLine != null)
            {
                selected.lines.remove(selectedLine);
                lines.getItems().remove(selectedLine);
                changed();
            }
        });
        Button map = button("Apply Mappings to Same Source Labels", "mapExchequerLabels", this::applyMatchingMappings);
        map.setTooltip(new Tooltip("Apply this account to matching source bank/category labels and this fund to matching source fund labels across proposed entries."));
        selectedValidation.setWrapText(true);
        VBox editor = new VBox(6, form, new FlowPane(8, 6, add, remove, map), totals, selectedValidation);
        editor.setMinWidth(Region.USE_PREF_SIZE);
        SplitPane split = new SplitPane(scroll(editor), lines);
        split.setId("exchequerEntryEditorSplit");
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.58);
        CompanySplitPaneStateBinder.bind(split, "exchequer-entry-editor", 0.58);
        return split;
    }

    private void capture()
    {
        if (loading || selected == null || accepted)
        {
            return;
        }
        selected.date = date.getText();
        selected.memo = memo.getText();
        selected.payee = payee.getValue();
        if (selectedLine != null)
        {
            selectedLine.account = account.getValue();
            selectedLine.fund = fund.getValue();
            selectedLine.budget = budget.getValue();
            selectedLine.activity = activity.getValue();
            selectedLine.merchant = merchant.getValue();
            selectedLine.function = function.getValue();
            // Keep exact raw numeric input; display normalization must not round stored precision.
            selectedLine.notes = notes.getText();
        }
        changed();
    }

    private void selectEntry(EntryDraft entry)
    {
        selected = entry;
        loading = true;
        date.setText(entry == null ? "" : entry.date);
        memo.setText(entry == null ? "" : entry.memo);
        payee.setValue(entry == null ? null : entry.payee);
        lines.getItems().setAll(entry == null ? List.of() : entry.lines);
        loading = false;
        lines.getSelectionModel().selectFirst();
        if (entry == null)
        {
            selectLine(null);
        }
        updateTotals();
    }

    private void selectLine(LineDraft line)
    {
        selectedLine = line;
        loading = true;
        account.setValue(line == null ? null : line.account);
        fund.setValue(line == null ? null : line.fund);
        budget.setValue(line == null ? null : line.budget);
        activity.setValue(line == null ? null : line.activity);
        merchant.setValue(line == null ? null : line.merchant);
        function.setValue(line == null ? null : line.function);
        debit.setText(line == null ? "" : displayMoney(line.debit));
        credit.setText(line == null ? "" : displayMoney(line.credit));
        notes.setText(line == null ? "" : line.notes);
        loading = false;
    }

    private void applyMatchingMappings()
    {
        if (selected == null || selectedLine == null)
        {
            return;
        }
        for (var entry : entries.getItems())
        {
            for (var line : entry.lines)
            {
                if (!selectedLine.hint.equals("Added line") && line.hint.equals(selectedLine.hint) && selectedLine.account != null)
                {
                    line.account = selectedLine.account;
                }
                if (entry.source.fund().equals(selected.source.fund()) && selectedLine.fund != null)
                {
                    line.fund = selectedLine.fund;
                }
            }
        }
        changed();
    }

    private void deleteSelected()
    {
        if (!tasks.busyProperty().get() && !accepted && entries.getSelectionModel().getSelectedItem() != null)
        {
            entries.getItems().remove(entries.getSelectionModel().getSelectedItem());
            changed();
            status.setText("Proposed entry removed. The source CSV and existing journal entries are unchanged.");
        }
    }

    private void changed()
    {
        reviewed.setSelected(false);
        similar.setSelected(false);
        entries.refresh();
        lines.refresh();
        updateTotals();
        updateAvailability();
    }

    private void updateTotals()
    {
        BigDecimal dr = BigDecimal.ZERO;
        BigDecimal cr = BigDecimal.ZERO;
        if (selected != null)
        {
            for (var line : selected.lines)
            {
                BigDecimal d = format.parseMoney(line.debit);
                BigDecimal c = format.parseMoney(line.credit);
                dr = dr.add(d == null ? BigDecimal.ZERO : d);
                cr = cr.add(c == null ? BigDecimal.ZERO : c);
            }
        }
        totals.setText("Debit: " + format.formatMoney(dr) + "   Credit: " + format.formatMoney(cr)
                + "   Difference: " + format.formatMoney(dr.subtract(cr)));
        totals.setWrapText(true);
        selectedValidation.setText(selected == null ? "Select an entry to edit." : validation(selected));
    }

    private String validation(EntryDraft entry)
    {
        if (entry.imported)
        {
            return "Already imported — delete this proposal";
        }
        try
        {
            if (entry.memo != null && entry.memo.length() > 500)
            {
                return "Memo must be at most 500 characters; full source text remains in source evidence.";
            }
            if (entry.lines.stream().anyMatch(line -> line.notes.length() > 500))
            {
                return "Line notes must be at most 500 characters.";
            }
            var result = new TransactionCommandValidator().validate(entry.command());
            return result.valid() ? "Ready for review" : String.join(" ", result.errors());
        }
        catch (IllegalArgumentException ex)
        {
            return ex.getMessage();
        }
    }

    private void updateAvailability()
    {
        boolean invalid = entries.getItems().stream().anyMatch(entry -> !validation(entry).equals("Ready for review"));
        commit.setDisable(preview == null || accepted || tasks.busyProperty().get() || invalid
                || entries.getItems().isEmpty() || !reviewed.isSelected() || !scopeMatches.getAsBoolean());
        capabilitiesChanged.run();
    }

    private void confirmCommit()
    {
        if (commit.isDisabled())
        {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Save " + entries.getItems().size() + " reviewed entries to Journal for " + preview.companyCode()
                + "? All entries will be saved together. Afterwards, corrections use the Journal workflow.", ButtonType.OK, ButtonType.CANCEL);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK)
        {
            return;
        }
        var proposals = entries.getItems().stream()
                .map(entry -> new ExchequerImportService.ProposedEntry(entry.sourceLine, entry.command())).toList();
        boolean acknowledge = similar.isSelected();
        status.setText("Saving reviewed entries…");
        tasks.runCommit("exchequer-commit", "Save reviewed Exchequer entries",
                () -> service.commit(preview, proposals, acknowledge), result ->
                {
                    accepted = true;
                    pages.setDisable(true);
                    reviewed.setDisable(true);
                    similar.setDisable(true);
                    status.setText("Saved " + result.transactionIds().size() + " entries to Journal. Transaction IDs: " + result.transactionIds());
                    updateAvailability();
                    resultListener.accept(new AppPanel.RunCommandResult(true, status.getText()));
                }, this::failed);
    }

    private void failed(Throwable error)
    {
        status.setText("Import not committed: " + error.getMessage());
        resultListener.accept(new AppPanel.RunCommandResult(false, status.getText()));
        updateAvailability();
    }

    void onShown()
    {
        if (!scopeMatches.getAsBoolean())
        {
            status.setText("This review belongs to another company or database. Choose another CSV for the current company, or return to the original company/database.");
        }
        updateAvailability();
    }

    String statusMessage()
    {
        return status.getText();
    }

    Button commitButton()
    {
        return commit;
    }

    boolean hasUnsavedChanges()
    {
        return tasks.busyProperty().get() || preview != null && !accepted && !entries.getItems().isEmpty();
    }

    void setCapabilitiesChanged(Runnable listener)
    {
        capabilitiesChanged = listener;
    }

    void setResultListener(Consumer<AppPanel.RunCommandResult> listener)
    {
        resultListener = listener;
    }

    javafx.beans.property.ReadOnlyBooleanProperty busyProperty()
    {
        return tasks.busyProperty();
    }

    private void refreshChoices()
    {
        if (tasks.busyProperty().get() || accepted || !scopeMatches.getAsBoolean())
        {
            return;
        }
        tasks.runPreview("exchequer-refresh", "Refresh mapping choices", references, refs ->
        {
            loading = true;
            replaceChoices(refs);
            selectEntry(selected);
            loading = false;
            changed();
            status.setText("Choices refreshed; review the retained draft mappings before commit.");
        }, this::failed, () -> { });
    }

    private void replaceChoices(TransactionLineEditorModel.ReferenceData refs)
    {
        account.getItems().setAll(refs.accounts());
        fund.getItems().setAll(refs.funds());
        for (var pair : List.of(Map.entry(budget, refs.budgetCategories()), Map.entry(activity, refs.activities()),
                Map.entry(merchant, refs.merchants()), Map.entry(payee, refs.counterparties())))
        {
            pair.getKey().getItems().setAll(pair.getValue());
            pair.getKey().getItems().add(0, null);
        }
    }

    private static Button button(String text, String id, Runnable action)
    {
        Button button = new Button(text);
        button.setId(id);
        button.setWrapText(true);
        button.setTooltip(new Tooltip(text));
        button.setOnAction(event -> action.run());
        return button;
    }

    private static ScrollPane scroll(Node content)
    {
        ScrollPane pane = new ScrollPane(content);
        pane.setFitToWidth(true);
        pane.setMinSize(0, 0);
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return pane;
    }

    private static <T> void column(TableView<T> table, String title, Function<T, String> value, double width)
    {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setId(title.toLowerCase(Locale.ROOT).replace(' ', '-'));
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setCellFactory(ignored -> new TableCell<>()
        {
            protected void updateItem(String item, boolean empty)
            {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setTooltip(empty || item == null || item.isBlank() ? null : new Tooltip(item));
            }
        });
        table.getColumns().add(column);
    }

    private static <T, V> void typedColumn(TableView<T> table, String title, Function<T, V> value,
                                          Function<V, String> display, double width)
    {
        TableColumn<T, V> column = new TableColumn<>(title);
        column.setId(title.toLowerCase(Locale.ROOT).replace(' ', '-'));
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new javafx.beans.property.SimpleObjectProperty<>(value.apply(cell.getValue())));
        column.setCellFactory(ignored -> new TableCell<>()
        {
            @Override
            protected void updateItem(V item, boolean empty)
            {
                super.updateItem(item, empty);
                String text = empty ? null : display.apply(item);
                setText(text);
                setTooltip(text == null || text.isBlank() ? null : new Tooltip(text));
            }
        });
        table.getColumns().add(column);
    }

    private static String label(TransactionLineEditorModel.Option option)
    {
        return option == null ? "" : option.label() + " (ID " + option.id() + ")";
    }

    private String displayMoney(String value)
    {
        BigDecimal amount = format.parseMoney(value);
        return amount == null ? value : format.formatMoney(amount);
    }

    private static String sourceText(ExchequerImportService.Preview preview)
    {
        StringBuilder text = new StringBuilder();
        text.append("Organization: ").append(preview.source().metadata().organizationName()).append('\n')
                .append("Report heading (informational): ").append(preview.source().metadata().reportLabel()).append('\n')
                .append("Opening balance (source fact only): ").append(preview.source().metadata().openingBalance()).append('\n')
                .append("Source SHA-256: ").append(preview.source().sourceSha256()).append('\n');
        for (var message : preview.source().messages())
        {
            text.append(message).append('\n');
        }
        for (var row : preview.source().rows())
        {
            text.append("\nSource line ").append(row.sourceLine()).append(" / worksheet row ")
                    .append(row.worksheetRow()).append(" / ").append(row.kind()).append('\n')
                    .append("Date: ").append(row.transactionDate()).append(" | Payee: ").append(row.payee())
                    .append(" | Reference: ").append(row.reference()).append(" | Notes: ").append(row.notes()).append('\n')
                    .append("Bank: ").append(row.bankAccount()).append(" | Fund: ").append(row.fund())
                    .append(" | Net: ").append(row.netAmount()).append(" | Bank timing: ").append(row.bankTiming())
                    .append(" | Budget timing: ").append(row.budgetTiming()).append('\n')
                    .append("Budget: ").append(row.budgetCategory()).append(" | Merchant: ").append(row.merchant())
                    .append(" | Statement: ").append(row.statementDateText()).append(" | Linked row: ").append(row.linkedRow())
                    .append(" | Incoming: ").append(row.incomingDate()).append(" | Reversal: ").append(row.reversalDate()).append('\n');
            for (var split : row.splits())
            {
                text.append("  Split ").append(split.ordinal()).append(": ").append(split.side())
                        .append(" | ").append(split.category()).append(" | Amount: ").append(split.amount())
                        .append(" | Purpose: ").append(split.usedFor()).append(" | Item: ").append(split.itemNumber())
                        .append(" | Quantity: ").append(split.quantity()).append('\n');
            }
            text.append("Original CSV record: ").append(row.originalRecord()).append('\n');
        }
        return text.toString();
    }

    private final class EntryDraft
    {
        private final int sourceLine;
        private final ExchequerReportCsvDocument.Row source;
        private final boolean imported;
        private final List<LineDraft> lines = new ArrayList<>();
        private String date;
        private String memo;
        private TransactionLineEditorModel.Option payee;

        EntryDraft(ExchequerImportService.ProposedEntry proposal, ExchequerReportCsvDocument.Row source, boolean imported)
        {
            sourceLine = proposal.sourceLine();
            this.source = source;
            this.imported = imported;
            date = format.formatDate(proposal.command().date());
            memo = proposal.command().memo();
            for (var line : proposal.command().lines())
            {
                LineDraft draft = new LineDraft();
                draft.hint = line.notes();
                draft.notes = line.notes();
                draft.debit = line.debit().toPlainString();
                draft.credit = line.credit().toPlainString();
                lines.add(draft);
            }
        }

        TransactionCommand command()
        {
            if (format.parseDate(date) == null)
            {
                throw new IllegalArgumentException("Enter a valid transaction date.");
            }
            return new TransactionCommand(format.parseDate(date), id(payee), memo, null,
                    lines.stream().map(LineDraft::command).toList());
        }
    }

    private final class LineDraft
    {
        private String hint = "Added line";
        private String debit = "0";
        private String credit = "0";
        private String notes = "";
        private TransactionLineEditorModel.Option account, fund, budget, activity, merchant;
        private ExpenseFunction function;

        TransactionLineCommand command()
        {
            BigDecimal dr = format.parseMoney(debit);
            BigDecimal cr = format.parseMoney(credit);
            if (dr == null || cr == null)
            {
                throw new IllegalArgumentException("Enter valid debit and credit amounts.");
            }
            return new TransactionLineCommand(id(account), id(fund), id(budget), id(activity), id(merchant),
                    dr, cr, false, notes, null, function);
        }
    }

    private static Long id(TransactionLineEditorModel.Option option)
    {
        return option == null ? null : option.id();
    }
}
