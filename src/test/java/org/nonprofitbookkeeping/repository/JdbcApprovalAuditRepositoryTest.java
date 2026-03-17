package org.nonprofitbookkeeping.repository;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JdbcApprovalAuditRepositoryTest component.
 */
public class JdbcApprovalAuditRepositoryTest
{
    @Test
    public void appendAndFindById_roundTripsRecord()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcApprovalAuditRepository repository = new JdbcApprovalAuditRepository(ds);

        UUID id = UUID.randomUUID();
        repository.append(new ApprovalAuditRecord(
                id,
                "BARONY-RED",
                "RECONCILIATION",
                UUID.randomUUID(),
                ApprovalDecision.APPROVED,
                "manager@example.org",
                "approved after variance review",
                LocalDateTime.of(2026, 3, 1, 8, 30)));

        ApprovalAuditRecord loaded = repository.findById(id).orElseThrow();
        assertEquals("BARONY-RED", loaded.groupCode());
        assertEquals(ApprovalDecision.APPROVED, loaded.decision());
        assertEquals("manager@example.org", loaded.actor());
    }

    @Test
    public void listByGroup_returnsNewestFirstAndScopesByGroup()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcApprovalAuditRepository repository = new JdbcApprovalAuditRepository(ds);

        repository.append(new ApprovalAuditRecord(
                UUID.randomUUID(), "BARONY-BLUE", "PERIOD_CLOSE", null,
                ApprovalDecision.REJECTED, "auditor1", "need docs", LocalDateTime.of(2026, 3, 1, 8, 30)));
        repository.append(new ApprovalAuditRecord(
                UUID.randomUUID(), "BARONY-BLUE", "PERIOD_CLOSE", null,
                ApprovalDecision.ESCALATED, "auditor2", "to CFO", LocalDateTime.of(2026, 3, 1, 9, 30)));
        repository.append(new ApprovalAuditRecord(
                UUID.randomUUID(), "BARONY-GREEN", "PERIOD_CLOSE", null,
                ApprovalDecision.APPROVED, "auditor3", "ok", LocalDateTime.of(2026, 3, 1, 10, 30)));

        List<ApprovalAuditRecord> rows = repository.listByGroup("BARONY-BLUE", 10);
        assertEquals(2, rows.size());
        assertEquals(ApprovalDecision.ESCALATED, rows.get(0).decision());
        assertEquals(ApprovalDecision.REJECTED, rows.get(1).decision());
        assertTrue(rows.stream().allMatch(r -> "BARONY-BLUE".equals(r.groupCode())));
    }
}
