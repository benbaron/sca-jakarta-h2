package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.interchange.InterchangeFormat;

import java.time.Instant;

public record InterchangeIdentityView(
        Long id,
        String companyCode,
        InterchangeFormat format,
        String sourceSystem,
        String entityType,
        String externalId,
        String normalizedContentHash,
        String localEntityId,
        Instant createdAt,
        Instant updatedAt)
{
}
