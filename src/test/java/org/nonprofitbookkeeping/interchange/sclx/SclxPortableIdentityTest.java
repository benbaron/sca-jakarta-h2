package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SclxPortableIdentityTest
{
    @Test
    void derivesStableCompanyScopedBusinessIdentities()
    {
        assertEquals("organization:CAER-GALEN", SclxPortableIdentity.organization("CAER-GALEN"));
        assertEquals("account:CAER-GALEN:1010", SclxPortableIdentity.account("CAER-GALEN", "1010"));
        assertEquals("fund:CAER-GALEN:GENERAL", SclxPortableIdentity.fund("CAER-GALEN", "GENERAL"));
        assertEquals("activity:CAER-GALEN:EVENT", SclxPortableIdentity.activity("CAER-GALEN", "EVENT"));
        assertEquals("budget:CAER-GALEN:2026:ADOPTED",
                SclxPortableIdentity.budget("CAER-GALEN", 2026, "ADOPTED"));
        String budgetId = SclxPortableIdentity.budget("CAER-GALEN", 2026, "ADOPTED");
        assertNotEquals(
                SclxPortableIdentity.budgetLine(budgetId, "SUPPLIES", null, "fund-1", "2026-07"),
                SclxPortableIdentity.budgetLine(budgetId, "SUPPLIES", null, "fund-1", "2026-08"));
    }

    @Test
    void percentEncodesSeparatorsWhitespaceAndUnicodeAsUtf8()
    {
        assertEquals("account:TEST:Cash%20%26%20Savings",
                SclxPortableIdentity.account("TEST", "Cash & Savings"));
        assertEquals("fund:TEST:Caf%C3%A9", SclxPortableIdentity.fund("TEST", "Café"));
        assertEquals("transaction:TEST:2026%2F07%2F001",
                SclxPortableIdentity.transaction("TEST", "2026/07/001"));
    }

    @Test
    void normalizesEquivalentUnicodeToTheSameIdentity()
    {
        String composed = SclxPortableIdentity.fund("TEST", "Café");
        String decomposed = SclxPortableIdentity.fund("TEST", "Cafe\u0301");

        assertEquals(composed, decomposed);
    }

    @Test
    void scopesSameBusinessCodeByCompany()
    {
        assertNotEquals(
                SclxPortableIdentity.account("BRANCH-A", "1010"),
                SclxPortableIdentity.account("BRANCH-B", "1010"));
    }

    @Test
    void derivesLineIdentityFromTransactionAndPositiveOrdinal()
    {
        String transactionId = SclxPortableIdentity.transaction("TEST", "TX-1");

        assertEquals("transaction-line:transaction%3ATEST%3ATX-1:1",
                SclxPortableIdentity.transactionLine(transactionId, 1));
        assertThrows(IllegalArgumentException.class,
                () -> SclxPortableIdentity.transactionLine(transactionId, 0));
    }

    @Test
    void rejectsBlankAndOverlongParts()
    {
        assertThrows(IllegalArgumentException.class,
                () -> SclxPortableIdentity.account("TEST", "   "));
        assertThrows(IllegalArgumentException.class,
                () -> SclxPortableIdentity.account("TEST", "x".repeat(200)));
    }
}
