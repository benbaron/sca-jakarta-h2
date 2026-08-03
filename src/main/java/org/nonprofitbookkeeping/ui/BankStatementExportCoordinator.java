package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.nonprofitbookkeeping.interchange.InterchangeMessageSeverity;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.interchange.bank.BankStatementCsvExportService;
import org.nonprofitbookkeeping.interchange.bank.BankStatementExportRequest;
import org.nonprofitbookkeeping.interchange.bank.BankStatementExportResult;
import org.nonprofitbookkeeping.interchange.bank.BankStatementOfxExportRequest;
import org.nonprofitbookkeeping.interchange.bank.BankStatementOfxExportService;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Coordinates exact-scope production bank-statement export without blocking JavaFX. */
final class BankStatementExportCoordinator implements BankStatementExportActions
{
    private final Supplier<BankStatementCsvExportService> csvExportService;
    private final Supplier<BankStatementOfxExportService> ofxExportService;
    private final WorkspaceContext context;
    private final Supplier<Window> ownerWindow;
    private final BankStatementExportDialogs dialogs;
    private final Executor executor;
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper status = new ReadOnlyStringWrapper(
            "Choose one configured account, date range, and statement format to export durable review rows.");

    BankStatementExportCoordinator(
            Supplier<BankStatementCsvExportService> csvExportService,
            Supplier<BankStatementOfxExportService> ofxExportService,
            WorkspaceContext context,
            Supplier<Window> ownerWindow)
    {
        this(csvExportService, ofxExportService, context, ownerWindow,
                new JavaFxBankStatementExportDialogs(), BankStatementExportCoordinator::startDaemonThread);
    }

    BankStatementExportCoordinator(
            Supplier<BankStatementCsvExportService> csvExportService,
            Supplier<BankStatementOfxExportService> ofxExportService,
            WorkspaceContext context,
            Supplier<Window> ownerWindow,
            BankStatementExportDialogs dialogs,
            Executor executor)
    {
        this.csvExportService = Objects.requireNonNull(csvExportService, "csvExportService");
        this.ofxExportService = Objects.requireNonNull(ofxExportService, "ofxExportService");
        this.context = Objects.requireNonNull(context, "context");
        this.ownerWindow = Objects.requireNonNull(ownerWindow, "ownerWindow");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public ReadOnlyBooleanProperty busyProperty()
    {
        return busy.getReadOnlyProperty();
    }

    @Override
    public ReadOnlyStringProperty statusProperty()
    {
        return status.getReadOnlyProperty();
    }

    @Override
    public void requestExport(
            long bankAccountId,
            LocalDate fromDate,
            LocalDate throughDate,
            BankStatementExportFormat format)
    {
        if (busy.get())
        {
            return;
        }
        Objects.requireNonNull(fromDate, "fromDate");
        Objects.requireNonNull(throughDate, "throughDate");
        Objects.requireNonNull(format, "format");
        if (bankAccountId < 1L)
        {
            status.set("Select a configured bank account before exporting.");
            return;
        }
        if (fromDate.isAfter(throughDate))
        {
            status.set("Export start date must be on or before the end date.");
            return;
        }

        String companyCode = context.activeCompanyCode();
        if (!context.databaseAvailable() || companyCode == null || companyCode.isBlank())
        {
            status.set("Bank-statement export is unavailable until a database and active company are selected.");
            return;
        }
        String fixedCompanyCode = companyCode.strip();
        Window owner = ownerWindow.get();
        Optional<Path> selected = dialogs.chooseDestination(
                owner, fixedCompanyCode, fromDate, throughDate, format);
        if (selected.isEmpty())
        {
            status.set("Bank-statement export cancelled; no file was written.");
            return;
        }

        Path destination = normalizeDestination(selected.get(), format);
        boolean overwrite = Files.exists(destination, LinkOption.NOFOLLOW_LINKS);
        if (overwrite && !dialogs.confirmOverwrite(owner, destination, format))
        {
            status.set("Bank-statement export cancelled; the existing file was not changed.");
            return;
        }

        BankStatementExportOperation operation;
        try
        {
            operation = operation(
                    fixedCompanyCode, bankAccountId, fromDate, throughDate,
                    destination, overwrite, format);
        }
        catch (RuntimeException ex)
        {
            String message = UiErrors.safeMessage(ex);
            status.set("Bank-statement export could not start: " + message);
            dialogs.showFailure(owner, message);
            return;
        }
        runAsync(owner, fixedCompanyCode, bankAccountId, format, destination, operation);
    }

    private BankStatementExportOperation operation(
            String companyCode,
            long bankAccountId,
            LocalDate fromDate,
            LocalDate throughDate,
            Path destination,
            boolean overwrite,
            BankStatementExportFormat format)
    {
        if (format == BankStatementExportFormat.NORMALIZED_CSV)
        {
            BankStatementCsvExportService service = Objects.requireNonNull(
                    csvExportService.get(), "csvExportService returned null");
            BankStatementExportRequest request = new BankStatementExportRequest(
                    companyCode, bankAccountId, fromDate, throughDate, destination, overwrite);
            return () -> service.export(request);
        }

        BankStatementOfxExportService service = Objects.requireNonNull(
                ofxExportService.get(), "ofxExportService returned null");
        BankStatementOfxExportRequest.Profile profile =
                format == BankStatementExportFormat.OFX_2_XML
                        ? BankStatementOfxExportRequest.Profile.OFX_2_XML
                        : BankStatementOfxExportRequest.Profile.QFX_2_XML;
        BankStatementOfxExportRequest request = new BankStatementOfxExportRequest(
                companyCode, bankAccountId, fromDate, throughDate, destination, overwrite, profile);
        return () -> service.export(request);
    }

    private void runAsync(
            Window owner,
            String companyCode,
            long bankAccountId,
            BankStatementExportFormat format,
            Path destination,
            BankStatementExportOperation operation)
    {
        busy.set(true);
        status.set("Exporting " + format.displayName() + " for " + companyCode
                + " configured account " + bankAccountId + " to " + destination + "...");
        Task<BankStatementExportResult> task = new Task<>()
        {
            @Override
            protected BankStatementExportResult call()
            {
                return operation.export();
            }
        };
        task.setOnSucceeded(event ->
        {
            busy.set(false);
            BankStatementExportResult result = task.getValue();
            status.set(completionStatus(result, format));
            dialogs.showCompleted(owner, result, format, resultDetails(result, format));
        });
        task.setOnFailed(event ->
        {
            busy.set(false);
            String message = UiErrors.safeMessage(task.getException());
            status.set("Bank-statement export failed: " + message);
            dialogs.showFailure(owner, message);
        });
        executor.execute(task);
    }

    static Path normalizeDestination(Path selected, BankStatementExportFormat format)
    {
        Path absolute = Objects.requireNonNull(selected, "selected").toAbsolutePath().normalize();
        String value = absolute.toString();
        return value.toLowerCase(Locale.ROOT).endsWith(format.extension())
                ? absolute
                : Path.of(value + format.extension());
    }

    static String defaultFilename(
            String companyCode,
            LocalDate fromDate,
            LocalDate throughDate,
            BankStatementExportFormat format)
    {
        String safeCompany = Objects.requireNonNull(companyCode, "companyCode")
                .strip()
                .replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeCompany.isBlank())
        {
            safeCompany = "company";
        }
        return safeCompany + "-bank-statement-" + fromDate + "-to-" + throughDate + format.extension();
    }

