package org.nonprofitbookkeeping.repository;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JdbcPeriodCloseRunRepositoryTest
{
    @Test
    public void appendAndFindById_roundTripsRecord()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcPeriodCloseRunRepository repo = new JdbcPeriodCloseRunRepository(ds);

        PeriodCloseRunRecord record = new PeriodCloseRunRecord(
                UUID.randomUUID(),
                "BARONY-DRAGON",
                LocalDate.of(2026, 3, 31),
                "COMPLETED",
                null,
                "March close completed");

        repo.append(record);

        PeriodCloseRunRecord loaded = repo.findById(record.id()).orElseThrow();
        assertEquals(record.groupCode(), loaded.groupCode());
        assertEquals(record.closeDate(), loaded.closeDate());
        assertEquals(record.status(), loaded.status());
        assertEquals(record.notes(), loaded.notes());
    }

    @Test
    public void findByGroupAndDateRange_filtersAndSortsByCloseDate()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcPeriodCloseRunRepository repo = new JdbcPeriodCloseRunRepository(ds);

        repo.append(new PeriodCloseRunRecord(UUID.randomUUID(), "BARONY-DRAGON", LocalDate.of(2026, 2, 28), "COMPLETED", null, "Feb"));
        repo.append(new PeriodCloseRunRecord(UUID.randomUUID(), "BARONY-DRAGON", LocalDate.of(2026, 3, 31), "COMPLETED", null, "Mar"));
        repo.append(new PeriodCloseRunRecord(UUID.randomUUID(), "BARONY-PHOENIX", LocalDate.of(2026, 3, 31), "COMPLETED", null, "Other"));

        List<PeriodCloseRunRecord> rows = repo.findByGroupAndDateRange(
                "BARONY-DRAGON",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31));

        assertEquals(1, rows.size());
        assertEquals("Mar", rows.get(0).notes());
        assertTrue(rows.get(0).closeDate().isEqual(LocalDate.of(2026, 3, 31)));
    }
}
