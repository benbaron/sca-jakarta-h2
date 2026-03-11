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
        assertEquals(new BigDecimal("125.00"), openSnapshot.openAmount());
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
        assertEquals(new BigDecimal("300.00"), openSnapshot.openAmount());

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
        assertEquals(1, settledSnapshot.version());
        assertEquals(1, transitionCount(ds, settledSnapshot.id()));
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
