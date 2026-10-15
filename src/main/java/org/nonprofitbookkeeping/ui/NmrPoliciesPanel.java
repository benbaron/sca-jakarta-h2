package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.ApplicationPermission;
import org.nonprofitbookkeeping.service.NmrPolicyTerms;
import org.nonprofitbookkeeping.service.NmrPolicyView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Dated configuration only: no NMR assessment, collection or remittance. */
public final class NmrPoliciesPanel implements AppPanel
{
    private final VBox root = new VBox(8);
    private final TableView<NmrPolicyView> table = new TableView<>();
    private final Map<String, TextInputControl> text = new LinkedHashMap<>();
    private final DatePicker from = new DatePicker();
    private final DatePicker through = new DatePicker();
    private final DatePicker sourceDate = new DatePicker();
    private final TextField rate = new TextField();
    private final GridPane form = new GridPane();
    private final Label status = new Label("Choose New Version to enter source-backed configuration.");
    private final Label mode = new Label("Retained policy history");
    private final Button save = new Button("Save Policy Version");
    private final Button end = new Button("End Selected Version…");
    private final BooleanSupplier confirmDiscard;
    private CompanyUiFormat format = CompanyUiFormat.activeCompany();
    private Runnable capabilitiesChanged = () ->
    {
    };
    private UUID request;
    private BigDecimal exactRate;
    private boolean loading;
    private boolean newMode;
    private boolean dirty;
    private String draftContext;

