package org.nonprofitbookkeeping.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.nonprofitbookkeeping.interchange.coa.*;
import org.nonprofitbookkeeping.persistence.DatabaseLocationService;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.ApplicationPermission;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Chart maintenance and its inline JSON proposal review share one workspace tab. */
public final class ChartOfAccountsInterchangePanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final ChartOfAccountsPanel delegate;
    private final Button importJson = new Button("Import JSON…");
    private final Button exportJson = new Button("Export JSON…");
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Label interchangeStatus = new Label("Chart of Accounts JSON is separate from SCLX and database backup.");
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final CoaJsonReviewPane review;
    private final BiFunction<FileChooser, Window, File> chooser;
    private boolean reviewing;
    private Runnable capabilitiesChanged = () ->
    {
    };

    public ChartOfAccountsInterchangePanel()
    {
        this(new ChartOfAccountsPanel());
    }
    ChartOfAccountsInterchangePanel(ChartOfAccountsPanel delegate)
    {
        this(delegate,(fileChooser,owner) -> fileChooser.showOpenDialog(owner));
    }
    ChartOfAccountsInterchangePanel(ChartOfAccountsPanel delegate, BiFunction<FileChooser,Window,File> chooser)
    {
        this.delegate = Objects.requireNonNull(delegate);
        this.chooser = chooser;
        review = new CoaJsonReviewPane(new CoaJsonReviewPane.Operations()
        {
            @Override
            public CoaImportPreview preview(CoaImportRequest request, CoaJsonReviewPane.Scope scope)
            {
                return scoped(scope,jpa -> new ChartOfAccountsJsonService(jpa,scope::company).preview(request));
            }
            @Override
            public String sourceText(CoaImportPreview preview, CoaJsonReviewPane.Scope scope)
            {
                return scoped(scope,jpa -> new ChartOfAccountsJsonService(jpa,scope::company).sourceText(preview));
            }
            @Override
            public CoaImportResult commit(CoaImportPreview approved, CoaJsonReviewPane.Scope scope)
            {
                if (!scope.currentScope()) throw new IllegalStateException("Return to the original company/database before importing.");
                return UiServiceRegistry.coaJsonImport().commit(approved);
            }
        },this::chooseImportFile,this::showAccounts,CompanyUiFormat.activeCompany());
        review.setCommandCapabilitiesChangedListener(this::changed);
        delegate.setCommandCapabilitiesChangedListener(this::changed);
        busy.addListener((o,a,b) -> changed());
        importJson.setId("coaImportJsonButton");
        exportJson.setId("coaExportJsonButton");
        UiPermissionGate.gate(importJson,ApplicationPermission.BOOKKEEPING_WRITE,"Review Chart JSON import");
        UiPermissionGate.gate(exportJson,ApplicationPermission.EXPORT,"Export Chart JSON");
        importJson.setOnAction(event -> requestImport());
        exportJson.setOnAction(event -> requestExport());
        interchangeStatus.setWrapText(true);
        progress.setMaxSize(18,18);
        FlowPane toolbar = new FlowPane(8,6,importJson,exportJson,progress,interchangeStatus);
        toolbar.setPadding(new Insets(8));
        toolbar.setMinWidth(0);
        root.setMinSize(0,0);
        interchangeStatus.prefWidthProperty().bind(root.widthProperty().subtract(24));
        root.setTop(new VBox(toolbar,new Separator()));
        root.setCenter(delegate.root());
        changed();
    }

    private <T> T scoped(CoaJsonReviewPane.Scope scope, Function<Jpa,T> operation)
    {
        try (Jpa jpa = new Jpa(DatabaseLocationService.resolveDatabasePath(scope.database())))
        {
            return operation.apply(jpa);
        }
    }

    private void requestImport()
    {
        if (busy.get() || review.busy() || !UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE)) return;
        reviewing = true;
        root.setCenter(review);
        changed();
        if (!review.hasUnsavedChanges()) review.chooseSource();
    }

    private void showAccounts()
    {
        if (busy.get() || review.busy()) return;
        reviewing = false;
        root.setCenter(delegate.root());
        delegate.onPanelShown();
        changed();
    }

    private void changed()
    {
        boolean working = busy.get() || review.busy();
        importJson.setDisable(working);
        exportJson.setDisable(working);
        progress.setVisible(working);
        progress.setManaged(working);
        delegate.root().setDisable(working);
        capabilitiesChanged.run();
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

    private Optional<Path> chooseImportFile()
    {
        Window owner = ownerWindow();
        if (owner == null)
        {
            interchangeStatus.setText("Chart of Accounts JSON import is unavailable until the window is shown.");
            return Optional.empty();
        }
        FileChooser chooser = jsonFileChooser("Import Chart of Accounts JSON");
        File selected = this.chooser.apply(chooser,owner);
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

    static Map<String, String> parseMappings(String text)
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

    private AppPanel active()
    {
        return reviewing ? review : delegate;
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
    public Set<AppCommand> commandCapabilities()
    {
        return busy.get() ? Set.of() : active().commandCapabilities();
    }

    @Override
    public Optional<ApplicationPermission> requiredPermission(AppCommand command)
    {
        return active().requiredPermission(command);
    }

    @Override
    public String commandLabel(AppCommand command)
    {
        return active().commandLabel(command);
    }

    @Override
    public String saveUnavailableReason()
    {
        return active().saveUnavailableReason();
    }

    @Override
    public RunCommandResult executeCommand(AppCommand command)
    {
        return commandCapabilities().contains(command) ? active().executeCommand(command)
                : new RunCommandResult(false,GlobalCommandRegistry.label(command) + " is not available in "
                        + title() + ". " + saveUnavailableReason());
    }

    @Override
    public void onNew()
    {
        active().onNew();
    }

    @Override
    public void onSave()
    {
        active().onSave();
    }

    @Override
    public String commandResultMessage(AppCommand command)
    {
        return active().commandResultMessage(command);
    }

    @Override
    public boolean hasUnsavedChanges()
    {
        return delegate.hasUnsavedChanges() || review.hasUnsavedChanges();
    }

    @Override
    public void onPanelShown()
    {
        active().onPanelShown();
    }

    @Override
    public void onPanelShownFrom(AppPanelId origin, String title)
    {
        delegate.onPanelShownFrom(origin,title);
        if (reviewing) review.onPanelShown();
    }

    @Override
    public void onImportAddSuggestion(ImportAddSuggestion suggestion)
    {
        if (busy.get() || review.busy()) return;
        showAccounts();
        delegate.onImportAddSuggestion(suggestion);
    }

    @Override
    public void setCommandCapabilitiesChangedListener(Runnable listener)
    {
        capabilitiesChanged = listener;
    }

    @Override
    public void setCommandResultListener(Consumer<RunCommandResult> listener)
    {
        delegate.setCommandResultListener(listener);
        review.setCommandResultListener(listener);
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

    static Map<String,String> parseMappingsForTests(String text)
    {
        return parseMappings(text);
    }
}
