package org.nonprofitbookkeeping.interchange.coa;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable transient edits/removals; source ordinals never become saved account IDs. */
public record CoaReviewEdits(String sourceSha256, CoaImportScope scope,
        Map<Integer, CoaAccountData> replacements, Set<Integer> excludedRows)
{
    public CoaReviewEdits
    {
        Objects.requireNonNull(sourceSha256, "Original source hash");
        Objects.requireNonNull(scope, "Reviewed destination");
        replacements = Map.copyOf(replacements);
        excludedRows = Set.copyOf(excludedRows);
    }
}
