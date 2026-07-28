package org.nonprofitbookkeeping.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PartyPortableIdentityTest
{
    @Test
    void newCounterpartiesReceiveDistinctPortableIdentities()
    {
        Counterparty first = new Counterparty();
        Counterparty second = new Counterparty();

        assertNotNull(first.getPortableId());
        assertNotNull(second.getPortableId());
        assertNotEquals(first.getPortableId(), second.getPortableId());
    }

    @Test
    void newMerchantsReceiveDistinctPortableIdentities()
    {
        Merchant first = new Merchant();
        Merchant second = new Merchant();

        assertNotNull(first.getPortableId());
        assertNotNull(second.getPortableId());
        assertNotEquals(first.getPortableId(), second.getPortableId());
    }
}
