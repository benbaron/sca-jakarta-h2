package org.nonprofitbookkeeping.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.nonprofitbookkeeping.interchange.InterchangeConfirmation;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.interchange.coa.ChartOfAccountsJsonImportService;
import org.nonprofitbookkeeping.interchange.coa.ChartOfAccountsJsonService;
import org.nonprofitbookkeeping.interchange.coa.CoaExportResult;
import org.nonprofitbookkeeping.interchange.coa.CoaImportMode;
import org.nonprofitbookkeeping.interchange.coa.CoaImportPreview;
import org.nonprofitbookkeeping.interchange.coa.CoaImportRequest;
import org.nonprofitbookkeeping.interchange.coa.CoaImportResult;
import org.nonprofitbookkeeping.interchange.coa.CoaPreviewItem;
import org.nonprofitbookkeeping.persistence.DatabaseLocationService;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Adds governed Chart of Accounts JSON operations around the existing H2-backed editor. */
public final class ChartOfAccountsInterchangePanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final ChartOfAccountsPanel delegate;
    private final Button importJson = new Button("Import JSON…");
    private final Button exportJson = new Button("Export JSON…");
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Label interchangeStatus = new Label("Chart of Accounts JSON is separate from SCLX and database backup.");
    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    public ChartOfAccountsInterchangePanel()
    {
        this(new ChartOfAccountsPanel());
    }

    ChartOfAccountsInterchangePanel(ChartOfAccountsPanel delegate)
    {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        importJson.setId("coaImportJsonButton");
        exportJson.setId("coaExportJsonButton");
        progress.setId("coaJsonProgress");
        progress.setMaxSize(18.0, 18.0);
        progress.visibleProperty().bind(busy);
        progress.managedProperty().bind(progress.visibleProperty());
        importJson.disableProperty().bind(busy);
        exportJson.disableProperty().bind(busy);
        importJson.setOnAction(event -> requestImport());
        exportJson.setOnAction(event -> requestExport());

        HBox toolbar = new HBox(8, importJson, exportJson, progress, interchangeStatus);
        toolbar.setPadding(new Insets(8));
        HBox.setHgrow(interchangeStatus, Priority.ALWAYS);
        root.setTop(new VBox(toolbar, new Separator()));
        root.setCenter(delegate.root());
    }

    private void requestExport()
    {
        Optional<Path> destination = chooseExportFile();
        if (destination.isEmpty())
        {
            return;
        }
        Path target = normalizeJsonPath(destination.get());
        if (Files.exists(target))
        {
            showError(
                    "Chart of Accounts JSON not exported",
                    "A file already exists at " + target.toAbsolutePath()
                            + ". Choose a new file name; existing exports are not overwritten silently.");
            return;
        }

        setBusy(true, "Exporting active Chart of Accounts to " + target.toAbsolutePath() + "…");
        UiAsync.run(
                "coa-json-export",
                () -> withJpa(jpa -> new ChartOfAccountsJsonService(jpa, this::activeCompanyCode)
                        .exportActiveChart(target)),
                this::exportCompleted,
                error -> operationFailed("Chart of Accounts JSON export failed", error));
    }

    private void exportCompleted(CoaExportResult result)
    {
        setBusy(false, "Exported " + result.accountCount() + " account(s) to "
                + result.destination() + "; SHA-256 " + result.sha256() + ".");
        showInformation(
                "Chart of Accounts JSON exported",
                "Destination: " + result.destination()
                        + "\nBytes: " + result.byteCount()
                        + "\nAccounts: " + result.accountCount()
                        + "\nSHA-256: " + result.sha256());
    }

    private void requestImport()
    {
        if (delegate.hasUnsavedChanges() && !confirmDiscardUnsavedEditor())
        {
            return;
        }
        Optional<Path> source = chooseImportFile();
        if (source.isEmpty())
        {
            return;
        }
        Optional<ImportOptions> options = showImportOptions();
        if (options.isEmpty())
        {
            return;
        }

        CoaImportRequest request;
        try
        {
            request = new CoaImportRequest(
                    source.get(),
                    options.get().mode(),
                    options.get().chartName(),
                    options.get().chartVersion(),
                    parseMappings(options.get().mappingText()),
                    false);
        }
        catch (RuntimeException ex)
        {
            showError("Chart of Accounts JSON options are invalid", UiErrors.safeMessage(ex));
            return;
        }

        setBusy(true, "Previewing Chart of Accounts JSON from " + source.get().toAbsolutePath() + "…");
        UiAsync.run(
                "coa-json-preview",
                () -> withJpa(jpa -> new ChartOfAccountsJsonService(jpa, this::activeCompanyCode).preview(request)),
                this::previewCompleted,
                error -> operationFailed("Chart of Accounts JSON preview failed", error));
    }

    private void previewCompleted(CoaImportPreview preview)
    {
        setBusy(false, "Preview ready: " + preview.counts().total() + " account(s), "
                + preview.counts().errors() + " blocking error(s), "
                + preview.counts().warnings() + " warning(s).");
        Optional<Boolean> decision = showPreview(preview);
        if (decision.isEmpty() || !decision.get())
        {
            interchangeStatus.setText("Chart of Accounts JSON import cancelled after preview.");
            return;
        }

        CoaImportRequest request = preview.request();
        CoaImportRequest confirmedRequest = new CoaImportRequest(
                request.sourceFile(),
                request.mode(),
                request.targetChartName(),
                request.targetChartVersion(),
                request.codeMappings(),
                true);
        setBusy(true, "Revalidating and importing Chart of Accounts JSON into " + preview.targetLabel() + "…");
        UiAsync.run(
                "coa-json-import",
                () -> withJpa(jpa -> {
                    ChartOfAccountsJsonService previewService = new ChartOfAccountsJsonService(
                            jpa,
                            this::activeCompanyCode);
                    CoaImportPreview confirmed = previewService.preview(confirmedRequest);
                    return new ChartOfAccountsJsonImportService(jpa, this::activeCompanyCode).commit(confirmed);
                }),
                this::importCompleted,
                error -> operationFailed("Chart of Accounts JSON import failed", error));
    }

    private void importCompleted(CoaImportResult result)
    {
        if (!result.committed())
        {
            setBusy(false, "Chart of Accounts JSON import rolled back; the active chart was not changed.");
            String message = result.messages().stream()
                    .filter(InterchangeValidationMessage::blocking)
                    .reduce((left, right) -> right)
                    .map(InterchangeValidationMessage::message)
                    .orElse("The import transaction rolled back.");
            showError("Chart of Accounts JSON import rolled back", message);
            return;
        }

        delegate.onPanelShown();
        setBusy(false, "Imported Chart of Accounts JSON: "
                + result.counts().created() + " created, "
                + result.counts().updated() + " updated, "
                + result.counts().identical() + " identical.");
        showInformation(
                "Chart of Accounts JSON imported",
                "Target: " + result.targetLabel()
                        + "\nCreated: " + result.counts().created()
                        + "\nUpdated: " + result.counts().updated()
                        + "\nIdentical: " + result.counts().identical()
                        + "\nSource SHA-256: " + result.sourceSha256());
    }

    private Optional<ImportOptions> showImportOptions()
    {
        Dialog<ImportOptions> dialog = new Dialog<>();
        dialog.setTitle("Chart of Accounts JSON Import Options");
        dialog.setHeaderText("Choose how source account codes target the active company.");
        initOwner(dialog);

        ComboBox<CoaImportMode> mode = new ComboBox<>();
        mode.setId("coaJsonImportMode");
        mode.getItems().setAll(CoaImportMode.values());
        mode.setValue(CoaImportMode.MERGE_BY_CODE);
        TextField chartName = new TextField("Imported Chart");
        chartName.setId("coaJsonTargetChartName");
        TextField chartVersion = new TextField("1");
        chartVersion.setId("coaJsonTargetChartVersion");
        TextArea mappings = new TextArea();
        mappings.setId("coaJsonCodeMappings");
        mappings.setPromptText("One mapping per line: sourceCode=targetCode");
        mappings.setPrefRowCount(6);
        mappings.setWrapText(false);

        Label mappingHelp = new Label(
                "MAP_CODES requires every source code to have one explicit sourceCode=targetCode line.");
        mappingHelp.setWrapText(true);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Import mode"), mode);
        grid.addRow(1, new Label("New chart name"), chartName);
        grid.addRow(2, new Label("New chart version"), chartVersion);
        grid.add(new Label("Code mappings"), 0, 3);
        grid.add(mappings, 1, 3);
        grid.add(mappingHelp, 1, 4);
        grid.setPadding(new Insets(8));

        Runnable visibility = () -> {
            boolean newChart = mode.getValue() == CoaImportMode.CREATE_NEW_CHART;
            chartName.setDisable(!newChart);
            chartVersion.setDisable(!newChart);
            boolean mapped = mode.getValue() == CoaImportMode.MAP_CODES;
            mappings.setDisable(!mapped);
            mappingHelp.setDisable(!mapped);
        };
        mode.valueProperty().addListener((observable, oldValue, newValue) -> visibility.run());
        visibility.run();

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new ImportOptions(
                        mode.getValue(),
                        chartName.getText(),
                        chartVersion.getText(),
                        mappings.getText())
                : null);
        return dialog.showAndWait();
    }

    private Optional<Boolean> showPreview(CoaImportPreview preview)
    {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Chart of Accounts JSON Preview");
        dialog.setHeaderText(preview.hasBlockingErrors()
                ? "Import is blocked until validation errors are corrected."
                : "Review every proposed account action before importing.");
        initOwner(dialog);

        Label source = new Label("Source: " + preview.sourceFile());
        Label hash = new Label("SHA-256: " + preview.sourceSha256());
        Label target = new Label("Target: " + preview.targetLabel());
        Label counts = new Label(
                "Accounts: " + preview.counts().total()
                        + " | create " + preview.counts().created()
                        + " | update " + preview.counts().updated()
                        + " | identical " + preview.counts().identical()
                        + " | blocked " + preview.counts().skipped());

        TableView<CoaPreviewItem> table = new TableView<>();
        table.setId("coaJsonPreviewTable");
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        TableColumn<CoaPreviewItem, String> sourceCode = new TableColumn<>("Source Code");
        sourceCode.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().account().sourceCode()));
        TableColumn<CoaPreviewItem, String> targetCode = new TableColumn<>("Target Code");
        targetCode.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().targetCode()));
        TableColumn<CoaPreviewItem, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().account().name()));
        TableColumn<CoaPreviewItem, String> action = new TableColumn<>("Action");
        action.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().disposition().name()));
        table.getColumns().setAll(sourceCode, targetCode, name, action);
        table.getItems().setAll(preview.items());
        table.setPrefHeight(300.0);

        ListView<String> messages = new ListView<>();
        messages.setId("coaJsonValidationMessages");
        messages.getItems().setAll(preview.messages().stream()
                .map(message -> message.severity() + " " + message.code() + " "
                        + message.path() + " — " + message.message())
                .toList());
        messages.setPrefHeight(150.0);

        CheckBox confirmation = new CheckBox(confirmationText(preview.confirmations()));
        confirmation.setId("coaJsonConfirmation");
        confirmation.setWrapText(true);
        confirmation.setVisible(!preview.confirmations().isEmpty());
        confirmation.setManaged(confirmation.isVisible());

        VBox content = new VBox(
                6,
                source,
                hash,
                target,
                counts,
                new Label("Proposed account actions"),
                table,
                new Label("Validation messages"),
                messages,
                confirmation);
        content.setPadding(new Insets(8));
        VBox.setVgrow(table, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);

        ButtonType importButton = new ButtonType("Import", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(importButton, ButtonType.CANCEL);
        Node importNode = dialog.getDialogPane().lookupButton(importButton);
        importNode.setDisable(preview.hasBlockingErrors()
                || (!preview.confirmations().isEmpty() && !confirmation.isSelected()));
        confirmation.selectedProperty().addListener((observable, oldValue, selected) -> importNode.setDisable(
                preview.hasBlockingErrors() || (!preview.confirmations().isEmpty() && !selected)));
        dialog.setResultConverter(button -> button == importButton);
        return dialog.showAndWait();
    }

    private static String confirmationText(List<InterchangeConfirmation> confirmations)
    {
        if (confirmations.isEmpty())
        {
            return "No additional confirmation is required.";
        }
        return confirmations.stream()
                .map(InterchangeConfirmation::label)
                .reduce((left, right) -> left + " " + right)
                .orElse("Confirm import.");
    }

    private Optional<Path> chooseImportFile()
    {
        Window owner = ownerWindow();
        if (owner == null)
        {
            interchangeStatus.setText("Chart of Accounts JSON import is unavailable until the window is shown.");
            return Optional.empty();
        }
        FileChooser chooser = jsonFileChooser("Import Chart of Accounts JSON");
        File selected = chooser.showOpenDialog(owner);
        return selected == null ? Optional.empty() : Optional.of(selected.toPath().toAbsolutePath().normalize());
    }

    private Optional<Path> chooseExportFile()
    {
        Window owner = ownerWindow();
        if (owner == null)
        {
            interchangeStatus.setText("Chart of Accounts JSON export is unavailable until the window is shown.");
            return Optional.empty();
        }
        FileChooser chooser = jsonFileChooser("Export Chart of Accounts JSON");
        chooser.setInitialFileName("chart-of-accounts.json");
        File selected = chooser.showSaveDialog(owner);
        return selected == null ? Optional.empty() : Optional.of(selected.toPath().toAbsolutePath().normalize());
    }

    private static FileChooser jsonFileChooser(String title)
    {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Chart of Accounts JSON", "*.json"));
        return chooser;
    }

    private static Path normalizeJsonPath(Path path)
    {
        String raw = path.toString();
        return raw.toLowerCase().endsWith(".json") ? path : Path.of(raw + ".json");
    }

    private boolean confirmDiscardUnsavedEditor()
    {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Import Chart of Accounts JSON");
        alert.setHeaderText("Discard unsaved account edits before import?");
        alert.setContentText(
                "Import refreshes the Chart of Accounts editor after a successful transaction. Save the current account first or choose Cancel.");
        initOwner(alert);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private static Map<String, String> parseMappings(String text)
    {
        Map<String, String> mappings = new LinkedHashMap<>();
        if (text == null || text.isBlank())
        {
            return Map.of();
        }
        String[] lines = text.split("\\R", -1);
        for (int index = 0; index < lines.length; index++)
        {
            String line = lines[index].trim();
            if (line.isEmpty())
            {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0 || separator == line.length() - 1)
            {
                throw new IllegalArgumentException(
                        "Mapping line " + (index + 1) + " must be sourceCode=targetCode.");
            }
            String source = line.substring(0, separator).trim();
            String target = line.substring(separator + 1).trim();
            if (mappings.putIfAbsent(source, target) != null)
            {
                throw new IllegalArgumentException("Source code is mapped more than once: " + source + ".");
            }
        }
        return Map.copyOf(mappings);
    }

    private <T> T withJpa(Function<Jpa, T> operation)
    {
        Path database = DatabaseLocationService.resolveDatabasePath(
                MainWindow.sharedSessionState().databaseSelection().activeDatabasePath());
        try (Jpa jpa = new Jpa(database))
        {
            return operation.apply(jpa);
        }
    }

    private String activeCompanyCode()
    {
        String code = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
        return code == null || code.isBlank() ? "DEFAULT" : code.trim();
    }

    private void operationFailed(String title, Throwable error)
    {
        setBusy(false, title + ": " + UiErrors.safeMessage(error));
        showError(title, UiErrors.safeMessage(error));
    }

    private void setBusy(boolean value, String message)
    {
        busy.set(value);
        interchangeStatus.setText(message);
    }

    private void showError(String title, String message)
    {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        initOwner(alert);
        alert.showAndWait();
    }

    private void showInformation(String title, String message)
    {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        initOwner(alert);
        alert.showAndWait();
    }

    private Window ownerWindow()
    {
        return root.getScene() == null ? null : root.getScene().getWindow();
    }

    private void initOwner(Dialog<?> dialog)
    {
        Window owner = ownerWindow();
        if (owner != null)
        {
            dialog.initOwner(owner);
        }
    }

    private void initOwner(Alert alert)
    {
        Window owner = ownerWindow();
        if (owner != null)
        {
            alert.initOwner(owner);
        }
    }

    @Override
    public String title()
    {
        return delegate.title();
    }

    @Override
    public Node root()
    {
        return root;
    }

    @Override
    public java.util.Set<AppCommand> commandCapabilities()
    {
        return delegate.commandCapabilities();
    }

    @Override
    public RunCommandResult executeCommand(AppCommand command)
    {
        return delegate.executeCommand(command);
    }

    @Override
    public void onNew()
    {
        delegate.onNew();
    }

    @Override
    public void onSave()
    {
        delegate.onSave();
    }

    @Override
    public boolean hasUnsavedChanges()
    {
        return delegate.hasUnsavedChanges();
    }

    @Override
    public void onPanelShown()
    {
        delegate.onPanelShown();
    }

    Button importJsonButtonForTests()
    {
        return importJson;
    }

    Button exportJsonButtonForTests()
    {
        return exportJson;
    }

    Label interchangeStatusForTests()
    {
        return interchangeStatus;
    }

    static Map<String, String> parseMappingsForTests(String text)
    {
        return parseMappings(text);
    }

    private record ImportOptions(
            CoaImportMode mode,
            String chartName,
            String chartVersion,
            String mappingText)
    {
    }
}
