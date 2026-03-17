package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.domain.core.EntrySide;
import org.nonprofitbookkeeping.domain.core.JournalTransaction;
import org.nonprofitbookkeeping.domain.core.PostingLine;
import org.nonprofitbookkeeping.domain.timing.TimingPosition;
import org.nonprofitbookkeeping.domain.timing.TransactionTiming;
import org.nonprofitbookkeeping.repository.JdbcJournalTransactionRepository;
import org.nonprofitbookkeeping.repository.JdbcOpenItemSnapshotRepository;
import org.nonprofitbookkeeping.repository.OpenItemKind;
import org.nonprofitbookkeeping.repository.OpenItemSnapshotRecord;
import org.nonprofitbookkeeping.repository.RepositoryIntegrationSupport;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.nonprofitbookkeeping.testutil.TestAmountAssertions.assertAmountEquals;

/**
 * JournalPostingServiceIntegrationTest component.
 */
public class JournalPostingServiceIntegrationTest
{
    @Test
    public void post_derivesReceivableOpenSnapshot_thenSettlesWithTransition()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JournalPostingService service = new JournalPostingService(
                new JdbcJournalTransactionRepository(ds),
                new JdbcOpenItemSnapshotRepository(ds));

        JournalTransaction issueReceivable = JournalTransaction.create(
                "BARONY-RED",
                LocalDate.of(2026, 5, 1),
                "Issue receivable invoice",
                TransactionTiming.of(TimingPosition.FUTURE, TimingPosition.NOW),
                List.of(
                        new PostingLine("1100-AR", "GENERAL", EntrySide.DEBIT, new BigDecimal("125.00")),
                        new PostingLine("4100-DONATION-INCOME", "GENERAL", EntrySide.CREDIT, new BigDecimal("125.00"))));

        service.post(issueReceivable);

        OpenItemSnapshotRecord openSnapshot = snapshot(ds, "BARONY-RED", OpenItemKind.RECEIVABLE, "1100-AR|GENERAL");
        assertEquals("OPEN", openSnapshot.state());
        assertAmountEquals("125.00", openSnapshot.openAmount());
        assertEquals(0, openSnapshot.version());

        JournalTransaction receiveCash = JournalTransaction.create(
                "BARONY-RED",
                LocalDate.of(2026, 5, 3),
                "Receive cash on invoice",
                TransactionTiming.of(TimingPosition.NOW, TimingPosition.PREVIOUSLY),
                List.of(
                        new PostingLine("1000-BANK", "GENERAL", EntrySide.DEBIT, new BigDecimal("125.00")),
                        new PostingLine("1100-AR", "GENERAL", EntrySide.CREDIT, new BigDecimal("125.00"))));

        service.post(receiveCash);

