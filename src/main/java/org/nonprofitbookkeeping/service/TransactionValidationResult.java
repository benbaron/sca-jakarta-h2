package org.nonprofitbookkeeping.service;

import java.util.List;

/**
 * Immutable validation result for transaction command screens and services.
 */
public record TransactionValidationResult(boolean valid,
                                          List<String> errors,
                                          List<String> warnings)
{
    public TransactionValidationResult
    {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static TransactionValidationResult ok(List<String> warnings)
    {
        return new TransactionValidationResult(true, List.of(), warnings);
    }

    public static TransactionValidationResult invalid(List<String> errors, List<String> warnings)
    {
        return new TransactionValidationResult(false, errors, warnings);
    }
}
