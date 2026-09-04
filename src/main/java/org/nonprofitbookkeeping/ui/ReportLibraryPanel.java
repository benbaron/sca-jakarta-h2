package org.nonprofitbookkeeping.ui;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
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
import org.nonprofitbookkeeping.report.AssetInventoryReportQueryService;
import org.nonprofitbookkeeping.report.ReportDomainFilter;
import org.nonprofitbookkeeping.report.ReportExecutionService;
import org.nonprofitbookkeeping.report.ReportFundOption;
import org.nonprofitbookkeeping.report.ReportPresentationMetadata;
import org.nonprofitbookkeeping.report.ReportRequest;
import org.nonprofitbookkeeping.report.ReportResult;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.InventoryItem;
import org.nonprofitbookkeeping.service.ApplicationPermission;
import org.nonprofitbookkeeping.service.FiscalPeriodRange;
import org.nonprofitbookkeeping.service.CompanyReportingDefaults;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Typed, service-backed Report Library workspace. */
public class ReportLibraryPanel implements AppPanel
{
    private static final String STATE_DIVIDER = "reportLibrary.divider";
    private static final String STATE_PREVIEW_DIVIDER = "reportLibrary.previewDivider";

    private final BorderPane root = new BorderPane();
    private final ListView<ReportDefinition> reportList = new ListView<>();
    private final TextArea preview = new TextArea();
    private final BorderPane previewHost = new BorderPane();
    private final Label status = new Label();
    private final Label startLabel = new Label("Start date:");
    private final Label endLabel = new Label("End date:");
    private final Label fundLabel = new Label("Fund:");
    private final Label rowLimitLabel = new Label("Maximum rows:");
    private final Label domainAccountLabel = new Label("Control account:");
    private final Label domainSubjectLabel = new Label("Asset:");
    private final Label domainStatusLabel = new Label("Status:");
    private final DatePicker startDate = new DatePicker();
    private final DatePicker endDate = new DatePicker();
    private final ComboBox<ReportFundOption> fund = new ComboBox<>();
    private final ComboBox<ReportAccountOption> domainAccount = new ComboBox<>();
    private final ComboBox<AssetInventoryReportQueryService.FilterOption> domainSubject =
            new ComboBox<>();
    private final ComboBox<Object> domainStatus = new ComboBox<>();
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
    private final CompanyReportingDefaults reportingDefaults =
            preferencesService.loadReportingDefaults(companyCode);
    private final CompanyUiFormat companyFormat = new CompanyUiFormat(preferencesService.load(companyCode));
    private final ReportPresentationMetadata reportPresentationMetadata =
            ReportPresentationMetadata.from(
                    UiServiceRegistry.companyAdmin().findCompany(companyCode).orElse(null));
    private final AssetInventoryReportQueryService assetInventoryReportService =
            UiServiceRegistry.assetInventoryReports();
    private final ReportExecutionService executionService =
            new ReportExecutionService(
                    UiServiceRegistry.financialReports(),
                    companyFormat,
                    UiServiceRegistry.semanticAccountingReports(),
                    assetInventoryReportService,
                    reportPresentationMetadata);
    private final PauseTransition dividerSaveDelay = new PauseTransition(Duration.millis(350));
    private final PauseTransition previewDividerSaveDelay = new PauseTransition(Duration.millis(350));

    private SplitPane workspaceSplit;
    private SplitPane parameterPreviewSplit;
    private ReportResult currentResult;
    private AssetInventoryReportQueryService.FilterCatalog domainFilterCatalog;
    private List<ReportAccountOption> reportAccounts = List.of();
    private boolean followActivePeriodDefaults;
    private boolean applyingActivePeriodDefaults;

