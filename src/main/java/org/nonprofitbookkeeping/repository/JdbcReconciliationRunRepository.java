package org.nonprofitbookkeeping.repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JdbcReconciliationRunRepository component.
 */
public class JdbcReconciliationRunRepository implements ReconciliationRunRepository
{
    private final DataSource dataSource;

    public JdbcReconciliationRunRepository(DataSource dataSource)
    {
        this.dataSource = dataSource;
    }

    @Override
    public void append(ReconciliationRunRecord record)
    {
        String sql = """
                INSERT INTO reconciliation_run
                (id, group_code, statement_ending_on, bank_format, imported_transaction_count, status, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setObject(1, record.id());
            ps.setString(2, record.groupCode());
            ps.setDate(3, Date.valueOf(record.statementEndingOn()));
            ps.setString(4, record.bankFormat().name());
            ps.setInt(5, record.importedTransactionCount());
            ps.setString(6, record.status().name());
            ps.setString(7, record.notes());
            ps.executeUpdate();
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException("Could not append reconciliation run", ex);
        }
    }

    @Override
    public Optional<ReconciliationRunRecord> findById(UUID id)
    {
        String sql = """
                SELECT id, group_code, statement_ending_on, bank_format, imported_transaction_count, status, notes
                  FROM reconciliation_run
                 WHERE id = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery())
            {
                if (!rs.next())
                {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException("Could not query reconciliation run", ex);
        }
    }

    @Override
    public List<ReconciliationRunRecord> findByGroupAndDateRange(String groupCode, LocalDate fromDate, LocalDate toDate)
    {
        String sql = """
                SELECT id, group_code, statement_ending_on, bank_format, imported_transaction_count, status, notes
                  FROM reconciliation_run
                 WHERE group_code = ?
                   AND statement_ending_on BETWEEN ? AND ?
                 ORDER BY statement_ending_on, id
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setString(1, groupCode);
            ps.setDate(2, Date.valueOf(fromDate));
            ps.setDate(3, Date.valueOf(toDate));
            try (ResultSet rs = ps.executeQuery())
            {
                List<ReconciliationRunRecord> runs = new ArrayList<>();
                while (rs.next())
                {
                    runs.add(map(rs));
                }
                return runs;
            }
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException("Could not query reconciliation runs", ex);
        }
    }

    private static ReconciliationRunRecord map(ResultSet rs) throws SQLException
    {
        return new ReconciliationRunRecord(
                rs.getObject("id", UUID.class),
                rs.getString("group_code"),
                rs.getDate("statement_ending_on").toLocalDate(),
                org.nonprofitbookkeeping.model.BankingDataFormat.valueOf(rs.getString("bank_format")),
                rs.getInt("imported_transaction_count"),
                WorkflowRunStatus.valueOf(rs.getString("status")),
                rs.getString("notes"));
    }
}