        OpenItemSnapshotRecord settledSnapshot = snapshot(ds, "BARONY-RED", OpenItemKind.RECEIVABLE, "1100-AR|GENERAL");
        assertEquals("SETTLED_BY_CASH", settledSnapshot.state());
        assertEquals(receiveCash.transactionId(), settledSnapshot.lastTransactionId());
        assertAmountEquals("0.00", settledSnapshot.openAmount());
        assertEquals(1, settledSnapshot.version());
        assertEquals(1, transitionCount(ds, settledSnapshot.id()));
    }

    @Test
    public void post_derivesPrepaidOpenSnapshot_thenFullyRecognizesWithTransition()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JournalPostingService service = new JournalPostingService(
                new JdbcJournalTransactionRepository(ds),
                new JdbcOpenItemSnapshotRepository(ds));

        JournalTransaction payPrepaid = JournalTransaction.create(
                "BARONY-BLUE",
                LocalDate.of(2026, 6, 10),
                "Pay annual insurance prepaid",
                TransactionTiming.of(TimingPosition.NOW, TimingPosition.FUTURE),
                List.of(
                        new PostingLine("1200-PREPAID-INSURANCE", "GENERAL", EntrySide.DEBIT, new BigDecimal("300.00")),
                        new PostingLine("1000-BANK", "GENERAL", EntrySide.CREDIT, new BigDecimal("300.00"))));

        service.post(payPrepaid);

        OpenItemSnapshotRecord openSnapshot = snapshot(ds, "BARONY-BLUE", OpenItemKind.PREPAID_EXPENSE, "1200-PREPAID-INSURANCE|GENERAL");
        assertEquals("OPEN", openSnapshot.state());
        assertAmountEquals("300.00", openSnapshot.openAmount());

        JournalTransaction recognizePrepaid = JournalTransaction.create(
                "BARONY-BLUE",
                LocalDate.of(2026, 7, 1),
                "Recognize prepaid insurance",
                TransactionTiming.of(TimingPosition.PREVIOUSLY, TimingPosition.NOW),
                List.of(
                        new PostingLine("5100-INSURANCE-EXPENSE", "GENERAL", EntrySide.DEBIT, new BigDecimal("300.00")),
                        new PostingLine("1200-PREPAID-INSURANCE", "GENERAL", EntrySide.CREDIT, new BigDecimal("300.00"))));

        service.post(recognizePrepaid);

        OpenItemSnapshotRecord settledSnapshot = snapshot(ds, "BARONY-BLUE", OpenItemKind.PREPAID_EXPENSE, "1200-PREPAID-INSURANCE|GENERAL");
        assertEquals("FULLY_RECOGNIZED", settledSnapshot.state());
        assertEquals(recognizePrepaid.transactionId(), settledSnapshot.lastTransactionId());
        assertAmountEquals("0.00", settledSnapshot.openAmount());
        assertEquals(1, settledSnapshot.version());
        assertEquals(1, transitionCount(ds, settledSnapshot.id()));
    }

    @Test
    public void post_supportsReceivablePartialThenFullSettlement()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JournalPostingService service = new JournalPostingService(
                new JdbcJournalTransactionRepository(ds),
                new JdbcOpenItemSnapshotRepository(ds));

        service.post(JournalTransaction.create(
                "BARONY-GOLD",
                LocalDate.of(2026, 9, 1),
                "Issue AR",
                TransactionTiming.of(TimingPosition.FUTURE, TimingPosition.NOW),
                List.of(
                        new PostingLine("1100-AR", "GENERAL", EntrySide.DEBIT, new BigDecimal("150.00")),
                        new PostingLine("4100-INCOME", "GENERAL", EntrySide.CREDIT, new BigDecimal("150.00")))));

        service.post(JournalTransaction.create(
                "BARONY-GOLD",
                LocalDate.of(2026, 9, 2),
                "Partial cash",
                TransactionTiming.of(TimingPosition.NOW, TimingPosition.PREVIOUSLY),
                List.of(
                        new PostingLine("1000-BANK", "GENERAL", EntrySide.DEBIT, new BigDecimal("50.00")),
                        new PostingLine("1100-AR", "GENERAL", EntrySide.CREDIT, new BigDecimal("50.00")))));

        OpenItemSnapshotRecord partial = snapshot(ds, "BARONY-GOLD", OpenItemKind.RECEIVABLE, "1100-AR|GENERAL");
        assertEquals("PARTIALLY_APPLIED", partial.state());
        assertAmountEquals("100.00", partial.openAmount());
        assertEquals(1, partial.version());

        service.post(JournalTransaction.create(
                "BARONY-GOLD",
                LocalDate.of(2026, 9, 3),
                "Final cash",
                TransactionTiming.of(TimingPosition.NOW, TimingPosition.PREVIOUSLY),
                List.of(
                        new PostingLine("1000-BANK", "GENERAL", EntrySide.DEBIT, new BigDecimal("100.00")),
                        new PostingLine("1100-AR", "GENERAL", EntrySide.CREDIT, new BigDecimal("100.00")))));

        OpenItemSnapshotRecord settled = snapshot(ds, "BARONY-GOLD", OpenItemKind.RECEIVABLE, "1100-AR|GENERAL");
        assertEquals("SETTLED_BY_CASH", settled.state());
        assertAmountEquals("0.00", settled.openAmount());
        assertEquals(2, settled.version());
        assertEquals(2, transitionCount(ds, settled.id()));
    }

    @Test
    public void post_supportsPrepaidPartialThenFullRecognition()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JournalPostingService service = new JournalPostingService(
                new JdbcJournalTransactionRepository(ds),
                new JdbcOpenItemSnapshotRepository(ds));

        service.post(JournalTransaction.create(
                "BARONY-SILVER",
                LocalDate.of(2026, 10, 1),
                "Pay prepaid",
                TransactionTiming.of(TimingPosition.NOW, TimingPosition.FUTURE),
                List.of(
                        new PostingLine("1200-PREPAID-RENT", "GENERAL", EntrySide.DEBIT, new BigDecimal("90.00")),
                        new PostingLine("1000-BANK", "GENERAL", EntrySide.CREDIT, new BigDecimal("90.00")))));

        service.post(JournalTransaction.create(
                "BARONY-SILVER",
                LocalDate.of(2026, 10, 15),
                "Recognize month 1",
                TransactionTiming.of(TimingPosition.PREVIOUSLY, TimingPosition.NOW),
                List.of(
                        new PostingLine("5100-RENT-EXPENSE", "GENERAL", EntrySide.DEBIT, new BigDecimal("30.00")),
                        new PostingLine("1200-PREPAID-RENT", "GENERAL", EntrySide.CREDIT, new BigDecimal("30.00")))));

        OpenItemSnapshotRecord partial = snapshot(ds, "BARONY-SILVER", OpenItemKind.PREPAID_EXPENSE, "1200-PREPAID-RENT|GENERAL");
        assertEquals("PARTIALLY_RECOGNIZED", partial.state());
        assertAmountEquals("60.00", partial.openAmount());
        assertEquals(1, partial.version());

        service.post(JournalTransaction.create(
                "BARONY-SILVER",
                LocalDate.of(2026, 11, 1),
                "Recognize remaining",
                TransactionTiming.of(TimingPosition.PREVIOUSLY, TimingPosition.NOW),
                List.of(
                        new PostingLine("5100-RENT-EXPENSE", "GENERAL", EntrySide.DEBIT, new BigDecimal("60.00")),
                        new PostingLine("1200-PREPAID-RENT", "GENERAL", EntrySide.CREDIT, new BigDecimal("60.00")))));

        OpenItemSnapshotRecord recognized = snapshot(ds, "BARONY-SILVER", OpenItemKind.PREPAID_EXPENSE, "1200-PREPAID-RENT|GENERAL");
        assertEquals("FULLY_RECOGNIZED", recognized.state());
        assertAmountEquals("0.00", recognized.openAmount());
        assertEquals(2, recognized.version());
        assertEquals(2, transitionCount(ds, recognized.id()));
    }

    @Test
    public void post_ignoresNonMappedAccountsForReceivableAndPrepaidDerivation()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JournalPostingService service = new JournalPostingService(
                new JdbcJournalTransactionRepository(ds),
                new JdbcOpenItemSnapshotRepository(ds));

        JournalTransaction transaction = JournalTransaction.create(
                "BARONY-GREEN",
                LocalDate.of(2026, 8, 1),
                "Future bank with non-AR account",
                TransactionTiming.of(TimingPosition.FUTURE, TimingPosition.NOW),
                List.of(
                        new PostingLine("1199-NON-AR", "GENERAL", EntrySide.DEBIT, new BigDecimal("55.00")),
                        new PostingLine("4100-INCOME", "GENERAL", EntrySide.CREDIT, new BigDecimal("55.00"))));

        service.post(transaction);

        assertEquals(0, new JdbcOpenItemSnapshotRepository(ds)
                .findByGroupAndKind("BARONY-GREEN", OpenItemKind.RECEIVABLE)
                .size());
        assertEquals(0, new JdbcOpenItemSnapshotRepository(ds)
                .findByGroupAndKind("BARONY-GREEN", OpenItemKind.PREPAID_EXPENSE)
                .size());
    }

    private OpenItemSnapshotRecord snapshot(DataSource ds, String groupCode, OpenItemKind itemKind, String itemRef)
    {
        return new JdbcOpenItemSnapshotRepository(ds)
                .findByGroupAndKind(groupCode, itemKind)
                .stream()
                .filter(row -> row.itemRef().equals(itemRef))
                .findFirst()
                .orElseThrow();
    }

    private int transitionCount(DataSource ds, java.util.UUID snapshotId)
    {
        String sql = "SELECT COUNT(*) FROM open_item_transition WHERE snapshot_id = ?";
        try (Connection connection = ds.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setObject(1, snapshotId);
            try (ResultSet rs = ps.executeQuery())
            {
                rs.next();
                return rs.getInt(1);
            }
        }
        catch (Exception ex)
        {
            throw new IllegalStateException(ex);
        }
    }
}
