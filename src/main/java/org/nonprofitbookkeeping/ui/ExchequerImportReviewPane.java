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
    private final TextField informationReference = new TextField();
    private final TextField informationStatement = new TextField();
    private final TextField informationIncoming = new TextField();
    private final SuggestedImportComboBox payee = new SuggestedImportComboBox(true, this::capture);
    private final SuggestedImportComboBox account = new SuggestedImportComboBox(false, this::capture);
    private final SuggestedImportComboBox fund = new SuggestedImportComboBox(false, this::capture);
    private final SuggestedImportComboBox budget = new SuggestedImportComboBox(true, this::capture);
    private final SuggestedImportComboBox activity = new SuggestedImportComboBox(true, this::capture);
    private final SuggestedImportComboBox merchant = new SuggestedImportComboBox(true, this::capture);
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
    private final Button applyEntry = new Button("Apply Entry Changes");
    private final Button cancelEntry = new Button("Cancel Entry Changes");
    private final Button deleteEntries = new Button("Delete Selected Entries");
    private final Button refresh = new Button("Refresh Review");
    private final WorkPaneTabs pages = new WorkPaneTabs("exchequerReviewPages");
    private ExchequerImportService.Preview preview;
    private TransactionLineEditorModel.ReferenceData currentChoices = new TransactionLineEditorModel.ReferenceData(
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
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
        refresh.setId("refreshExchequerChoices");
        refresh.setOnAction(event -> refreshChoices());
        refresh.setTooltip(new Tooltip("Reload available choices and revalidate. Applied edits stay accepted; valid updated rows return to normal text."));
        VBox header = new VBox(6, new FlowPane(8, 6, commit, refresh), reviewed, similar, status);
        ScrollPane headerScroll = scroll(header);
        buildEntryTable();
        buildLineTable();
        Button edit = button("Edit Selected", "editExchequerEntry", () -> pages.getSelectionModel().select(1));
        deleteEntries.setId("deleteExchequerEntry");
        deleteEntries.setOnAction(event -> deleteSelected());
        deleteEntries.setTooltip(new Tooltip("Remove selected proposals only. Use Ctrl/Cmd or Shift to select several entries; CSV and Journal are unchanged."));
        entries.setRowFactory(table ->
        {
            TableRow<EntryDraft> row = new TableRow<>()
            {
                @Override
                protected void updateItem(EntryDraft item, boolean empty)
                {
                    super.updateItem(item, empty);
                    getStyleClass().removeAll("import-entry-invalid", "import-entry-updated");
                    if (!empty && item != null)
                    {
                        if (!validation(item).equals("Ready for review"))
                        {
                            getStyleClass().add("import-entry-invalid");
                        }
                        else if (item.updated)
                        {
                            getStyleClass().add("import-entry-updated");
                        }
                    }
                }
            };
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
        entries.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        entries.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<EntryDraft>) change -> updateAvailability());
        entries.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> selectEntry(b));
        lines.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> selectLine(b));
        Button showSplits = button("Show Splits 1–4", "showExchequerSplits", () -> entries.getColumns().stream()
                .filter(column -> "split-1".equals(column.getId())).findFirst().ifPresent(entries::scrollToColumn));
        VBox list = new VBox(6, new FlowPane(8, 6, edit, deleteEntries, showSplits), entries);
        VBox.setVgrow(entries, Priority.ALWAYS);
        pages.addPane("Proposed Entries", list);
        pages.addPane("Edit Entry", buildEditor());
        source.setId("exchequerSourceFacts");
        source.setEditable(false);
        source.setWrapText(false);
        pages.addPane("Original Source and Diagnostics", source);
        pages.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> updateAvailability());
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
        column(entries, "Review status", row -> row.unapplied ? "Unapplied edits"
                : !validation(row).equals("Ready for review") ? "Needs attention" : row.updated ? "Updated" : "Ready", 160);
        typedColumn(entries, "Worksheet row", row -> row.source.worksheetRow(), value -> Objects.toString(value, ""), 110);
        typedColumn(entries, "Date", row -> format.parseDate(row.date), format::formatDate, 120);
        column(entries, "Memo / Payee", row -> row.memo, 310);
        suggestionColumn(entries, "Payee", row -> row.payee, 220);
        column(entries, "Transfer ID / Check #", row -> row.informationReference, 180);
        typedColumn(entries, "Statement date (information)", row -> format.parseDate(row.informationStatement), format::formatDate, 160);
        typedColumn(entries, "Incoming date", row -> format.parseDate(row.informationIncoming), format::formatDate, 140);
        for (int ordinal = 1; ordinal <= 4; ordinal++)
        {
            final int split = ordinal;
            column(entries, "Split " + ordinal, row -> row.splitSummary(split), 220);
        }
        typedColumn(entries, "Source net", row -> row.source.netAmount(), format::formatMoney, 120);
        typedColumn(entries, "Proposed debit", row -> row.total(true), format::formatMoney, 130);
        typedColumn(entries, "Proposed credit", row -> row.total(false), format::formatMoney, 130);
        typedColumn(entries, "Debit minus credit", row -> row.total(true).subtract(row.total(false)), this::differenceText, 180);
        column(entries, "CSV Fund label", row -> row.source.fund(), 150);
        column(entries, "Validation", this::validation, 430);
        entries.setPlaceholder(new Label("No proposed monetary entries."));
    }

    private void buildLineTable()
    {
        lines.setId("exchequerProposedLines");
        lines.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        lines.setMinSize(0, 0);
        column(lines, "Source split", row -> row.sourceOrdinal == 0 ? "Bank / added line" : "Split " + row.sourceOrdinal, 120);
        column(lines, "Source label", row -> row.hint, 220);
        suggestionColumn(lines, "Account", row -> row.account, 250);
        suggestionColumn(lines, "Fund", row -> row.fund, 200);
        typedColumn(lines, "Debit", row -> format.parseMoney(row.debit), format::formatMoney, 120);
        typedColumn(lines, "Credit", row -> format.parseMoney(row.credit), format::formatMoney, 120);
        suggestionColumn(lines, "Budget", row -> row.budget, 180);
        suggestionColumn(lines, "Event", row -> row.activity, 180);
        suggestionColumn(lines, "Merchant", row -> row.merchant, 180);
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
        payee.setId("exchequerEntryPayee");
        budget.setId("exchequerLineBudget");
        activity.setId("exchequerLineEvent");
        merchant.setId("exchequerLineMerchant");
        account.setId("exchequerLineAccount");
        fund.setId("exchequerLineFund");
        debit.setId("exchequerLineDebit");
        credit.setId("exchequerLineCredit");
        function.getItems().add(null);
        function.getItems().addAll(ExpenseFunction.values());
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(6);
        form.addRow(0, new Label("Date"), date, new Label("Payee (optional)"), payee.field());
        form.addRow(1, new Label("Memo"), memo);
        GridPane.setColumnSpan(memo, 3);
        informationReference.setId("exchequerTransferCheckReference");
        informationStatement.setId("exchequerInformationStatementDate");
        informationIncoming.setId("exchequerInformationIncomingDate");
        informationStatement.setTooltip(new Tooltip("Informational source statement date; it does not mark bank lines cleared."));
        form.addRow(2, new Label("Transfer ID / Check #"), informationReference,
                new Label("Statement date (information)"), informationStatement);
        form.addRow(3, new Label("Incoming check / transfer date"), informationIncoming);
        form.addRow(4, new Label("Account"), account.field(), new Label("Fund"), fund.field());
        form.addRow(5, new Label("Debit"), debit, new Label("Credit"), credit);
        form.addRow(6, new Label("Budget (optional)"), budget.field(), new Label("Event (optional)"), activity.field());
        form.addRow(7, new Label("Merchant (optional)"), merchant.field(), new Label("Expense function"), function);
        form.addRow(8, new Label("Line notes"), notes);
        GridPane.setColumnSpan(notes, 3);
        form.setMinWidth(Region.USE_PREF_SIZE);
        for (var field : List.of(date, memo, notes, informationReference, informationStatement, informationIncoming))
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
        function.valueProperty().addListener((o, a, b) -> capture());
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
        for (var dateField : List.of(date, informationStatement, informationIncoming))
        {
            dateField.focusedProperty().addListener((o, a, b) ->
            {
                if (!b && format.parseDate(dateField.getText()) != null)
                {
                    loading = true;
                    dateField.setText(format.formatDate(format.parseDate(dateField.getText())));
                    loading = false;
                }
            });
        }
        Button add = button("Add Line", "addExchequerLine", () ->
        {
            if (selected != null)
            {
                LineDraft line = new LineDraft();
                selected.lines.add(line);
                selected.unapplied = true;
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
                selected.unapplied = true;
                lines.getItems().remove(selectedLine);
                changed();
            }
        });
        Button map = button("Apply Mappings to Same Source Labels", "mapExchequerLabels", this::applyMatchingMappings);
        map.setTooltip(new Tooltip("Reuse resolved Account, Fund, Payee, Budget and Merchant choices or explicit clearing for the same nonblank source labels across proposed entries."));
        selectedValidation.setWrapText(true);
        applyEntry.setId("applyExchequerEntryChanges");
        applyEntry.setOnAction(event -> applyEntryChanges());
        applyEntry.setTooltip(new Tooltip("Validate and accept this whole entry into the proposed batch. Journal is written only by Commit Reviewed Entries."));
        UiPermissionGate.gate(applyEntry, ApplicationPermission.BOOKKEEPING_WRITE, "Apply proposed entry changes");
        cancelEntry.setId("cancelExchequerEntryChanges");
        cancelEntry.setOnAction(event -> cancelEntryChanges());
        cancelEntry.setTooltip(new Tooltip("Restore this entry's last applied proposal, including all lines, mappings and information."));
        VBox editor = new VBox(6, new FlowPane(8, 6, applyEntry, cancelEntry), form,
                new FlowPane(8, 6, add, remove, map), totals, selectedValidation);
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
        selected.unapplied = true;
        selected.date = date.getText();
        selected.memo = memo.getText();
        selected.informationReference = informationReference.getText();
        selected.informationStatement = informationStatement.getText();
        selected.informationIncoming = informationIncoming.getText();
        if (selectedLine != null)
        {
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
        informationReference.setText(entry == null ? "" : entry.informationReference);
        informationStatement.setText(entry == null ? "" : entry.informationStatement);
        informationIncoming.setText(entry == null ? "" : entry.informationIncoming);
        payee.show(entry == null ? null : entry.payee);
        lines.getItems().setAll(entry == null ? List.of() : entry.lines);
        loading = false;
        lines.getSelectionModel().selectFirst();
        if (entry == null)
        {
            selectLine(null);
        }
        updateTotals();
        updateAvailability();
    }

    private void selectLine(LineDraft line)
    {
        selectedLine = line;
        loading = true;
        account.show(line == null ? null : line.account);
        fund.show(line == null ? null : line.fund);
        budget.show(line == null ? null : line.budget);
        activity.show(line == null ? null : line.activity);
        merchant.show(line == null ? null : line.merchant);
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
            boolean modified = copySameSource(entry.payee, selected.payee);
            for (var line : entry.lines)
            {
                if (!selectedLine.hint.equals("Added line") && line.hint.equals(selectedLine.hint))
                {
                    modified |= line.account.copyResolvedChoice(selectedLine.account);
                }
                modified |= copySameSource(line.fund, selectedLine.fund);
                modified |= copySameSource(line.budget, selectedLine.budget);
                modified |= copySameSource(line.merchant, selectedLine.merchant);
            }
            if (modified && !entry.unapplied)
            {
                entry.updated = true;
                entry.baseline = entry.copy();
            }
        }
        changed();
    }

    private static boolean copySameSource(ImportReferenceSuggestion target, ImportReferenceSuggestion from)
    {
        if (!from.sourceText().isBlank() && target.sourceText().equals(from.sourceText()))
        {
            return target.copyResolvedChoice(from);
        }
        return false;
    }

    private void applyEntryChanges()
    {
        if (selected == null || applyEntry.isDisabled())
        {
            return;
        }
        String problem = validation(selected, false);
        if (!problem.equals("Ready for review"))
        {
            status.setText("Entry changes not applied: " + problem);
            selectedValidation.setText(problem);
            return;
        }
        selected.unapplied = false;
        selected.updated = true;
        selected.baseline = selected.copy();
        changed();
        pages.getSelectionModel().select(0);
        status.setText("Entry changes accepted in the proposed batch. Bold marks the updated entry; Journal has not been written.");
        resultListener.accept(new AppPanel.RunCommandResult(true, status.getText()));
    }

    private void cancelEntryChanges()
    {
        if (selected != null && selected.unapplied && !tasks.busyProperty().get() && !accepted)
        {
            selected.restoreBaseline();
            selectEntry(selected);
            changed();
            status.setText("Entry changes cancelled; its last applied proposal is restored.");
        }
    }

    private void deleteSelected()
    {
        if (!tasks.busyProperty().get() && !accepted)
        {
            var removed = List.copyOf(entries.getSelectionModel().getSelectedItems());
            entries.getItems().removeAll(removed);
            if (entries.getSelectionModel().getSelectedItem() == null && !entries.getItems().isEmpty())
            {
                entries.getSelectionModel().selectFirst();
            }
            changed();
            status.setText("Removed " + removed.size() + " proposed entries. The source CSV and Journal are unchanged.");
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
                + "   Difference: " + differenceText(dr.subtract(cr)));
        totals.setWrapText(true);
        String problem = selected == null ? "Select an entry to edit." : validation(selected, false);
        selectedValidation.setText(selected != null && selected.unapplied && problem.equals("Ready for review")
                ? "Ready to apply this entry's changes." : problem);
    }

    private String validation(EntryDraft entry)
    {
        return validation(entry, true);
    }

    private String validation(EntryDraft entry, boolean includePending)
    {
        if (includePending && entry.unapplied)
        {
            return "Apply or cancel this entry's pending changes.";
        }
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
            var errors = new ArrayList<String>();
            errors.add(entry.payee.validation("Payee"));
            for (int index = 0; index < entry.lines.size(); index++)
            {
                var line = entry.lines.get(index);
                String prefix = "Line " + (index + 1) + " ";
                errors.add(line.budget.validation(prefix + "Budget category"));
                errors.add(line.activity.validation(prefix + "Event"));
                errors.add(line.merchant.validation(prefix + "Merchant"));
            }
            errors.addAll(ExchequerImportService.validateCandidate(entry.command()).errors());
            errors.removeIf(String::isBlank);
            return errors.isEmpty() ? "Ready for review" : String.join(" ", errors);
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
        boolean busy = tasks.busyProperty().get() || accepted;
        applyEntry.setDisable(busy || selected == null || !selected.unapplied || !scopeMatches.getAsBoolean());
        cancelEntry.setDisable(busy || selected == null || !selected.unapplied);
        refresh.setDisable(busy || !scopeMatches.getAsBoolean());
        refresh.setTooltip(new Tooltip("Reload choices without accepting or discarding unfinished edits. Applied changes remain accepted; recent-update markers clear."));
        int count = entries.getSelectionModel().getSelectedItems().size();
        deleteEntries.setDisable(busy || count == 0);
        deleteEntries.setText("Delete Selected Entries (" + count + ")");
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

    Button selectedSaveButton()
    {
        return pages.getSelectionModel().getSelectedIndex() == 1 ? applyEntry : commit;
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
        if (refresh.isDisabled())
        {
            return;
        }
        tasks.runPreview("exchequer-refresh", "Refresh mapping choices", references, refs ->
        {
            loading = true;
            var retainedLine = selectedLine;
            replaceChoices(refs);
            entries.getItems().forEach(entry ->
            {
                entry.updated = false;
                if (entry.unapplied)
                {
                    entry.baseline.payee.refresh(refs.counterparties());
                    entry.baseline.lines.forEach(LineDraft::refreshChoices);
                    entry.baseline.updated = false;
                }
                else
                {
                    entry.baseline = entry.copy();
                }
            });
            selectEntry(selected);
            if (selected != null && selected.lines.contains(retainedLine))
            {
                lines.getSelectionModel().select(retainedLine);
            }
            loading = false;
            changed();
            status.setText("Choices refreshed; review the retained draft mappings before commit.");
        }, this::failed, () -> { });
    }

    private void replaceChoices(TransactionLineEditorModel.ReferenceData refs)
    {
        currentChoices = refs;
        for (var entry : entries.getItems())
        {
            entry.payee.refresh(refs.counterparties());
            entry.lines.forEach(LineDraft::refreshChoices);
        }
        account.replaceChoices(refs.accounts());
        fund.replaceChoices(refs.funds());
        budget.replaceChoices(refs.budgetCategories());
        activity.replaceChoices(refs.activities());
        merchant.replaceChoices(refs.merchants());
        payee.replaceChoices(refs.counterparties());
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

    private static <T> void suggestionColumn(TableView<T> table, String title,
            Function<T, ImportReferenceSuggestion> value, double width)
    {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setId(title.toLowerCase(Locale.ROOT).replace(' ', '-'));
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue()).displayText()));
        column.setCellFactory(ignored -> new TableCell<>()
        {
            @Override
            protected void updateItem(String text, boolean empty)
            {
                super.updateItem(text, empty);
                setText(empty ? null : text);
                getStyleClass().remove("import-source-suggestion");
                var row = getTableRow() == null ? null : getTableRow().getItem();
                boolean suggested = !empty && row != null && value.apply(row).suggested();
                if (suggested)
                {
                    getStyleClass().add("import-source-suggestion");
                }
                setTooltip(empty || text == null || text.isBlank() ? null
                        : new Tooltip((suggested ? "Unmapped CSV value: " : "") + text));
            }
        });
        table.getColumns().add(column);
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

    private String differenceText(BigDecimal difference)
    {
        String display = format.formatMoney(difference);
        if (difference.signum() != 0 && difference.setScale(2, java.math.RoundingMode.HALF_UP).signum() == 0)
        {
            display += " (exact " + difference.toPlainString() + ")";
        }
        return display;
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
        private String informationReference;
        private String informationStatement;
        private String informationIncoming;
        private final ImportReferenceSuggestion payee;
        private boolean unapplied;
        private boolean updated;
        private EntryDraft baseline;

        EntryDraft(ExchequerImportService.ProposedEntry proposal, ExchequerReportCsvDocument.Row source, boolean imported)
        {
            sourceLine = proposal.sourceLine();
            this.source = source;
            this.imported = imported;
            payee = new ImportReferenceSuggestion(source.payee(), false);
            payee.refresh(currentChoices.counterparties());
            date = format.formatDate(proposal.command().date());
            memo = proposal.command().memo();
            informationReference = Objects.toString(proposal.command().information().transferOrCheckReference(), "");
            informationStatement = format.formatDate(proposal.command().information().statementDate());
            informationIncoming = format.formatDate(proposal.command().information().incomingDate());
            var ordinals = source.splits().stream().filter(split -> split.bankEffect() != null && split.bankEffect().signum() != 0)
                    .map(org.nonprofitbookkeeping.interchange.exchequer.ExchequerReportCsvDocument.Split::ordinal).iterator();
            for (var line : proposal.command().lines())
            {
                String accountHint = line.notes().startsWith("Bank:") ? source.bankAccount()
                        : line.notes().startsWith("Category: ") ? line.notes().substring(10) : "";
                LineDraft draft = new LineDraft(accountHint, source.fund(), source.budgetCategory(), source.merchant());
                draft.hint = line.notes();
                if (!line.notes().startsWith("Bank:") && ordinals.hasNext())
                {
                    draft.sourceOrdinal = ordinals.next();
                }
                draft.notes = line.notes();
                draft.debit = line.debit().toPlainString();
                draft.credit = line.credit().toPlainString();
                lines.add(draft);
            }
            baseline = copy();
        }

        private EntryDraft(EntryDraft from)
        {
            sourceLine = from.sourceLine;
            source = from.source;
            imported = from.imported;
            date = from.date;
            memo = from.memo;
            informationReference = from.informationReference;
            informationStatement = from.informationStatement;
            informationIncoming = from.informationIncoming;
            payee = from.payee.copy();
            from.lines.forEach(line -> lines.add(line.copy()));
            updated = from.updated;
        }

        EntryDraft copy()
        {
            return new EntryDraft(this);
        }

        void restoreBaseline()
        {
            date = baseline.date;
            memo = baseline.memo;
            informationReference = baseline.informationReference;
            informationStatement = baseline.informationStatement;
            informationIncoming = baseline.informationIncoming;
            payee.restore(baseline.payee);
            lines.clear();
            baseline.lines.forEach(line -> lines.add(line.copy()));
            unapplied = false;
            updated = baseline.updated;
        }

        BigDecimal total(boolean debit)
        {
            return lines.stream().map(line -> format.parseMoney(debit ? line.debit : line.credit))
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        String splitSummary(int ordinal)
        {
            return lines.stream().filter(line -> line.sourceOrdinal == ordinal).map(line ->
            {
                BigDecimal dr = format.parseMoney(line.debit);
                BigDecimal cr = format.parseMoney(line.credit);
                String amount = dr == null || cr == null || dr.signum() != 0 && cr.signum() != 0
                        ? "DR " + line.debit + " / CR " + line.credit
                        : dr.signum() != 0 ? "DR " + format.formatMoney(dr) : "CR " + format.formatMoney(cr);
                String category = line.hint.startsWith("Category: ") ? line.hint.substring(10) : line.hint;
                return amount + " — " + category + " — " + (line.account.value() == null ? "Unmapped" : line.account.value().code());
            }).collect(java.util.stream.Collectors.joining("; "));
        }

        TransactionCommand command()
        {
            if (format.parseDate(date) == null)
            {
                throw new IllegalArgumentException("Enter a valid transaction date.");
            }
            return new TransactionCommand(format.parseDate(date), id(payee.value()), memo, null,
                    lines.stream().map(LineDraft::command).toList(), List.of(),
                    new org.nonprofitbookkeeping.model.TransactionInformation(informationReference,
                            optionalDate(informationStatement, "statement date"), optionalDate(informationIncoming, "incoming date")));
        }
    }

    private final class LineDraft
    {
        private String hint = "Added line";
        private int sourceOrdinal;
        private String debit = "0";
        private String credit = "0";
        private String notes = "";
        private final ImportReferenceSuggestion account, fund, budget, activity, merchant;

        LineDraft()
        {
            this("", "", "", "");
        }

        LineDraft(String accountHint, String fundHint, String budgetHint, String merchantHint)
        {
            account = new ImportReferenceSuggestion(accountHint, true);
            fund = new ImportReferenceSuggestion(fundHint, true);
            budget = new ImportReferenceSuggestion(budgetHint, false);
            activity = new ImportReferenceSuggestion("", false);
            merchant = new ImportReferenceSuggestion(merchantHint, false);
            refreshChoices();
        }

        private LineDraft(LineDraft from)
        {
            hint = from.hint;
            sourceOrdinal = from.sourceOrdinal;
            debit = from.debit;
            credit = from.credit;
            notes = from.notes;
            function = from.function;
            account = from.account.copy();
            fund = from.fund.copy();
            budget = from.budget.copy();
            activity = from.activity.copy();
            merchant = from.merchant.copy();
        }

        LineDraft copy()
        {
            return new LineDraft(this);
        }

        void refreshChoices()
        {
            account.refresh(currentChoices.accounts());
            fund.refresh(currentChoices.funds());
            budget.refresh(currentChoices.budgetCategories());
            activity.refresh(currentChoices.activities());
            merchant.refresh(currentChoices.merchants());
        }
        private ExpenseFunction function;

        TransactionLineCommand command()
        {
            BigDecimal dr = format.parseMoney(debit);
            BigDecimal cr = format.parseMoney(credit);
            if (dr == null || cr == null)
            {
                throw new IllegalArgumentException("Enter valid debit and credit amounts.");
            }
            return new TransactionLineCommand(id(account.value()), id(fund.value()), id(budget.value()), id(activity.value()), id(merchant.value()),
                    dr, cr, false, notes, null, function);
        }
    }

    private java.time.LocalDate optionalDate(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        var parsed = format.parseDate(value);
        if (parsed == null)
        {
            throw new IllegalArgumentException("Enter a valid " + label + ".");
        }
        return parsed;
    }

    private static Long id(TransactionLineEditorModel.Option option)
    {
        return option == null ? null : option.id();
    }
}