    private static String completionStatus(
            BankStatementExportResult result,
            BankStatementExportFormat format)
    {
        long warnings = result.messages().stream()
                .filter(message -> message.severity() == InterchangeMessageSeverity.WARNING)
                .count();
        return format.displayName() + " export completed: " + result.rowCount() + " row(s), "
                + result.byteCount() + " byte(s), " + warnings + " warning(s), SHA-256 "
                + result.sha256() + ", destination " + result.destination() + ".";
    }

    private static String resultDetails(
            BankStatementExportResult result,
            BankStatementExportFormat format)
    {
        String messages = result.messages().isEmpty()
                ? "  (none)"
                : result.messages().stream()
                        .map(BankStatementExportCoordinator::messageLine)
                        .collect(Collectors.joining("\n  - ", "  - ", ""));
        return "Format: " + format.displayName()
                + "\nCompany: " + result.companyCode()
                + "\nConfigured-account portable ID: " + result.bankAccountExternalId()
                + "\nDate range: " + result.fromDate() + " through " + result.throughDate()
                + "\nRows: " + result.rowCount()
                + "\nBytes: " + result.byteCount()
                + "\nSHA-256: " + result.sha256()
                + "\nDestination: " + result.destination()
                + "\nMessages:\n" + messages;
    }

    private static String messageLine(InterchangeValidationMessage message)
    {
        return message.severity() + " " + message.code()
                + (message.path().isBlank() ? "" : " at " + message.path())
                + ": " + message.message();
    }

    private static void startDaemonThread(Runnable command)
    {
        Thread thread = new Thread(command, "npbk-bank-statement-export");
        thread.setDaemon(true);
        thread.start();
    }
}

