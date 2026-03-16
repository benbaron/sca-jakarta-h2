package org.nonprofitbookkeeping.repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JdbcApprovalAuditRepository component.
 */
public class JdbcApprovalAuditRepository implements ApprovalAuditRepository
{
    private final DataSource dataSource;

    public JdbcApprovalAuditRepository(DataSource dataSource)
    {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public void append(ApprovalAuditRecord record)
    {
        Objects.requireNonNull(record, "record");
        String sql = """
                INSERT INTO approval_audit_record
                (id, group_code, workflow_type, workflow_run_id, decision, actor, rationale, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql))
        {
            ps.setObject(1, record.id());
            ps.setString(2, record.groupCode());
            ps.setString(3, record.workflowType());
            ps.setObject(4, record.workflowRunId());
            ps.setString(5, record.decision().name());
            ps.setString(6, record.actor());
            ps.setString(7, record.rationale());
            ps.setTimestamp(8, Timestamp.valueOf(record.createdAt()));
            ps.executeUpdate();
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("Failed to append approval audit record", ex);
        }
    }

    @Override
    public Optional<ApprovalAuditRecord> findById(UUID id)
    {
        String sql = """
                SELECT id, group_code, workflow_type, workflow_run_id, decision, actor, rationale, created_at
                FROM approval_audit_record
                WHERE id = ?
                """;

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql))
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
        catch (Exception ex)
        {
            throw new IllegalStateException("Failed to load approval audit record", ex);
        }
    }

    @Override
    public List<ApprovalAuditRecord> listByGroup(String groupCode, int maxRows)
    {
        String sql = """
                SELECT id, group_code, workflow_type, workflow_run_id, decision, actor, rationale, created_at
                FROM approval_audit_record
                WHERE group_code = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """;

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql))
        {
            ps.setString(1, groupCode);
            ps.setInt(2, Math.max(1, maxRows));

            List<ApprovalAuditRecord> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                {
                    out.add(map(rs));
                }
            }
            return out;
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("Failed to list approval audit records", ex);
        }
    }

    private static ApprovalAuditRecord map(ResultSet rs) throws Exception
    {
        return new ApprovalAuditRecord(
                rs.getObject("id", UUID.class),
                rs.getString("group_code"),
                rs.getString("workflow_type"),
                rs.getObject("workflow_run_id", UUID.class),
                ApprovalDecision.valueOf(rs.getString("decision")),
                rs.getString("actor"),
                rs.getString("rationale"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }
}
