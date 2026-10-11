package org.nonprofitbookkeeping.ui;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.nonprofitbookkeeping.service.ApplicationPermission;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.BankAccountOption;
import org.nonprofitbookkeeping.service.CheckExceptionService.Row;
import org.nonprofitbookkeeping.service.PaymentReference;
import java.time.LocalDate;
import java.util.function.Function;
/** Reachable reconciliation check review, with explicit ledger versus factual actions. */
final class CheckExceptionsPane
{
    private final CompanyUiFormat format = CompanyUiFormat.activeCompany();
    private final VBox root = new VBox(8);
    private final TableView<Row> table = new TableView<>();
    private final ComboBox<BankAccountOption> bank = new ComboBox<>();
    private final DatePicker through = new DatePicker(ActivePeriodContext.get());
    private final DatePicker ageCutoff = new DatePicker();
    private final DatePicker actionDate = new DatePicker(ActivePeriodContext.get());
    private final DatePicker delivery = new DatePicker();
    private final TextField evidence = new TextField();
    private final TextField newNumber = new TextField();
    private final TextArea note = new TextArea();
    private final Label status = new Label();
    private final CheckBox exceptionsOnly = new CheckBox("Exceptions only");
    private java.util.List<Row> rows = java.util.List.of();
    private boolean busy;
    private boolean dirty;
    private boolean loading;
    private Row selectedRow;
    private VBox editor;
    CheckExceptionsPane()
    {
        root.setId("checkExceptionsPane");
        root.setMinSize(0, 0);
        table.setId("checkExceptionsTable");
        table.setMinSize(0, 0);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        column("Bank", "bank", Row::bank, value -> value, 180);
        column("Check / Reference", "reference", Row::reference, value -> value, 150);
        column("Date", "date", Row::date, format::formatDate, 110);
        column("Amount", "amount", Row::amount, format::formatMoney, 110);
        column("Lifecycle", "lifecycle", Row::lifecycle, value -> value, 130);
        column("Transaction", "transaction", Row::transactionId, CheckExceptionsPane::text, 110);
        column("Reversal", "reversal", Row::reversalId, CheckExceptionsPane::text, 110);
        column("Replacement", "replacement", Row::replacementId, CheckExceptionsPane::text, 110);
        column("Replaces", "replaces", Row::replacesId, CheckExceptionsPane::text, 110);
        column("Issue", "issued", r -> r.facts() == null ? null : r.facts().issuedOn(), format::formatDate, 110);
        column("Delivery", "delivery", r -> r.facts() == null ? null : r.facts().deliveredOn(), format::formatDate, 110);
        column("Evidence reference", "evidence", r -> r.facts() == null ? null : r.facts().evidenceReference(), value -> value, 220);
        column("Review date", "reviewed", r -> r.facts() == null ? null : r.facts().reviewedOn(), format::formatDate, 110);
        column("Review note", "reviewNote", r -> r.facts() == null ? null : r.facts().reviewNote(), value -> value, 260);
        column("Exceptions / history", "exceptions", Row::exceptions, value -> value, 380);
        bank.setId("checkReviewBank");
        bank.setPromptText("All configured banks");
        bank.setPrefWidth(280);
        bank.setConverter(new StringConverter<>()
        {
            @Override
            public String toString(BankAccountOption value)
            {
                return value == null ? "All banks" : value.label();
            }
            @Override
            public BankAccountOption fromString(String value)
            {
                return null;
            }
        });
        through.setId("checkReviewThrough");
        ageCutoff.setId("checkAgeCutoff");
        actionDate.setId("checkActionDate");
        delivery.setId("checkDeliveryDate");
        for (DatePicker picker : new DatePicker[]{through, ageCutoff, actionDate, delivery})
        {
            format.install(picker);
        }
        note.setId("checkReviewNote");
        note.setPrefRowCount(3);
        note.setWrapText(true);
        evidence.setId("checkEvidenceReference");
        newNumber.setId("checkReplacementNumber");
        Label explanation = new Label("Age review never changes the ledger or extinguishes an obligation. Record Review changes delivery/evidence facts only. Void requires an explicit cancellation reason and reverses the whole selected transaction. Reissue reverses one check transaction and creates a linked new check with unchanged allocation: no double expense. Cleared-state changes belong to Match. Retained correction history cannot be deleted or edited; correct the current instrument by reversal.");
        explanation.setWrapText(true);
        Label policy = new Label("D04 stale-triggered accounting awaits the accounting authority. Choose an age cutoff for review; no expiry is assumed. External evidence references are factual pointers, not attachments or proof verification. Bank errors/differences must be resolved in Bank Transactions or the matching workflow.");
        policy.setWrapText(true);
        Button refresh = action("Refresh", "checkReviewRefresh", this::refresh, false);
        Button allBanks = action("All banks", "checkReviewAllBanks", () ->
        {
            bank.setValue(null);
            refresh();
        }, false);
        exceptionsOnly.setOnAction(event -> filter());
        FlowPane filters = new FlowPane(8, 8, new Label("Bank"), bank, new Label("Review through"), through,
                new Label("Issued on/before (age review)"), ageCutoff, exceptionsOnly, refresh, allBanks);
        VBox header = new VBox(8, explanation, policy, filters, status);
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("Review / correction date"), actionDate);
        form.addRow(1, new Label("Delivery date (optional)"), delivery);
        form.addRow(2, new Label("External evidence reference"), evidence);
        form.addRow(3, new Label("New check number (Reissue)"), newNumber);
        form.addRow(4, new Label("Factual review / cancellation reason"), note);
        GridPane.setHgrow(evidence, Priority.ALWAYS);
        GridPane.setHgrow(note, Priority.ALWAYS);
        FlowPane actions = new FlowPane(8, 8,
                action("Record Review", "checkRecordReview", this::review, true),
                action("Void Selected", "checkVoid", () -> correct(false), true),
                action("Reissue Selected", "checkReissue", () -> correct(true), true),
                action("Open in Journal", "checkOpenJournal", this::openJournal, false),
                action("Open Reconciliation", "checkOpenReconciliation", this::openReconciliation, false));
        editor = new VBox(8, form, actions);
        editor.setMinSize(javafx.scene.layout.Region.USE_PREF_SIZE, javafx.scene.layout.Region.USE_PREF_SIZE);
        SplitPane detail = new SplitPane(table, scroll(editor, "checkReviewEditorScroll"));
        detail.setId("checkReviewDetailSplit");
        detail.setOrientation(Orientation.VERTICAL);
        detail.setMinSize(0, 0);
        detail.setDividerPositions(.65);
        CompanySplitPaneStateBinder.bind(detail, "check-review-detail", .65);
        SplitPane split = new SplitPane(scroll(header, "checkReviewHeaderScroll"), detail);
        split.setId("checkReviewSplit");
        split.setOrientation(Orientation.VERTICAL);
        split.setMinSize(0, 0);
        split.setDividerPositions(.25);
        CompanySplitPaneStateBinder.bind(split, "check-review", .25);
        root.getChildren().add(split);
        VBox.setVgrow(split, Priority.ALWAYS);
        CompanyTableStateBinder.applyProductionPanel(root, AppPanelId.RECONCILIATION_RUNS);
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, row) ->
        {
            if (loading || busy)
            {
                return;
            }
            if (!discard())
            {
                loading = true;
                table.getSelectionModel().select(old);
                loading = false;
                return;
            }
            selectedRow = row;
            loading = true;
            delivery.setValue(row == null || row.facts() == null ? null : row.facts().deliveredOn());
            evidence.setText(row == null || row.facts() == null ? "" : text(row.facts().evidenceReference()));
            note.setText(row == null || row.facts() == null ? "" : text(row.facts().reviewNote()));
            newNumber.clear();
            loading = false;
            dirty = false;
        });
        note.textProperty().addListener((obs, old, value) -> changed());
        evidence.textProperty().addListener((obs, old, value) -> changed());
        newNumber.textProperty().addListener((obs, old, value) -> changed());
        delivery.valueProperty().addListener((obs, old, value) -> changed());
        actionDate.valueProperty().addListener((obs, old, value) -> changed());
        var reconciliationService = UiServiceRegistry.bankReconciliationWorkspace();
        String company = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
        UiAsync.run("check-review-bank-options", () -> reconciliationService.listConfiguredBankAccounts(company),
                values -> bank.getItems().setAll(values), ex -> status.setText(UiErrors.safeMessage(ex)));
        refresh();
    }

    Node root()
    {
        return root;
    }

    boolean hasUnsavedChanges()
    {
        return dirty;
    }

    private void changed()
    {
        if (!loading && selectedRow != null)
        {
            dirty = true;
        }
    }

    private boolean discard()
    {
        if (!dirty)
        {
            return true;
        }
        boolean accepted = new Alert(Alert.AlertType.CONFIRMATION, "Discard unsaved check review fields?",
                ButtonType.OK, ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
        if (accepted)
        {
            dirty = false;
        }
        return accepted;
    }

    private void refresh()
    {
        if (busy || !discard())
        {
            return;
        }
        Long bankId = bank.getValue() == null ? null : bank.getValue().id();
        LocalDate date = through.getValue(), cutoff = ageCutoff.getValue();
        busy = true;
        editor.setDisable(true);
        var reviewService = UiServiceRegistry.checkExceptions();
        UiAsync.run("check-exception-report", () -> reviewService.report(bankId, date, cutoff),
                values ->
                {
                    busy = false;
                    editor.setDisable(false);
                    rows = values;
                    loading = true;
                    filter();
                    loading = false;
                    selectedRow = null;
                    status.setText("Check and bank exception report loaded.");
                },
                ex ->
                {
                    busy = false;
                    editor.setDisable(false);
                    status.setText("Review failed: " + UiErrors.safeMessage(ex));
                });
    }

    private void filter()
    {
        if (!loading && !discard())
        {
            return;
        }
        table.getItems().setAll(rows.stream().filter(r -> !exceptionsOnly.isSelected() || !r.exceptions().isBlank()).toList());
    }

    private Row selected()
    {
        Row row = selectedRow;
        if (row == null || row.facts() == null || row.transactionId() == null)
        {
            throw new IllegalArgumentException("Select a check instrument row.");
        }
        return row;
    }

    private void review()
    {
        try
        {
            Row row = selected();
            LocalDate date = actionDate.getValue(), delivered = delivery.getValue();
            String external = evidence.getText(), reason = note.getText();
            var reviewService = UiServiceRegistry.checkExceptions();
            run(() ->
            {
                reviewService.review(row.transactionId(), row.accountId(), row.reference(),
                        delivered, external, date, reason, DesktopActorIdentity.current());
                return "Review saved; ledger and obligation unchanged.";
            });
        }
        catch (RuntimeException ex)
        {
            status.setText(UiErrors.safeMessage(ex));
        }
    }

    private void correct(boolean reissue)
    {
        try
        {
            Row row = selected();
            LocalDate date = actionDate.getValue();
            String reason = note.getText();
            if (reason == null || reason.isBlank())
            {
                throw new IllegalArgumentException("An explicit cancellation reason is required.");
            }
            PaymentReference replacement = reissue ? new PaymentReference(PaymentReference.Method.CHECK,
                    newNumber.getText(), date, null, evidence.getText(), null, null) : null;
            if (!"OUTSTANDING".equals(row.lifecycle()))
            {
                throw new IllegalArgumentException("Select an outstanding check; cleared/cancelled history requires reconciliation review.");
            }
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    (reissue ? "Void and replace" : "Void") + " transaction #" + row.transactionId()
                            + " in full on " + format.formatDate(date) + "? Confirm the cancellation decision; age alone is insufficient.",
                    ButtonType.OK, ButtonType.CANCEL);
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK)
            {
                return;
            }
            var correctionService = UiServiceRegistry.transactionCorrection();
            run(() ->
            {
                var result = reissue ? correctionService.reissueCheck(row.transactionId(), date,
                        DesktopActorIdentity.current(), reason, replacement)
                        : correctionService.reverse(row.transactionId(), date, DesktopActorIdentity.current(), reason, false);
                return "Reversal #" + result.reversalTransactionId() + (result.replacementTransactionId() == null ? ""
                        : "; replacement #" + result.replacementTransactionId()) + ". Ledger changed; retained history is linked.";
            });
        }
        catch (RuntimeException ex)
        {
            status.setText(UiErrors.safeMessage(ex));
        }
    }

    private void run(java.util.function.Supplier<String> operation)
    {
        if (busy)
        {
            return;
        }
        busy = true;
        editor.setDisable(true);
        UiAsync.run("check-review-action", operation, value ->
        {
            busy = false;
            editor.setDisable(false);
            dirty = false;
            refresh();
            status.setText(value);
        }, ex ->
        {
            busy = false;
            editor.setDisable(false);
            status.setText("Action failed: " + UiErrors.safeMessage(ex));
        });
    }

    private void openJournal()
    {
        Row row = table.getSelectionModel().getSelectedItem();
        if (row != null && row.transactionId() != null)
        {
            DrillThroughCoordinator.openTransactionEditorWithContext("Txn #" + row.transactionId());
        }
        else
        {
            status.setText("Select a row with a ledger transaction.");
        }
    }

    private void openReconciliation()
    {
        Row row = table.getSelectionModel().getSelectedItem();
        if (row != null && row.sessionId() != null)
        {
            DrillThroughCoordinator.openPanelWithContext(AppPanelId.RECONCILIATION_RUNS,
                    BankImportNavigationContext.forReconciliationSession(row.sessionId()));
        }
        else
        {
            status.setText("Select a bank difference row with a reconciliation session.");
        }
    }

    private Button action(String title, String id, Runnable runnable, boolean write)
    {
        Button button = new Button(title);
        button.setId(id);
        button.setTooltip(new Tooltip(title));
        button.setOnAction(event -> runnable.run());
        if (write)
        {
            UiPermissionGate.gate(button, ApplicationPermission.BOOKKEEPING_WRITE, title);
        }
        return button;
    }

    private static ScrollPane scroll(Node node, String id)
    {
        ScrollPane pane = new ScrollPane(node);
        pane.setId(id);
        pane.setMinSize(0, 0);
        pane.setFitToWidth(true);
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return pane;
    }

    private <T> void column(String title, String key, Function<Row, T> value, Function<T, String> render, double width)
    {
        TableColumn<Row, T> column = new TableColumn<>(title);
        column.setUserData(key);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        column.setCellFactory(ignored -> new TableCell<>()
        {
            @Override
            protected void updateItem(T item, boolean empty)
            {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : render.apply(item));
            }
        });
        table.getColumns().add(column);
    }

    private static String text(Object value)
    {
        return value == null ? "" : value.toString();
    }
}
