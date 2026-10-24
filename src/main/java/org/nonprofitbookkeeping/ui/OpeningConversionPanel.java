package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.ApplicationPermission;
import org.nonprofitbookkeeping.service.OpeningConversionService;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Reviews imported/opening facts and retains one immutable acceptance without creating ledger entries. */
public final class OpeningConversionPanel implements AppPanel
{
    private final VBox root = new VBox(8);
    private final DatePicker asOf = new DatePicker(LocalDate.now());
    private final Button previewButton = new Button("Preview Conversion");
    private final Button saveButton = new Button("Save Conversion Acceptance");
    private final TextArea note = new TextArea();
    private final Label status = new Label("Preview the existing opening and carryover facts before acceptance.");
    private final TableView<OpeningConversionService.Check> checks = new TableView<>();
    private final TableView<OpeningConversionService.AccountOpening> accounts = new TableView<>();
    private final TableView<OpeningConversionService.BankOpening> banks = new TableView<>();
    private final TableView<OpeningConversionService.BankOutstandingItem> outstanding = new TableView<>();
    private final TableView<OpeningConversionService.OpenItemSummary> openItems = new TableView<>();
    private final TableView<OpeningConversionService.OpenItemGap> gaps = new TableView<>();
    private final Supplier<OpeningConversionService> services;
    private final BooleanSupplier confirmAcceptance;
    private OpeningConversionService.Preview preview;
    private UUID requestId = UUID.randomUUID();
    private Runnable capabilitiesChanged = () -> { };

