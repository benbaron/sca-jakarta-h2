package org.nonprofitbookkeeping.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistence DTO for open-item snapshot rows.
 */
public record OpenItemSnapshotRecord(
        UUID id,
        String groupCode,
        String itemKind,
        String itemRef,
        String state,
        BigDecimal originalAmount,
        BigDecimal openAmount,
        UUID lastTransactionId,
        LocalDate lastUpdatedOn)
{
    public OpenItemSnapshotRecord
    {
        id = Objects.requireNonNull(id, "id");
        groupCode = require(groupCode, "groupCode");
        itemKind = require(itemKind, "itemKind");
        itemRef = require(itemRef, "itemRef");
        state = require(state, "state");
        originalAmount = Objects.requireNonNull(originalAmount, "originalAmount");
        openAmount = Objects.requireNonNull(openAmount, "openAmount");
        lastUpdatedOn = Objects.requireNonNull(lastUpdatedOn, "lastUpdatedOn");
    }

    private static String require(String value, String field)
    {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty())
        {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return normalized;
    }
}
