package org.nonprofitbookkeeping.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalAuditRepository
{
    void append(ApprovalAuditRecord record);

    Optional<ApprovalAuditRecord> findById(UUID id);

    List<ApprovalAuditRecord> listByGroup(String groupCode, int maxRows);
}
