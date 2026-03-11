package org.nonprofitbookkeeping.repository;

import java.math.BigDecimal;
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

    default void transition(UUID snapshotId,
                            String fromState,
                            String toState,
                            UUID triggerTransactionId,
                            String notes,
                            LocalDate transitionOn,
                            long expectedVersion)
    {
        transition(snapshotId, fromState, toState, null, triggerTransactionId, notes, transitionOn, expectedVersion);
    }

    void transition(UUID snapshotId,
                    String fromState,
                    String toState,
                    BigDecimal newOpenAmount,
                    UUID triggerTransactionId,
                    String notes,
                    LocalDate transitionOn,
                    long expectedVersion);

    Optional<OpenItemSnapshotRecord> findById(UUID snapshotId);

    List<OpenItemSnapshotRecord> findByGroupAndKind(String groupCode, OpenItemKind itemKind);

    Optional<OpenItemSnapshotRecord> findByGroupKindAndItemRef(String groupCode, OpenItemKind itemKind, String itemRef);
}
