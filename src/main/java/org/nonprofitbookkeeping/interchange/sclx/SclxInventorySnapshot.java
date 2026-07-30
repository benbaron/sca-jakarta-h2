package org.nonprofitbookkeeping.interchange.sclx;

import org.nonprofitbookkeeping.model.InventoryItem;
import org.nonprofitbookkeeping.model.InventoryMovement;

import java.util.List;
import java.util.Objects;

/** Bounded selected-company inventory graph used for SCLX assembly. */
record SclxInventorySnapshot(List<InventoryItem> items, List<InventoryMovement> movements)
{
    SclxInventorySnapshot
    {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        movements = List.copyOf(Objects.requireNonNull(movements, "movements"));
    }

    static SclxInventorySnapshot empty()
    {
        return new SclxInventorySnapshot(List.of(), List.of());
    }
}
