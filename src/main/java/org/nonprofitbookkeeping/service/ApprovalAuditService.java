package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.repository.ApprovalAuditRecord;
import org.nonprofitbookkeeping.repository.ApprovalAuditRepository;
import org.nonprofitbookkeeping.repository.ApprovalDecision;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ApprovalAuditService component.
 */
public class ApprovalAuditService
{
    private final ApprovalAuditRepository repository;

    public ApprovalAuditService(ApprovalAuditRepository repository)
    {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public UUID recordDecision(String groupCode,
                               String workflowType,
                               UUID workflowRunId,
                               ApprovalDecision decision,
                               String actor,
                               String rationale)
    {
        require(groupCode, "groupCode");
        require(workflowType, "workflowType");
        require(actor, "actor");
        Objects.requireNonNull(decision, "decision");

        UUID id = UUID.randomUUID();
        repository.append(new ApprovalAuditRecord(
                id,
                groupCode,
                workflowType,
                workflowRunId,
                decision,
                actor,
                rationale == null ? "" : rationale,
                LocalDateTime.now()));
        return id;
    }

    public List<ApprovalAuditRecord> listRecent(String groupCode, int maxRows)
    {
        require(groupCode, "groupCode");
        return repository.listByGroup(groupCode, maxRows);
    }

    private static void require(String value, String field)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
