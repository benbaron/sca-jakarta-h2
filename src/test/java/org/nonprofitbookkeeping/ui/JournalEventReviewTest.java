package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.service.TransactionView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JournalEventReviewTest
{
    @Test
    void savedReviewShowsIndependentEventNamesAndExplicitUntaggedLine()
    {
        var tagged = new TransactionView.Line(1L, 1L, "1000", "Cash", 1L, "A", "Fund A",
                null, 7L, null, BigDecimal.TEN, BigDecimal.ZERO, false, null,
                false, false, null, null, "FAIR-26", "Historic Fair");
        var untagged = new TransactionView.Line(2L, 2L, "4000", "Income", 2L, "B", "Fund B",
                null, null, null, BigDecimal.ZERO, BigDecimal.TEN, false, null);
        var view = new TransactionView(1L, LocalDate.of(2026, 3, 14), null, null, "Mixed", null, null,
                "ENTERED", List.of(tagged, untagged));
        var row = JournalWorkspacePanel.JournalTransactionRow.from(view);
        assertEquals("FAIR-26 — Historic Fair\nNo event", row.events());
        assertEquals("A Fund A\nB Fund B", row.funds());
    }
}