    public OpeningConversionPanel()
    {
        this(UiServiceRegistry::openingConversion, () -> new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "Save this reviewed opening conversion as the company's one immutable acceptance? No accounting entry will be created.",
                javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL).showAndWait()
                .orElse(javafx.scene.control.ButtonType.CANCEL) == javafx.scene.control.ButtonType.OK);
    }

    OpeningConversionPanel(
            Supplier<OpeningConversionService> services,
            BooleanSupplier confirmAcceptance)
    {
        this.services = Objects.requireNonNull(services, "services");
        this.confirmAcceptance = Objects.requireNonNull(confirmAcceptance, "confirmAcceptance");
        build();
    }

    private void build()
    {
        root.setPadding(new Insets(8));
        root.setMinSize(0, 0);
        Label title = new Label("Opening Conversion Review");
        title.getStyleClass().add("panel-title");
        Label explanation = new Label("Review existing account openings, configured bank baselines, outstanding bank items, fixed assets, inventory, and supplemental schedules. Acceptance records an audit fact only; it does not create or balance accounting entries.");
        explanation.setWrapText(true);

        asOf.setId("openingConversionAsOf");
        previewButton.setId("openingConversionPreview");
        saveButton.setId("openingConversionSave");
        note.setId("openingConversionNote");
        note.setPromptText("Optional acceptance note (maximum 1,000 characters)");
        note.setPrefRowCount(2);
        status.setId("openingConversionStatus");
        status.setWrapText(true);
        previewButton.setOnAction(event -> loadPreview());
        saveButton.setOnAction(event -> onSave());
        asOf.valueProperty().addListener((observable, oldValue, newValue) -> invalidatePreview());
        note.textProperty().addListener((observable, oldValue, newValue) -> capabilitiesChanged.run());
        UiPermissionGate.gate(saveButton, ApplicationPermission.BOOKKEEPING_WRITE,
                "Saving conversion acceptance");

        configureChecks();
        configureAccounts();
        configureBanks();
        configureOutstanding();
        configureOpenItems();
        configureGaps();

        TabPane details = new TabPane(
                tab("Account Openings", accounts),
                tab("Bank Openings", banks),
                tab("Outstanding Bank Items", outstanding),
                tab("Supplemental Schedules", openItems),
                tab("Open-item Gaps", gaps));
        details.setId("openingConversionDetailsTabs");
        SplitPane workspace = new SplitPane(checks, details);
        workspace.setId("openingConversionWorkspaceSplit");
        workspace.setOrientation(Orientation.VERTICAL);
        workspace.setDividerPositions(0.42);
        VBox.setVgrow(workspace, Priority.ALWAYS);

        FlowPane actions = new FlowPane(8, 6, new Label("As of"), asOf, previewButton, saveButton);
        ScrollPane header = new ScrollPane(new VBox(6, title, explanation, actions, note, status));
        header.setFitToWidth(true);
        header.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        header.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        header.setId("openingConversionHeaderScroll");
        root.getChildren().addAll(header, workspace);
        updateAvailability();
    }

    private void configureChecks()
    {
        checks.setId("openingConversionChecks");
        add(checks, "Area", OpeningConversionService.Check::area, 180);
        add(checks, "Status", row -> row.status().name(), 90);
        add(checks, "Difference", row -> row.difference().toPlainString(), 120);
        add(checks, "Detail / repair", OpeningConversionService.Check::detail, 520);
    }

    private void configureAccounts()
    {
        accounts.setId("openingConversionAccountOpenings");
        add(accounts, "Code", OpeningConversionService.AccountOpening::accountCode, 100);
        add(accounts, "Account", OpeningConversionService.AccountOpening::accountName, 220);
        add(accounts, "Debit", row -> row.debit().toPlainString(), 120);
        add(accounts, "Credit", row -> row.credit().toPlainString(), 120);
    }

    private void configureBanks()
    {
        banks.setId("openingConversionBankOpenings");
        add(banks, "Bank", OpeningConversionService.BankOpening::bankAccountName, 180);
        add(banks, "Ledger account", OpeningConversionService.BankOpening::accountCode, 120);
        add(banks, "Opening date", row -> String.valueOf(row.openingDate()), 110);
        add(banks, "Ledger", row -> row.ledgerOpening().toPlainString(), 110);
        add(banks, "Configured", row -> row.configuredOpening().toPlainString(), 110);
        add(banks, "Repair", OpeningConversionService.BankOpening::repair, 360);
    }

    private void configureOutstanding()
    {
        outstanding.setId("openingConversionOutstandingBankItems");
        add(outstanding, "Date", row -> row.date().toString(), 110);
        add(outstanding, "Account", OpeningConversionService.BankOutstandingItem::accountCode, 110);
        add(outstanding, "Amount", row -> row.amount().toPlainString(), 120);
        add(outstanding, "Reference", OpeningConversionService.BankOutstandingItem::reference, 300);
        add(outstanding, "Transaction", row -> Long.toString(row.transactionId()), 110);
    }

    private void configureOpenItems()
    {
        openItems.setId("openingConversionOpenItems");
        add(openItems, "Schedule", row -> row.kind().displayName(), 190);
        add(openItems, "Rows", row -> Integer.toString(row.rowCount()), 80);
        add(openItems, "Open", row -> Long.toString(row.openCount()), 80);
        add(openItems, "Open amount", row -> row.openAmount().toPlainString(), 120);
        add(openItems, "Gaps", row -> Integer.toString(row.gapCount()), 80);
        add(openItems, "Difference", row -> row.difference().toPlainString(), 120);
    }

    private void configureGaps()
    {
        gaps.setId("openingConversionOpenItemGaps");
        add(gaps, "Schedule", row -> row.kind().displayName(), 180);
        add(gaps, "Date", row -> row.date().toString(), 110);
        add(gaps, "Account", OpeningConversionService.OpenItemGap::accountCode, 110);
        add(gaps, "Unmatched", row -> row.unmatched().toPlainString(), 120);
        add(gaps, "Repair", OpeningConversionService.OpenItemGap::repair, 360);
    }

    private static <T> void add(TableView<T> table, String title,
            java.util.function.Function<T, String> value, double width)
    {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleObjectProperty<>(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        table.getColumns().add(column);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    }

    private static Tab tab(String title, Node content)
    {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private void loadPreview()
    {
        try
        {
            if (asOf.getValue() == null)
            {
                throw new IllegalArgumentException("An as-of date is required.");
            }
            preview = services.get().preview(asOf.getValue());
            checks.getItems().setAll(preview.checks());
            accounts.getItems().setAll(preview.accountOpenings());
            banks.getItems().setAll(preview.bankOpenings());
            outstanding.getItems().setAll(preview.outstandingBankItems());
            openItems.getItems().setAll(preview.openItems());
            gaps.getItems().setAll(preview.openItemGaps());
            if (preview.acceptance() != null)
            {
                status.setText("Accepted through " + preview.acceptance().asOfDate()
                        + " by " + preview.acceptance().actor() + ". The immutable audit fact is retained.");
            }
            else if (preview.ready())
            {
                status.setText("Ready for acceptance. Review every tab, then save the immutable audit fact.");
            }
            else
            {
                long blocked = preview.checks().stream().filter(OpeningConversionService.Check::blocking).count();
                status.setText(blocked + " blocking check(s) require repair before acceptance.");
            }
        }
        catch (RuntimeException ex)
        {
            preview = null;
            clearRows();
            status.setText("Could not preview conversion: " + UiErrors.safeMessage(ex));
        }
        updateAvailability();
    }

    private void invalidatePreview()
    {
        preview = null;
        requestId = UUID.randomUUID();
        clearRows();
        status.setText("As-of date changed. Preview again before acceptance.");
        updateAvailability();
    }

    private void clearRows()
    {
        checks.getItems().clear();
        accounts.getItems().clear();
        banks.getItems().clear();
        outstanding.getItems().clear();
        openItems.getItems().clear();
        gaps.getItems().clear();
    }

    private void updateAvailability()
    {
        saveButton.setDisable(preview == null || !preview.saveAvailable());
        capabilitiesChanged.run();
    }

    @Override public String title() { return "Opening Conversion"; }
    @Override public Node root() { return root; }

    @Override
    public Set<AppCommand> commandCapabilities()
    {
        return preview != null && preview.saveAvailable()
                && UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE)
                ? Set.of(AppCommand.SAVE_ACTIVE) : Set.of();
    }

    @Override
    public Optional<ApplicationPermission> requiredPermission(AppCommand command)
    {
        return command == AppCommand.SAVE_ACTIVE
                ? Optional.of(ApplicationPermission.BOOKKEEPING_WRITE) : Optional.empty();
    }

    @Override public String commandLabel(AppCommand command)
    {
        return command == AppCommand.SAVE_ACTIVE ? "Save Conversion Acceptance" : AppPanel.super.commandLabel(command);
    }

    @Override
    public String saveUnavailableReason()
    {
        if (!UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE))
        {
            return UiPermissionGate.deniedExplanation(
                    ApplicationPermission.BOOKKEEPING_WRITE, "Saving conversion acceptance");
        }
        if (preview == null)
        {
            return "Preview the current conversion facts before saving acceptance.";
        }
        if (preview.acceptance() != null)
        {
            return "This company already has an immutable opening conversion acceptance.";
        }
        return "Resolve every blocking conversion check, then preview again.";
    }

    @Override public boolean hasUnsavedChanges()
    {
        return preview != null && preview.saveAvailable();
    }

    @Override public void setCommandCapabilitiesChangedListener(Runnable listener)
    {
        capabilitiesChanged = listener == null ? () -> { } : listener;
        capabilitiesChanged.run();
    }

    @Override
    public void onSave()
    {
        if (!supportsCommand(AppCommand.SAVE_ACTIVE))
        {
            throw new IllegalStateException(saveUnavailableReason());
        }
        if (!confirmAcceptance.getAsBoolean())
        {
            status.setText("Conversion acceptance was cancelled.");
            return;
        }
        try
        {
            OpeningConversionService.Acceptance accepted = services.get().saveAcceptance(
                    requestId, preview.asOfDate(), preview.fingerprint(), note.getText());
            loadPreview();
            status.setText("Conversion acceptance saved by " + accepted.actor()
                    + ". No accounting entry was created.");
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not save conversion acceptance: " + UiErrors.safeMessage(ex));
            throw ex;
        }
    }

    @Override public String commandResultMessage(AppCommand command) { return status.getText(); }
}
