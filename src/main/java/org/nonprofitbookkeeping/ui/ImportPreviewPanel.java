package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.service.CoaCsvMapper;
import org.nonprofitbookkeeping.service.ImportPreviewService;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;

/**
 * ImportPreviewPanel component.
 */
public class ImportPreviewPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final ImportPreviewService previewService = new ImportPreviewService();
    private final Label status = new Label("Choose a COA CSV or OFX/QFX file to preview before import.");
    private final ListView<String> warnings = new ListView<>();
    private final TableView<CoaCsvMapper.CoaCsvRow> acceptedCoaRows = new TableView<>();
    private final TableView<ImportPreviewService.RejectedCoaRow> rejectedCoaRows = new TableView<>();
    private ImportPreviewService.CoaPreviewResult lastCoaPreview;

    public ImportPreviewPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Import Preview");
        title.getStyleClass().add("panel-title");

        Button previewCoa = new Button("Preview COA CSV…");
        previewCoa.setOnAction(e -> chooseAndPreviewCoa());

        Button previewBank = new Button("Preview Bank OFX/QFX…");
        previewBank.setOnAction(e -> chooseAndPreviewBank());
        Button commitAccepted = new Button("Commit Accepted COA Rows");
        commitAccepted.setOnAction(e -> commitAcceptedCoaRows());

        root.setTop(new VBox(6, title, new HBox(8, previewCoa, previewBank, commitAccepted), status, new Separator()));

        buildAcceptedTable();
        buildRejectedTable();

        warnings.setPlaceholder(new Label("No validation warnings."));

        SplitPane rowTables = new SplitPane(
                new VBox(6, new Label("Accepted COA Rows"), acceptedCoaRows),
                new VBox(6, new Label("Rejected COA Rows"), rejectedCoaRows));
        rowTables.setDividerPositions(0.58);

        VBox center = new VBox(8, new Label("Preview Warnings"), warnings, rowTables);
        root.setCenter(center);
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
        acceptedCoaRows.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
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
        rejectedCoaRows.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
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

    private void chooseAndPreviewBank()
    {
        chooseOpenFile("Preview Bank OFX/QFX", new FileChooser.ExtensionFilter("Bank Statement Files", "*.ofx", "*.qfx"))
                .ifPresent(this::previewBank);
    }

    private void previewCoa(Path file)
    {
        UiAsync.run("import-preview-coa", () -> previewService.previewCoaCsv(file), result -> {
            lastCoaPreview = result;
            acceptedCoaRows.getItems().setAll(result.acceptedRows());
            rejectedCoaRows.getItems().setAll(result.rejectedRows());
            warnings.getItems().setAll(result.warnings());
            status.setText("Previewed " + result.totalRowCount()
                    + " COA row(s) from " + result.sourceName()
                    + ": accepted " + result.acceptedCount() + ", rejected " + result.rejectedCount() + ".");
        }, ex -> {
            warnings.getItems().clear();
            acceptedCoaRows.getItems().clear();
            rejectedCoaRows.getItems().clear();
            status.setText("Could not preview COA CSV: " + UiErrors.safeMessage(ex));
        });
    }

    private void previewBank(Path file)
    {
        UiAsync.run("import-preview-bank", () -> previewService.previewBankStatement(file), result -> {
            lastCoaPreview = null;
            warnings.getItems().clear();
            acceptedCoaRows.getItems().clear();
            rejectedCoaRows.getItems().clear();
            status.setText("Previewed " + result.format() + " statement with " + result.transactionCount() + " transaction(s) from " + result.sourceName() + ".");
        }, ex -> {
            warnings.getItems().clear();
            acceptedCoaRows.getItems().clear();
            rejectedCoaRows.getItems().clear();
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
}