    public NmrPoliciesPanel()
    {
        this(() -> new Alert(Alert.AlertType.CONFIRMATION, "Discard unsaved NMR policy configuration?",
                ButtonType.OK, ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK);
    }

    NmrPoliciesPanel(BooleanSupplier confirmDiscard)
    {
        this.confirmDiscard = Objects.requireNonNull(confirmDiscard, "confirmDiscard");
        root.setPadding(new Insets(8));
        root.setMinSize(0, 0);
        build();
        reload(null);
    }

    private void build()
    {
        Label title = new Label("NMR Policies");
        title.getStyleClass().add("panel-title");
        Label help = new Label("Record dated source policy for this company. Saving does not verify external approval, calculate NMR, or create accounting entries. No rate or currency is prefilled.");
        help.setWrapText(true);
        Label retained = new Label("Saved version terms cannot be edited or deleted. End a version with a reason, then add a non-overlapping successor. Rule descriptions are recorded evidence, not calculated rules. Whole-database backup preserves these records; SCLX currently omits them.");
        retained.setId("nmrPolicyRetention");
        retained.setWrapText(true);
        status.setWrapText(true);
        Button add = new Button("New Version");
        add.setId("nmrPolicyNew");
        add.setOnAction(event -> onNew());
        save.setId("nmrPolicySave");
        save.setOnAction(event -> onSave());
        end.setId("nmrPolicyEnd");
        end.setOnAction(event -> endSelected());
        Button refresh = new Button("Refresh");
        refresh.setId("nmrPolicyRefresh");
        refresh.setOnAction(event ->
        {
            if (discard())
            {
                reload(null);
            }
        });
        UiPermissionGate.gate(add, ApplicationPermission.COMPANY_ADMIN, "Create NMR policy configuration");
        UiPermissionGate.gate(save, ApplicationPermission.COMPANY_ADMIN, "Save NMR policy configuration");
        UiPermissionGate.gate(end, ApplicationPermission.COMPANY_ADMIN, "End an NMR policy version");
        VBox header = new VBox(6, title, help, new FlowPane(8, 6, add, save, end, refresh), status, retained);
        ScrollPane headerScroll = scroll(header, "nmrPolicyHeaderScroll", true);

        table.setId("nmrPolicyTable");
        table.setMinSize(0, 0);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No NMR policy versions saved for this company."));
        column("code", "Policy", p -> p.terms().policyCode(), 150);
        column("category", "Category", p -> p.terms().category(), 150);
        dateColumn("from", "Effective from", p -> p.terms().validFrom());
        dateColumn("through", "Effective through", p -> p.terms().validTo());
        TableColumn<NmrPolicyView, BigDecimal> rateColumn = column("rate", "Rate", p -> p.terms().rate(), 120);
        rateColumn.setCellFactory(ignored -> new TableCell<>()
        {
            @Override
            protected void updateItem(BigDecimal value, boolean empty)
            {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : format.formatMoney(value));
                setTooltip(empty || value == null ? null : new Tooltip("Exact source rate: " + value.toPlainString()));
            }
        });
        column("currency", "ISO currency", p -> p.terms().currency(), 110);
        column("recipient", "Recipient / legal identity", p -> p.terms().recipient(), 260);
        column("source", "Source reference", p -> p.terms().sourceReference(), 280);
        column("actor", "Saved by", NmrPolicyView::savedBy, 140);
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) ->
        {
            if (loading || selected == null)
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
            show(selected);
        });

        form.setHgap(10);
        form.setVgap(8);
        form.setPadding(new Insets(8));
        int row = 0;
        row = field(row, "Policy code", "Code", false);
        row = field(row, "Rate category", "Category", false);
        from.setId("nmrPolicyFrom");
        through.setId("nmrPolicyThrough");
        sourceDate.setId("nmrPolicySourceDate");
        form.addRow(row++, new Label("Effective from (inclusive)"), from);
        form.addRow(row++, new Label("Effective through (optional)"), through);
        rate.setId("nmrPolicyRate");
        rate.setPrefColumnCount(26);
        rate.setTooltip(new Tooltip("Enter an explicit source rate, up to four decimal places. Blank never means zero. Focus the field to inspect exact precision."));
        form.addRow(row++, new Label("Rate"), rate);
        row = field(row, "ISO currency", "Currency", false);
        row = field(row, "Rate date basis", "DateBasis", false);
        row = field(row, "Rounding rule", "RoundingRule", false);
        row = field(row, "Eligibility rule", "Eligibility", true);
        row = field(row, "Exemption rule", "Exemptions", true);
        row = field(row, "Recipient / legal identity", "Recipient", false);
        row = field(row, "Legal reporting classification", "Reporting", true);
        row = field(row, "Merchant / payment path", "MerchantPath", true);
        row = field(row, "Source reference", "SourceReference", false);
        row = field(row, "Authority", "Authority", false);
        form.addRow(row, new Label("Source date"), sourceDate);
        for (DatePicker picker : List.of(from, through, sourceDate))
        {
            format.install(picker);
            picker.valueProperty().addListener((obs, old, value) -> changed());
            picker.getEditor().textProperty().addListener((obs, old, value) -> changed());
            UiPermissionGate.gate(picker, ApplicationPermission.COMPANY_ADMIN, "Enter NMR policy dates");
        }
        rate.textProperty().addListener((obs, old, value) ->
        {
            if (!loading)
            {
                exactRate = value == null || value.isBlank() ? null : format.parseMoney(value);
                changed();
            }
        });
        rate.focusedProperty().addListener((obs, old, focused) ->
        {
            if (exactRate != null)
            {
                loading = true;
                rate.setText(focused ? exactRate.toPlainString() : format.formatMoney(exactRate));
                loading = false;
            }
        });
        UiPermissionGate.gate(rate, ApplicationPermission.COMPANY_ADMIN, "Enter an NMR policy rate");
        form.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        VBox editor = new VBox(6, mode, form);
        editor.setPadding(new Insets(6));
        editor.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        ScrollPane editorScroll = scroll(editor, "nmrPolicyEditorScroll", false);
        VBox history = new VBox(6, new Label("Retained versions — company configuration only"), table);
        history.setMinSize(0, 0);
        VBox.setVgrow(table, Priority.ALWAYS);
        SplitPane details = new SplitPane(history, editorScroll);
        details.setId("nmrPolicyDetailSplit");
        details.setOrientation(Orientation.VERTICAL);
        details.setMinSize(0, 0);
        CompanySplitPaneStateBinder.bind(details, "nmr-policy-detail", .45);
        SplitPane main = new SplitPane(headerScroll, details);
        main.setId("nmrPolicyMainSplit");
        main.setOrientation(Orientation.VERTICAL);
        main.setMinSize(0, 0);
        CompanySplitPaneStateBinder.bind(main, "nmr-policy-main", .25);
        root.getChildren().setAll(main);
        VBox.setVgrow(main, Priority.ALWAYS);
        CompanyTableStateBinder.applyProductionPanel(root, AppPanelId.SETTINGS);
        FullTextTooltipInstaller.install(root);
    }

    private int field(int row, String label, String key, boolean multiline)
    {
        TextInputControl control = multiline ? new TextArea() : new TextField();
        control.setId("nmrPolicy" + key);
        if (control instanceof TextArea area)
        {
            area.setPrefRowCount(2);
            area.setWrapText(true);
            area.setPrefColumnCount(26);
        }
        else
        {
            ((TextField) control).setPrefColumnCount(26);
        }
        control.textProperty().addListener((obs, old, value) -> changed());
        UiPermissionGate.gate(control, ApplicationPermission.COMPANY_ADMIN, "Enter NMR policy configuration");
        text.put(key, control);
        form.addRow(row, new Label(label), control);
        GridPane.setHgrow(control, Priority.ALWAYS);
        return row + 1;
    }

    private <T> TableColumn<NmrPolicyView, T> column(String id, String label, Function<NmrPolicyView, T> value, double width)
    {
        TableColumn<NmrPolicyView, T> column = new TableColumn<>(label);
        column.setId("nmrPolicyColumn" + id);
        column.setUserData(id);
        column.setPrefWidth(width);
        column.setCellValueFactory(row -> new SimpleObjectProperty<>(value.apply(row.getValue())));
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        table.getColumns().add(column);
        return column;
    }

    private void dateColumn(String id, String label, Function<NmrPolicyView, LocalDate> value)
    {
        TableColumn<NmrPolicyView, LocalDate> column = column(id, label, value, 150);
        column.setCellFactory(ignored -> new TableCell<>()
        {
            @Override
            protected void updateItem(LocalDate date, boolean empty)
            {
                super.updateItem(date, empty);
                setText(empty || date == null ? null : format.formatDate(date));
            }
        });
    }

    private static ScrollPane scroll(Region content, String id, boolean fit)
    {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setId(id);
        scroll.setFitToWidth(fit);
        scroll.setMinSize(0, 0);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return scroll;
    }

    private String value(String key)
    {
        return text.get(key).getText();
    }

    private void changed()
    {
        if (!loading && newMode)
        {
            dirty = true;
        }
    }

    private boolean discard()
    {
        if (!dirty || confirmDiscard.getAsBoolean())
        {
            return true;
        }
        status.setText("Cancelled; unsaved policy configuration remains.");
        return false;
    }

    private void show(NmrPolicyView policy)
    {
        loading = true;
        newMode = policy == null;
        request = newMode ? UUID.randomUUID() : policy.portableId();
        text.values().forEach(TextInputControl::clear);
        from.setValue(null);
        through.setValue(null);
        sourceDate.setValue(null);
        rate.clear();
        exactRate = null;
        if (policy != null)
        {
            NmrPolicyTerms t = policy.terms();
            String[] keys = {"Code", "Category", "Currency", "DateBasis", "RoundingRule", "Eligibility", "Exemptions", "Recipient", "Reporting", "MerchantPath", "SourceReference", "Authority"};
            String[] values = {t.policyCode(), t.category(), t.currency(), t.dateBasis(), t.roundingRule(), t.eligibility(), t.exemptions(), t.recipient(), t.reporting(), t.merchantPath(), t.sourceReference(), t.authority()};
            for (int i = 0; i < keys.length; i++)
            {
                text.get(keys[i]).setText(values[i]);
            }
            from.setValue(t.validFrom());
            through.setValue(t.validTo());
            sourceDate.setValue(t.sourceDate());
            exactRate = t.rate();
            rate.setText(exactRate.toPlainString());
        }
        draftContext = context();
        dirty = false;
        loading = false;
        mode.setText(newMode ? "New policy version — all values must be supplied" : "Saved version — terms are retained and read-only");
        updateAvailability();
    }

    private void updateAvailability()
    {
        form.setDisable(!newMode);
        save.setDisable(!newMode);
        end.setDisable(newMode || table.getSelectionModel().getSelectedItem() == null);
        capabilitiesChanged.run();
    }

    private boolean reload(Long select)
    {
        try
        {
            format = CompanyUiFormat.activeCompany();
            var rows = UiServiceRegistry.nmrPolicies().list();
            loading = true;
            table.getItems().setAll(rows);
            table.sort();
            loading = false;
            NmrPolicyView selected = rows.stream().filter(row -> row.id().equals(select)).findFirst().orElse(null);
            show(selected);
            if (selected == null)
            {
                newMode = false;
                updateAvailability();
                mode.setText("Retained policy history — choose New Version");
            }
            else
            {
                loading = true;
                table.getSelectionModel().select(selected);
                loading = false;
                updateAvailability();
            }
            return true;
        }
        catch (RuntimeException ex)
        {
            loading = true;
            table.getItems().clear();
            loading = false;
            show(null);
            newMode = false;
            updateAvailability();
            status.setText("Could not load policy configuration: " + UiErrors.safeMessage(ex));
            return false;
        }
    }

    private void endSelected()
    {
        NmrPolicyView row = table.getSelectionModel().getSelectedItem();
        if (row == null)
        {
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("End NMR Policy Version");
        DatePicker date = new DatePicker();
        format.install(date);
        date.setId("nmrPolicyEndDate");
        TextField reason = new TextField();
        reason.setPromptText("Required reason");
        reason.setId("nmrPolicyEndReason");
        Label error = new Label();
        error.setId("nmrPolicyEndError");
        error.setWrapText(true);
        dialog.getDialogPane().setContent(new VBox(8, new Label("Inclusive effective through date"), date, reason, error));
        ButtonType saveEnd = new ButtonType("Save End Date", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(saveEnd, ButtonType.CANCEL);
        FullTextTooltipInstaller.install(dialog.getDialogPane());
        if (root.getScene() != null)
        {
            dialog.initOwner(root.getScene().getWindow());
        }
        dialog.getDialogPane().lookupButton(saveEnd).addEventFilter(javafx.event.ActionEvent.ACTION, event ->
        {
            try
            {
                commitDate(date, "End date");
                UiServiceRegistry.nmrPolicies().endVersion(row.id(), date.getValue(), reason.getText());
            }
            catch (RuntimeException ex)
            {
                error.setText("Could not end policy version: " + UiErrors.safeMessage(ex));
                event.consume();
            }
        });
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == saveEnd)
        {
            if (reload(row.id()))
            {
                status.setText("Policy interval ended; saved terms and audit are retained. No accounting entry was created.");
            }
            else
            {
                status.setText("Policy interval ended. " + status.getText());
            }
        }
    }

    private void commitDate(DatePicker picker, String label)
    {
        String value = picker.getEditor().getText();
        if (value == null || value.isBlank())
        {
            picker.setValue(null);
            return;
        }
        LocalDate date = format.parseDate(value);
        if (date == null)
        {
            throw new IllegalArgumentException(label + " is invalid.");
        }
        picker.setValue(date);
    }

    private static String context()
    {
        var state = MainWindow.sharedSessionState();
        return state.databaseSelection().activeDatabasePath() + "/" + state.multiCompany().activeCompanyCode();
    }

    @Override
    public Node root()
    {
        return root;
    }

    @Override
    public String title()
    {
        return "NMR Policies";
    }

    @Override
    public boolean hasUnsavedChanges()
    {
        return dirty;
    }

    @Override
    public Set<AppCommand> commandCapabilities()
    {
        return newMode ? AppPanel.capabilities(AppCommand.NEW_ACTIVE, AppCommand.SAVE_ACTIVE) : AppPanel.capabilities(AppCommand.NEW_ACTIVE);
    }

    @Override
    public java.util.Optional<ApplicationPermission> requiredPermission(AppCommand command)
    {
        return java.util.Optional.of(ApplicationPermission.COMPANY_ADMIN);
    }

    @Override
    public String commandLabel(AppCommand command)
    {
        return command == AppCommand.SAVE_ACTIVE ? "Save Policy Version" : AppPanel.super.commandLabel(command);
    }

    @Override
    public String saveUnavailableReason()
    {
        return "Choose New Version to save configuration. Saved version terms are retained and read-only.";
    }

    @Override
    public void setCommandCapabilitiesChangedListener(Runnable listener)
    {
        capabilitiesChanged = Objects.requireNonNull(listener);
        listener.run();
    }

    @Override
    public String commandResultMessage(AppCommand command)
    {
        return status.getText();
    }

    @Override
    public void onNew()
    {
        if (!discard())
        {
            return;
        }
        loading = true;
        table.getSelectionModel().clearSelection();
        loading = false;
        show(null);
        status.setText("Enter source-backed policy values, then Save Policy Version.");
    }

    @Override
    public void onSave()
    {
        try
        {
            if (!newMode)
            {
                throw new IllegalStateException(saveUnavailableReason());
            }
            if (!Objects.equals(draftContext, context()))
            {
                throw new IllegalStateException("Company/database changed. Start a new policy version in the current context.");
            }
            commitDate(from, "Effective from");
            commitDate(through, "Effective through");
            commitDate(sourceDate, "Source date");
            var terms = new NmrPolicyTerms(value("Code"), value("Category"), from.getValue(), through.getValue(), exactRate,
                    value("Currency"), value("DateBasis"), value("RoundingRule"), value("Eligibility"), value("Exemptions"),
                    value("Recipient"), value("Reporting"), value("MerchantPath"), value("SourceReference"), value("Authority"), sourceDate.getValue());
            NmrPolicyView saved = UiServiceRegistry.nmrPolicies().save(request, terms);
            if (reload(saved.id()))
            {
                status.setText("Policy version saved. No assessment, remittance or accounting entry was created.");
            }
            else
            {
                status.setText("Policy version saved. " + status.getText());
            }
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save policy version: " + UiErrors.safeMessage(ex));
        }
    }

    @Override
    public void onPanelShown()
    {
        if (!dirty)
        {
            reload(table.getSelectionModel().getSelectedItem() == null ? null : table.getSelectionModel().getSelectedItem().id());
        }
    }
}
