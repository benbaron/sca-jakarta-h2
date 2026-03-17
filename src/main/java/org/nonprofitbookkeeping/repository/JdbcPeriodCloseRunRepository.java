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
 * JdbcPeriodCloseRunRepository component.
 */
public class JdbcPeriodCloseRunRepository implements PeriodCloseRunRepository
{
    private final DataSource dataSource;

    public JdbcPeriodCloseRunRepository(DataSource dataSource)
    {
        this.dataSource = dataSource;
    }

    @Override
    public void append(PeriodCloseRunRecord record)
    {
        String sql = """
                INSERT INTO period_close_run
                (id, group_code, close_date, status, produced_transaction_id, notes)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setObject(1, record.id());
            ps.setString(2, record.groupCode());
            ps.setDate(3, Date.valueOf(record.closeDate()));
            ps.setString(4, record.status().name());
            ps.setObject(5, record.producedTransactionId());
            ps.setString(6, record.notes());
            ps.executeUpdate();
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException("Could not append period-close run", ex);
        }
    }

    @Override
    public Optional<PeriodCloseRunRecord> findById(UUID id)
    {
        String sql = """
                SELECT id, group_code, close_date, status, produced_transaction_id, notes
                  FROM period_close_run
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
            throw new IllegalStateException("Could not query period-close run", ex);
        }
    }

    @Override
    public List<PeriodCloseRunRecord> findByGroupAndDateRange(String groupCode, LocalDate fromDate, LocalDate toDate)
    {
        String sql = """
                SELECT id, group_code, close_date, status, produced_transaction_id, notes
                  FROM period_close_run
                 WHERE group_code = ?
                   AND close_date BETWEEN ? AND ?
                 ORDER BY close_date, id
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setString(1, groupCode);
            ps.setDate(2, Date.valueOf(fromDate));
            ps.setDate(3, Date.valueOf(toDate));
            try (ResultSet rs = ps.executeQuery())
            {
                List<PeriodCloseRunRecord> runs = new ArrayList<>();
                while (rs.next())
                {
                    runs.add(map(rs));
                }
                return runs;
            }
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException("Could not query period-close runs", ex);
        }
    }

    private static PeriodCloseRunRecord map(ResultSet rs) throws SQLException
    {
        return new PeriodCloseRunRecord(
                rs.getObject("id", UUID.class),
                rs.getString("group_code"),
                rs.getDate("close_date").toLocalDate(),
                WorkflowRunStatus.valueOf(rs.getString("status")),
                rs.getObject("produced_transaction_id", UUID.class),
                rs.getString("notes"));
    }
}
