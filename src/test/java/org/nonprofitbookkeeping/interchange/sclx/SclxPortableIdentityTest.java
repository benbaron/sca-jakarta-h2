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
        assertEquals("counterparty:CAER-GALEN:11111111-1111-1111-1111-111111111111",
                SclxPortableIdentity.counterparty("CAER-GALEN", "11111111-1111-1111-1111-111111111111"));
        assertEquals("merchant:CAER-GALEN:22222222-2222-2222-2222-222222222222",
                SclxPortableIdentity.merchant("CAER-GALEN", "22222222-2222-2222-2222-222222222222"));
        assertEquals("bank:CAER-GALEN:33333333-3333-3333-3333-333333333333",
                SclxPortableIdentity.bank("CAER-GALEN", "33333333-3333-3333-3333-333333333333"));
        assertEquals("bank-account:CAER-GALEN:44444444-4444-4444-4444-444444444444",
                SclxPortableIdentity.bankAccount("CAER-GALEN", "44444444-4444-4444-4444-444444444444"));
        assertEquals("bank-import-batch:CAER-GALEN:55555555-5555-5555-5555-555555555555",
                SclxPortableIdentity.bankImportBatch("CAER-GALEN", "55555555-5555-5555-5555-555555555555"));
        assertEquals("bank-statement-line:CAER-GALEN:66666666-6666-6666-6666-666666666666",
                SclxPortableIdentity.bankStatementLine("CAER-GALEN", "66666666-6666-6666-6666-666666666666"));
        assertEquals("bank-import-issue:CAER-GALEN:77777777-7777-7777-7777-777777777777",
                SclxPortableIdentity.bankImportIssue("CAER-GALEN", "77777777-7777-7777-7777-777777777777"));
        assertEquals("reconciliation-session:CAER-GALEN:88888888-8888-8888-8888-888888888888",
                SclxPortableIdentity.reconciliationSession("CAER-GALEN", "88888888-8888-8888-8888-888888888888"));
        assertEquals("reconciliation-match:CAER-GALEN:99999999-9999-9999-9999-999999999999",
                SclxPortableIdentity.reconciliationMatch("CAER-GALEN", "99999999-9999-9999-9999-999999999999"));
        assertEquals("fixed-asset:CAER-GALEN:aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                SclxPortableIdentity.fixedAsset("CAER-GALEN", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        assertEquals("fixed-asset-depreciation-run:CAER-GALEN:bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                SclxPortableIdentity.fixedAssetDepreciationRun(
                        "CAER-GALEN", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
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
        assertEquals("supplemental-detail:transaction%3ATEST%3ATX-1:1",
                SclxPortableIdentity.supplementalDetail(transactionId, 1));
        assertThrows(IllegalArgumentException.class,
                () -> SclxPortableIdentity.supplementalDetail(transactionId, 0));
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
