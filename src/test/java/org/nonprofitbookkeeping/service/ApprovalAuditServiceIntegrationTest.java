package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.repository.ApprovalDecision;
import org.nonprofitbookkeeping.repository.JdbcApprovalAuditRepository;
import org.nonprofitbookkeeping.repository.RepositoryIntegrationSupport;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ApprovalAuditServiceIntegrationTest component.
 */
public class ApprovalAuditServiceIntegrationTest
{
    @Test
    public void recordDecision_persistsAndIsQueryable()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        ApprovalAuditService service = new ApprovalAuditService(new JdbcApprovalAuditRepository(ds));

        UUID id = service.recordDecision(
                "BARONY-GOLD",
                "RECONCILIATION",
                UUID.randomUUID(),
                ApprovalDecision.APPROVED,
                "controller@example.org",
                "matches supporting docs");

        assertEquals(1, service.listRecent("BARONY-GOLD", 10).size());
        assertEquals(id, service.listRecent("BARONY-GOLD", 10).get(0).id());
    }

    @Test
    public void recordDecision_validatesRequiredFields()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        ApprovalAuditService service = new ApprovalAuditService(new JdbcApprovalAuditRepository(ds));

        assertThrows(IllegalArgumentException.class,
                () -> service.recordDecision("", "RECONCILIATION", null, ApprovalDecision.REJECTED, "actor", "reason"));
        assertThrows(IllegalArgumentException.class,
                () -> service.recordDecision("BARONY", "", null, ApprovalDecision.REJECTED, "actor", "reason"));
        assertThrows(IllegalArgumentException.class,
                () -> service.recordDecision("BARONY", "PERIOD_CLOSE", null, ApprovalDecision.REJECTED, "", "reason"));
    }
}
