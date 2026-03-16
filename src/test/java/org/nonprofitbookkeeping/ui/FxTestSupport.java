package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import org.junit.jupiter.api.Assumptions;

import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FxTestSupport component.
 */
final class FxTestSupport
{
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static volatile boolean available = false;
    private static volatile String unavailableReason = "JavaFX toolkit unavailable";

    private FxTestSupport()
    {
    }

    static void initToolkitOrSkip()
    {
        if (!INITIALIZED.compareAndSet(false, true))
        {
            Assumptions.assumeTrue(available, unavailableReason);
            return;
        }

        if (isHeadlessLinuxWithoutDisplay())
        {
            available = false;
            unavailableReason = "Skipping JavaFX tests: DISPLAY is unavailable in this environment.";
            Assumptions.assumeTrue(false, unavailableReason);
            return;
        }

        try
        {
            Platform.startup(() -> { });
            available = true;
        }
        catch (Throwable ex)
        {
            available = false;
            unavailableReason = "Skipping JavaFX tests: " + ex.getMessage();
            Assumptions.assumeTrue(false, unavailableReason);
        }
    }

    static <T> T onFx(Callable<T> callable)
    {
        initToolkitOrSkip();
        Assumptions.assumeTrue(available, unavailableReason);

        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try
            {
                future.complete(callable.call());
            }
            catch (Exception ex)
            {
                future.completeExceptionally(ex);
            }
        });
        return future.join();
    }

    private static boolean isHeadlessLinuxWithoutDisplay()
    {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("linux"))
        {
            return false;
        }
        String display = System.getenv("DISPLAY");
        return display == null || display.isBlank();
    }
}
