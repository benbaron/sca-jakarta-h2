package org.nonprofitbookkeeping.repository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Record type for ApprovalAuditRecord.
 */
public record ApprovalAuditRecord(UUID id,
                                  String groupCode,
                                  String workflowType,
                                  UUID workflowRunId,
                                  ApprovalDecision decision,
                                  String actor,
                                  String rationale,
                                  LocalDateTime createdAt)
{
}
