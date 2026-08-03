package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import org.nonprofitbookkeeping.interchange.InterchangeProgress;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Owns one transient JavaFX interchange task. Preview work may be cancelled;
 * durable commit work deliberately cannot be cancelled after it starts.
 */
final class InterchangeTaskController
{
    private final Executor executor;
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper cancelable = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyObjectWrapper<InterchangeProgress> progress =
            new ReadOnlyObjectWrapper<>(new InterchangeProgress("Idle", 0L, 1L, false, false));
    private Task<?> currentTask;

    InterchangeTaskController()
    {
        this(InterchangeTaskController::startDaemonThread);
    }

    InterchangeTaskController(Executor executor)
    {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    ReadOnlyBooleanProperty busyProperty()
    {
        return busy.getReadOnlyProperty();
    }

    ReadOnlyBooleanProperty cancelableProperty()
    {
        return cancelable.getReadOnlyProperty();
    }

    ReadOnlyObjectProperty<InterchangeProgress> progressProperty()
    {
        return progress.getReadOnlyProperty();
    }

    <T> boolean runPreview(
            String threadName,
            String stage,
            Supplier<T> operation,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure,
            Runnable onCancelled)
    {
        return start(threadName, stage, false, operation, onSuccess, onFailure, onCancelled);
    }

    <T> boolean runCommit(
            String threadName,
            String stage,
            Supplier<T> operation,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure)
    {
        return start(threadName, stage, true, operation, onSuccess, onFailure, () -> { });
    }

    boolean cancelPreview()
    {
        Task<?> task = currentTask;
        if (task == null || !busy.get() || !cancelable.get())
        {
            return false;
        }
        return task.cancel(true);
    }

    private <T> boolean start(
            String threadName,
            String stage,
            boolean commitStarted,
            Supplier<T> operation,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure,
            Runnable onCancelled)
    {
        if (busy.get())
        {
            return false;
        }
        String fixedThreadName = required(threadName, "threadName");
        String fixedStage = required(stage, "stage");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(onSuccess, "onSuccess");
        Objects.requireNonNull(onFailure, "onFailure");
        Objects.requireNonNull(onCancelled, "onCancelled");

        Task<T> task = new Task<>()
        {
            @Override
            protected T call()
            {
                return operation.get();
            }
        };
        currentTask = task;
        busy.set(true);
        cancelable.set(!commitStarted);
        progress.set(new InterchangeProgress(fixedStage, 0L, 1L, !commitStarted, commitStarted));

        task.setOnSucceeded(event ->
        {
            progress.set(new InterchangeProgress(fixedStage, 1L, 1L, false, commitStarted));
            finish(task);
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event ->
        {
            finish(task);
            onFailure.accept(task.getException());
        });
        task.setOnCancelled(event ->
        {
            progress.set(new InterchangeProgress("Cancelled before commit", 0L, 1L, false, false));
            finish(task);
            onCancelled.run();
        });
        executor.execute(task);
        return true;
    }

    private void finish(Task<?> task)
    {
        if (currentTask == task)
        {
            currentTask = null;
            cancelable.set(false);
            busy.set(false);
        }
    }

    private static String required(String value, String field)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }

    private static void startDaemonThread(Runnable command)
    {
        Thread thread = new Thread(command, "npbk-interchange-task");
        thread.setDaemon(true);
        thread.start();
    }
}
