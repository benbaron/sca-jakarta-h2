package org.nonprofitbookkeeping.repository;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class JdbcOpenItemSnapshotRepositoryTest
{
    @Test
    public void createAndFindById_roundTripsSnapshot()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcOpenItemSnapshotRepository repository = new JdbcOpenItemSnapshotRepository(ds);

        OpenItemSnapshotRecord snapshot = new OpenItemSnapshotRecord(
                UUID.randomUUID(),
                "BARONY-RED",
                "RECEIVABLE",
                "AR-2026-001",
                "OPEN",
                new BigDecimal("120.00"),
                new BigDecimal("120.00"),
                null,
                LocalDate.of(2026, 4, 10));

        repository.create(snapshot);

        OpenItemSnapshotRecord loaded = repository.findById(snapshot.id()).orElseThrow();
        assertEquals("RECEIVABLE", loaded.itemKind());
        assertEquals("OPEN", loaded.state());
    }

    @Test
    public void transition_updatesSnapshotState_andQueryByGroupKindWorks()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcOpenItemSnapshotRepository repository = new JdbcOpenItemSnapshotRepository(ds);

        UUID id = UUID.randomUUID();
        UUID triggerTransaction = UUID.randomUUID();

        repository.create(new OpenItemSnapshotRecord(
                id,
                "BARONY-RED",
                "RECEIVABLE",
                "AR-2026-002",
                "OPEN",
                new BigDecimal("75.00"),
                new BigDecimal("75.00"),
                null,
                LocalDate.of(2026, 4, 10)));

        repository.transition(id, "OPEN", "SETTLED_BY_CASH", triggerTransaction, "Paid by check", LocalDate.of(2026, 4, 11));

        OpenItemSnapshotRecord loaded = repository.findById(id).orElseThrow();
        assertEquals("SETTLED_BY_CASH", loaded.state());
        assertEquals(triggerTransaction, loaded.lastTransactionId());

        List<OpenItemSnapshotRecord> rows = repository.findByGroupAndKind("BARONY-RED", "RECEIVABLE");
        assertEquals(1, rows.size());
        assertEquals("AR-2026-002", rows.get(0).itemRef());
    }
}
