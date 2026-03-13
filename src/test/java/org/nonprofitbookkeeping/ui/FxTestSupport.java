package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

final class FxTestSupport
{
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private FxTestSupport()
    {
    }

    static void initToolkit()
    {
        if (STARTED.compareAndSet(false, true))
        {
            Platform.startup(() -> { });
        }
    }

    static <T> T onFx(Callable<T> callable)
    {
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
}
