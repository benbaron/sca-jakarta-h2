package org.nonprofitbookkeeping.ui;

import java.util.Objects;
import java.util.function.Supplier;

/** Tracks editor dirtiness by comparing its current immutable snapshot to the last clean snapshot. */
final class FormDirtyTracker
{
    private final Supplier<?> snapshotSupplier;
    private Object cleanSnapshot;

    FormDirtyTracker(Supplier<?> snapshotSupplier)
    {
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        markClean();
    }

    boolean isDirty()
    {
        return !Objects.equals(cleanSnapshot, snapshotSupplier.get());
    }

    void markClean()
    {
        cleanSnapshot = snapshotSupplier.get();
    }
}
