package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.service.AccountingJournalProjection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JournalPaneTest
{
    @Test
    public void centeredContext_carriesStableTransactionId()
    {
        String context = JournalPane.centeredContext(44L, "Ledger Register");

        assertEquals(Long.valueOf(44L), JournalPane.transactionIdFromContext(context));
        assertNull(JournalPane.transactionIdFromContext("Inspect journal from left navigation"));
    }

    @Test
    public void rowsFor_groupsJournalProjectionIntoTransactionBlock()
    {
        AccountingJournalProjection projection = new AccountingJournalProjection(
                77L,
                LocalDate.of(2026, 7, 6),
                "Sample Payee",
                "Reference memo",
                List.of(
                        new AccountingJournalProjection.Line("1000", "Operating Bank", "GEN", "General",
                                new BigDecimal("25.00"), BigDecimal.ZERO, "deposit"),
                        new AccountingJournalProjection.Line("4000", "Contributions", "GEN", "General",
                                BigDecimal.ZERO, new BigDecimal("25.00"), "income")));

        List<JournalPane.JournalRow> rows = JournalPane.rowsFor(projection);

        assertEquals(1, rows.size());
        assertEquals(77L, rows.get(0).transactionId());
        assertEquals("2026-07-06", rows.get(0).date());
        assertTrue(rows.get(0).account().contains("1000 Operating Bank"));
        assertTrue(rows.get(0).account().contains("4000 Contributions"));
        assertTrue(rows.get(0).fund().contains("GEN General"));
        assertTrue(rows.get(0).debit().contains("$25.00"));
        assertTrue(rows.get(0).credit().contains("$25.00"));
        assertTrue(rows.get(0).memo().contains("Memo: Reference memo"));
        assertTrue(rows.get(0).memo().contains("Payee: Sample Payee"));
        assertEquals("Schedules (0)", rows.get(0).supplemental());
    }
}