    public ReportLibraryPanel()
    {
        root.setPadding(new Insets(8));
        root.setMinWidth(0.0);
        root.setMinHeight(0.0);

        Label title = new Label("Report Library");
        title.getStyleClass().add("panel-title");

        adapters.put(FinancialReportExportFormat.PDF,
                new JasperPdfFinancialReportAdapter(companyFormat));
        adapters.put(FinancialReportExportFormat.XLSX, new PoiXlsxFinancialReportAdapter());

        Button run = new Button("Run");
        Button export = new Button("Export");
        UiPermissionGate.gate(export, ApplicationPermission.EXPORT, "Export a financial report");
        Button drillLedger = new Button("Drill to Journal");
        exportFormat.getItems().setAll(FinancialReportExportFormat.values());
        exportFormat.getSelectionModel().select(reportingDefaults.defaultExportFormat());
        exportFormat.setPrefWidth(160);
        HBox actions = new HBox(8, run, export, drillLedger, new Label("Export format:"), exportFormat);

        root.setTop(new VBox(6, title, actions, status, new Separator()));

        configureReportList();
        configureParameters();
        configurePreview();
        configureWorkspace();
        configureActions(run, export, drillLedger);
        loadFunds();
        loadDomainFilters();

        ReportDefinition openingReport = reportingDefaults.defaultReport();
        reportList.getSelectionModel().select(openingReport);
        refreshParameterVisibility(openingReport);
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
        followActivePeriodDefaults = defaults.isAll();
        if (followActivePeriodDefaults)
        {
            applyActivePeriodDefaults(ActivePeriodContext.get());
        }
        else
        {
            FiscalPeriodRange activeFiscal = UiServiceRegistry.budgetPlan().fiscalRange(ActivePeriodContext.get());
            LocalDate end = defaults.endInclusive() == null ? activeFiscal.periodEnd() : defaults.endInclusive();
            LocalDate start = defaults.startInclusive();
            if (start == null)
            {
                start = UiServiceRegistry.budgetPlan().fiscalRange(end).fiscalYearStart();
            }
            startDate.setValue(start);
            endDate.setValue(end);
        }
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

        configureAccountFilterCombo();
        configureDomainFilterCombo(domainSubject, "All records");
        domainStatus.setPromptText("All statuses");
        domainStatus.setPrefWidth(260.0);

        startDate.valueProperty().addListener((obs, oldValue, newValue) -> reportDateChanged());
        endDate.valueProperty().addListener((obs, oldValue, newValue) -> reportDateChanged());
        ActivePeriodContext.activeDateProperty().addListener(
                (obs, oldValue, newValue) -> activePeriodChanged(newValue));
        fund.valueProperty().addListener((obs, oldValue, newValue) -> parametersChanged());
        rowLimit.valueProperty().addListener((obs, oldValue, newValue) -> parametersChanged());
        domainAccount.valueProperty().addListener((obs, oldValue, newValue) -> parametersChanged());
        domainSubject.valueProperty().addListener((obs, oldValue, newValue) -> parametersChanged());
        domainStatus.valueProperty().addListener((obs, oldValue, newValue) -> parametersChanged());
    }

    private void applyActivePeriodDefaults(LocalDate selectedPeriodStart)
    {
        FiscalPeriodRange fiscal = UiServiceRegistry.budgetPlan().fiscalRange(selectedPeriodStart);
        applyingActivePeriodDefaults = true;
        try
        {
            startDate.setValue(fiscal.fiscalYearStart());
            endDate.setValue(fiscal.periodEnd());
        }
        finally
        {
            applyingActivePeriodDefaults = false;
        }
    }

    private void reportDateChanged()
    {
        if (!applyingActivePeriodDefaults)
        {
            followActivePeriodDefaults = false;
        }
        parametersChanged();
    }

    private void activePeriodChanged(LocalDate selectedPeriodStart)
    {
        if (!followActivePeriodDefaults || selectedPeriodStart == null)
        {
            return;
        }
        applyActivePeriodDefaults(selectedPeriodStart);
        if (reportList.getSelectionModel().getSelectedItem() != null)
        {
            runReport();
        }
    }

    private void configureDomainFilterCombo(
            ComboBox<AssetInventoryReportQueryService.FilterOption> combo,
            String prompt)
    {
        combo.setPromptText(prompt);
        combo.setPrefWidth(260.0);
        combo.setConverter(new StringConverter<>()
        {
            @Override
            public String toString(AssetInventoryReportQueryService.FilterOption option)
            {
                return option == null ? "" : option.label();
            }

            @Override
            public AssetInventoryReportQueryService.FilterOption fromString(String value)
            {
                return null;
            }
        });
    }

