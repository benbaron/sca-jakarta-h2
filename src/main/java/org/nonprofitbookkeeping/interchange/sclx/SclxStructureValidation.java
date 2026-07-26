package org.nonprofitbookkeeping.interchange.sclx;

import java.util.List;
import java.util.Objects;

/** Non-mutating structural validation result for a parsed SCLX document. */
public record SclxStructureValidation(
        SclxSectionCounts counts,
        List<String> errors,
        List<String> warnings)
{
    public SclxStructureValidation
    {
        Objects.requireNonNull(counts, "counts");
        errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }

    public boolean valid()
    {
        return errors.isEmpty();
    }
}
