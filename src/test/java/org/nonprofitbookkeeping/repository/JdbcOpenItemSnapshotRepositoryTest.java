package org.nonprofitbookkeeping.repository;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.testutil.TestAmountAssertions.assertAmountEquals;

/**
 * JdbcOpenItemSnapshotRepositoryTest component.
 */
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
                OpenItemKind.RECEIVABLE,
                "AR-2026-001",
                "OPEN",
                new BigDecimal("120.00"),
                new BigDecimal("120.00"),
                null,
                LocalDate.of(2026, 4, 10),
                0);

        repository.create(snapshot);

        OpenItemSnapshotRecord loaded = repository.findById(snapshot.id()).orElseThrow();
        assertEquals(OpenItemKind.RECEIVABLE, loaded.itemKind());
        assertEquals("OPEN", loaded.state());
        assertEquals(0, loaded.version());
    }

    @Test
    public void create_rejectsInvalidStateTokenForKind()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcOpenItemSnapshotRepository repository = new JdbcOpenItemSnapshotRepository(ds);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> repository.create(new OpenItemSnapshotRecord(
                UUID.randomUUID(),
                "BARONY-RED",
                OpenItemKind.PREPAID_EXPENSE,
                "PP-2026-001",
                "SETTLED_BY_CASH",
                new BigDecimal("40.00"),
                new BigDecimal("40.00"),
                null,
                LocalDate.of(2026, 4, 10),
                0)));

        assertTrue(ex.getMessage().contains("Invalid state token"));
    }

    @Test
    public void transition_updatesSnapshotState_andQueryByGroupKindWorks()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcOpenItemSnapshotRepository repository = new JdbcOpenItemSnapshotRepository(ds);

        UUID id = UUID.randomUUID();
        repository.create(new OpenItemSnapshotRecord(
                id,
                "BARONY-RED",
                OpenItemKind.RECEIVABLE,
                "AR-2026-002",
                "OPEN",
                new BigDecimal("75.00"),
                new BigDecimal("75.00"),
                null,
                LocalDate.of(2026, 4, 10),
                0));

        repository.transition(id, "OPEN", "SETTLED_BY_CASH", null, "Paid by check", LocalDate.of(2026, 4, 11), 0);

        OpenItemSnapshotRecord loaded = repository.findById(id).orElseThrow();
        assertEquals("SETTLED_BY_CASH", loaded.state());
        assertAmountEquals("75.00", loaded.openAmount());
        assertNull(loaded.lastTransactionId());
        assertEquals(1, loaded.version());
        assertEquals(1, transitionCount(ds, id));

        List<OpenItemSnapshotRecord> rows = repository.findByGroupAndKind("BARONY-RED", OpenItemKind.RECEIVABLE);
        assertEquals(1, rows.size());
        assertEquals("AR-2026-002", rows.get(0).itemRef());
    }

    @Test
    public void transition_rejectsPolicyDisallowedTransition_andDoesNotRecordHistory()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcOpenItemSnapshotRepository repository = new JdbcOpenItemSnapshotRepository(ds);

        UUID id = UUID.randomUUID();

        repository.create(new OpenItemSnapshotRecord(
                id,
                "BARONY-RED",
                OpenItemKind.RECEIVABLE,
                "AR-2026-099",
                "SETTLED_BY_CASH",
                new BigDecimal("10.00"),
                new BigDecimal("0.00"),
                null,
                LocalDate.of(2026, 4, 10),
                0));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                repository.transition(id, "SETTLED_BY_CASH", "OPEN", null, "invalid lifecycle", LocalDate.of(2026, 4, 11), 0));

        assertTrue(ex.getMessage().contains("Transition not allowed"));
        assertEquals(0, transitionCount(ds, id));

        OpenItemSnapshotRecord loaded = repository.findById(id).orElseThrow();
        assertEquals("SETTLED_BY_CASH", loaded.state());
        assertEquals(0, loaded.version());
    }

    @Test
    public void transition_rejectsInvalidStateTokenForKind()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcOpenItemSnapshotRepository repository = new JdbcOpenItemSnapshotRepository(ds);

        UUID id = UUID.randomUUID();
        repository.create(new OpenItemSnapshotRecord(
                id,
                "BARONY-RED",
                OpenItemKind.PAYABLE,
                "AP-2026-009",
                "OPEN",
                new BigDecimal("50.00"),
                new BigDecimal("50.00"),
                null,
                LocalDate.of(2026, 4, 10),
                0));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                repository.transition(id, "OPEN", "SETTLED_BY_CASH", null, "invalid token", LocalDate.of(2026, 4, 11), 0));

        assertTrue(ex.getMessage().contains("Invalid toState token"));
        assertEquals(0, transitionCount(ds, id));
    }

    @Test
    public void transition_rejectsUnexpectedState_andDoesNotRecordChange()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcOpenItemSnapshotRepository repository = new JdbcOpenItemSnapshotRepository(ds);

        UUID id = UUID.randomUUID();

        repository.create(new OpenItemSnapshotRecord(
                id,
                "BARONY-RED",
                OpenItemKind.PAYABLE,
                "AP-2026-010",
                "OPEN",
                new BigDecimal("50.00"),
                new BigDecimal("50.00"),
                null,
                LocalDate.of(2026, 4, 10),
                0));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                repository.transition(id, "PAID", "REVERSED", null, "bad request", LocalDate.of(2026, 4, 11), 0));

        assertTrue(ex.getMessage().contains("state mismatch"));
        assertEquals(0, transitionCount(ds, id));

        OpenItemSnapshotRecord loaded = repository.findById(id).orElseThrow();
        assertEquals("OPEN", loaded.state());
        assertEquals(0, loaded.version());
    }

    @Test
    public void transition_rejectsUnexpectedVersion_andDoesNotRecordChange()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcOpenItemSnapshotRepository repository = new JdbcOpenItemSnapshotRepository(ds);

        UUID id = UUID.randomUUID();

        repository.create(new OpenItemSnapshotRecord(
                id,
                "BARONY-RED",
                OpenItemKind.RECEIVABLE,
                "AR-2026-003",
                "OPEN",
                new BigDecimal("80.00"),
                new BigDecimal("80.00"),
                null,
                LocalDate.of(2026, 4, 10),
                0));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                repository.transition(id, "OPEN", "PARTIALLY_APPLIED", null, "stale version", LocalDate.of(2026, 4, 11), 99));

        assertTrue(ex.getMessage().contains("version mismatch"));
        assertEquals(0, transitionCount(ds, id));

        OpenItemSnapshotRecord loaded = repository.findById(id).orElseThrow();
        assertEquals("OPEN", loaded.state());
        assertEquals(0, loaded.version());
    }


    @Test
    public void transition_withOpenAmountUpdate_persistsNewAmount()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcOpenItemSnapshotRepository repository = new JdbcOpenItemSnapshotRepository(ds);

        UUID id = UUID.randomUUID();
        repository.create(new OpenItemSnapshotRecord(
                id,
                "BARONY-RED",
                OpenItemKind.RECEIVABLE,
                "AR-2026-004",
                "OPEN",
                new BigDecimal("90.00"),
                new BigDecimal("90.00"),
                null,
                LocalDate.of(2026, 4, 10),
                0));

        repository.transition(id, "OPEN", "PARTIALLY_APPLIED", new BigDecimal("40.00"),
                null, "partial payment", LocalDate.of(2026, 4, 11), 0);

        OpenItemSnapshotRecord loaded = repository.findById(id).orElseThrow();
        assertEquals("PARTIALLY_APPLIED", loaded.state());
        assertAmountEquals("40.00", loaded.openAmount());
        assertEquals(1, loaded.version());
    }

    @Test
    public void findByGroupKindAndItemRef_returnsSnapshotWhenPresent()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcOpenItemSnapshotRepository repository = new JdbcOpenItemSnapshotRepository(ds);

        UUID id = UUID.randomUUID();
        repository.create(new OpenItemSnapshotRecord(
                id,
                "BARONY-RED",
                OpenItemKind.PREPAID_EXPENSE,
                "PP-2026-011",
                "OPEN",
                new BigDecimal("42.00"),
                new BigDecimal("42.00"),
                null,
                LocalDate.of(2026, 4, 10),
                0));

        OpenItemSnapshotRecord loaded = repository.findByGroupKindAndItemRef("BARONY-RED", OpenItemKind.PREPAID_EXPENSE, "PP-2026-011")
                .orElseThrow();

        assertEquals(id, loaded.id());
    }

    @Test
    public void findById_rejectsUnsupportedPersistedItemKind()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcOpenItemSnapshotRepository repository = new JdbcOpenItemSnapshotRepository(ds);
        UUID id = UUID.randomUUID();

        insertInvalidItemKindRow(ds, id);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> repository.findById(id));
        assertTrue(ex.getMessage().contains("Unsupported open-item kind"));
    }

    private void insertInvalidItemKindRow(DataSource ds, UUID id)
    {
        String sql = """
                INSERT INTO open_item_snapshot
                (id, group_code, item_kind, item_ref, state, original_amount, open_amount, last_transaction_id, last_updated_on, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = ds.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql))
        {
            ps.setObject(1, id);
            ps.setString(2, "BARONY-RED");
            ps.setString(3, "NOT_A_KIND");
            ps.setString(4, "BAD-1");
            ps.setString(5, "OPEN");
            ps.setBigDecimal(6, new BigDecimal("10.00"));
            ps.setBigDecimal(7, new BigDecimal("10.00"));
            ps.setObject(8, null);
            ps.setDate(9, Date.valueOf(LocalDate.of(2026, 4, 10)));
            ps.setLong(10, 0);
            ps.executeUpdate();
        }
        catch (Exception ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    private int transitionCount(DataSource ds, UUID snapshotId)
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
            throw new IllegalStateException("Could not count transitions", ex);
        }
    }

}
