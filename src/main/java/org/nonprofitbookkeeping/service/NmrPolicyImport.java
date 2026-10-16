package org.nonprofitbookkeeping.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Retained source save metadata and current policy terms; never an accounting command. */
public record NmrPolicyImport(UUID portableId, String requestHash, Instant savedAt,
        String savedBy, NmrPolicyTerms terms)
{
    public NmrPolicyImport
    {
        Objects.requireNonNull(portableId, "Policy UUID");
        if (requestHash == null || !requestHash.matches("[0-9a-f]{64}"))
        {
            throw new IllegalArgumentException("Policy requestHash must contain 64 lowercase hexadecimal characters.");
        }
        Objects.requireNonNull(savedAt, "Policy savedAt");
        if (savedAt.isBefore(Instant.parse("1900-01-01T00:00:00Z"))
                || savedAt.isAfter(Instant.parse("9999-12-31T23:59:59.999999Z"))
                || savedAt.getNano() % 1000 != 0)
        {
            throw new IllegalArgumentException("Policy savedAt must be a supported database-precision ISO instant.");
        }
        if (savedBy == null || savedBy.isBlank() || savedBy.length() > 200 || !savedBy.equals(savedBy.strip()))
        {
            throw new IllegalArgumentException("Policy savedBy must be nonblank text up to 200 characters.");
        }
        terms = NmrPolicyService.normalize(terms);
    }
}
