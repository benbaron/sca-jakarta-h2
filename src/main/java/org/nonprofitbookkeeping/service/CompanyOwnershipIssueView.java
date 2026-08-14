package org.nonprofitbookkeeping.service;

import java.time.Instant;
import java.util.List;

public record CompanyOwnershipIssueView(
        Long id,
        String entityType,
        String entityId,
        String recordLabel,
        List<String> relationshipCompanyCodes,
        String issueCode,
        int candidateCompanyCount,
        String details,
        Instant detectedAt)
{
    public CompanyOwnershipIssueView
    {
        relationshipCompanyCodes = List.copyOf(relationshipCompanyCodes);
    }

    public boolean directlyAssignable()
    {
        return "UNRESOLVED_OWNER".equals(issueCode)
                && CompanyOwnershipService.supportsDirectAssignment(entityType)
                && relationshipCompanyCodes.size() <= 1;
    }

    public boolean companyChoiceCompatible(String companyCode)
    {
        return relationshipCompanyCodes.isEmpty()
                || relationshipCompanyCodes.stream().anyMatch(value -> value.equalsIgnoreCase(companyCode));
    }

    public String resolutionGuidance()
    {
        if (directlyAssignable())
        {
            String evidence = relationshipCompanyCodes.isEmpty()
                    ? "No company-owned relationship identifies the owner, so verify it from external historical evidence. "
                    : "Related records identify " + relationshipCompanyCodes.get(0) + " as the only compatible owner. ";
            return evidence + "Select the active company that actually owned this historical record, enter the actor "
                    + "and reason, then choose Assign Owner. Preview the SCLX file again afterward.";
        }
        return "This diagnostic describes conflicting accounting references, not a missing owner. "
                + "Correct the referenced records in their owning workspace or restore a consistent backup; "
                + "this screen will not guess which accounting link to rewrite.";
    }
}
