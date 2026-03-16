package org.nonprofitbookkeeping.domain.core;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.domain.timing.TimingPosition;
import org.nonprofitbookkeeping.domain.timing.TransactionTiming;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JournalTransactionTest component.
 */
public class JournalTransactionTest
{
    @Test
    public void create_balancedTransaction_succeeds()
    {
        JournalTransaction transaction = JournalTransaction.create(
                "BARONY-EXAMPLE",
                LocalDate.of(2026, 3, 10),
                "Event site deposit prepaid",
                TransactionTiming.of(TimingPosition.NOW, TimingPosition.FUTURE),
                List.of(
                        new PostingLine("1200-PREPAID", "GENERAL", EntrySide.DEBIT, new BigDecimal("200.00")),
                        new PostingLine("1000-BANK", "GENERAL", EntrySide.CREDIT, new BigDecimal("200.00"))
                ));

        assertNotNull(transaction.transactionId());
        assertEquals("BARONY-EXAMPLE", transaction.groupCode());
        assertNull(transaction.reversedTransactionId());
    }

    @Test
    public void create_unbalancedTransaction_throws()
    {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> JournalTransaction.create(
                "BARONY-EXAMPLE",
                LocalDate.of(2026, 3, 10),
                "Bad entry",
                TransactionTiming.of(TimingPosition.NOW, TimingPosition.NOW),
                List.of(
                        new PostingLine("5000-EXP", "GENERAL", EntrySide.DEBIT, new BigDecimal("100.00")),
                        new PostingLine("1000-BANK", "GENERAL", EntrySide.CREDIT, new BigDecimal("99.00"))
                )));

        assertTrue(ex.getMessage().contains("balance"));
    }

    @Test
    public void reverseOn_producesBalancedOppositeSideTransaction()
    {
        JournalTransaction original = JournalTransaction.create(
                "BARONY-EXAMPLE",
                LocalDate.of(2026, 3, 10),
                "Original",
                TransactionTiming.of(TimingPosition.NOW, TimingPosition.NOW),
                List.of(
                        new PostingLine("1000-BANK", "GENERAL", EntrySide.DEBIT, new BigDecimal("50.00")),
                        new PostingLine("4000-REV", "GENERAL", EntrySide.CREDIT, new BigDecimal("50.00"))
                ));

        JournalTransaction reversal = original.reverseOn(LocalDate.of(2026, 3, 11), "Reversal");

        assertEquals(original.transactionId(), reversal.reversedTransactionId());
        assertEquals(EntrySide.CREDIT, reversal.lines().get(0).side());
        assertEquals(EntrySide.DEBIT, reversal.lines().get(1).side());
    }
}
