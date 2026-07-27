package org.nonprofitbookkeeping.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TxnPortableIdentityTest
{
    @Test
    void newTransactionsReceiveDistinctPortableIdentities()
    {
        Txn first = new Txn();
        Txn second = new Txn();

        assertNotNull(first.getPortableId());
        assertNotNull(second.getPortableId());
        assertNotEquals(first.getPortableId(), second.getPortableId());
    }
}
