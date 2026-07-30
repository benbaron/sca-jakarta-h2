package org.nonprofitbookkeeping.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InventoryPortableIdentityTest
{
    @Test
    void newInventoryItemsReceiveDistinctPortableIdentities()
    {
        assertDistinct(new InventoryItem().getPortableId(), new InventoryItem().getPortableId());
    }

    @Test
    void newInventoryMovementsReceiveDistinctPortableIdentities()
    {
        assertDistinct(new InventoryMovement().getPortableId(),
                new InventoryMovement().getPortableId());
    }

    private static void assertDistinct(UUID first, UUID second)
    {
        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first, second);
    }
}