    private void configureAccountFilterCombo()
    {
        domainAccount.setPrefWidth(260.0);
        domainAccount.setConverter(new StringConverter<>()
        {
            @Override
            public String toString(ReportAccountOption option)
            {
                return option == null ? "" : option.label();
            }

            @Override
            public ReportAccountOption fromString(String value)
            {
                return null;
            }
        });
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
        parameters.add(domainAccountLabel, 0, 4);
        parameters.add(domainAccount, 1, 4);
        parameters.add(domainSubjectLabel, 0, 5);
        parameters.add(domainSubject, 1, 5);
        parameters.add(domainStatusLabel, 0, 6);
        parameters.add(domainStatus, 1, 6);

        VBox parameterRegion = new VBox(8,
                new Label("Report Parameters"),
                parameters);
        parameterRegion.setPadding(new Insets(8));
        parameterRegion.setMinWidth(0.0);
        parameterRegion.setMinHeight(0.0);

        VBox previewRegion = new VBox(8,
                new Label("Preview"),
                previewHost);
        previewRegion.setPadding(new Insets(8));
        previewRegion.setMinWidth(0.0);
        previewRegion.setMinHeight(0.0);
        VBox.setVgrow(previewHost, Priority.ALWAYS);

        parameterPreviewSplit = new SplitPane(parameterRegion, previewRegion);
        parameterPreviewSplit.setId("reportLibraryParameterPreviewSplit");
        parameterPreviewSplit.setOrientation(Orientation.VERTICAL);
        parameterPreviewSplit.setDividerPositions(loadDividerPosition(
                STATE_PREVIEW_DIVIDER, 0.32, 0.16, 0.68));
        installDividerPersistence(
                parameterPreviewSplit,
                STATE_PREVIEW_DIVIDER,
                previewDividerSaveDelay);

        workspaceSplit = new SplitPane(reportList, parameterPreviewSplit);
        workspaceSplit.setId("reportLibrarySplit");
        workspaceSplit.setDividerPositions(loadDividerPosition());
        installDividerPersistence(workspaceSplit, STATE_DIVIDER, dividerSaveDelay);
        root.setCenter(workspaceSplit);
    }

