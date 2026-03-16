package org.nonprofitbookkeeping.repository;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import org.nonprofitbookkeeping.model.BankingDataFormat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JdbcReconciliationRunRepositoryTest component.
 */
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
                BankingDataFormat.OFX,
                2,
                WorkflowRunStatus.COMPLETED,
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

        repo.append(new ReconciliationRunRecord(UUID.randomUUID(), "BARONY-DRAGON", LocalDate.of(2026, 3, 15), BankingDataFormat.OFX, 2, WorkflowRunStatus.COMPLETED, "March"));
        repo.append(new ReconciliationRunRecord(UUID.randomUUID(), "BARONY-DRAGON", LocalDate.of(2026, 4, 15), BankingDataFormat.QFX, 1, WorkflowRunStatus.COMPLETED, "April"));
        repo.append(new ReconciliationRunRecord(UUID.randomUUID(), "BARONY-PHOENIX", LocalDate.of(2026, 3, 15), BankingDataFormat.OFX, 4, WorkflowRunStatus.COMPLETED, "Other"));

        List<ReconciliationRunRecord> rows = repo.findByGroupAndDateRange(
                "BARONY-DRAGON",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31));

        assertEquals(1, rows.size());
        assertEquals("March", rows.get(0).notes());
    }


    @Test
    public void schema_rejectsUnsupportedBankFormatAndStatusTokens()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();

        assertThrows(IllegalStateException.class, () -> insertInvalid(ds, "CSV", "COMPLETED"));
        assertThrows(IllegalStateException.class, () -> insertInvalid(ds, "OFX", "DONE"));
    }

    private void insertInvalid(DataSource ds, String bankFormat, String status)
    {
        String sql = """
                INSERT INTO reconciliation_run
                (id, group_code, statement_ending_on, bank_format, imported_transaction_count, status, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = ds.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, "BARONY-DRAGON");
            ps.setDate(3, java.sql.Date.valueOf(LocalDate.of(2026, 3, 31)));
            ps.setString(4, bankFormat);
            ps.setInt(5, 1);
            ps.setString(6, status);
            ps.setString(7, "invalid");
            ps.executeUpdate();
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

}
