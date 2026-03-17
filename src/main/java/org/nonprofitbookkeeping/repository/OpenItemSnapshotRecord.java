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
        OpenItemKind itemKind,
        String itemRef,
        String state,
        BigDecimal originalAmount,
        BigDecimal openAmount,
        UUID lastTransactionId,
        LocalDate lastUpdatedOn,
        long version)
{
    public OpenItemSnapshotRecord
    {
        id = Objects.requireNonNull(id, "id");
        groupCode = require(groupCode, "groupCode");
        itemKind = Objects.requireNonNull(itemKind, "itemKind");
        itemRef = require(itemRef, "itemRef");
        state = require(state, "state");
        originalAmount = Objects.requireNonNull(originalAmount, "originalAmount");
        openAmount = Objects.requireNonNull(openAmount, "openAmount");
        lastUpdatedOn = Objects.requireNonNull(lastUpdatedOn, "lastUpdatedOn");

        if (version < 0)
        {
            throw new IllegalArgumentException("version cannot be negative");
        }
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
