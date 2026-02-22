package org.nonprofitbookkeeping.ui;

import javafx.concurrent.Task;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shared async helper for JavaFX panels.
 */
public final class UiAsync
{
    private UiAsync() {}

    public static <T> void run(String threadName,
                               Supplier<T> supplier,
                               Consumer<T> onSuccess,
                               Consumer<Throwable> onFailure)
    {
        Task<T> task = new Task<>()
        {
            @Override
            protected T call()
            {
                return supplier.get();
            }
        };

        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> onFailure.accept(task.getException()));

        Thread t = new Thread(task, threadName);
        t.setDaemon(true);
        t.start();
    }
}
