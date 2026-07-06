package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.service.AccountingJournalProjection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    public void rowsFor_flattensJournalProjectionIntoTraditionalJournalLines()
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

        assertEquals(2, rows.size());
        assertEquals(77L, rows.get(0).transactionId());
        assertEquals("2026-07-06", rows.get(0).date());
        assertEquals("Reference memo", rows.get(0).memo());
        assertEquals("1000 Operating Bank", rows.get(0).account());
        assertEquals("GEN General", rows.get(0).fund());
        assertEquals("25.00", rows.get(0).debit());
        assertEquals("0", rows.get(0).credit());
        assertEquals("deposit", rows.get(0).lineDetails());
    }
}
