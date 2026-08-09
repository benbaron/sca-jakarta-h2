package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BankImportNavigationContextTest
{
    @Test
    void roundTripsExactReconciliationImportScope()
    {
        String context = BankImportNavigationContext.forReconciliation(3001L, 9007L);
        var request = BankImportNavigationContext.parseImportRequest(context);

        assertTrue(request.isPresent());
        assertEquals(3001L, request.orElseThrow().bankAccountId());
        assertEquals(9007L, request.orElseThrow().reconciliationSessionId());
        assertEquals(9007L, BankImportNavigationContext.parseReconciliationReturn(
                BankImportNavigationContext.returnToReconciliation(9007L)).orElseThrow());
        assertEquals(9007L, BankImportNavigationContext.parseReconciliationSession(
                BankImportNavigationContext.forReconciliationSession(9007L)).orElseThrow());
    }

    @Test
    void rejectsUnrelatedOrMalformedContext()
    {
        assertTrue(BankImportNavigationContext.parseImportRequest("Banking: import statement").isEmpty());
        assertTrue(BankImportNavigationContext.parseImportRequest(
                "bank-import:account=abc;reconciliation=4").isEmpty());
        assertFalse(BankImportNavigationContext.parseReconciliationReturn(
                "bank-import-return:reconciliation=0").isPresent());
        assertFalse(BankImportNavigationContext.parseReconciliationSession(
                "reconciliation:session=not-a-number").isPresent());
    }
}
