package org.nonprofitbookkeeping.repository;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.domain.core.EntrySide;
import org.nonprofitbookkeeping.domain.core.JournalTransaction;
import org.nonprofitbookkeeping.domain.core.PostingLine;
import org.nonprofitbookkeeping.domain.timing.TimingPosition;
import org.nonprofitbookkeeping.domain.timing.TransactionTiming;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JdbcJournalTransactionRepositoryTest component.
 */
public class JdbcJournalTransactionRepositoryTest
{
    @Test
    public void appendAndFindById_roundTripsTransaction()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcJournalTransactionRepository repository = new JdbcJournalTransactionRepository(ds);

        JournalTransaction transaction = JournalTransaction.create(
                "BARONY-RED",
                LocalDate.of(2026, 4, 10),
                "Prepaid hall rental",
                TransactionTiming.of(TimingPosition.NOW, TimingPosition.FUTURE),
                List.of(
                        new PostingLine("1200-PREPAID", "GENERAL", EntrySide.DEBIT, new BigDecimal("300.00")),
                        new PostingLine("1000-BANK", "GENERAL", EntrySide.CREDIT, new BigDecimal("300.00"))
                ));

        repository.append(transaction);

        JournalTransaction loaded = repository.findById(transaction.transactionId()).orElseThrow();
        assertEquals(transaction.transactionId(), loaded.transactionId());
        assertEquals("BARONY-RED", loaded.groupCode());
        assertEquals(2, loaded.lines().size());
        assertEquals(EntrySide.DEBIT, loaded.lines().get(0).side());
    }

    @Test
    public void findByGroupAndDateRange_filtersByGroupAndDate()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcJournalTransactionRepository repository = new JdbcJournalTransactionRepository(ds);

        repository.append(JournalTransaction.create(
                "BARONY-RED",
                LocalDate.of(2026, 4, 1),
                "Txn 1",
                TransactionTiming.of(TimingPosition.NOW, TimingPosition.NOW),
                List.of(
                        new PostingLine("1000-BANK", "GENERAL", EntrySide.DEBIT, new BigDecimal("100.00")),
                        new PostingLine("4000-INCOME", "GENERAL", EntrySide.CREDIT, new BigDecimal("100.00"))
                )));

        repository.append(JournalTransaction.create(
                "BARONY-BLUE",
                LocalDate.of(2026, 4, 2),
                "Txn 2",
                TransactionTiming.of(TimingPosition.NOW, TimingPosition.NOW),
                List.of(
                        new PostingLine("1000-BANK", "GENERAL", EntrySide.DEBIT, new BigDecimal("80.00")),
                        new PostingLine("4000-INCOME", "GENERAL", EntrySide.CREDIT, new BigDecimal("80.00"))
                )));

        List<JournalTransaction> rows = repository.findByGroupAndDateRange(
                "BARONY-RED",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30));

        assertEquals(1, rows.size());
        assertEquals("BARONY-RED", rows.get(0).groupCode());
    }
}
