package org.nonprofitbookkeeping.ui;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.nonprofitbookkeeping.report.ReportDefinition;
import org.nonprofitbookkeeping.report.ReportExecutionService;
import org.nonprofitbookkeeping.report.ReportFundOption;
import org.nonprofitbookkeeping.report.ReportRequest;
import org.nonprofitbookkeeping.report.ReportResult;
import org.nonprofitbookkeeping.service.CompanyUiPreferencesService;
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

/** Typed, service-backed Report Library workspace. */
public class ReportLibraryPanel implements AppPanel
{
    private static final String STATE_DIVIDER = "reportLibrary.divider";

    private final BorderPane root = new BorderPane();
    private final ListView<ReportDefinition> reportList = new ListView<>();
    private final TextArea preview = new TextArea();
    private final BorderPane previewHost = new BorderPane();
    private final Label status = new Label();
    private final Label startLabel = new Label("Start date:");
    private final Label endLabel = new Label("End date:");
    private final Label fundLabel = new Label("Fund:");
    private final Label rowLimitLabel = new Label("Maximum rows:");
    private final DatePicker startDate = new DatePicker();
    private final DatePicker endDate = new DatePicker();
    private final ComboBox<ReportFundOption> fund = new ComboBox<>();
    private final Spinner<Integer> rowLimit = new Spinner<>(
            1,
            ReportRequest.MAX_ROW_LIMIT,
            ReportRequest.DEFAULT_ROW_LIMIT,
            100);
    private final ComboBox<FinancialReportExportFormat> exportFormat = new ComboBox<>();
    private final Map<FinancialReportExportFormat, FinancialReportExportAdapter> adapters =
            new EnumMap<>(FinancialReportExportFormat.class);
    private final CompanyUiPreferencesService preferencesService = UiServiceRegistry.companyUiPreferences();
    private final String companyCode = activeCompanyCode();
    private final CompanyUiFormat companyFormat = new CompanyUiFormat(preferencesService.load(companyCode));
    private final ReportExecutionService executionService =
            new ReportExecutionService(UiServiceRegistry.financialReports(), companyFormat);
    private final PauseTransition dividerSaveDelay = new PauseTransition(Duration.millis(350));

    private SplitPane workspaceSplit;
    private ReportResult currentResult;

    public ReportLibraryPanel()
    {
        root.setPadding(new Insets(8));
        root.setMinWidth(0.0);
        root.setMinHeight(0.0);

        Label title = new Label("Report Library");
        title.getStyleClass().add("panel-title");

        adapters.put(FinancialReportExportFormat.PDF, new JasperPdfFinancialReportAdapter());
        adapters.put(FinancialReportExportFormat.XLSX, new PoiXlsxFinancialReportAdapter());

        Button run = new Button("Run");
        Button export = new Button("Export");
        Button drillLedger = new Button("Drill to Journal");
        exportFormat.getItems().setAll(FinancialReportExportFormat.values());
        exportFormat.getSelectionModel().select(FinancialReportExportFormat.TEXT);
        exportFormat.setPrefWidth(160);
        HBox actions = new HBox(8, run, export, drillLedger, new Label("Export format:"), exportFormat);

        root.setTop(new VBox(6, title, actions, status, new Separator()));

        configureReportList();
        configureParameters();
        configurePreview();
        configureWorkspace();
        configureActions(run, export, drillLedger);
        loadFunds();

        reportList.getSelectionModel().select(ReportDefinition.TRIAL_BALANCE);
        refreshParameterVisibility(ReportDefinition.TRIAL_BALANCE);
        runReport();
    }

