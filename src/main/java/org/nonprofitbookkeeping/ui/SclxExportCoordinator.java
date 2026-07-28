package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.interchange.sclx.SclxExportCounts;
import org.nonprofitbookkeeping.interchange.sclx.SclxExportRequest;
import org.nonprofitbookkeeping.interchange.sclx.SclxExportResult;
import org.nonprofitbookkeeping.interchange.sclx.SclxExportSection;
import org.nonprofitbookkeeping.interchange.sclx.SclxFileExportService;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Coordinates the production selected-company SCLX export interaction. */
final class SclxExportCoordinator implements SclxExportActions
{
    private final SclxExportOperationFactory exportOperationFactory;
    private final WorkspaceContext context;
    private final Supplier<Window> ownerWindow;
    private final SclxExportDialogs dialogs;
    private final Executor executor;
    private final Supplier<Instant> clock;

    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper available = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper status = new ReadOnlyStringWrapper(
            "Export the selected active company's currently supported business data to SCLX 1.3.");
    private final ReadOnlyObjectWrapper<SclxExportResult> lastResult = new ReadOnlyObjectWrapper<>();

    SclxExportCoordinator(
            BiFunction<String, Path, SclxFileExportService> exportServiceFactory,
            WorkspaceContext context,
            Supplier<Window> ownerWindow)
    {
        this(
                serviceOperationFactory(exportServiceFactory),
                context,
                ownerWindow,
                new JavaFxSclxExportDialogs(),
                SclxExportCoordinator::startDaemonThread,
                Instant::now);
    }

    SclxExportCoordinator(
            Function<SclxExportRequest, SclxExportResult> exportOperation,
            WorkspaceContext context,
            Supplier<Window> ownerWindow,
            SclxExportDialogs dialogs,
            Executor executor,
            Supplier<Instant> clock)
    {
        this(
                (companyCode, databasePath) -> Objects.requireNonNull(exportOperation, "exportOperation"),
                context,
                ownerWindow,
                dialogs,
                executor,
                clock);
    }

    SclxExportCoordinator(
            SclxExportOperationFactory exportOperationFactory,
            WorkspaceContext context,
            Supplier<Window> ownerWindow,
            SclxExportDialogs dialogs,
            Executor executor,
            Supplier<Instant> clock)
    {
        this.exportOperationFactory = Objects.requireNonNull(exportOperationFactory, "exportOperationFactory");
        this.context = Objects.requireNonNull(context, "context");
        this.ownerWindow = Objects.requireNonNull(ownerWindow, "ownerWindow");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");

        context.activeCompanyCodeProperty().addListener((observable, oldValue, newValue) -> refreshAvailability());
        context.activeDatabasePathProperty().addListener((observable, oldValue, newValue) -> refreshAvailability());
        context.databaseFailureProperty().addListener((observable, oldValue, newValue) -> refreshAvailability());
        refreshAvailability();
    }

