package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.nonprofitbookkeeping.service.ApplicationPermission;
import org.nonprofitbookkeeping.service.CoreAccountingPacketService;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Report Library action with captured dates and service, off-thread snapshot/export and live permission gating. */
final class CorePacketExportAction
{
    private final Button button = new Button("Export Core Packet (all funds/events)");
    private CompletableFuture<CoreAccountingPacketService.Result> completion;

    CorePacketExportAction(Supplier<CoreAccountingPacketService> service, Supplier<LocalDate> start,
            Supplier<LocalDate> end, Consumer<String> status, Supplier<Window> window)
    {
        this(service, start, end, () -> choose(window.get()), status);
    }

    // Distinct parameter order keeps the injected destination seam separate from the production chooser.
    CorePacketExportAction(Supplier<CoreAccountingPacketService> service, Supplier<LocalDate> start,
            Supplier<LocalDate> end, Supplier<Path> destination, Consumer<String> status)
    {
        button.setId("reportLibraryCorePacketExport");
        button.setTooltip(new Tooltip("Exports core statements only. Operational sections are omitted and the packet is marked incomplete. Uses the selected dates, all funds and all events."));
        UiPermissionGate.gate(button, ApplicationPermission.EXPORT, "Export core accounting packet");
        button.setOnAction(event ->
        {
            if (completion != null && !completion.isDone())
            {
                return;
            }
            Path target = destination.get();
            if (target == null)
            {
                status.accept("Core packet export cancelled.");
                return;
            }
            LocalDate from = start.get();
            LocalDate through = end.get();
            CoreAccountingPacketService captured = service.get();
            status.accept("Exporting incomplete core packet for all funds/events…");
            completion = new CompletableFuture<>();
            button.setDisable(true);
            Thread worker = new Thread(() ->
            {
                try
                {
                    var result = captured.export(from, through, target);
                    Platform.runLater(() ->
                    {
                        status.accept("Exported INCOMPLETE core packet to " + result.destination()
                                + ". Operational sections remain omitted; see manifest.");
                        button.setDisable(false);
                        completion.complete(result);
                    });
                }
                catch (Exception ex)
                {
                    Platform.runLater(() ->
                    {
                        status.accept("Could not export core packet: " + UiErrors.safeMessage(ex));
                        button.setDisable(false);
                        completion.completeExceptionally(ex);
                    });
                }
            }, "core-accounting-packet-export");
            worker.setDaemon(true);
            worker.start();
        });
    }

    Button button() { return button; }
    CompletableFuture<CoreAccountingPacketService.Result> completion() { return completion; }

    private static Path choose(Window window)
    {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Incomplete Core Accounting Packet — Choose a New File");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Core packet ZIP", "*.zip"));
        chooser.setInitialFileName("core-accounting-packet.zip");
        var file = chooser.showSaveDialog(window);
        return file == null ? null : file.toPath();
    }
}
