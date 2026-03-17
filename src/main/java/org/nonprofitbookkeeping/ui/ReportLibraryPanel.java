package org.nonprofitbookkeeping.ui;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.nonprofitbookkeeping.service.FinancialReportRenderer;
import org.nonprofitbookkeeping.service.FinancialReportService;
import org.nonprofitbookkeeping.service.FinancialReportExportAdapter;
import org.nonprofitbookkeeping.service.FinancialReportExportFormat;
import org.nonprofitbookkeeping.service.JasperPdfFinancialReportAdapter;
import org.nonprofitbookkeeping.service.PoiXlsxFinancialReportAdapter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Represents the ReportLibraryPanel component in the nonprofit bookkeeping application.
 */
public class ReportLibraryPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final ListView<String> reportList = new ListView<>();
    private final TextArea preview = new TextArea();
    private final Label status = new Label();
    private final ComboBox<FinancialReportExportFormat> exportFormat = new ComboBox<>();
    private final Map<FinancialReportExportFormat, FinancialReportExportAdapter> adapters = new EnumMap<>(FinancialReportExportFormat.class);

    private record RenderedReport(String text, String csv) {}

    public ReportLibraryPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Reports Library");
        Label range = new Label();
        range.textProperty().bind(Bindings.createStringBinding(() -> "Date Range: " + DateRangeContext.get(), DateRangeContext.selectedProperty()));
        title.getStyleClass().add("panel-title");

        adapters.put(FinancialReportExportFormat.PDF, new JasperPdfFinancialReportAdapter());
        adapters.put(FinancialReportExportFormat.XLSX, new PoiXlsxFinancialReportAdapter());

        Button run = new Button("Run");
        Button export = new Button("Export");
        Button drillLedger = new Button("Drill to Ledger");
        exportFormat.getItems().setAll(FinancialReportExportFormat.values());
        exportFormat.getSelectionModel().select(FinancialReportExportFormat.TEXT);
        exportFormat.setPrefWidth(160);
        HBox actions = new HBox(8, run, export, drillLedger, new Label("Export format:"), exportFormat);

        root.setTop(new VBox(6, title, range, actions, status, new Separator()));

        reportList.getItems().addAll(
                "Trial Balance",
                "General Ledger Detail",
                "Balance Sheet",
                "Income Statement"
        );
        reportList.getSelectionModel().select(0);

        preview.setEditable(false);
        preview.setWrapText(false);

        VBox right = new VBox(8,
                new Label("Report Parameters"),
                new Label("Current period: " + DateRangeContext.get()),
                new Label("Data source: live database records"),
                new Separator(),
                new Label("Preview"),
                preview);
        right.setPadding(new Insets(8));

        SplitPane sp = new SplitPane(reportList, right);
        sp.setDividerPositions(0.30);
        root.setCenter(sp);

        run.setOnAction(e -> runReport());
        export.setOnAction(e -> exportReport());
        reportList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> runReport());
        drillLedger.setOnAction(e -> drillToLedger());

        runReport();
    }

    private void runReport()
    {
        String reportName = reportList.getSelectionModel().getSelectedItem();
        if (reportName == null)
        {
            return;
        }

        status.setText("Generating " + reportName + "...");
        UiAsync.run("report-preview-" + reportName,
                () -> buildPreview(reportName),
                rendered -> {
                    preview.setText(rendered.text());
                    status.setText("Preview ready for " + reportName + ".");
                },
                ex -> {
                    preview.setText("Could not generate preview: " + UiErrors.safeMessage(ex));
                    status.setText("Preview failed.");
                });
    }

    private RenderedReport buildPreview(String reportName)
    {
        FinancialReportService reports = UiServiceRegistry.financialReports();
        DateRange range = DateRangeContext.get();
        LocalDate start = range.startInclusive();
        LocalDate end = range.endInclusive() == null ? LocalDate.now() : range.endInclusive();

        return switch (reportName)
        {
            case "Trial Balance" -> {
                FinancialReportService.TrialBalanceReport report = reports.trialBalance(end, null);
                yield new RenderedReport(
                        FinancialReportRenderer.renderTrialBalanceText(report),
                        FinancialReportRenderer.renderTrialBalanceCsv(report));
            }
            case "General Ledger Detail" -> {
                java.util.List<FinancialReportService.GeneralLedgerRow> rows = reports.generalLedgerDetail(start, end, null, 400);
                yield new RenderedReport(
                        FinancialReportRenderer.renderGeneralLedgerText(rows),
                        FinancialReportRenderer.renderGeneralLedgerCsv(rows));
            }
            case "Balance Sheet" -> {
                FinancialReportService.BalanceSheetReport report = reports.balanceSheet(end, null);
                yield new RenderedReport(
                        FinancialReportRenderer.renderBalanceSheetText(report),
                        FinancialReportRenderer.renderBalanceSheetCsv(report));
            }
            case "Income Statement" -> {
                FinancialReportService.IncomeStatementReport report = reports.incomeStatement(start, end, null);
                yield new RenderedReport(
                        FinancialReportRenderer.renderIncomeStatementText(report),
                        FinancialReportRenderer.renderIncomeStatementCsv(report));
            }
            default -> new RenderedReport("Report not implemented: " + reportName, "");
        };
    }

    private void drillToLedger()
    {
        String reportName = reportList.getSelectionModel().getSelectedItem();
        if (reportName == null)
        {
            return;
        }
        DrillThroughCoordinator.openLedgerWithContext("Report drill-through: " + reportName + " | " + DateRangeContext.get());
    }

    private void exportReport()
    {
        String reportName = reportList.getSelectionModel().getSelectedItem();
        if (reportName == null)
        {
            status.setText("Select a report before exporting.");
            return;
        }
        RenderedReport rendered = buildPreview(reportName);
        String previewText = rendered.text();
        if (previewText == null || previewText.isBlank())
        {
            status.setText("Run the report preview before exporting.");
            return;
        }

        FinancialReportExportFormat format = exportFormat.getValue() == null
                ? FinancialReportExportFormat.TEXT
                : exportFormat.getValue();

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Report Preview");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(format.label(), "*." + format.extension()));
        chooser.setInitialFileName(buildReportExportFileName(reportName, LocalDate.now(), format));
        File selected = chooser.showSaveDialog(root.getScene() == null ? null : root.getScene().getWindow());
        if (selected == null)
        {
            status.setText("Report export cancelled.");
            return;
        }

        try
        {
            Path path = selected.toPath();
            writeExport(path, reportName, rendered, format);
            status.setText("Exported " + reportName + " (" + format.label() + ") to " + path.getFileName() + ".");
        }
        catch (IOException | RuntimeException ex)
        {
            status.setText("Could not export report preview: " + UiErrors.safeMessage(ex));
        }
    }


    void setExportFormatForTests(FinancialReportExportFormat format)
    {
        exportFormat.getSelectionModel().select(format == null ? FinancialReportExportFormat.TEXT : format);
    }

    void exportReportToPathForTests(Path path) throws IOException
    {
        String reportName = reportList.getSelectionModel().getSelectedItem();
        if (reportName == null)
        {
            throw new IllegalStateException("No report selected.");
        }
        RenderedReport rendered = buildPreview(reportName);
        FinancialReportExportFormat format = exportFormat.getValue() == null
                ? FinancialReportExportFormat.TEXT
                : exportFormat.getValue();
        writeExport(path, reportName, rendered, format);
    }

    private void writeExport(Path path,
                             String reportName,
                             RenderedReport rendered,
                             FinancialReportExportFormat format) throws IOException
    {
        String previewText = rendered.text();
        switch (format)
        {
            case TEXT -> Files.writeString(path, previewText, StandardCharsets.UTF_8);
            case CSV -> Files.writeString(path, rendered.csv(), StandardCharsets.UTF_8);
            case PDF, XLSX -> {
                FinancialReportExportAdapter adapter = adapters.get(format);
                if (adapter == null)
                {
                    throw new IllegalStateException("No export adapter configured for format: " + format);
                }
                Files.write(path, adapter.render(reportName, previewText, rendered.csv()));
            }
        }
    }

    static String buildReportExportFileName(String reportName, LocalDate date, FinancialReportExportFormat format)
    {
        String normalized = reportName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (normalized.isBlank())
        {
            normalized = "report";
        }
        FinancialReportExportFormat effective = format == null ? FinancialReportExportFormat.TEXT : format;
        return normalized + "-" + date + "." + effective.extension();
    }

    @Override public String title() { return "Reports Library"; }
    @Override public Node root() { return root; }
}
