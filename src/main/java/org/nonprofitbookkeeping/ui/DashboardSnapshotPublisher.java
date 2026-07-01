package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.service.dashboard.DashboardSnapshot;

import java.util.Objects;
import java.util.function.Consumer;

/** Publishes the current dashboard projection to the workspace inspector. */
final class DashboardSnapshotPublisher
{
    private static Consumer<DashboardSnapshot> listener = snapshot -> { };

    private DashboardSnapshotPublisher()
    {
    }

    static void register(Consumer<DashboardSnapshot> nextListener)
    {
        listener = Objects.requireNonNull(nextListener, "nextListener");
    }

    static void publish(DashboardSnapshot snapshot)
    {
        listener.accept(snapshot);
    }
}