    private void installDividerPersistence(
            SplitPane split,
            String stateKey,
            PauseTransition delay)
    {
        delay.setOnFinished(event -> preferencesService.saveState(
                companyCode,
                Map.of(stateKey, Double.toString(split.getDividerPositions()[0]))));
        split.getDividers().get(0).positionProperty().addListener(
                (obs, oldValue, newValue) -> delay.playFromStart());
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

    private void loadDomainFilters()
    {
        UiAsync.run("report-library-domain-filters",
                () -> new ReportParameterCatalog(
                        assetInventoryReportService.filterCatalog(),
                        UiServiceRegistry.accountLookup()
                                .listPostingAccountsIncludingInactive()
                                .stream()
                                .map(account -> new ReportAccountOption(
                                        account.getId(),
                                        account.getCode() + " — " + account.getName()
                                                + (account.isActive() ? "" : " (inactive)")))
                                .toList()),
                catalog -> {
                    domainFilterCatalog = catalog.domainFilters();
                    reportAccounts = catalog.accounts();
                    ReportDefinition definition = reportList.getSelectionModel().getSelectedItem();
                    if (definition != null)
                    {
                        applyDomainFilterCatalog(definition.domainFilterMode());
                    }
                },
                ex -> status.setText("Could not load report filters: "
                        + UiErrors.safeMessage(ex)));
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

        ReportDefinition.DomainFilterMode mode = definition.domainFilterMode();
        boolean account = mode != ReportDefinition.DomainFilterMode.NONE;
        boolean subject = mode == ReportDefinition.DomainFilterMode.FIXED_ASSET
                || mode == ReportDefinition.DomainFilterMode.INVENTORY;
        setVisibleManaged(domainAccountLabel, account);
        setVisibleManaged(domainAccount, account);
        setVisibleManaged(domainSubjectLabel, subject);
        setVisibleManaged(domainSubject, subject);
        setVisibleManaged(domainStatusLabel, subject);
        setVisibleManaged(domainStatus, subject);
        applyDomainFilterCatalog(definition.domainFilterMode());
    }

    private void applyDomainFilterCatalog(ReportDefinition.DomainFilterMode mode)
    {
        domainAccount.getSelectionModel().clearSelection();
        domainSubject.getSelectionModel().clearSelection();
        domainStatus.getSelectionModel().clearSelection();
        domainAccount.getItems().clear();
        domainSubject.getItems().clear();
        domainStatus.getItems().clear();
        if (mode == ReportDefinition.DomainFilterMode.ACCOUNT)
        {
            domainAccountLabel.setText("Account:");
            domainAccount.setPromptText("All accounts");
            domainAccount.getItems().add(new ReportAccountOption(null, "All accounts"));
            domainAccount.getItems().addAll(reportAccounts);
            domainAccount.getSelectionModel().selectFirst();
        }
        else if (mode == ReportDefinition.DomainFilterMode.FIXED_ASSET)
        {
            domainAccountLabel.setText("Control account:");
            domainAccount.setPromptText("All control accounts");
            domainSubjectLabel.setText("Asset:");
            domainSubject.setPromptText("All assets");
            domainStatus.getItems().addAll(FixedAsset.Status.values());
            domainAccount.getItems().add(new ReportAccountOption(null, "All control accounts"));
            if (domainFilterCatalog != null)
            {
                domainAccount.getItems().addAll(domainFilterCatalog.assetAccounts().stream()
                        .map(ReportAccountOption::from)
                        .toList());
                domainSubject.getItems().addAll(domainFilterCatalog.assets());
            }
            domainAccount.getSelectionModel().selectFirst();
        }
        else if (mode == ReportDefinition.DomainFilterMode.INVENTORY)
        {
            domainAccountLabel.setText("Control account:");
            domainAccount.setPromptText("All control accounts");
            domainSubjectLabel.setText("Item:");
            domainSubject.setPromptText("All items");
            domainStatus.getItems().addAll(InventoryItem.Status.values());
            domainAccount.getItems().add(new ReportAccountOption(null, "All control accounts"));
            if (domainFilterCatalog != null)
            {
                domainAccount.getItems().addAll(domainFilterCatalog.inventoryAccounts().stream()
                        .map(ReportAccountOption::from)
                        .toList());
                domainSubject.getItems().addAll(domainFilterCatalog.inventoryItems());
            }
            domainAccount.getSelectionModel().selectFirst();
        }
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
        ReportDomainFilter selectedDomain = switch (definition.domainFilterMode())
        {
            case NONE -> ReportDomainFilter.NONE;
            case ACCOUNT -> new ReportDomainFilter.AccountSelection(
                    selectedAccountId(domainAccount));
            case FIXED_ASSET -> new ReportDomainFilter.FixedAssetSelection(
                    selectedId(domainSubject),
                    selectedAccountId(domainAccount),
                    (FixedAsset.Status) domainStatus.getValue());
            case INVENTORY -> new ReportDomainFilter.InventorySelection(
                    selectedId(domainSubject),
                    selectedAccountId(domainAccount),
                    (InventoryItem.Status) domainStatus.getValue());
        };
        return new ReportRequest(
                definition,
                startDate.getValue(),
                endDate.getValue(),
                selectedFund,
                rowLimit.getValue(),
                selectedDomain);
    }

    private static Long selectedId(
            ComboBox<AssetInventoryReportQueryService.FilterOption> combo)
    {
        AssetInventoryReportQueryService.FilterOption selected = combo.getValue();
        return selected == null ? null : selected.id();
    }

    private static Long selectedAccountId(ComboBox<ReportAccountOption> combo)
    {
        ReportAccountOption selected = combo.getValue();
        return selected == null ? null : selected.id();
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
            previewHost.setCenter(new SemanticReportFxRenderer(companyFormat).render(
                    result.semanticTemplate(),
                    result.semanticValues()));
        }
        else if (result.tabular())
        {
            Node tablePreview = new FormattedReportFxRenderer(companyFormat).render(
                    result.tableModel());
            CompanyTableStateBinder.apply(
                    tablePreview,
                    AppPanelId.REPORT_LIBRARY,
                    preferencesService,
                    companyCode);
            previewHost.setCenter(tablePreview);
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

    LocalDate startDateForTests()
    {
        return startDate.getValue();
    }

    LocalDate endDateForTests()
    {
        return endDate.getValue();
    }

    boolean followsActivePeriodForTests()
    {
        return followActivePeriodDefaults;
    }

    void setReportDatesForTests(LocalDate start, LocalDate end)
    {
        startDate.setValue(start);
        endDate.setValue(end);
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
                        result.csv(),
                        result.tableModel()));
            }
        }
    }

    private double loadDividerPosition()
    {
        return loadDividerPosition(STATE_DIVIDER, 0.28, 0.15, 0.60);
    }

    private double loadDividerPosition(
            String stateKey,
            double fallback,
            double minimum,
            double maximum)
    {
        String value = preferencesService.loadState(companyCode, "reportLibrary.")
                .get(stateKey);
        try
        {
            double position = value == null ? fallback : Double.parseDouble(value);
            return Math.max(minimum, Math.min(maximum, position));
        }
        catch (NumberFormatException ex)
        {
            return fallback;
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

    private record ReportParameterCatalog(
            AssetInventoryReportQueryService.FilterCatalog domainFilters,
            List<ReportAccountOption> accounts)
    {
        private ReportParameterCatalog
        {
            accounts = List.copyOf(accounts);
        }
    }

    private record ReportAccountOption(Long id, String label)
    {
        private ReportAccountOption
        {
            if (id != null && id <= 0)
            {
                throw new IllegalArgumentException("A selected account ID must be positive.");
            }
            if (label == null || label.isBlank())
            {
                throw new IllegalArgumentException("An account option label is required.");
            }
            label = label.strip();
        }

        private static ReportAccountOption from(
                AssetInventoryReportQueryService.FilterOption option)
        {
            return new ReportAccountOption(option.id(), option.label());
        }
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
