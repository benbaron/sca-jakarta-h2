package org.nonprofitbookkeeping.repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC repository for open-item snapshots and transition history.
 */
public class JdbcOpenItemSnapshotRepository implements OpenItemSnapshotRepository
{
    private final DataSource dataSource;

    public JdbcOpenItemSnapshotRepository(DataSource dataSource)
    {
        this.dataSource = dataSource;
    }

    @Override
    public void create(OpenItemSnapshotRecord snapshot)
    {
        String sql = """
                INSERT INTO open_item_snapshot
                (id, group_code, item_kind, item_ref, state, original_amount, open_amount, last_transaction_id, last_updated_on, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setObject(1, snapshot.id());
            ps.setString(2, snapshot.groupCode());
            ps.setString(3, snapshot.itemKind());
            ps.setString(4, snapshot.itemRef());
            ps.setString(5, snapshot.state());
            ps.setBigDecimal(6, snapshot.originalAmount());
            ps.setBigDecimal(7, snapshot.openAmount());
            ps.setObject(8, snapshot.lastTransactionId());
            ps.setDate(9, Date.valueOf(snapshot.lastUpdatedOn()));
            ps.setLong(10, snapshot.version());
            ps.executeUpdate();
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException("Could not create open-item snapshot", ex);
        }
    }

    @Override
    public void transition(UUID snapshotId, String fromState, String toState, UUID triggerTransactionId, String notes,
                           java.time.LocalDate transitionOn, long expectedVersion)
    {
        try (Connection connection = dataSource.getConnection())
        {
            connection.setAutoCommit(false);
            try
            {
                OpenItemSnapshotRecord current = loadSnapshotForUpdate(connection, snapshotId);
                if (!current.state().equals(fromState))
                {
                    throw new IllegalStateException("Open-item snapshot state mismatch for id " + snapshotId
                            + ": expected " + fromState + " but was " + current.state());
                }
                if (current.version() != expectedVersion)
                {
                    throw new IllegalStateException("Open-item snapshot version mismatch for id " + snapshotId
                            + ": expected " + expectedVersion + " but was " + current.version());
                }

                insertTransition(connection, snapshotId, fromState, toState, triggerTransactionId, notes, transitionOn);
                updateSnapshotState(connection, snapshotId, toState, triggerTransactionId, transitionOn, expectedVersion);
                connection.commit();
            }
            catch (SQLException ex)
            {
                connection.rollback();
                throw ex;
            }
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException("Could not transition open-item snapshot", ex);
        }
    }

    @Override
    public Optional<OpenItemSnapshotRecord> findById(UUID snapshotId)
    {
        String sql = """
                SELECT id, group_code, item_kind, item_ref, state, original_amount, open_amount,
                       last_transaction_id, last_updated_on, version
                  FROM open_item_snapshot
                 WHERE id = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setObject(1, snapshotId);
            try (ResultSet rs = ps.executeQuery())
            {
                if (!rs.next())
                {
                    return Optional.empty();
                }
                return Optional.of(mapSnapshot(rs));
            }
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException("Could not query open-item snapshot", ex);
        }
    }

    @Override
    public List<OpenItemSnapshotRecord> findByGroupAndKind(String groupCode, String itemKind)
    {
        String sql = """
                SELECT id, group_code, item_kind, item_ref, state, original_amount, open_amount,
                       last_transaction_id, last_updated_on, version
                  FROM open_item_snapshot
                 WHERE group_code = ?
                   AND item_kind = ?
                 ORDER BY item_ref
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setString(1, groupCode);
            ps.setString(2, itemKind);
            try (ResultSet rs = ps.executeQuery())
            {
                List<OpenItemSnapshotRecord> rows = new ArrayList<>();
                while (rs.next())
                {
                    rows.add(mapSnapshot(rs));
                }
                return rows;
            }
        }
        catch (SQLException ex)
        {
            throw new IllegalStateException("Could not query open-item snapshots", ex);
        }
    }

    private OpenItemSnapshotRecord loadSnapshotForUpdate(Connection connection, UUID snapshotId) throws SQLException
    {
        String sql = """
                SELECT id, group_code, item_kind, item_ref, state, original_amount, open_amount,
                       last_transaction_id, last_updated_on, version
                  FROM open_item_snapshot
                 WHERE id = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setObject(1, snapshotId);
            try (ResultSet rs = ps.executeQuery())
            {
                if (!rs.next())
                {
                    throw new IllegalStateException("Open-item snapshot not found for id " + snapshotId);
                }
                return mapSnapshot(rs);
            }
        }
    }

    private void insertTransition(Connection connection, UUID snapshotId, String fromState, String toState,
                                  UUID triggerTransactionId, String notes, java.time.LocalDate transitionOn) throws SQLException
    {
        String sql = """
                INSERT INTO open_item_transition
                (snapshot_id, transition_on, from_state, to_state, trigger_transaction_id, notes)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setObject(1, snapshotId);
            ps.setDate(2, Date.valueOf(transitionOn));
            ps.setString(3, fromState);
            ps.setString(4, toState);
            ps.setObject(5, triggerTransactionId);
            ps.setString(6, notes);
            ps.executeUpdate();
        }
    }

    private void updateSnapshotState(Connection connection, UUID snapshotId, String toState,
                                     UUID triggerTransactionId, java.time.LocalDate transitionOn,
                                     long expectedVersion) throws SQLException
    {
        String sql = """
                UPDATE open_item_snapshot
                   SET state = ?,
                       last_transaction_id = ?,
                       last_updated_on = ?,
                       version = version + 1
                 WHERE id = ?
                   AND version = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setString(1, toState);
            ps.setObject(2, triggerTransactionId);
            ps.setDate(3, Date.valueOf(transitionOn));
            ps.setObject(4, snapshotId);
            ps.setLong(5, expectedVersion);
            int updated = ps.executeUpdate();
            if (updated != 1)
            {
                throw new IllegalStateException("Open-item snapshot update failed for id " + snapshotId
                        + " due to concurrent modification");
            }
        }
    }

    private OpenItemSnapshotRecord mapSnapshot(ResultSet rs) throws SQLException
    {
        return new OpenItemSnapshotRecord(
                rs.getObject("id", UUID.class),
                rs.getString("group_code"),
                rs.getString("item_kind"),
                rs.getString("item_ref"),
                rs.getString("state"),
                rs.getBigDecimal("original_amount"),
                rs.getBigDecimal("open_amount"),
                rs.getObject("last_transaction_id", UUID.class),
                rs.getDate("last_updated_on").toLocalDate(),
                rs.getLong("version"));
    }
}
