package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.layout.GridPane;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.interchange.coa.*;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.service.ApplicationPermission;
import org.nonprofitbookkeeping.service.CompanyChartView;

import java.nio.file.Path;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.*;

/** Inline, transient Chart JSON review over the existing parser and atomic commit service. */
final class CoaJsonReviewPane extends VBox implements AppPanel
{
    record Scope(String database, String company)
    {
        static Scope current()
        {
            var state = MainWindow.sharedSessionState();
            return new Scope(state.databaseSelection().activeDatabasePath(), state.multiCompany().activeCompanyCode());
        }
        boolean currentScope()
        {
            return equals(current());
        }
    }
    interface Operations
    {
        CoaImportPreview preview(CoaImportRequest request, Scope scope);
        String sourceText(CoaImportPreview preview, Scope scope);
        CoaImportResult commit(CoaImportPreview approved, Scope scope);
    }

    private record Row(int sourceRow, CoaPreviewItem item, boolean invalid, String validation)
    {
    }
    private final Operations operations;
    private final Supplier<Optional<Path>> chooseFile;
    private final CompanyUiFormat format;
    private final TabPane pages = new TabPane();
    private final TableView<Row> accounts = new TableView<>();
    private final TableView<InterchangeValidationMessage> diagnostics = new TableView<>();
    private final TextArea originalText = new TextArea();
    private final Label sourceLabel = new Label("Choose a Chart of Accounts JSON file.");
    private final Label summary = new Label();
    private final Label status = new Label();
    private final Label selectedMessage = new Label();
    private final ComboBox<CoaImportMode> mode = combo(CoaImportMode.values(), false);
    private boolean configuringTargets;
    private final ComboBox<CompanyChartView> targetChart = new ComboBox<>();
    private final Label targetChartLabel = new Label("Target existing chart");
    private final TextField chartName = text("coaJsonTargetChartName");
    private final TextField chartVersion = text("coaJsonTargetChartVersion");
    private final TextArea mappings = new TextArea();
    private final Button previewButton = new Button("Start New Review");
    private final Button commit = new Button("Import Reviewed Accounts…");
    private final Button refresh = new Button("Refresh Review");
    private final Button edit = new Button("Edit Selected");
    private final Button delete = new Button("Delete Selected Proposals");
    private final Button apply = new Button("Apply Account Changes");
    private final Button cancel = new Button("Cancel Account Changes");
    private final CheckBox reviewed = new CheckBox("I reviewed the proposed accounts and warnings");
    private final CheckBox opening = new CheckBox("Import opening-balance fields without creating accounting transactions");
    private final TextField code = text("coaReviewCode");
    private final TextField name = text("coaReviewName");
    private final ComboBox<AccountType> type = combo(AccountType.values(), false);
    private final ComboBox<AccountFunction> function = combo(AccountFunction.values(), true);
    private final ComboBox<AccountSubtype> subtype = combo(AccountSubtype.values(), true);
    private final ComboBox<NormalBalance> balance = combo(NormalBalance.values(), false);
    private final TextField parent = text("coaReviewParent");
    private final CheckBox posting = new CheckBox("Posting");
    private final CheckBox active = new CheckBox("Active");
    private final TextField from = text("coaReviewEffectiveFrom");
    private final TextField to = text("coaReviewEffectiveTo");
    private final TextField amount = text("coaReviewOpeningBalance");
    private final TextField currency = text("coaReviewCurrency");
    private final TextArea description = new TextArea();
    private final Label sourceCode = new Label();
    private final Label editorValidation = new Label();
    private final TextArea compatibility = new TextArea();
    private final GridPane editor = new GridPane();
    private final GridPane setupForm = new GridPane();
    private Path source;
    private Scope scope;
    private CoaImportReviewDraft draft;
    private CoaImportPreview preview;
    private Integer editingRow;
    private boolean populating;
    private boolean editorDirty;
    private boolean busy;
    private boolean committed;
    private boolean setupDirty;
    private boolean validationUnavailable;
    private Runnable capabilitiesChanged = () ->
    {
    };
    private Consumer<RunCommandResult> resultListener = ignored ->
    {
    };
    private final javafx.beans.value.ChangeListener<Boolean> permissionChanged = (o, a, b) -> availability();
    CoaJsonReviewPane(Operations operations, Supplier<Optional<Path>> chooseFile, Runnable back, CompanyUiFormat format)
    {
        this.operations = operations;
        this.chooseFile = chooseFile;
        this.format = format;
        setId("coaJsonReviewPane");
        setSpacing(6);
        setPadding(new Insets(8));
        setMinSize(0, 0);
        Button backButton = new Button("Back to Accounts");
        backButton.setId("coaReviewBack");
        backButton.setOnAction(e ->
        {
            if (!busy) back.run();
        });
        Button choose = new Button("Choose JSON…");
        choose.setId("coaReviewChooseFile");
        choose.setOnAction(e -> chooseSource());
        previewButton.setId("coaReviewPreview");
        previewButton.setOnAction(e -> startReview());
        commit.setId("coaReviewCommit");
        commit.setOnAction(e -> commitReviewed());
        refresh.setId("coaReviewRefresh");
        refresh.setOnAction(e -> revalidate());
        reviewed.setId("coaReviewAcknowledged");
        reviewed.setWrapText(true);
        opening.setId("coaJsonConfirmation");
        opening.setWrapText(true);
        reviewed.selectedProperty().addListener((o,a,b) -> availability());
        opening.selectedProperty().addListener((o,a,b) -> availability());
        sourceLabel.setWrapText(true);
        summary.setWrapText(true);
        status.setWrapText(true);
        status.setId("coaReviewStatus");
        VBox header = new VBox(6, new FlowPane(8, 6, backButton, choose, previewButton, refresh, commit), sourceLabel, summary, reviewed, opening, status);
        ScrollPane headerScroll = scroll(header);
        headerScroll.setPrefHeight(165);
        pages.setId("coaReviewPages");
        pages.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        pages.getTabs().setAll(new Tab("Import Setup", setup()), new Tab("Proposed Accounts", proposals()),
            new Tab("Edit Account", editPage()), new Tab("Original Source and Diagnostics", evidence()));
        pages.getSelectionModel().selectedIndexProperty().addListener((o,a,b) ->
        {
            if (b.intValue() == 2) editSelected();
            availability();
        });
        VBox.setVgrow(pages, Priority.ALWAYS);
        SplitPane layout = new SplitPane(headerScroll, pages);
        layout.setOrientation(Orientation.VERTICAL);
        layout.setId("coaReviewSplit");
        CompanySplitPaneStateBinder.bind(layout,"coa-json-review",.28);
        VBox.setVgrow(layout, Priority.ALWAYS);
        getChildren().setAll(layout);
        UiPermissionGate.deniedProperty(ApplicationPermission.BOOKKEEPING_WRITE).addListener(new javafx.beans.value.WeakChangeListener<>(permissionChanged));
        var weakPanel = new java.lang.ref.WeakReference<>(this);
        Runnable scopeChanged = () ->
        {
            var panel = weakPanel.get();
            if (panel != null) panel.scopeChanged();
        };
        var session = MainWindow.sharedSessionState();
        session.onMultiCompanyChanged(ignored -> onFx(scopeChanged));
        session.onDatabaseSelectionChanged(ignored -> onFx(scopeChanged));
        CompanyTableStateBinder.applyProductionPanel(this, AppPanelId.CHART_OF_ACCOUNTS);
        FullTextTooltipInstaller.install(this);
        availability();
    }