interface BankStatementExportActions
{
    ReadOnlyBooleanProperty busyProperty();

    ReadOnlyStringProperty statusProperty();

    void requestExport(
            long bankAccountId,
            LocalDate fromDate,
            LocalDate throughDate,
            BankStatementExportFormat format);

    static BankStatementExportActions unavailable()
    {
        ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(false);
        ReadOnlyStringWrapper status = new ReadOnlyStringWrapper(
                "Bank-statement export is available in the production workspace.");
        return new BankStatementExportActions()
        {
            @Override
            public ReadOnlyBooleanProperty busyProperty()
            {
                return busy.getReadOnlyProperty();
            }

            @Override
            public ReadOnlyStringProperty statusProperty()
            {
                return status.getReadOnlyProperty();
            }

            @Override
            public void requestExport(
                    long bankAccountId,
                    LocalDate fromDate,
                    LocalDate throughDate,
                    BankStatementExportFormat format)
            {
                status.set("Bank-statement export is unavailable outside the production workspace.");
            }
        };
    }
}

enum BankStatementExportFormat
{
    NORMALIZED_CSV("Normalized Bank CSV 1.0", ".csv", "Normalized Bank CSV", "*.csv"),
    OFX_2_XML("OFX 2.x XML", ".ofx", "OFX 2.x", "*.ofx"),
    QFX_2_XML("QFX 2.x XML", ".qfx", "QFX 2.x", "*.qfx");

    private final String displayName;
    private final String extension;
    private final String filterName;
    private final String filterPattern;

    BankStatementExportFormat(
            String displayName,
            String extension,
            String filterName,
            String filterPattern)
    {
        this.displayName = displayName;
        this.extension = extension;
        this.filterName = filterName;
        this.filterPattern = filterPattern;
    }

    String displayName() { return displayName; }
    String extension() { return extension; }
    String filterName() { return filterName; }
    String filterPattern() { return filterPattern; }

    @Override
    public String toString()
    {
        return displayName;
    }
}

@FunctionalInterface
interface BankStatementExportOperation
{
    BankStatementExportResult export();
}

interface BankStatementExportDialogs
{
    Optional<Path> chooseDestination(
            Window owner,
            String companyCode,
            LocalDate fromDate,
            LocalDate throughDate,
            BankStatementExportFormat format);

    boolean confirmOverwrite(Window owner, Path destination, BankStatementExportFormat format);

    void showCompleted(
            Window owner,
            BankStatementExportResult result,
            BankStatementExportFormat format,
            String details);

    void showFailure(Window owner, String message);
}

final class JavaFxBankStatementExportDialogs implements BankStatementExportDialogs
{
    @Override
    public Optional<Path> chooseDestination(
            Window owner,
            String companyCode,
            LocalDate fromDate,
            LocalDate throughDate,
            BankStatementExportFormat format)
    {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Durable Bank Statement Activity");
        chooser.setInitialFileName(BankStatementExportCoordinator.defaultFilename(
                companyCode, fromDate, throughDate, format));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(format.filterName(), format.filterPattern()));
        File selected = chooser.showSaveDialog(owner);
        return selected == null ? Optional.empty() : Optional.of(selected.toPath());
    }

    @Override
    public boolean confirmOverwrite(
            Window owner,
            Path destination,
            BankStatementExportFormat format)
    {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Replace Existing Bank Statement File");
        alert.setHeaderText("Replace the existing " + format.displayName() + " file?");
        alert.setContentText("Existing file:\n" + destination
                + "\n\nThe file is replaced only after the governed export is fully serialized and validated.");
        initOwner(alert, owner);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    @Override
    public void showCompleted(
            Window owner,
            BankStatementExportResult result,
            BankStatementExportFormat format,
            String details)
    {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Bank Statement Export Complete");
        alert.setHeaderText(format.displayName() + " export completed for " + result.companyCode() + ".");
        alert.setContentText(result.rowCount() + " durable review row(s), " + result.byteCount()
                + " byte(s).\nDestination:\n" + result.destination());
        TextArea detailArea = new TextArea(details);
        detailArea.setEditable(false);
        detailArea.setWrapText(false);
        detailArea.setPrefColumnCount(82);
        detailArea.setPrefRowCount(18);
        alert.getDialogPane().setExpandableContent(detailArea);
        alert.getDialogPane().setExpanded(true);
        initOwner(alert, owner);
        alert.showAndWait();
    }

    @Override
    public void showFailure(Window owner, String message)
    {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Bank Statement Export Failed");
        alert.setHeaderText("No bank-statement export file was committed.");
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
