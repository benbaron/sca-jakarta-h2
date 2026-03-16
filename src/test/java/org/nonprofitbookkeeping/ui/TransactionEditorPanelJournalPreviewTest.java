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
 * TransactionEditorPanelJournalPreviewTest component.
 */
public class TransactionEditorPanelJournalPreviewTest
{
    @Test
    public void findContextMatches_filtersByProvidedContextFields()
    {
        List<LedgerQueryService.LedgerRow> rows = List.of(
                new LedgerQueryService.LedgerRow(10L, LocalDate.of(2026, 3, 1), "Donor A", "March gift", "Main Bank", 2),
                new LedgerQueryService.LedgerRow(11L, LocalDate.of(2026, 3, 2), "Donor B", "Program fee", "Ops Bank", 2));

        List<LedgerQueryService.LedgerRow> matches = TransactionEditorPanel.findContextMatches(
                rows,
                "2026-03-01",
                "donor a",
                "",
                "main");

        assertEquals(1, matches.size());
        assertEquals(10L, matches.get(0).id());
    }

    @Test
    public void renderContextJournalPreview_includesMatchedHeaderAndFirstLine()
    {
        LedgerQueryService.LedgerRow row = new LedgerQueryService.LedgerRow(
                15L,
                LocalDate.of(2026, 4, 5),
                "Payee",
                "Memo",
                "Bank",
                2);
        List<JournalLine> lines = List.of(new JournalLine(
                LocalDate.of(2026, 4, 5),
                15L,
                "Memo",
                "Payee",
                "1000",
                "Cash",
                "F01",
                "Fund 01",
                BigDecimal.TEN,
                BigDecimal.ZERO));

        String preview = TransactionEditorPanel.renderContextJournalPreview(row, lines);

        assertTrue(preview.contains("Txn #15"));
        assertTrue(preview.contains("1000/F01"));
    }
}
