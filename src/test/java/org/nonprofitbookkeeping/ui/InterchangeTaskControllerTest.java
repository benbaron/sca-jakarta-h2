package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("javafx-runtime")
class InterchangeTaskControllerTest
{
    @Test
    void busyOperationRejectsASecondWriterAndFailureAllowsAFreshRequest() throws Exception
    {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        AtomicInteger writes = new AtomicInteger();
        AtomicReference<Throwable> reported = new AtomicReference<>();
        IllegalArgumentException failure = new IllegalArgumentException("Invalid test source");
        InterchangeTaskController controller = FxTestSupport.onFx(() ->
        {
            InterchangeTaskController value = new InterchangeTaskController(queued::set);
            assertTrue(value.runPreview("invalid-preview", "Reading invalid source", () ->
            {
                throw failure;
            }, ignored -> { }, reported::set, () -> { }));
            Runnable original = queued.get();
            assertFalse(value.runCommit("second-writer", "Writing", writes::incrementAndGet,
                    ignored -> { }, reported::set));
            assertSame(original, queued.get(), "Rejecting a second request must preserve the running operation");
            return value;
        });
        Thread first = new Thread(queued.get(), "invalid-preview-test");
        first.start(); first.join();
        FxTestSupport.onFx(() ->
        {
            assertSame(failure, reported.get());
            assertFalse(controller.busyProperty().get());
            assertFalse(controller.cancelableProperty().get());
            assertEquals(0, writes.get());
            assertTrue(controller.runCommit("retry", "Writing retry", writes::incrementAndGet,
                    ignored -> { }, reported::set));
            return null;
        });
        Thread retry = new Thread(queued.get(), "retry-commit-test");
        retry.start(); retry.join();
        FxTestSupport.onFx(() ->
        {
            assertEquals(1, writes.get());
            assertFalse(controller.busyProperty().get());
            assertEquals(1.0, controller.progressProperty().get().fraction());
            return null;
        });
    }

    @Test
    void previewCanBeCancelledBeforeItRunsAndNeverPublishesSuccess()
    {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        InterchangeTaskController controller = FxTestSupport.onFx(() ->
        {
            InterchangeTaskController value = new InterchangeTaskController(queued::set);
            assertTrue(value.runPreview(
                    "preview-test",
                    "Reading test file",
                    () -> "result",
                    ignored -> successes.incrementAndGet(),
                    ignored -> { },
                    cancellations::incrementAndGet));
            assertTrue(value.busyProperty().get());
            assertTrue(value.cancelableProperty().get());
            assertFalse(value.progressProperty().get().commitStarted());
            assertTrue(value.cancelPreview());
            return value;
        });

        FxTestSupport.onFx(() ->
        {
            assertFalse(controller.busyProperty().get());
            assertFalse(controller.cancelableProperty().get());
            assertEquals("Cancelled before commit", controller.progressProperty().get().stage());
            assertEquals(0, successes.get());
            assertEquals(1, cancellations.get());
            return null;
        });
    }

    @Test
    void commitLocksCancellationAndReportsBoundedCompletion() throws Exception
    {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        AtomicInteger successes = new AtomicInteger();
        InterchangeTaskController controller = FxTestSupport.onFx(() ->
        {
            InterchangeTaskController value = new InterchangeTaskController(queued::set);
            assertTrue(value.runCommit(
                    "commit-test",
                    "Committing test import",
                    () -> "committed",
                    ignored -> successes.incrementAndGet(),
                    ignored -> { }));
            assertTrue(value.busyProperty().get());
            assertFalse(value.cancelableProperty().get());
            assertTrue(value.progressProperty().get().commitStarted());
            assertFalse(value.cancelPreview());
            return value;
        });

        Thread worker = new Thread(queued.get(), "interchange-controller-test");
        worker.start();
        worker.join();
        FxTestSupport.onFx(() ->
        {
            assertFalse(controller.busyProperty().get());
            assertEquals(1.0, controller.progressProperty().get().fraction());
            assertEquals(1, successes.get());
            return null;
        });
    }
}
