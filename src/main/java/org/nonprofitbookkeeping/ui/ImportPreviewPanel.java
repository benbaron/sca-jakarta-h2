package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportEntityPreview;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportMappingRequirement;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportPreview;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportPreviewService;
import org.nonprofitbookkeeping.interchange.sclx.SclxImportTransactionPreview;
import org.nonprofitbookkeeping.service.CoaCsvMapper;
import org.nonprofitbookkeeping.service.ImportPreviewService;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
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
    private ImportPreviewService.CoaPreviewResult lastCoaPreview;

    public ImportPreviewPanel()
    {
        this(new ImportPreviewService(),
                (Supplier<SclxImportPreviewService>) UiServiceRegistry::sclxImportPreview);
    }

    ImportPreviewPanel(
            ImportPreviewService previewService,
            Function<Path, SclxImportPreview> sclxPreview)
    {
        this(previewService, () -> Objects.requireNonNull(sclxPreview, "sclxPreview"));
    }

    ImportPreviewPanel(
            ImportPreviewService previewService,
            Supplier<SclxImportPreviewService> sclxPreviewService)
    {
        this(previewService, () -> Objects.requireNonNull(
                sclxPreviewService.get(), "sclxPreviewService result")::preview);
    }

    private ImportPreviewPanel(
            ImportPreviewService previewService,
            SclxPreviewOperationFactory sclxPreviewFactory)
    {
        this.previewService = Objects.requireNonNull(previewService, "previewService");
        this.sclxPreviewFactory = Objects.requireNonNull(sclxPreviewFactory, "sclxPreviewFactory");
        root.setPadding(new Insets(8));

        Label title = new Label("Import Preview");
        title.getStyleClass().add("panel-title");

        previewSclx.setOnAction(e -> chooseAndPreviewSclx());
        previewSclx.setId("previewSclxButton");

        Button previewCoa = new Button("Preview COA CSV…");
        previewCoa.setOnAction(e -> chooseAndPreviewCoa());

        Button previewBank = new Button("Preview Bank OFX/QFX…");
        previewBank.setOnAction(e -> chooseAndPreviewBank());
        commitAccepted.setOnAction(e -> commitAcceptedCoaRows());
        commitAccepted.setId("commitAcceptedCoaRowsButton");
        commitAccepted.setDisable(true);

        status.setId("importPreviewStatus");
        status.setWrapText(true);
        root.setTop(new VBox(6, title,
                new HBox(8, previewSclx, previewCoa, previewBank, commitAccepted),
                status, new Separator()));

        buildAcceptedTable();
        buildRejectedTable();
        buildSclxEntityTable();
        buildSclxMappingTable();
        buildSclxTransactionTable();

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
                tab("SCLX Transactions", tableRegion("Transaction Diagnostics", sclxTransactions)));

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
        chooseOpenFile("Preview Bank OFX/QFX", new FileChooser.ExtensionFilter("Bank Statement Files", "*.ofx", "*.qfx"))
                .ifPresent(this::previewBank);
    }

    private void previewCoa(Path file)
    {
        UiAsync.run("import-preview-coa", () -> previewService.previewCoaCsv(file), result -> {
            clearSclxPreview();
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
            warnings.getItems().clear();
            acceptedCoaRows.getItems().clear();
            rejectedCoaRows.getItems().clear();
            commitAccepted.setDisable(true);
            status.setText("Could not preview COA CSV: " + UiErrors.safeMessage(ex));
        });
    }

    private void previewSclx(Path file)
    {
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
            applySclxPreview(result);
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
        Objects.requireNonNull(result, "result");
        clearCoaPreview();
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

        String outcome = result.hasBlockingErrors() ? "BLOCKED" : "READY FOR MAPPING REVIEW";
        status.setText("Previewed SCLX " + result.version().externalValue()
                + " from " + result.operation().sourceName()
                + " for target " + result.targetCompanyCode()
                + ": " + result.sectionCounts().totalEntities() + " entities, "
                + result.operation().counts().created() + " new, "
                + result.operation().counts().identical() + " identical, "
                + result.operation().counts().errors() + " blocking error(s); account mode "
                + result.recommendedAccountMode() + "; " + outcome + ". No data was changed.");
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
        sclxCounts.getItems().clear();
        sclxEntities.getItems().clear();
        sclxMappings.getItems().clear();
        sclxTransactions.getItems().clear();
    }

    private void previewBank(Path file)
    {
        UiAsync.run("import-preview-bank", () -> previewService.previewBankStatement(file), result -> {
            clearCoaPreview();
            clearSclxPreview();
            warnings.getItems().clear();
            status.setText("Previewed " + result.format() + " statement with " + result.transactionCount() + " transaction(s) from " + result.sourceName() + ".");
        }, ex -> {
            clearCoaPreview();
            clearSclxPreview();
            warnings.getItems().clear();
            status.setText("Could not preview bank statement: " + UiErrors.safeMessage(ex));
        });
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
