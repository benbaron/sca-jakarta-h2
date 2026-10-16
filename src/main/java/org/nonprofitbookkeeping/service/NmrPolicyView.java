package org.nonprofitbookkeeping.service;

import java.time.Instant;
import java.util.UUID;

/** Detached retained policy version for company administration. */
public record NmrPolicyView(Long id, UUID portableId, String companyCode,
        Instant savedAt, String savedBy, NmrPolicyTerms terms)
{
}