    private Node setup()
    {
        mode.setId("coaJsonImportMode");
        mode.setValue(CoaImportMode.MERGE_BY_CODE);
        mode.setConverter(new javafx.util.StringConverter<CoaImportMode>()
        {
            @Override public String toString(CoaImportMode value)
            {
                if (value == null) return "";
                return switch (value)
                {
                    case CREATE_NEW_CHART -> "Create a new chart";
                    case MERGE_BY_CODE -> "Merge into an existing chart";
                    case MAP_CODES -> "Map source codes into an existing chart";
                };
            }
            @Override public CoaImportMode fromString(String value)
            {
                throw new UnsupportedOperationException("Choose an import mode.");
            }
        });
        targetChart.setId("coaJsonTargetChart");
        targetChart.setMaxWidth(Double.MAX_VALUE);
        targetChart.setMinWidth(0);
        targetChart.setPromptText("Select the chart to merge into");
        targetChart.valueProperty().addListener((o,a,b) -> setupChanged());
        chartName.setText("Imported Chart");
        chartVersion.setText("1");
        mappings.setId("coaJsonCodeMappings");
        mappings.setPrefRowCount(6);
        mappings.setPromptText("One sourceCode=targetCode mapping per line");
        GridPane grid = setupForm;
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0,new Label("Import mode"),mode);
        grid.addRow(1,targetChartLabel,targetChart);
        grid.addRow(2,new Label("New chart name"),chartName);
        grid.addRow(3,new Label("New chart version"),chartVersion);
        grid.addRow(4,new Label("Code mappings"),mappings);
        Label help = new Label("Start New Review replaces the current proposed batch. Source files and saved accounts are unchanged until Import Reviewed Accounts.");
        help.setWrapText(true);
        grid.add(help,0,5,2,1);
        mode.valueProperty().addListener((o,a,b) ->
        {
            setupVisibility();
            setupChanged();
        });
        chartName.textProperty().addListener((o,a,b) -> setupChanged());
        chartVersion.textProperty().addListener((o,a,b) -> setupChanged());
        mappings.textProperty().addListener((o,a,b) -> setupChanged());
        setupVisibility();
        return scroll(grid);
    }

    private void setupVisibility()
    {
        boolean existing = mode.getValue() != CoaImportMode.CREATE_NEW_CHART;
        targetChart.setVisible(existing);
        targetChart.setManaged(existing);
        targetChartLabel.setVisible(existing);
        targetChartLabel.setManaged(existing);
        targetChart.setDisable(!existing);
        chartName.setDisable(existing);
        chartVersion.setDisable(mode.getValue() != CoaImportMode.CREATE_NEW_CHART);
        mappings.setDisable(mode.getValue() != CoaImportMode.MAP_CODES);
    }
    /** Refresh owned target choices without redirecting a retained review to another chart. */
    void configureTargets(List<CompanyChartView> choices, Long viewedId)
    {
        boolean fresh = !hasUnsavedChanges();
        CompanyChartView previous = targetChart.getValue();
        Long requested = fresh ? viewedId : previous == null ? null : previous.id();
        var eligible = choices.stream().filter(c -> c.status() != ChartStatus.RETIRED).toList();
        configuringTargets = true;
        try
        {
            targetChart.getItems().setAll(eligible);
            targetChart.setValue(eligible.stream().filter(c -> Objects.equals(c.id(),requested)).findFirst().orElse(null));
            if (fresh && eligible.isEmpty()) mode.setValue(CoaImportMode.CREATE_NEW_CHART);
            setupVisibility();
        }
        finally
        {
            configuringTargets = false;
        }
        if (!fresh && mode.getValue() != CoaImportMode.CREATE_NEW_CHART
            && !Objects.equals(previous, targetChart.getValue())) setupChanged();
    }

    private void setupChanged()
    {
        if (configuringTargets) return;
        if (draft != null)
        {
            setupDirty = true;
            reviewed.setSelected(false);
            status.setText("Import setup changed. Start New Review to use the new options.");
            availability();
        }
    }

    private static void onFx(Runnable action)
    {
        if (javafx.application.Platform.isFxApplicationThread()) action.run();
        else javafx.application.Platform.runLater(action);
    }

    private void scopeChanged()
    {
        if (draft != null && !committed)
        {
            validationUnavailable = true;
            reviewed.setSelected(false);
            status.setText(scope.currentScope()
                    ? "Original scope restored. Refresh Review before continuing; editor values are retained."
                    : "Company/database changed. Return to the original scope and refresh; this review is retained.");
        }
        availability();
    }

    private Node proposals()
    {
        accounts.setId("coaJsonPreviewTable");
        accounts.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        accounts.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        accounts.setPlaceholder(new Label("Start a review to see proposed accounts."));
        column(accounts,"Source code",r -> r.item().account().sourceCode(),110);
        column(accounts,"Target code",r -> r.item().targetCode(),110);
        column(accounts,"Name",r -> r.item().account().name(),230);
        column(accounts,"Status",r -> rowStatus(r),130);
        column(accounts,"Action",r -> r.item().disposition().name(),100);
        column(accounts,"Type",r -> Objects.toString(r.item().account().type(),""),100);
        column(accounts,"Function",r -> Objects.toString(r.item().account().function(),""),100);
        column(accounts,"Subtype",r -> Objects.toString(r.item().account().subtype(),""),125);
        column(accounts,"Normal",r -> Objects.toString(r.item().account().normalBalance(),""),90);
        column(accounts,"Parent source code",r -> Objects.toString(r.item().account().parentCode(),""),140);
        column(accounts,"Validation",Row::validation,650);
        accounts.setRowFactory(t -> new TableRow<>()
        {
            @Override protected void updateItem(Row row, boolean empty)
            {
                super.updateItem(row,empty);
                getStyleClass().removeAll("import-entry-invalid","import-entry-updated");
                if (!empty && row != null)
                {
                    if (row.invalid() || editorDirty && Objects.equals(editingRow,row.sourceRow())) getStyleClass().add("import-entry-invalid");
                    else if (draft.updated(row.sourceRow())) getStyleClass().add("import-entry-updated");
                }
            }
        });
        accounts.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> availability());
        accounts.setOnMouseClicked(e ->
        {
            if (e.getClickCount() == 2) editSelected();
        });
        edit.setId("coaReviewEdit");
        edit.setOnAction(e -> editSelected());
        delete.setId("coaReviewDelete");
        delete.setOnAction(e -> deleteSelected());
        VBox body = new VBox(6,new FlowPane(8,6,edit,delete),accounts);
        VBox.setVgrow(accounts,Priority.ALWAYS);
        body.setMinSize(0,0);
        return body;
    }

    private Node editPage()
    {
        type.setId("coaReviewType");
        function.setId("coaReviewFunction");
        subtype.setId("coaReviewSubtype");
        balance.setId("coaReviewNormalBalance");
        active.setId("coaReviewActive");
        posting.setId("coaReviewPosting");
        description.setId("coaReviewDescription");
        description.setPrefRowCount(3);
        description.setWrapText(true);
        compatibility.setEditable(false);
        compatibility.setPrefRowCount(3);
        editor.setHgap(8);
        editor.setVgap(8);
        editor.setMinWidth(Region.USE_PREF_SIZE);
        editor.addRow(0,new Label("Original source code"),sourceCode);
        editor.addRow(1,new Label("Target code"),code,new Label("Name"),name);
        editor.addRow(2,new Label("Type"),type,new Label("Function (optional)"),optionalField(function,"coaReviewClearFunction"));
        editor.addRow(3,new Label("Subtype (optional)"),optionalField(subtype,"coaReviewClearSubtype"),new Label("Normal balance"),balance);
        editor.addRow(4,new Label("Parent source / existing code"),parent,posting,active);
        editor.addRow(5,new Label("Effective from"),from,new Label("Effective to"),to);
        editor.addRow(6,new Label("Opening balance field"),amount,new Label("Currency"),currency);
        editor.addRow(7,new Label("Description"),description);
        GridPane.setColumnSpan(description,3);
        editor.addRow(8,new Label("Retained compatibility facts"),compatibility);
        GridPane.setColumnSpan(compatibility,3);
        editorValidation.setId("coaReviewEditorValidation");
        editorValidation.setWrapText(true);
        editorValidation.setMaxWidth(750);
        editor.add(editorValidation,0,9,4,1);
        for (var control : List.of(code,name,parent,from,to,amount,currency,description))
        control.textProperty().addListener((o,a,b) -> editorChanged());
        for (var control : List.of(type,function,subtype,balance)) control.valueProperty().addListener((o,a,b) ->
        {
            if (!populating) control.getButtonCell().getStyleClass().remove("import-source-suggestion");
            editorChanged();
        });
        posting.selectedProperty().addListener((o,a,b) -> editorChanged());
        active.selectedProperty().addListener((o,a,b) -> editorChanged());
        apply.setId("coaReviewApply");
        apply.setOnAction(e -> applyEditor());
        cancel.setId("coaReviewCancel");
        cancel.setOnAction(e -> cancelEditor());
        return scroll(new VBox(8,new FlowPane(8,6,apply,cancel),editor));
    }

    private Node evidence()
    {
        diagnostics.setId("coaJsonValidationMessages");
        diagnostics.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        column(diagnostics,"Severity",m -> m.severity().name(),85);
        column(diagnostics,"Code",InterchangeValidationMessage::code,230);
        column(diagnostics,"Location",InterchangeValidationMessage::path,220);
        column(diagnostics,"Message",InterchangeValidationMessage::message,750);
        selectedMessage.setWrapText(true);
        diagnostics.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> selectedMessage.setText(b == null ? "" : b.path() + " — " + b.message()));
        Button show = new Button("Show Account");
        show.setId("coaReviewShowDiagnosticAccount");
        show.setOnAction(e -> showDiagnosticAccount());
        VBox messageBox = new VBox(6,new FlowPane(show),diagnostics,selectedMessage);
        VBox.setVgrow(diagnostics,Priority.ALWAYS);
        originalText.setId("coaReviewOriginalSource");
        originalText.setEditable(false);
        originalText.setWrapText(false);
        TabPane evidence = new TabPane(new Tab("Validation Messages",messageBox), new Tab("Original JSON",originalText));
        evidence.setId("coaReviewEvidencePages");
        evidence.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        return evidence;
    }

    private void editorChanged()
    {
        if (!populating && editingRow != null)
        {
            editorDirty = true;
            reviewed.setSelected(false);
            accounts.refresh();
            availability();
        }
    }

    void chooseSource()
    {
        if (busy) return;
        chooseFile.get().ifPresent(path ->
        {
            source = path;
            sourceLabel.setText("Source: " + source);
            setupDirty = draft != null;
            reviewed.setSelected(false);
            pages.getSelectionModel().select(0);
            status.setText("Choose import options, then Start New Review.");
            availability();
        });
    }

    private void startReview()
    {
        if (source == null || busy || !UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE)) return;
        try
        {
            if (mode.getValue() != CoaImportMode.CREATE_NEW_CHART && targetChart.getValue() == null)
            {
                throw new IllegalArgumentException("Select the target existing chart, or choose Create a new chart.");
            }
            Long target = mode.getValue() == CoaImportMode.CREATE_NEW_CHART ? null : targetChart.getValue().id();
            CoaImportRequest request = new CoaImportRequest(source,mode.getValue(),chartName.getText(),chartVersion.getText(),
                ChartOfAccountsInterchangePanel.parseMappings(mappings.getText()),false,null,target);
            Scope requestedScope = Scope.current();
            run("coa-json-preview",() ->
            {
                var result = operations.preview(request,requestedScope);
                return new AbstractMap.SimpleImmutableEntry<>(result,operations.sourceText(result,requestedScope));
            },result ->
            {
                scope = requestedScope;
                draft = new CoaImportReviewDraft(result.getKey());
                preview = result.getKey();
                originalText.setText(result.getValue());
                committed = false;
                editingRow = null;
                editorDirty = false;
                setupDirty = false;
                validationUnavailable = false;
                reviewed.setSelected(false);
                opening.setSelected(false);
                render();
                pages.getSelectionModel().select(1);
                status.setText("Review the proposed accounts. Correct or remove proposals before importing.");
            });
        }
        catch (RuntimeException ex)
        {
            fail(ex);
        }
    }

    private void render()
    {
        List<Row> rows = new ArrayList<>();
        List<Integer> sourceRows = draft.rows();
        Map<Integer,List<InterchangeValidationMessage>> byRow = new HashMap<>();
        boolean globalError = false;
        var pattern = java.util.regex.Pattern.compile("\\$\\.(?:accounts|chartOfAccounts)\\[([0-9]+)\\]");
        for (var message : preview.messages())
        {
            var match = pattern.matcher(message.path());
            if (match.find()) byRow.computeIfAbsent(Integer.parseInt(match.group(1)),ignored -> new ArrayList<>()).add(message);
            else if (message.blocking()) globalError = true;
        }
        for (int i = 0; i < preview.items().size(); i++)
        {
            var messages = byRow.getOrDefault(i,List.of());
            boolean invalid = globalError || preview.items().get(i).disposition() == CoaPreviewItem.Disposition.BLOCKED
            || messages.stream().anyMatch(InterchangeValidationMessage::blocking);
            String validation = String.join(" | ",messages.stream().map(InterchangeValidationMessage::message).toList());
            if (globalError) validation += " Graph validation requires attention; see Diagnostics.";
            rows.add(new Row(sourceRows.get(i),preview.items().get(i),invalid,validation));
        }
        accounts.getItems().setAll(rows);
        diagnostics.getItems().setAll(preview.messages());
        summary.setText("Target: " + preview.targetLabel() + " | " + rows.size() + " proposals | " + draft.removedCount()
            + " removed | " + preview.counts().errors() + " errors | " + preview.counts().warnings() + " warnings | SHA-256 " + preview.sourceSha256());
        opening.setVisible(!preview.confirmations().isEmpty());
        opening.setManaged(opening.isVisible());
        availability();
    }

    private String rowStatus(Row row)
    {
        if (editorDirty && Objects.equals(editingRow,row.sourceRow())) return "Unapplied edits";
        if (row.invalid()) return "Needs attention";
        return draft.updated(row.sourceRow()) ? "Updated — ready" : "Ready";
    }

    private void editSelected()
    {
        Row selected = accounts.getSelectionModel().getSelectedItem();
        if (selected == null || busy || committed) return;
        if (editorDirty)
        {
            if (!Objects.equals(editingRow,selected.sourceRow())) status.setText("Apply or cancel the current account changes before editing another proposal.");
            return;
        }
        editingRow = selected.sourceRow();
        loadEditor();
        pages.getSelectionModel().select(2);
    }

    private void loadEditor()
    {
        CoaAccountData value = draft.account(editingRow);
        populating = true;
        try
        {
            sourceCode.setText(value.sourceCode());
            code.setText(value.code());
            name.setText(value.name());
            type.setValue(value.type());
            function.setValue(value.function());
            subtype.setValue(value.subtype());
            balance.setValue(value.normalBalance());
            parent.setText(Objects.toString(value.parentCode(),""));
            posting.setSelected(value.posting());
            active.setSelected(value.active());
            from.setText(format.formatDate(value.effectiveFrom()));
            to.setText(format.formatDate(value.effectiveTo()));
            amount.setText(value.openingBalance().stripTrailingZeros().scale() > 2 ? value.openingBalance().toPlainString() : format.formatMoney(value.openingBalance()));
            currency.setText(Objects.toString(value.currency(),""));
            description.setText(Objects.toString(value.description(),""));
            compatibility.setText("Fund IDs: " + value.associatedFundIds() + "\nSupplemental kinds: " + value.supplementalLineKinds() + "\nUnsupported fields: " + value.unsupportedFields());
            editorValidation.setText(accounts.getItems().stream().filter(row -> row.sourceRow() == editingRow)
                .map(Row::validation).findFirst().orElse(""));
            int ordinal = draft.rows().indexOf(editingRow);
            Map<String,ComboBox<?>> lookupFields = Map.of("type",type,"function",function,"subtype",subtype,"normalBalance",balance);
            lookupFields.forEach((field,combo) ->
            {
                combo.getButtonCell().getStyleClass().remove("import-source-suggestion");
                if (preview.messages().stream().anyMatch(message -> message.blocking()
                        && message.path().equals("$.accounts[" + ordinal + "]." + field)))
                {
                    combo.getButtonCell().getStyleClass().add("import-source-suggestion");
                }
            });
            editorDirty = false;
        }
        finally
        {
            populating = false;
        }
        availability();
    }

    private void applyEditor()
    {
        if (editingRow == null || !canEdit()) return;
        try
        {
            BigDecimal openingValue = format.parseMoney(amount.getText());
            if (openingValue == null) throw new IllegalArgumentException("Opening balance must be a valid amount.");
            openingValue = openingValue.setScale(2,RoundingMode.UNNECESSARY);
            var value = CoaImportReviewDraft.definition(draft.account(editingRow),code.getText(),name.getText(),type.getValue(),function.getValue(),
                subtype.getValue(),balance.getValue(),parent.getText(),posting.isSelected(),active.isSelected(),date(from),date(to),openingValue,
                description.getText(),currency.getText());
            var candidate = draft.copy();
            candidate.apply(editingRow,value);
            revalidate(candidate,false,() ->
            {
                editorDirty = false;
                pages.getSelectionModel().select(1);
                status.setText("Account changes applied to the proposal; all blocking errors must be resolved before import.");
            });
        }
        catch (RuntimeException ex)
        {
            fail(ex);
        }
    }

    private LocalDate date(TextField field)
    {
        if (field.getText().isBlank()) return null;
        LocalDate date = format.parseDate(field.getText());
        if (date == null) throw new IllegalArgumentException("Invalid date: " + field.getText());
        return date;
    }

    private void cancelEditor()
    {
        if (busy || editingRow == null) return;
        loadEditor();
        accounts.refresh();
        pages.getSelectionModel().select(1);
        status.setText("Unapplied account changes cancelled.");
    }

    private void deleteSelected()
    {
        if (!canEdit() || editorDirty) return;
        var selected = accounts.getSelectionModel().getSelectedItems().stream().map(Row::sourceRow).toList();
        if (selected.isEmpty()) return;
        var candidate = draft.copy();
        candidate.remove(selected);
        revalidate(candidate,false,() ->
        {
            editingRow = null;
            status.setText("Removed " + selected.size() + " proposed accounts; saved accounts and source file are unchanged.");
        });
    }

    private void revalidate()
    {
        if (draft == null || busy || committed || !scope.currentScope()) return;
        revalidate(draft.copy(),true,() -> status.setText(editorDirty ? "Review refreshed; unapplied editor fields are retained." : "Review refreshed; update markers cleared."));
    }

    private void revalidate(CoaImportReviewDraft candidate, boolean clearMarkers, Runnable completed)
    {
        reviewed.setSelected(false);
        opening.setSelected(false);
        run("coa-json-review-validate",() -> operations.preview(candidate.request(false),scope),result ->
        {
            draft = candidate;
            preview = result;
            validationUnavailable = false;
            if (clearMarkers) draft.clearUpdateMarkers();
            render();
            completed.run();
        });
    }

    private void commitReviewed()
    {
        if (!ready()) return;
        CoaImportRequest request = draft.request(opening.isSelected());
        Scope frozen = scope;
        run("coa-json-reviewed-import",() ->
        {
            CoaImportPreview confirmed = operations.preview(request,frozen);
            return operations.commit(confirmed,frozen);
        },result ->
        {
            if (!result.committed())
            {
                validationUnavailable = true;
                diagnostics.getItems().setAll(result.messages());
                status.setText("Import rolled back: " + result.messages().stream().filter(InterchangeValidationMessage::blocking)
                    .map(InterchangeValidationMessage::message).reduce((a,b) -> b).orElse("No accounts saved."));
                resultListener.accept(new RunCommandResult(false,status.getText()));
                return;
            }
            committed = true;
            editorDirty = false;
            status.setText("Imported reviewed accounts: " + result.counts().created() + " created, " + result.counts().updated() + " updated, " + result.counts().identical() + " identical.");
            resultListener.accept(new RunCommandResult(true,status.getText()));
        });
    }

    private <T> void run(String task, Supplier<T> action, Consumer<T> completed)
    {
        busy = true;
        status.setText("Working…");
        availability();
        Scope started = Scope.current();
        UiAsync.run(task,action,result ->
        {
            busy = false;
            if (!started.currentScope())
            {
                validationUnavailable = true;
                status.setText("Company/database changed; the original review was retained. Return to its original scope and refresh before continuing.");
                availability();
                return;
            }
            completed.accept(result);
            availability();
        },error ->
        {
            busy = false;
            validationUnavailable = true;
            reviewed.setSelected(false);
            fail(error);
            resultListener.accept(new RunCommandResult(false,status.getText()));
            availability();
        });
    }

    private void showDiagnosticAccount()
    {
        var selected = diagnostics.getSelectionModel().getSelectedItem();
        if (selected == null || draft == null) return;
        var match = java.util.regex.Pattern.compile("\\[(\\d+)\\]").matcher(selected.path());
        if (match.find())
        {
            int index = Integer.parseInt(match.group(1));
            List<Integer> sourceRows = draft.rows();
            if (index < sourceRows.size())
            {
                int sourceRow = sourceRows.get(index);
                Row account = accounts.getItems().stream().filter(row -> row.sourceRow() == sourceRow).findFirst().orElse(null);
                accounts.getSelectionModel().clearSelection();
                accounts.getSelectionModel().select(account);
                editSelected();
            }
        }
    }

    private boolean canEdit()
    {
        return draft != null && !busy && !committed && scope.currentScope() && UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE);
    }

    private boolean ready()
    {
        int page = pages.getSelectionModel().getSelectedIndex();
        return canEdit() && (page == 1 || page == 3) && !setupDirty && !validationUnavailable
        && !editorDirty && !preview.hasBlockingErrors() && !draft.rows().isEmpty()
        && reviewed.isSelected() && (!opening.isVisible() || opening.isSelected());
    }

    private void availability()
    {
        boolean writable = canEdit();
        commit.setDisable(!ready());
        refresh.setDisable(draft == null || busy || committed || scope != null && !scope.currentScope());
        edit.setDisable(!writable || accounts.getSelectionModel().getSelectedItem() == null);
        delete.setDisable(!writable || editorDirty || accounts.getSelectionModel().getSelectedItems().isEmpty());
        apply.setDisable(!writable || editingRow == null);
        cancel.setDisable(busy || editingRow == null || committed);
        editor.setDisable(!writable || editingRow == null);
        setupForm.setDisable(busy);
        previewButton.setDisable(source == null || busy || !UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE));
        reviewed.setDisable(!writable || editorDirty);
        opening.setDisable(!writable || editorDirty);
        capabilitiesChanged.run();
    }

    private void fail(Throwable error)
    {
        status.setText(UiErrors.safeMessage(error));
        availability();
    }

    @Override
    public void onPanelShown()
    {
        availability();
    }

    boolean busy()
    {
        return busy;
    }

    boolean hasReview()
    {
        return draft != null;
    }

    @Override
    public String title()
    {
        return "Chart JSON Review";
    }

    @Override
    public Node root()
    {
        return this;
    }

    @Override
    public boolean hasUnsavedChanges()
    {
        return !committed && (source != null || draft != null || editorDirty);
    }

    @Override
    public Set<AppCommand> commandCapabilities()
    {
        return (pages.getSelectionModel().getSelectedIndex() == 2 ? canEdit() && editingRow != null : ready())
        ? AppPanel.capabilities(AppCommand.SAVE_ACTIVE) : Set.of();
    }

    @Override
    public String commandLabel(AppCommand command)
    {
        return pages.getSelectionModel().getSelectedIndex() == 2 ? "Apply Account Changes" : "Import Reviewed Accounts…";
    }

    @Override
    public Optional<ApplicationPermission> requiredPermission(AppCommand command)
    {
        return Optional.of(ApplicationPermission.BOOKKEEPING_WRITE);
    }

    @Override
    public void onSave()
    {
        if (pages.getSelectionModel().getSelectedIndex() == 2) applyEditor();
        else commitReviewed();
    }

    @Override
    public String commandResultMessage(AppCommand command)
    {
        return status.getText();
    }

    @Override
    public String saveUnavailableReason()
    {
        return "Resolve blocking errors, Apply/Cancel editor changes and review the proposals and opening balances before importing.";
    }

    @Override
    public void setCommandCapabilitiesChangedListener(Runnable listener)
    {
        capabilitiesChanged = listener;
    }

    @Override
    public void setCommandResultListener(Consumer<RunCommandResult> listener)
    {
        resultListener = listener;
    }

    private static TextField text(String id)
    {
        TextField field = new TextField();
        field.setId(id);
        return field;
    }

    private static Node optionalField(ComboBox<?> combo, String id)
    {
        Button clear = new Button("Clear");
        clear.setId(id);
        clear.setOnAction(event -> combo.setValue(null));
        return new HBox(6,combo,clear);
    }

    private static <T> ComboBox<T> combo(T[] values, boolean optional)
    {
        ComboBox<T> combo = new ComboBox<>();
        if (optional) combo.getItems().add(null);
        combo.getItems().addAll(values);
        combo.setMinWidth(100);
        Function<Boolean,ListCell<T>> cell = button -> new ListCell<>()
        {
            @Override
            protected void updateItem(T value, boolean empty)
            {
                super.updateItem(value,empty);
                setText(empty && !button ? null : value == null ? (optional ? "None" : "Choose…") : value.toString());
            }
        };
        combo.setCellFactory(ignored -> cell.apply(false));
        combo.setButtonCell(cell.apply(true));
        return combo;
    }

    private static ScrollPane scroll(Node content)
    {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setMinSize(0,0);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return scroll;
    }

    private static <T> void column(TableView<T> table, String title, Function<T,String> value, double width)
    {
        TableColumn<T,String> column = new TableColumn<>(title);
        column.setUserData(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        column.setMinWidth(65);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        table.getColumns().add(column);
    }
}
