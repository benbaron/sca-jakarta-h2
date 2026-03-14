package org.nonprofitbookkeeping.repository;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JdbcReconciliationRunRepositoryTest
{
    @Test
    public void appendAndFindById_roundTripsRecord()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcReconciliationRunRepository repo = new JdbcReconciliationRunRepository(ds);

        ReconciliationRunRecord record = new ReconciliationRunRecord(
                UUID.randomUUID(),
                "BARONY-DRAGON",
                LocalDate.of(2026, 3, 15),
                "OFX",
                2,
                "COMPLETED",
                "Imported and matched");

        repo.append(record);

        ReconciliationRunRecord loaded = repo.findById(record.id()).orElseThrow();
        assertEquals(record.groupCode(), loaded.groupCode());
        assertEquals(record.statementEndingOn(), loaded.statementEndingOn());
        assertEquals(record.bankFormat(), loaded.bankFormat());
        assertEquals(record.importedTransactionCount(), loaded.importedTransactionCount());
    }

    @Test
    public void findByGroupAndDateRange_filtersByGroupAndDate()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcReconciliationRunRepository repo = new JdbcReconciliationRunRepository(ds);

        repo.append(new ReconciliationRunRecord(UUID.randomUUID(), "BARONY-DRAGON", LocalDate.of(2026, 3, 15), "OFX", 2, "COMPLETED", "March"));
        repo.append(new ReconciliationRunRecord(UUID.randomUUID(), "BARONY-DRAGON", LocalDate.of(2026, 4, 15), "QFX", 1, "COMPLETED", "April"));
        repo.append(new ReconciliationRunRecord(UUID.randomUUID(), "BARONY-PHOENIX", LocalDate.of(2026, 3, 15), "OFX", 4, "COMPLETED", "Other"));

        List<ReconciliationRunRecord> rows = repo.findByGroupAndDateRange(
                "BARONY-DRAGON",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31));

        assertEquals(1, rows.size());
        assertEquals("March", rows.get(0).notes());
    }
}