    private void configureReportList()
    {
        reportList.getItems().setAll(ReportDefinition.catalog());
        reportList.setCellFactory(list -> new ListCell<>()
        {
            @Override
            protected void updateItem(ReportDefinition item, boolean empty)
            {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });
        reportList.setMinWidth(180.0);
    }

    private void configureParameters()
    {
        DateRange defaults = DateRangeContext.get();
        LocalDate end = defaults.endInclusive() == null ? LocalDate.now() : defaults.endInclusive();
        startDate.setValue(defaults.startInclusive() == null ? end.withDayOfYear(1) : defaults.startInclusive());
        endDate.setValue(end);
        companyFormat.install(startDate);
        companyFormat.install(endDate);

        fund.setConverter(new StringConverter<>()
        {
            @Override
            public String toString(ReportFundOption option)
            {
                return option == null ? "" : option.displayLabel();
            }

            @Override
            public ReportFundOption fromString(String value)
            {
                return null;
            }
        });
        fund.setPrefWidth(260.0);
        fund.getItems().setAll(ReportFundOption.ALL_FUNDS);
        fund.getSelectionModel().selectFirst();
        rowLimit.setEditable(true);

        startDate.valueProperty().addListener((obs, oldValue, newValue) -> parametersChanged());
        endDate.valueProperty().addListener((obs, oldValue, newValue) -> parametersChanged());
        fund.valueProperty().addListener((obs, oldValue, newValue) -> parametersChanged());
        rowLimit.valueProperty().addListener((obs, oldValue, newValue) -> parametersChanged());
    }

    private void configurePreview()
    {
        preview.setEditable(false);
        preview.setWrapText(false);
        previewHost.setCenter(preview);
        previewHost.setMinWidth(0.0);
        previewHost.setMinHeight(0.0);
    }

    private void configureWorkspace()
    {
        GridPane parameters = new GridPane();
        parameters.setHgap(8);
        parameters.setVgap(8);
        parameters.add(startLabel, 0, 0);
        parameters.add(startDate, 1, 0);
        parameters.add(endLabel, 0, 1);
        parameters.add(endDate, 1, 1);
        parameters.add(fundLabel, 0, 2);
        parameters.add(fund, 1, 2);
        parameters.add(rowLimitLabel, 0, 3);
        parameters.add(rowLimit, 1, 3);

        VBox right = new VBox(8,
                new Label("Report Parameters"),
                parameters,
                new Separator(),
                new Label("Preview"),
                previewHost);
        right.setPadding(new Insets(8));
        right.setMinWidth(0.0);
        right.setMinHeight(0.0);
        VBox.setVgrow(previewHost, Priority.ALWAYS);

        workspaceSplit = new SplitPane(reportList, right);
        workspaceSplit.setId("reportLibrarySplit");
        workspaceSplit.setDividerPositions(loadDividerPosition());
        workspaceSplit.getDividers().get(0).positionProperty().addListener((obs, oldValue, newValue) -> {
            dividerSaveDelay.setOnFinished(event -> preferencesService.saveState(
                    companyCode,
                    Map.of(STATE_DIVIDER, Double.toString(newValue.doubleValue()))));
            dividerSaveDelay.playFromStart();
        });
        root.setCenter(workspaceSplit);
    }

    private void configureActions(Button run, Button export, Button drillLedger)
    {
        run.setOnAction(event -> runReport());
        export.setOnAction(event -> exportReport());
        drillLedger.setOnAction(event -> drillToJournal());
        reportList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, definition) -> {
            if (definition != null)
            {
                refreshParameterVisibility(definition);
                parametersChanged();
                runReport();
            }
        });
    }

    private void loadFunds()
    {
        UiAsync.run("report-library-funds",
                () -> UiServiceRegistry.fundLookup().listActiveFunds().stream()
                        .map(ReportFundOption::from)
                        .toList(),
                options -> {
                    ReportFundOption selected = fund.getValue();
                    fund.getItems().setAll(ReportFundOption.ALL_FUNDS);
                    fund.getItems().addAll(options);
                    if (selected != null && fund.getItems().contains(selected))
                    {
                        fund.getSelectionModel().select(selected);
                    }
                    else
                    {
                        fund.getSelectionModel().selectFirst();
                    }
                },
                ex -> status.setText("Could not load funds: " + UiErrors.safeMessage(ex)));
    }

    private void refreshParameterVisibility(ReportDefinition definition)
    {
        boolean range = definition.dateMode() == ReportDefinition.DateMode.RANGE;
        setVisibleManaged(startLabel, range);
        setVisibleManaged(startDate, range);
        endLabel.setText(range ? "End date:" : "As of date:");

        setVisibleManaged(fundLabel, definition.supportsFund());
        setVisibleManaged(fund, definition.supportsFund());
        if (!definition.supportsFund())
        {
            fund.getSelectionModel().select(ReportFundOption.ALL_FUNDS);
        }

        setVisibleManaged(rowLimitLabel, definition.supportsRowLimit());
        setVisibleManaged(rowLimit, definition.supportsRowLimit());
    }

    private static void setVisibleManaged(Node node, boolean visible)
    {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void parametersChanged()
    {
        currentResult = null;
    }

    private ReportRequest buildRequest()
    {
        ReportDefinition definition = reportList.getSelectionModel().getSelectedItem();
        if (definition == null)
        {
            throw new IllegalStateException("Select a report.");
        }
        ReportFundOption selectedFund = definition.supportsFund()
                ? fund.getValue()
                : ReportFundOption.ALL_FUNDS;
        return new ReportRequest(
                definition,
                startDate.getValue(),
                endDate.getValue(),
                selectedFund,
                rowLimit.getValue());
    }

    private void runReport()
    {
        ReportRequest request;
        try
        {
            request = buildRequest();
        }
        catch (RuntimeException ex)
        {
            status.setText(UiErrors.safeMessage(ex));
            return;
        }

        status.setText("Generating " + request.definition().displayName() + "...");
        UiAsync.run("report-preview-" + request.definition().id(),
                () -> executionService.execute(request),
                result -> {
                    currentResult = result;
                    setPreview(result);
                    status.setText("Preview ready: " + request.contextSummary());
                },
                ex -> {
                    preview.setText("Could not generate preview: " + UiErrors.safeMessage(ex));
                    previewHost.setCenter(preview);
                    status.setText("Preview failed.");
                });
    }

    private void setPreview(ReportResult result)
    {
        if (result.semantic())
        {
            previewHost.setCenter(new SemanticReportFxRenderer().render(
                    result.semanticTemplate(),
                    result.semanticValues()));
        }
        else
        {
            preview.setText(result.text());
            previewHost.setCenter(preview);
        }
    }

    private void drillToJournal()
    {
        try
        {
            ReportRequest request = currentResult == null ? buildRequest() : currentResult.request();
            DrillThroughCoordinator.openLedgerWithContext("Report drill-through: " + request.contextSummary());
        }
        catch (RuntimeException ex)
        {
            status.setText(UiErrors.safeMessage(ex));
        }
    }

    private void exportReport()
    {
        ReportResult result;
        try
        {
            ReportRequest request = buildRequest();
            result = currentResult != null && currentResult.request().equals(request)
                    ? currentResult
                    : executionService.execute(request);
        }
        catch (RuntimeException ex)
        {
            status.setText("Could not generate export: " + UiErrors.safeMessage(ex));
            return;
        }

        FinancialReportExportFormat format = selectedExportFormat();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Report");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                format.label(),
                "*." + format.extension()));
        chooser.setInitialFileName(buildReportExportFileName(
                result.request().definition().displayName(),
                result.request().endDate(),
                format));
        File selected = chooser.showSaveDialog(root.getScene() == null ? null : root.getScene().getWindow());
        if (selected == null)
        {
            status.setText("Report export cancelled.");
            return;
        }

        try
        {
            writeExport(selected.toPath(), result, format);
            currentResult = result;
            status.setText("Exported " + result.request().definition().displayName()
                    + " (" + format.label() + ") to " + selected.getName() + ".");
        }
        catch (IOException | RuntimeException ex)
        {
            status.setText("Could not export report: " + UiErrors.safeMessage(ex));
        }
    }

    private FinancialReportExportFormat selectedExportFormat()
    {
        return exportFormat.getValue() == null
                ? FinancialReportExportFormat.TEXT
                : exportFormat.getValue();
    }

    void setExportFormatForTests(FinancialReportExportFormat format)
    {
        exportFormat.getSelectionModel().select(
                format == null ? FinancialReportExportFormat.TEXT : format);
    }

    void exportReportToPathForTests(Path path) throws IOException
    {
        ReportRequest request = buildRequest();
        ReportResult result = currentResult != null && currentResult.request().equals(request)
                ? currentResult
                : executionService.execute(request);
        writeExport(path, result, selectedExportFormat());
    }

    private void writeExport(
            Path path,
            ReportResult result,
            FinancialReportExportFormat format) throws IOException
    {
        switch (format)
        {
            case TEXT -> Files.writeString(path, result.text(), StandardCharsets.UTF_8);
            case CSV -> Files.writeString(path, result.csv(), StandardCharsets.UTF_8);
            case PDF, XLSX -> {
                FinancialReportExportAdapter adapter = adapters.get(format);
                if (adapter == null)
                {
                    throw new IllegalStateException("No export adapter configured for format: " + format);
                }
                Files.write(path, adapter.render(
                        result.request().definition().displayName(),
                        result.text(),
                        result.csv()));
            }
        }
    }

    private double loadDividerPosition()
    {
        String value = preferencesService.loadState(companyCode, "reportLibrary.")
                .get(STATE_DIVIDER);
        try
        {
            double position = value == null ? 0.28 : Double.parseDouble(value);
            return Math.max(0.15, Math.min(0.60, position));
        }
        catch (NumberFormatException ex)
        {
            return 0.28;
        }
    }

    private static String activeCompanyCode()
    {
        String company = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
        String value = company == null || company.isBlank()
                ? "DEFAULT"
                : company.trim().toUpperCase(Locale.ROOT);
        return value.replaceAll("[^A-Z0-9_-]", "_");
    }

    static String buildReportExportFileName(
            String reportName,
            LocalDate date,
            FinancialReportExportFormat format)
    {
        String normalized = reportName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (normalized.isBlank())
        {
            normalized = "report";
        }
        FinancialReportExportFormat effective = format == null
                ? FinancialReportExportFormat.TEXT
                : format;
        LocalDate effectiveDate = date == null ? LocalDate.now() : date;
        return normalized + "-" + effectiveDate + "." + effective.extension();
    }

    @Override
    public String title()
    {
        return "Report Library";
    }

    @Override
    public Node root()
    {
        return root;
    }
}
