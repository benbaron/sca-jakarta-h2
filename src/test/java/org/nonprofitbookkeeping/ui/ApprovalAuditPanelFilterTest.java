package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.repository.ApprovalAuditRecord;
import org.nonprofitbookkeeping.repository.ApprovalDecision;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ApprovalAuditPanelFilterTest component.
 */
public class ApprovalAuditPanelFilterTest
{
    @Test
    public void filter_appliesWorkflowDecisionActorAndDateRange()
    {
        List<ApprovalAuditRecord> rows = List.of(
                new ApprovalAuditRecord(UUID.randomUUID(), "BARONY", "RECONCILIATION", UUID.randomUUID(),
                        ApprovalDecision.APPROVED, "alice", "ok", LocalDateTime.of(2026, 4, 1, 10, 0)),
                new ApprovalAuditRecord(UUID.randomUUID(), "BARONY", "PERIOD_CLOSE", UUID.randomUUID(),
                        ApprovalDecision.REJECTED, "bob", "fix", LocalDateTime.of(2026, 4, 2, 10, 0)));

        List<ApprovalAuditRecord> filtered = ApprovalAuditPanel.filter(
                rows,
                "recon",
                "approved",
                "ali",
                "",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 1));

        assertEquals(1, filtered.size());
        assertEquals("RECONCILIATION", filtered.get(0).workflowType());
    }
    @Test
    public void filter_canMatchByRunIdSubstring()
    {
        UUID runId = UUID.randomUUID();
        List<ApprovalAuditRecord> rows = List.of(
                new ApprovalAuditRecord(UUID.randomUUID(), "BARONY", "RECONCILIATION", runId,
                        ApprovalDecision.APPROVED, "alice", "ok", LocalDateTime.of(2026, 4, 1, 10, 0)));

        List<ApprovalAuditRecord> filtered = ApprovalAuditPanel.filter(
                rows,
                "",
                "",
                "",
                runId.toString().substring(0, 8),
                null,
                null);

        assertEquals(1, filtered.size());
    }

}
