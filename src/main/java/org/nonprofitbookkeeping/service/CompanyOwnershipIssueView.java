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
                    ? "No company-owned relationship conflicts with the selected import target. "
                    : "Related records require " + relationshipCompanyCodes.get(0) + " as the compatible target. ";
            return evidence + "Confirm the active company receiving the import, enter the actor and an audit note, "
                    + "then choose Assign to Import Company. Re-preview the same SCLX file afterward.";
        }
        return "This diagnostic describes conflicting accounting references, not a missing owner. "
                + "Correct the referenced records in their owning workspace or restore a consistent backup; "
                + "this screen will not guess which accounting link to rewrite.";
    }
}
