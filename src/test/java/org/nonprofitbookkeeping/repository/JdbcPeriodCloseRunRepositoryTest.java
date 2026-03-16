package org.nonprofitbookkeeping.repository;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JdbcPeriodCloseRunRepositoryTest component.
 */
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
                WorkflowRunStatus.COMPLETED,
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

        repo.append(new PeriodCloseRunRecord(UUID.randomUUID(), "BARONY-DRAGON", LocalDate.of(2026, 2, 28), WorkflowRunStatus.COMPLETED, null, "Feb"));
        repo.append(new PeriodCloseRunRecord(UUID.randomUUID(), "BARONY-DRAGON", LocalDate.of(2026, 3, 31), WorkflowRunStatus.COMPLETED, null, "Mar"));
        repo.append(new PeriodCloseRunRecord(UUID.randomUUID(), "BARONY-PHOENIX", LocalDate.of(2026, 3, 31), WorkflowRunStatus.COMPLETED, null, "Other"));

        List<PeriodCloseRunRecord> rows = repo.findByGroupAndDateRange(
                "BARONY-DRAGON",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31));

        assertEquals(1, rows.size());
        assertEquals("Mar", rows.get(0).notes());
        assertTrue(rows.get(0).closeDate().isEqual(LocalDate.of(2026, 3, 31)));
    }


    @Test
    public void schema_rejectsUnsupportedPeriodCloseStatusToken()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();

        assertThrows(IllegalStateException.class, () -> insertInvalid(ds, "DONE"));
    }

    private void insertInvalid(DataSource ds, String status)
    {
        String sql = """
                INSERT INTO period_close_run
                (id, group_code, close_date, status, produced_transaction_id, notes)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = ds.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, "BARONY-DRAGON");
            ps.setDate(3, java.sql.Date.valueOf(LocalDate.of(2026, 3, 31)));
            ps.setString(4, status);
            ps.setObject(5, null);
            ps.setString(6, "invalid");
            ps.executeUpdate();
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

}
