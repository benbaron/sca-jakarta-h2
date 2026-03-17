package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.service.JournalLine;
import org.nonprofitbookkeeping.service.LedgerQueryService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LedgerRegisterPanelTest component.
 */
public class LedgerRegisterPanelTest
{
    @Test
    public void toRow_mapsBlanksToNoneAndPreservesSplitCount()
    {
        LedgerQueryService.LedgerRow source = new LedgerQueryService.LedgerRow(
                101L,
                LocalDate.of(2026, 3, 13),
                "",
                "",
                "",
                3);

        LedgerRegisterPanel.Row row = LedgerRegisterPanel.toRow(source);

        assertEquals(101L, row.id());
        assertEquals("2026-03-13", row.date());
        assertEquals("(none)", row.payee());
        assertEquals("(none)", row.memo());
        assertEquals("(none)", row.bank());
        assertEquals("3", row.splitCount());
        assertEquals("Posted", row.status());
    }

    @Test
    public void renderJournal_formatsDrCrUsingJournalLineGetters()
    {
        LedgerRegisterPanel.Row row = new LedgerRegisterPanel.Row(
                77L,
                "2026-03-13",
                "Acme",
                "Office supplies",
                "1000-BANK",
                "2",
                "Posted");

        List<JournalLine> lines = List.of(
                new JournalLine(LocalDate.of(2026, 3, 13), 77L, "Office supplies", "Acme",
                        "6100-EXP", "Supplies Expense", "GEN", "General",
                        new BigDecimal("25.00"), BigDecimal.ZERO),
                new JournalLine(LocalDate.of(2026, 3, 13), 77L, "Office supplies", "Acme",
                        "1000-BANK", "Operating Bank", "GEN", "General",
                        BigDecimal.ZERO, new BigDecimal("25.00")));

        String rendered = LedgerRegisterPanel.renderJournal(row, lines);

        assertTrue(rendered.contains("Txn #77 | Date 2026-03-13 | Payee Acme"));
        assertTrue(rendered.contains("Memo: Office supplies"));
        assertTrue(rendered.contains("6100-EXP Supplies Expense | Fund GEN | DR 25.00 | CR 0"));
        assertTrue(rendered.contains("1000-BANK Operating Bank | Fund GEN | DR 0 | CR 25.00"));
    }
}