    @Override
    public ReadOnlyBooleanProperty busyProperty()
    {
        return busy.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyBooleanProperty availableProperty()
    {
        return available.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyStringProperty statusProperty()
    {
        return status.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyObjectProperty<SclxExportResult> lastResultProperty()
    {
        return lastResult.getReadOnlyProperty();
    }

    @Override
    public void requestExport()
    {
        if (busy.get())
        {
            status.set("Another selected-company SCLX export is already running.");
            return;
        }
        if (!available.get())
        {
            status.set("Select an active company in an available database before exporting SCLX.");
            return;
        }

        Window owner = ownerWindow.get();
        if (owner == null)
        {
            status.set("The application window is not ready for selected-company SCLX export.");
            return;
        }

        String companyCode = context.activeCompanyCode().strip();
        Path databasePath = context.activeDatabasePath().toAbsolutePath().normalize();
        Optional<Path> selected = dialogs.chooseDestination(owner, companyCode);
        if (selected.isEmpty())
        {
            status.set("Selected-company SCLX export cancelled.");
            return;
        }

        Path destination = normalizeSclxPath(selected.get());
        boolean overwrite = Files.exists(destination, LinkOption.NOFOLLOW_LINKS);
        if (overwrite && !dialogs.confirmOverwrite(owner, destination))
        {
            status.set("Selected-company SCLX export cancelled; the existing file was not changed.");
            return;
        }

        Function<SclxExportRequest, SclxExportResult> operation;
        try
        {
            operation = Objects.requireNonNull(
                    exportOperationFactory.forScope(companyCode, databasePath),
                    "export operation factory returned null");
        }
        catch (RuntimeException ex)
        {
            String message = UiErrors.safeMessage(ex);
            status.set("Selected-company SCLX export could not start: " + message);
            dialogs.showFailure(owner, message);
            return;
        }

        SclxExportRequest request = new SclxExportRequest(
                destination,
                Objects.requireNonNull(clock.get(), "clock returned null"),
                overwrite);
        runAsync(owner, companyCode, request, operation);
    }

    private void runAsync(
            Window owner,
            String companyCode,
            SclxExportRequest request,
            Function<SclxExportRequest, SclxExportResult> operation)
    {
        busy.set(true);
        status.set("Exporting active company " + companyCode + " to "
                + request.destination() + "...");

        Task<SclxExportResult> task = new Task<>()
        {
            @Override
            protected SclxExportResult call()
            {
                return operation.apply(request);
            }
        };
        task.setOnSucceeded(event ->
        {
            busy.set(false);
            SclxExportResult result = task.getValue();
            lastResult.set(result);
            status.set("Selected-company SCLX export completed: " + result.destination());
            dialogs.showCompleted(owner, result, resultSummary(result));
        });
        task.setOnFailed(event ->
        {
            busy.set(false);
            Throwable failure = task.getException();
            String message = UiErrors.safeMessage(failure);
            status.set("Selected-company SCLX export failed: " + message);
            dialogs.showFailure(owner, message);
        });
        executor.execute(task);
    }

    private void refreshAvailability()
    {
        String companyCode = context.activeCompanyCode();
        available.set(
                context.databaseAvailable()
                        && context.activeDatabasePath() != null
                        && companyCode != null
                        && !companyCode.isBlank());
    }

    static Path normalizeSclxPath(Path selected)
    {
        Path absolute = Objects.requireNonNull(selected, "selected").toAbsolutePath().normalize();
        String value = absolute.toString();
        return value.toLowerCase(Locale.ROOT).endsWith(".sclx")
                ? absolute
                : Path.of(value + ".sclx");
    }

    static String defaultFilename(String companyCode)
    {
        String safe = Objects.requireNonNull(companyCode, "companyCode")
                .strip()
                .replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank())
        {
            safe = "active-company";
        }
        return safe + "-active-company.sclx";
    }

    static String resultSummary(SclxExportResult result)
    {
        Objects.requireNonNull(result, "result");
        SclxExportCounts counts = result.counts();
        String messages = formatLines(result.messages().stream()
                .map(SclxExportCoordinator::messageLabel)
                .toList());
        String deferred = formatLines(result.deferredSections().stream()
                .map(SclxExportCoordinator::sectionLabel)
                .toList());
        String excluded = formatLines(result.excludedSections().stream()
                .map(SclxExportCoordinator::sectionLabel)
                .toList());
        return "Active company: " + result.organizationCode()
                + "\nFormat: " + result.format() + " " + result.version()
                + "\nExported at: " + result.exportedAt()
                + "\nDestination: " + result.destination()
                + "\nBytes: " + result.byteCount()
                + "\nSHA-256: " + result.sha256()
                + "\n\nIncluded records"
                + "\n  Organizations: " + counts.organizations()
                + "\n  Accounts: " + counts.accounts()
                + "\n  Funds: " + counts.funds()
                + "\n  Activities: " + counts.activities()
                + "\n  Counterparties: " + counts.counterparties()
                + "\n  Merchants: " + counts.merchants()
                + "\n  Budgets: " + counts.budgets()
                + "\n  Budget lines: " + counts.budgetLines()
                + "\n  Transactions: " + counts.transactions()
                + "\n  Transaction lines: " + counts.transactionLines()
                + "\n  Supplemental details: " + counts.supplementalDetails()
                + "\n  Total entities: " + counts.totalEntities()
                + "\n\nWarnings: " + counts.warnings()
                + "\nValidation messages:" + messages
                + "\n\nDeferred governed sections: " + result.deferredSections().size() + deferred
                + "\n\nExplicitly excluded sections: " + result.excludedSections().size() + excluded;
    }

    private static SclxExportOperationFactory serviceOperationFactory(
            BiFunction<String, Path, SclxFileExportService> exportServiceFactory)
    {
        BiFunction<String, Path, SclxFileExportService> checkedFactory =
                Objects.requireNonNull(exportServiceFactory, "exportServiceFactory");
        return (companyCode, databasePath) ->
        {
            SclxFileExportService service = Objects.requireNonNull(
                    checkedFactory.apply(companyCode, databasePath),
                    "exportServiceFactory returned null");
            return service::export;
        };
    }

    private static String messageLabel(InterchangeValidationMessage message)
    {
        String path = message.path().isBlank() ? "" : " [" + message.path() + "]";
        return message.severity() + " " + message.code() + path + " — " + message.message();
    }

    private static String formatLines(java.util.List<String> lines)
    {
        if (lines.isEmpty())
        {
            return "\n  (none)";
        }
        return lines.stream().collect(Collectors.joining("\n  - ", "\n  - ", ""));
    }

    private static String sectionLabel(SclxExportSection section)
    {
        String path = section.outputPath() == null ? section.name() : section.outputPath();
        return path + " — " + section.description();
    }

    private static void startDaemonThread(Runnable command)
    {
        Thread thread = new Thread(command, "npbk-sclx-export");
        thread.setDaemon(true);
        thread.start();
    }
}

@FunctionalInterface
interface SclxExportOperationFactory
{
    Function<SclxExportRequest, SclxExportResult> forScope(String companyCode, Path databasePath);
}

interface SclxExportDialogs
{
    Optional<Path> chooseDestination(Window owner, String companyCode);

    boolean confirmOverwrite(Window owner, Path destination);

    void showCompleted(Window owner, SclxExportResult result, String details);

    void showFailure(Window owner, String message);
}

final class JavaFxSclxExportDialogs implements SclxExportDialogs
{
    @Override
    public Optional<Path> chooseDestination(Window owner, String companyCode)
    {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Active Company to SCLX");
        chooser.setInitialFileName(SclxExportCoordinator.defaultFilename(companyCode));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("SCLX Active Company Files", "*.sclx"));
        File selected = chooser.showSaveDialog(owner);
        return selected == null ? Optional.empty() : Optional.of(selected.toPath());
    }

    @Override
    public boolean confirmOverwrite(Window owner, Path destination)
    {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Replace Existing SCLX File");
        alert.setHeaderText("Replace the existing selected-company SCLX file?");
        alert.setContentText("Existing file:\n" + destination
                + "\n\nThe file is replaced only after the new export is fully validated and written.");
        initOwner(alert, owner);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    @Override
    public void showCompleted(Window owner, SclxExportResult result, String details)
    {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("SCLX Export Complete");
        alert.setHeaderText("Active company " + result.organizationCode() + " was exported to SCLX 1.3.");
        alert.setContentText("Destination:\n" + result.destination()
                + "\n\nWarnings: " + result.counts().warnings()
                + ". Deferred sections are listed in Details.");
        TextArea detailArea = new TextArea(details);
        detailArea.setEditable(false);
        detailArea.setWrapText(false);
        detailArea.setPrefColumnCount(78);
        detailArea.setPrefRowCount(24);
        alert.getDialogPane().setExpandableContent(detailArea);
        alert.getDialogPane().setExpanded(true);
        initOwner(alert, owner);
        alert.showAndWait();
    }

    @Override
    public void showFailure(Window owner, String message)
    {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("SCLX Export Failed");
        alert.setHeaderText("The active company was not exported.");
        alert.setContentText(message);
        initOwner(alert, owner);
        alert.showAndWait();
    }

    private static void initOwner(Alert alert, Window owner)
    {
        if (owner != null)
        {
            alert.initOwner(owner);
        }
    }
}
