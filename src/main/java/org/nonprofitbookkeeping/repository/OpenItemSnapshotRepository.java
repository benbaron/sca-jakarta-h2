package org.nonprofitbookkeeping.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for open-item projection snapshots and state transitions.
 */
public interface OpenItemSnapshotRepository
{
    void create(OpenItemSnapshotRecord snapshot);

    void transition(UUID snapshotId,
                    String fromState,
                    String toState,
                    UUID triggerTransactionId,
                    String notes,
                    LocalDate transitionOn,
                    long expectedVersion);

    Optional<OpenItemSnapshotRecord> findById(UUID snapshotId);

    List<OpenItemSnapshotRecord> findByGroupAndKind(String groupCode, String itemKind);
}
