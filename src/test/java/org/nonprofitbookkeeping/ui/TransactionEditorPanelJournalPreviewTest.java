package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.service.AccountingJournalProjection;

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
    public void renderJournalPreview_includesSavedHeaderAndFirstLine()
    {
        AccountingJournalProjection projection = new AccountingJournalProjection(
                15L,
                LocalDate.of(2026, 4, 5),
                "Payee",
                "Memo",
                List.of(new AccountingJournalProjection.Line(
                        "1000",
                        "Cash",
                        "F01",
                        "Fund 01",
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        "Line notes")));

        String preview = TransactionEditorPanel.renderJournalPreview(projection);

        assertTrue(preview.contains("Txn #15"));
        assertTrue(preview.contains("1000/F01"));
    }
}
