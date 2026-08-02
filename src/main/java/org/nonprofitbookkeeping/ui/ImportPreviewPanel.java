package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportEntityPreview;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportCommitService;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportMappingRequirement;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportPreview;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportPreviewService;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportResult;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportTransactionPreview;
import org.nonprofitbookkeeping.interchange.bank.BankCsvMappingProfileService;
import org.nonprofitbookkeeping.interchange.bank.BankCsvReviewPreview;
import org.nonprofitbookkeeping.interchange.bank.BankCsvReviewService;
import org.nonprofitbookkeeping.interchange.bank.BankStatementReviewPreview;
import org.nonprofitbookkeeping.interchange.bank.BankStatementReviewResult;
import org.nonprofitbookkeeping.interchange.bank.BankStatementReviewService;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.service.BankConfigurationService;
import org.nonprofitbookkeeping.service.BankImportNormalizationService;
import org.nonprofitbookkeeping.service.CoaCsvMapper;
import org.nonprofitbookkeeping.service.ImportPreviewService;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * ImportPreviewPanel component.
 */
public class ImportPreviewPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final ImportPreviewService previewService;
    private final SclxPreviewOperationFactory sclxPreviewFactory;
    private final Function<String, SclxImportCommitService> sclxCommitFactory;
    private final Supplier<String> activeCompanyCode;
    private final Supplier<BankConfigurationService> bankConfigurationService;
    private final Supplier<BankStatementReviewService> bankStatementReviewService;
    private final Supplier<BankCsvReviewService> bankCsvReviewService;
    private final Supplier<BankCsvMappingProfileService> bankCsvProfileService;
    private final Label status = new Label("Choose an SCLX, COA CSV, or OFX/QFX file to preview before import.");
    private final ListView<String> warnings = new ListView<>();
    private final TableView<CoaCsvMapper.CoaCsvRow> acceptedCoaRows = new TableView<>();
    private final TableView<ImportPreviewService.RejectedCoaRow> rejectedCoaRows = new TableView<>();
    private final ListView<String> sclxCounts = new ListView<>();
    private final TableView<SclxImportEntityPreview> sclxEntities = new TableView<>();
    private final TableView<SclxImportMappingRequirement> sclxMappings = new TableView<>();
    private final TableView<SclxImportTransactionPreview> sclxTransactions = new TableView<>();
    private final TabPane previewTabs = new TabPane();
    private final Button commitAccepted = new Button("Commit Accepted COA Rows");
    private final Button previewSclx = new Button("Preview SCLX…");
    private final Button commitSclx = new Button("Import Previewed SCLX…");
    private final Button previewBank = new Button("Preview Bank OFX/QFX…");
    private final Button previewBankCsv = new Button("Preview Mapped Bank CSV…");
    private final Button saveBankCsvProfile = new Button("Save CSV Profile…");
    private final Button commitBankReview = new Button("Commit Previewed Bank Review…");
    private final ComboBox<CompanyBankAccount> bankAccount = new ComboBox<>();
    private final ComboBox<BankCsvMappingProfileService.ProfileSummary> bankCsvProfile = new ComboBox<>();
    private final CheckBox confirmBankIdentity = new CheckBox("Confirm suffix-only account identity");
    private final TableView<BankImportNormalizationService.NormalizedBankStatementLine> bankRows = new TableView<>();
    private final TableView<org.nonprofitbookkeeping.interchange.bank.BankCsvParser.OriginalRow> bankCsvOriginalRows = new TableView<>();
    private final TextField sclxActor = new TextField("ui-operator");
    private ImportPreviewService.CoaPreviewResult lastCoaPreview;
    private Path lastSclxSource;
    private SclxImportPreview lastSclxPreview;
    private SclxImportCommitService lastSclxCommitService;
    private BankStatementReviewPreview lastBankReview;
    private BankCsvReviewPreview lastBankCsvReview;
    private BankStatementReviewService lastBankCommitService;
    private BankCsvReviewService lastBankCsvCommitService;

    public ImportPreviewPanel()
    {
        this(new ImportPreviewService(),
                (Supplier<SclxImportPreviewService>) UiServiceRegistry::sclxImportPreview,
                UiServiceRegistry::sclxImportCommit,
                () -> MainWindow.sharedSessionState().multiCompany().activeCompanyCode(),
                UiServiceRegistry::bankConfiguration,
                UiServiceRegistry::bankStatementReview,
                UiServiceRegistry::bankCsvReview,
                UiServiceRegistry::bankCsvMappingProfiles);
    }

    ImportPreviewPanel(
            ImportPreviewService previewService,
            Function<Path, SclxImportPreview> sclxPreview)
    {
        this(previewService,
                () -> Objects.requireNonNull(sclxPreview, "sclxPreview"),
                companyCode ->
                {
                    throw new IllegalStateException("SCLX commit service was not provided.");
                },
                () -> "DEFAULT",
                UiServiceRegistry::bankConfiguration,
                UiServiceRegistry::bankStatementReview,
                UiServiceRegistry::bankCsvReview,
                UiServiceRegistry::bankCsvMappingProfiles);
    }

    ImportPreviewPanel(
            ImportPreviewService previewService,
            Supplier<SclxImportPreviewService> sclxPreviewService,
            Function<String, SclxImportCommitService> sclxCommitFactory)
    {
        this(previewService,
                () -> Objects.requireNonNull(
                        sclxPreviewService.get(), "sclxPreviewService result")::preview,
                sclxCommitFactory,
                () -> MainWindow.sharedSessionState().multiCompany().activeCompanyCode(),
                UiServiceRegistry::bankConfiguration,
                UiServiceRegistry::bankStatementReview,
                UiServiceRegistry::bankCsvReview,
                UiServiceRegistry::bankCsvMappingProfiles);
    }

    ImportPreviewPanel(
            ImportPreviewService previewService,
            Supplier<SclxImportPreviewService> sclxPreviewService,
            Function<String, SclxImportCommitService> sclxCommitFactory,
            Supplier<String> activeCompanyCode,
            Supplier<BankConfigurationService> bankConfigurationService,
            Supplier<BankStatementReviewService> bankStatementReviewService,
            Supplier<BankCsvReviewService> bankCsvReviewService,
            Supplier<BankCsvMappingProfileService> bankCsvProfileService)
    {
        this(previewService,
                () -> Objects.requireNonNull(sclxPreviewService.get(), "sclxPreviewService result")::preview,
                sclxCommitFactory, activeCompanyCode, bankConfigurationService,
                bankStatementReviewService, bankCsvReviewService, bankCsvProfileService);
    }

    private ImportPreviewPanel(
            ImportPreviewService previewService,
            SclxPreviewOperationFactory sclxPreviewFactory,
            Function<String, SclxImportCommitService> sclxCommitFactory,
            Supplier<String> activeCompanyCode,
            Supplier<BankConfigurationService> bankConfigurationService,
            Supplier<BankStatementReviewService> bankStatementReviewService,
            Supplier<BankCsvReviewService> bankCsvReviewService,
            Supplier<BankCsvMappingProfileService> bankCsvProfileService)
    {
        this.previewService = Objects.requireNonNull(previewService, "previewService");
        this.sclxPreviewFactory = Objects.requireNonNull(sclxPreviewFactory, "sclxPreviewFactory");
        this.sclxCommitFactory = Objects.requireNonNull(sclxCommitFactory, "sclxCommitFactory");
        this.activeCompanyCode = Objects.requireNonNull(activeCompanyCode, "activeCompanyCode");
        this.bankConfigurationService = Objects.requireNonNull(bankConfigurationService, "bankConfigurationService");
        this.bankStatementReviewService = Objects.requireNonNull(bankStatementReviewService, "bankStatementReviewService");
        this.bankCsvReviewService = Objects.requireNonNull(bankCsvReviewService, "bankCsvReviewService");
        this.bankCsvProfileService = Objects.requireNonNull(bankCsvProfileService, "bankCsvProfileService");
        root.setPadding(new Insets(8));

        Label title = new Label("Import Preview");
        title.getStyleClass().add("panel-title");

        previewSclx.setOnAction(e -> chooseAndPreviewSclx());
        previewSclx.setId("previewSclxButton");
        commitSclx.setId("commitPreviewedSclxButton");
        commitSclx.setDisable(true);
        commitSclx.setOnAction(e -> confirmAndCommitSclx());
        sclxActor.setId("sclxImportActor");
        sclxActor.setPromptText("Audit actor");
        sclxActor.textProperty().addListener((observable, oldValue, newValue) -> updateSclxCommitAvailability());
        sclxActor.textProperty().addListener((observable, oldValue, newValue) -> updateBankCommitAvailability());

        Button previewCoa = new Button("Preview COA CSV…");
        previewCoa.setOnAction(e -> chooseAndPreviewCoa());

        previewBank.setOnAction(e -> chooseAndPreviewBank());
        previewBank.setId("previewBankStatementButton");
        previewBankCsv.setOnAction(e -> chooseAndPreviewBankCsv());
        previewBankCsv.setId("previewBankCsvButton");
        saveBankCsvProfile.setOnAction(e -> chooseAndSaveBankCsvProfile());
        saveBankCsvProfile.setId("saveBankCsvProfileButton");
        commitBankReview.setOnAction(e -> confirmAndCommitBankReview());
        commitBankReview.setId("commitPreviewedBankReviewButton");
        commitBankReview.setDisable(true);
        confirmBankIdentity.selectedProperty().addListener((observable, oldValue, newValue) -> updateBankCommitAvailability());
        configureBankSelectors();
        commitAccepted.setOnAction(e -> commitAcceptedCoaRows());
        commitAccepted.setId("commitAcceptedCoaRowsButton");
        commitAccepted.setDisable(true);

        status.setId("importPreviewStatus");
        status.setWrapText(true);
        root.setTop(new VBox(6, title,
                new HBox(8, previewSclx, previewCoa, previewBank, commitAccepted),
                new HBox(8, new Label("Configured bank account"), bankAccount,
                        new Label("CSV profile"), bankCsvProfile, saveBankCsvProfile, previewBankCsv),
                new HBox(8, new Label("Import actor"), sclxActor, commitSclx,
                        confirmBankIdentity, commitBankReview),
                status, new Separator()));

        buildAcceptedTable();
        buildRejectedTable();
        buildSclxEntityTable();
        buildSclxMappingTable();
        buildSclxTransactionTable();
        buildBankTables();

        warnings.setId("importPreviewMessages");
        warnings.setPlaceholder(new Label("No validation warnings."));
        sclxCounts.setId("sclxPreviewCounts");
        sclxCounts.setPlaceholder(new Label("Preview an SCLX file to see exact section counts."));

        SplitPane rowTables = new SplitPane(
                tableRegion("Accepted COA Rows", acceptedCoaRows),
                tableRegion("Rejected COA Rows", rejectedCoaRows));
        rowTables.setId("importPreviewRowsSplit");
        rowTables.setOrientation(Orientation.VERTICAL);
        rowTables.setDividerPositions(0.58);
        CompanySplitPaneStateBinder.bind(rowTables, "import-preview-rows", 0.58);

        previewTabs.setId("importPreviewResultTabs");
        previewTabs.getTabs().addAll(
                tab("COA Rows", rowTables),
                tab("SCLX Counts", sclxCounts),
                tab("SCLX Entities", tableRegion("Identity Dispositions", sclxEntities)),
                tab("SCLX Mappings", tableRegion("Account and Fund Mappings", sclxMappings)),
                tab("SCLX Transactions", tableRegion("Transaction Diagnostics", sclxTransactions)),
                tab("Bank Review Rows", tableRegion("Normalized Durable Review Preview", bankRows)),
                tab("CSV Original Rows", tableRegion("Original CSV Logical Rows", bankCsvOriginalRows)));

        VBox warningRegion = new VBox(6, new Label("Preview Warnings"), warnings);
        VBox.setVgrow(warnings, Priority.ALWAYS);
        SplitPane center = new SplitPane(warningRegion, previewTabs);
        center.setId("importPreviewWorkspaceSplit");
        center.setOrientation(Orientation.VERTICAL);
        center.setDividerPositions(0.28);
        CompanySplitPaneStateBinder.bind(center, "import-preview-workspace", 0.28);
        root.setCenter(center);
    }

    private static Tab tab(String title, Node content)
    {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private static VBox tableRegion(String title, TableView<?> table)
    {
        VBox region = new VBox(6, new Label(title), table);
        region.setMinHeight(0.0);
        VBox.setVgrow(table, Priority.ALWAYS);
        return region;
    }

    @Override
    public String title()
    {
        return "Import Preview";
    }

    @Override
    public Node root()
    {
        return root;
    }

    private void buildAcceptedTable()
    {
        TableColumn<CoaCsvMapper.CoaCsvRow, String> code = new TableColumn<>("Code");
        code.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().code()));
        TableColumn<CoaCsvMapper.CoaCsvRow, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().name()));
        TableColumn<CoaCsvMapper.CoaCsvRow, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().accountType()));
        TableColumn<CoaCsvMapper.CoaCsvRow, String> normal = new TableColumn<>("Normal");
        normal.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().normalBalance()));
        TableColumn<CoaCsvMapper.CoaCsvRow, String> parent = new TableColumn<>("Parent");
        parent.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().parentCode()));
        acceptedCoaRows.getColumns().addAll(code, name, type, normal, parent);
        acceptedCoaRows.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    }

    private void buildRejectedTable()
    {
        TableColumn<ImportPreviewService.RejectedCoaRow, String> line = new TableColumn<>("Line");
        line.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().lineNumber())));
        TableColumn<ImportPreviewService.RejectedCoaRow, String> raw = new TableColumn<>("Raw Row");
        raw.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().rawLine()));
        TableColumn<ImportPreviewService.RejectedCoaRow, String> reason = new TableColumn<>("Error Reason");
        reason.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().errorReason()));
        rejectedCoaRows.getColumns().addAll(line, raw, reason);
        rejectedCoaRows.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    }

    private void buildSclxEntityTable()
    {
        sclxEntities.setId("sclxPreviewEntities");
        sclxEntities.getColumns().addAll(
                stringColumn("Type", SclxImportEntityPreview::entityType),
                stringColumn("External ID", SclxImportEntityPreview::externalId),
                stringColumn("Disposition", value -> value.identityMatch().name()),
                stringColumn("Local ID", SclxImportEntityPreview::localEntityId),
                stringColumn("Source Path", SclxImportEntityPreview::path));
        sclxEntities.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    }

    private void buildSclxMappingTable()
    {
        sclxMappings.setId("sclxPreviewMappings");
        sclxMappings.getColumns().addAll(
                stringColumn("Kind", value -> value.kind().name()),
                stringColumn("Source", SclxImportMappingRequirement::sourceCode),
                stringColumn("Target", SclxImportMappingRequirement::targetCode),
                stringColumn("Resolution", value -> value.resolution().name()),
                stringColumn("Used", value -> value.used() ? "Yes" : "No"),
                stringColumn("Blocking", value -> value.blocking() ? "Yes" : "No"),
                stringColumn("Detail", SclxImportMappingRequirement::detail));
        sclxMappings.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    }

    private void buildSclxTransactionTable()
    {
        sclxTransactions.setId("sclxPreviewTransactions");
        sclxTransactions.getColumns().addAll(
                stringColumn("Transaction ID", SclxImportTransactionPreview::transactionId),
                stringColumn("Description", SclxImportTransactionPreview::description),
                stringColumn("Source Lines", value -> String.valueOf(value.sourceLineCount())),
                stringColumn("Posting Lines", value -> String.valueOf(value.postingLineCount())),
                stringColumn("Zero Lines", value -> String.valueOf(value.zeroValueLineCount())),
                stringColumn("Balanced", value -> value.balanced() ? "Yes" : "No"),
                stringColumn("Required Action", ImportPreviewPanel::transactionAction));
        sclxTransactions.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    }

    private void buildBankTables()
    {
        bankRows.setId("bankReviewPreviewRows");
        bankRows.getColumns().addAll(
                stringColumn("Source Row", value -> String.valueOf(value.sourceRowNumber())),
                stringColumn("Source ID", BankImportNormalizationService.NormalizedBankStatementLine::sourceTransactionId),
                stringColumn("Transaction Date", value -> Objects.toString(value.transactionDate(), "")),
                stringColumn("Posted Date", value -> Objects.toString(value.postedDate(), "")),
                stringColumn("Amount", value -> Objects.toString(value.amount(), "")),
                stringColumn("Currency", BankImportNormalizationService.NormalizedBankStatementLine::currency),
                stringColumn("Type", BankImportNormalizationService.NormalizedBankStatementLine::transactionType),
                stringColumn("Payee", BankImportNormalizationService.NormalizedBankStatementLine::name),
                stringColumn("Memo", BankImportNormalizationService.NormalizedBankStatementLine::memo),
                stringColumn("Duplicate", value -> value.exactDuplicate() ? "Exact"
                        : value.probableDuplicate() ? "Probable" : "No"));
        bankRows.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        bankRows.setPlaceholder(new Label("Preview an OFX/QFX or mapped CSV statement."));

        bankCsvOriginalRows.setId("bankCsvOriginalPreviewRows");
        bankCsvOriginalRows.getColumns().addAll(
                stringColumn("Source Row", value -> String.valueOf(value.sourceRowNumber())),
                stringColumn("Original Logical Row", org.nonprofitbookkeeping.interchange.bank.BankCsvParser.OriginalRow::originalText),
                stringColumn("Mapped Values", value -> value.mappedValues().toString()));
        bankCsvOriginalRows.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        bankCsvOriginalRows.setPlaceholder(new Label("Mapped CSV preview preserves original logical rows here."));
    }

    private void configureBankSelectors()
    {
        bankAccount.setId("bankReviewConfiguredAccount");
        bankAccount.setPromptText("Select configured account");
        bankAccount.setConverter(new javafx.util.StringConverter<>()
        {
            @Override public String toString(CompanyBankAccount value)
            {
                return value == null ? "" : value.getName();
            }
            @Override public CompanyBankAccount fromString(String value) { return null; }
        });
        bankAccount.setOnShowing(event -> reloadBankTargets());
        bankAccount.valueProperty().addListener((observable, oldValue, newValue) ->
        {
            clearBankPreview();
            reloadBankCsvProfiles();
        });

        bankCsvProfile.setId("bankCsvMappingProfile");
        bankCsvProfile.setPromptText("Select saved profile");
        bankCsvProfile.setConverter(new javafx.util.StringConverter<>()
        {
            @Override public String toString(BankCsvMappingProfileService.ProfileSummary value)
            {
                return value == null ? "" : value.profileName() + " v" + value.profileVersion();
            }
            @Override public BankCsvMappingProfileService.ProfileSummary fromString(String value) { return null; }
        });
        bankCsvProfile.setOnShowing(event -> reloadBankCsvProfiles());
    }

    private void reloadBankTargets()
    {
        Long selectedId = bankAccount.getValue() == null ? null : bankAccount.getValue().getId();
        try
        {
            var rows = bankConfigurationService.get().listBankAccounts(activeCompanyCode.get()).stream()
                    .filter(CompanyBankAccount::isActive)
                    .toList();
            bankAccount.getItems().setAll(rows);
            if (selectedId != null)
            {
                rows.stream().filter(value -> selectedId.equals(value.getId())).findFirst()
                        .ifPresent(bankAccount::setValue);
            }
            if (bankAccount.getValue() == null && rows.size() == 1) bankAccount.setValue(rows.get(0));
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not load configured bank accounts: " + UiErrors.safeMessage(ex));
        }
    }

    private void reloadBankCsvProfiles()
    {
        CompanyBankAccount account = bankAccount.getValue();
        bankCsvProfile.getItems().clear();
        bankCsvProfile.setValue(null);
        if (account == null) return;
        try
        {
            bankCsvProfile.getItems().setAll(bankCsvProfileService.get().list(activeCompanyCode.get()).stream()
                    .filter(value -> value.active() && value.bankAccountId() == account.getId())
                    .toList());
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not load bank CSV profiles: " + UiErrors.safeMessage(ex));
        }
    }

    private static <T> TableColumn<T, String> stringColumn(String title, Function<T, String> value)
    {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(
                Objects.toString(value.apply(cell.getValue()), "")));
        return column;
    }

    private static String transactionAction(SclxImportTransactionPreview transaction)
    {
        ArrayList<String> actions = new ArrayList<>();
        if (transaction.requiresBalancingAccount())
        {
            actions.add("Select balancing account");
        }
        if (transaction.closedPeriodConflict())
        {
            actions.add("Closed period");
        }
        if (transaction.finalizedReconciliationConflict())
        {
            actions.add("Finalized reconciliation");
        }
        return actions.isEmpty() ? "None" : String.join("; ", actions);
    }

    private void commitAcceptedCoaRows()
    {
        ImportPreviewService.CoaPreviewResult preview = lastCoaPreview;
        if (preview == null)
        {
            status.setText("Commit unavailable: preview a COA CSV first.");
            return;
        }
        if (preview.acceptedRows().isEmpty())
        {
            status.setText("Commit skipped: there are no accepted COA rows to commit.");
            return;
        }

        status.setText("Committing accepted COA rows...");
        UiAsync.run("import-preview-commit-coa", () -> previewService.commitAcceptedCoaRows(
                preview.acceptedRows(),
                row -> UiServiceRegistry.accountAdmin().upsert(
                        row.code(),
                        row.name(),
                        parseAccountTypeToken(row.accountType()),
                        parseNormalBalanceToken(row.normalBalance()),
                        null,
                        row.parentCode(),
                        true)),
                result -> {
                    status.setText("Committed " + result.committedCount() + " of " + result.totalAccepted()
                            + " accepted COA row(s); failed=" + result.failedCount() + ".");
                    warnings.getItems().setAll(result.errors());
                },
                ex -> status.setText("Could not commit accepted COA rows: " + UiErrors.safeMessage(ex)));
    }


    static AccountType parseAccountTypeToken(String token)
    {
        String normalized = normalizeEnumToken(token);
        if ("REVENUE".equals(normalized))
        {
            return AccountType.INCOME;
        }
        return AccountType.valueOf(normalized);
    }

    static NormalBalance parseNormalBalanceToken(String token)
    {
        String normalized = normalizeEnumToken(token);
        if ("DR".equals(normalized))
        {
            return NormalBalance.DEBIT;
        }
        if ("CR".equals(normalized))
        {
            return NormalBalance.CREDIT;
        }
        return NormalBalance.valueOf(normalized);
    }

    static String normalizeEnumToken(String token)
    {
        if (token == null || token.isBlank())
        {
            throw new IllegalArgumentException("Enum token is required.");
        }
        return token.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_')
                .replace('/', '_');
    }

    private void chooseAndPreviewCoa()
    {
        chooseOpenFile("Preview COA CSV", new FileChooser.ExtensionFilter("CSV Files", "*.csv"))
                .ifPresent(this::previewCoa);
    }

    private void chooseAndPreviewSclx()
    {
        chooseOpenFile("Preview SCLX Active Company File",
                new FileChooser.ExtensionFilter("SCLX Active Company Files", "*.sclx", "*.json"))
                .ifPresent(this::previewSclx);
    }

    private void chooseAndPreviewBank()
    {
        reloadBankTargets();
        if (bankAccount.getValue() == null)
        {
            status.setText("Select an active configured bank account before previewing OFX/QFX.");
            return;
        }
        chooseOpenFile("Preview Bank OFX/QFX", new FileChooser.ExtensionFilter("Bank Statement Files", "*.ofx", "*.qfx"))
                .ifPresent(this::previewBank);
    }

    private void chooseAndPreviewBankCsv()
    {
        reloadBankTargets();
        CompanyBankAccount account = bankAccount.getValue();
        BankCsvMappingProfileService.ProfileSummary profile = bankCsvProfile.getValue();
        if (account == null || profile == null)
        {
            status.setText("Select an active configured bank account and saved CSV profile before previewing CSV.");
            return;
        }
        chooseOpenFile("Preview Mapped Bank CSV", new FileChooser.ExtensionFilter("Bank CSV Files", "*.csv"))
                .ifPresent(this::previewBankCsv);
    }

    private void chooseAndSaveBankCsvProfile()
    {
        reloadBankTargets();
        CompanyBankAccount account = bankAccount.getValue();
        if (account == null)
        {
            status.setText("Select an active configured bank account before saving a CSV profile.");
            return;
        }
        chooseOpenFile("Save Bank CSV Mapping Profile",
                new FileChooser.ExtensionFilter("Bank CSV Mapping Profile", "*.json"))
                .ifPresent(path -> saveBankCsvProfile(path, account));
    }

    private void saveBankCsvProfile(Path profileFile, CompanyBankAccount account)
    {
        String company = activeCompanyCode.get();
        saveBankCsvProfile.setDisable(true);
        status.setText("Validating and saving the selected bank CSV mapping profile...");
        UiAsync.run("import-preview-save-bank-csv-profile",
                () -> bankCsvProfileService.get().create(
                        company, account.getId(), readProfile(profileFile)),
                result ->
                {
                    saveBankCsvProfile.setDisable(false);
                    reloadBankCsvProfiles();
                    bankCsvProfile.getItems().stream()
                            .filter(value -> value.id() == result.id())
                            .findFirst().ifPresent(bankCsvProfile::setValue);
                    status.setText("Saved CSV profile " + result.profileName() + " v"
                            + result.profileVersion() + " for " + account.getName() + ".");
                },
                ex ->
                {
                    saveBankCsvProfile.setDisable(false);
                    status.setText("Could not save bank CSV profile: " + UiErrors.safeMessage(ex));
                });
    }

    private void previewCoa(Path file)
    {
        clearBankPreview();
        UiAsync.run("import-preview-coa", () -> previewService.previewCoaCsv(file), result -> {
            clearSclxPreview();
            clearBankPreview();
            lastCoaPreview = result;
            acceptedCoaRows.getItems().setAll(result.acceptedRows());
            rejectedCoaRows.getItems().setAll(result.rejectedRows());
            warnings.getItems().setAll(result.warnings());
            commitAccepted.setDisable(result.acceptedRows().isEmpty());
            previewTabs.getSelectionModel().select(0);
            status.setText("Previewed " + result.totalRowCount()
                    + " COA row(s) from " + result.sourceName()
                    + ": accepted " + result.acceptedCount() + ", rejected " + result.rejectedCount() + ".");
        }, ex -> {
            clearBankPreview();
            warnings.getItems().clear();
            acceptedCoaRows.getItems().clear();
            rejectedCoaRows.getItems().clear();
            commitAccepted.setDisable(true);
            status.setText("Could not preview COA CSV: " + UiErrors.safeMessage(ex));
        });
    }

    private void previewSclx(Path file)
    {
        clearBankPreview();
        Function<Path, SclxImportPreview> fixedScopePreview;
        try
        {
            fixedScopePreview = Objects.requireNonNull(
                    sclxPreviewFactory.capture(), "captured SCLX preview operation");
        }
        catch (RuntimeException ex)
        {
            clearCoaPreview();
            clearSclxPreview();
            warnings.getItems().clear();
            status.setText("Could not prepare SCLX preview: " + UiErrors.safeMessage(ex));
            return;
        }
        previewSclx.setDisable(true);
        commitAccepted.setDisable(true);
        status.setText("Previewing SCLX without changing the active company...");
        UiAsync.run("import-preview-sclx", () -> fixedScopePreview.apply(file), result -> {
            previewSclx.setDisable(false);
            applySclxPreview(file, result);
        }, ex -> {
            previewSclx.setDisable(false);
            clearCoaPreview();
            clearSclxPreview();
            warnings.getItems().clear();
            status.setText("Could not preview SCLX: " + UiErrors.safeMessage(ex));
        });
    }

    void applySclxPreview(SclxImportPreview result)
    {
        applySclxPreview(null, result);
    }

    void applySclxPreview(Path source, SclxImportPreview result)
    {
        Objects.requireNonNull(result, "result");
        clearCoaPreview();
        clearBankPreview();
        lastSclxSource = source == null ? null : source.toAbsolutePath().normalize();
        lastSclxPreview = result;
        lastSclxCommitService = null;
        if (lastSclxSource != null && sclxCommitReady(result))
        {
            try
            {
                lastSclxCommitService = Objects.requireNonNull(
                        sclxCommitFactory.apply(result.targetCompanyCode()), "SCLX commit service");
            }
            catch (RuntimeException ex)
            {
                status.setText("Could not prepare SCLX import: " + UiErrors.safeMessage(ex));
            }
        }
        sclxEntities.getItems().setAll(result.operation().items());
        sclxMappings.getItems().setAll(result.mappings());
        sclxTransactions.getItems().setAll(result.transactions());
        warnings.getItems().setAll(result.operation().messages().stream()
                .map(ImportPreviewPanel::displayMessage)
                .toList());
        sclxCounts.getItems().setAll(result.sectionCounts().entitiesByType().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .toList());
        sclxCounts.getItems().addAll(
                "Total entities: " + result.sectionCounts().totalEntities(),
                "References: " + result.sectionCounts().referenceCount(),
                "Relationships: " + result.sectionCounts().relationshipCount(),
                "Unsupported sections: " + result.sectionCounts().unsupportedSectionCount());
        previewTabs.getSelectionModel().select(1);

        updateSclxCommitAvailability();
        String outcome = sclxCommitReady(result) ? "READY TO IMPORT" : "BLOCKED";
        status.setText("Previewed SCLX " + result.version().externalValue()
                + " from " + result.operation().sourceName()
                + " for target " + result.targetCompanyCode()
                + ": " + result.sectionCounts().totalEntities() + " entities, "
                + result.operation().counts().created() + " new, "
                + result.operation().counts().identical() + " identical, "
                + result.operation().counts().errors() + " blocking error(s); account mode "
                + result.recommendedAccountMode() + "; " + outcome + ". No data was changed.");
    }

    private static boolean sclxCommitReady(SclxImportPreview preview)
    {
        return !preview.hasBlockingErrors()
                && (!preview.targetPopulated() || identicalReimport(preview))
                && preview.recommendedAccountMode() == org.nonprofitbookkeeping.interchange.sclx.SclxAccountMode.AS_IS;
    }

    private static boolean identicalReimport(SclxImportPreview preview)
    {
        return !preview.operation().items().isEmpty()
                && preview.operation().items().stream()
                .allMatch(item -> item.identityMatch()
                        == org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch.IDENTICAL);
    }

    private void updateSclxCommitAvailability()
    {
        boolean ready = lastSclxSource != null
                && lastSclxPreview != null
                && lastSclxCommitService != null
                && sclxCommitReady(lastSclxPreview)
                && !sclxActor.getText().isBlank();
        commitSclx.setDisable(!ready);
    }

    private void confirmAndCommitSclx()
    {
        Path source = lastSclxSource;
        SclxImportPreview preview = lastSclxPreview;
        SclxImportCommitService commitService = lastSclxCommitService;
        String actor = sclxActor.getText().strip();
        if (source == null || preview == null || commitService == null || actor.isBlank()
                || !sclxCommitReady(preview))
        {
            status.setText("Import unavailable: preview a valid SCLX file for an empty target first.");
            updateSclxCommitAvailability();
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Import " + preview.sectionCounts().totalEntities() + " governed entities from "
                        + preview.operation().sourceName() + " into " + preview.operation().targetLabel()
                        + "?\n\nSHA-256: " + preview.operation().sourceSha256()
                        + "\n\nThe target must remain empty unless every governed identity is identical. "
                        + "Every section commits in one transaction; any failure rolls everything back.",
                ButtonType.OK, ButtonType.CANCEL);
        confirmation.setTitle("Confirm Atomic SCLX Import");
        confirmation.setHeaderText("Import the exact previewed SCLX file");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK)
        {
            status.setText("SCLX import cancelled; no data was changed.");
            return;
        }

        previewSclx.setDisable(true);
        commitSclx.setDisable(true);
        status.setText("Importing the exact previewed SCLX file atomically...");
        UiAsync.run("import-preview-sclx-commit",
                () -> commitService.commit(source, preview, actor),
                this::applySclxImportResult,
                ex ->
                {
                    previewSclx.setDisable(false);
                    updateSclxCommitAvailability();
                    status.setText("Could not import SCLX: " + UiErrors.safeMessage(ex));
                });
    }

    private void applySclxImportResult(SclxImportResult result)
    {
        previewSclx.setDisable(false);
        warnings.getItems().setAll(result.messages().stream()
                .map(ImportPreviewPanel::displayMessage)
                .toList());
        if (result.committed())
        {
            lastSclxSource = null;
            lastSclxPreview = null;
            lastSclxCommitService = null;
            commitSclx.setDisable(true);
            status.setText("Imported SCLX atomically into " + result.targetLabel()
                    + ": created " + result.counts().created() + ", identical "
                    + result.counts().identical() + ", SHA-256 " + result.sourceSha256()
                    + ". Reopen other workspaces to refresh imported data.");
            return;
        }
        lastSclxSource = null;
        lastSclxPreview = null;
        lastSclxCommitService = null;
        commitSclx.setDisable(true);
        status.setText("SCLX import rolled back; no partial data was kept. Review the reported errors and preview again.");
    }

    private static String displayMessage(InterchangeValidationMessage message)
    {
        String path = message.path().isBlank() ? "" : " [" + message.path() + "]";
        return message.severity() + " " + message.code() + path + ": " + message.message();
    }

    private void clearCoaPreview()
    {
        lastCoaPreview = null;
        acceptedCoaRows.getItems().clear();
        rejectedCoaRows.getItems().clear();
        commitAccepted.setDisable(true);
    }

    private void clearSclxPreview()
    {
        lastSclxSource = null;
        lastSclxPreview = null;
        lastSclxCommitService = null;
        commitSclx.setDisable(true);
        sclxCounts.getItems().clear();
        sclxEntities.getItems().clear();
        sclxMappings.getItems().clear();
        sclxTransactions.getItems().clear();
    }

    private void previewBank(Path file)
    {
        clearBankPreview();
        CompanyBankAccount account = bankAccount.getValue();
        String company = activeCompanyCode.get();
        BankStatementReviewService fixedScopeService = bankStatementReviewService.get();
        previewBank.setDisable(true);
        status.setText("Previewing OFX/QFX against " + account.getName() + " without changing H2...");
        UiAsync.run("import-preview-bank",
                () -> fixedScopeService.preview(file, company, account.getId()),
                result ->
                {
                    previewBank.setDisable(false);
                    lastBankCommitService = fixedScopeService;
                    lastBankCsvCommitService = null;
                    lastBankCsvReview = null;
                    applyBankPreview(result, List.of());
                },
                ex ->
                {
                    previewBank.setDisable(false);
                    clearBankPreview();
                    status.setText("Could not preview bank statement: " + UiErrors.safeMessage(ex));
                });
    }

    private void previewBankCsv(Path file)
    {
        clearBankPreview();
        CompanyBankAccount account = bankAccount.getValue();
        BankCsvMappingProfileService.ProfileSummary profile = bankCsvProfile.getValue();
        String company = activeCompanyCode.get();
        BankCsvReviewService fixedScopeService = bankCsvReviewService.get();
        previewBankCsv.setDisable(true);
        status.setText("Previewing mapped CSV against " + account.getName() + " without changing H2...");
        UiAsync.run("import-preview-bank-csv",
                () -> fixedScopeService.preview(file, company, account.getId(), profile.id()),
                result ->
                {
                    previewBankCsv.setDisable(false);
                    lastBankCsvCommitService = fixedScopeService;
                    lastBankCommitService = null;
                    lastBankCsvReview = result;
                    applyBankPreview(result.review(), result.originalRows());
                },
                ex ->
                {
                    previewBankCsv.setDisable(false);
                    clearBankPreview();
                    status.setText("Could not preview mapped bank CSV: " + UiErrors.safeMessage(ex));
                });
    }

    private void applyBankPreview(
            BankStatementReviewPreview result,
            List<org.nonprofitbookkeeping.interchange.bank.BankCsvParser.OriginalRow> originalRows)
    {
        clearCoaPreview();
        clearSclxPreview();
        lastBankReview = result;
        confirmBankIdentity.setSelected(false);
        bankRows.getItems().setAll(result.lines());
        bankCsvOriginalRows.getItems().setAll(originalRows);
        warnings.getItems().setAll(result.messages().stream()
                .map(ImportPreviewPanel::displayMessage)
                .toList());
        previewTabs.getSelectionModel().select(5);
        updateBankCommitAvailability();
        status.setText("Previewed " + result.document().variant() + " " + result.document().version()
                + " for " + result.configuredAccountName() + " in " + result.companyCode()
                + " with " + result.lines().size() + " row(s), account match "
                + result.accountMatchStatus() + ". No data was changed.");
    }

    private void updateBankCommitAvailability()
    {
        boolean ready = lastBankReview != null
                && (lastBankCommitService != null || lastBankCsvCommitService != null)
                && Objects.equals(lastBankReview.companyCode(), activeCompanyCode.get())
                && lastBankReview.commitAllowed(confirmBankIdentity.isSelected())
                && !sclxActor.getText().isBlank();
        commitBankReview.setDisable(!ready);
    }

    private void confirmAndCommitBankReview()
    {
        BankStatementReviewPreview preview = lastBankReview;
        BankCsvReviewPreview csvPreview = lastBankCsvReview;
        BankStatementReviewService fixedStatementCommit = lastBankCommitService;
        BankCsvReviewService fixedCsvCommit = lastBankCsvCommitService;
        String actor = sclxActor.getText().strip();
        boolean identityConfirmed = confirmBankIdentity.isSelected();
        if (preview == null || actor.isBlank()
                || !Objects.equals(preview.companyCode(), activeCompanyCode.get())
                || !preview.commitAllowed(identityConfirmed)
                || (fixedStatementCommit == null && fixedCsvCommit == null))
        {
            status.setText("Bank review import unavailable: preview a valid file, resolve account confirmation, and provide an actor.");
            updateBankCommitAvailability();
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Commit " + preview.lines().size() + " review row(s) from "
                        + preview.source().getFileName() + " into " + preview.companyCode()
                        + " / " + preview.configuredAccountName() + "?\n\nSHA-256: "
                        + preview.sourceHash()
                        + "\n\nThis creates durable review facts only. It does not create ledger transactions. "
                        + "The exact file, target, and mapping profile will be revalidated; any failure rolls back the batch.",
                ButtonType.OK, ButtonType.CANCEL);
        confirmation.setTitle("Confirm Atomic Bank Review Import");
        confirmation.setHeaderText("Commit the exact previewed bank statement");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK)
        {
            status.setText("Bank review import cancelled; no data was changed.");
            return;
        }

        previewBank.setDisable(true);
        previewBankCsv.setDisable(true);
        commitBankReview.setDisable(true);
        status.setText("Committing the exact previewed bank statement atomically...");
        UiAsync.run("import-preview-bank-commit",
                () -> csvPreview == null
                        ? fixedStatementCommit.commit(preview, identityConfirmed, actor)
                        : fixedCsvCommit.commit(csvPreview, identityConfirmed, actor),
                this::applyBankReviewResult,
                ex ->
                {
                    previewBank.setDisable(false);
                    previewBankCsv.setDisable(false);
                    clearBankPreview();
                    status.setText("Bank review import rolled back or was rejected: "
                            + UiErrors.safeMessage(ex) + ". Preview again before retrying.");
                });
    }

    private void applyBankReviewResult(BankStatementReviewResult result)
    {
        previewBank.setDisable(false);
        previewBankCsv.setDisable(false);
        clearBankPreview();
        status.setText((result.created() ? "Committed" : "Identical source already committed as")
                + " durable bank review batch " + result.batchId() + ": total "
                + result.totalLineCount() + ", reviewable " + result.reviewableLineCount()
                + ", duplicates " + result.duplicateLineCount() + ", errors "
                + result.errorLineCount() + ", issues " + result.issueCount()
                + ". No ledger transaction was created.");
    }

    private void clearBankPreview()
    {
        lastBankReview = null;
        lastBankCsvReview = null;
        lastBankCommitService = null;
        lastBankCsvCommitService = null;
        bankRows.getItems().clear();
        bankCsvOriginalRows.getItems().clear();
        confirmBankIdentity.setSelected(false);
        commitBankReview.setDisable(true);
    }

    private static String readProfile(Path path)
    {
        try
        {
            return java.nio.file.Files.readString(path);
        }
        catch (java.io.IOException ex)
        {
            throw new IllegalArgumentException("Cannot read bank CSV mapping profile: " + path, ex);
        }
    }

    private java.util.Optional<Path> chooseOpenFile(String title, FileChooser.ExtensionFilter filter)
    {
        if (root.getScene() == null || root.getScene().getWindow() == null)
        {
            status.setText("Preview unavailable: window is not ready.");
            return java.util.Optional.empty();
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(filter);
        File selected = chooser.showOpenDialog(root.getScene().getWindow());
        if (selected == null)
        {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(selected.toPath());
    }

    @FunctionalInterface
    private interface SclxPreviewOperationFactory
    {
        Function<Path, SclxImportPreview> capture();
    }
}
