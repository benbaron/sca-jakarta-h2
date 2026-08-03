package org.nonprofitbookkeeping.interchange.bank;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Objects;

/** Exact company, configured-account, date-range, and file scope for one bank CSV export. */
public record BankStatementExportRequest(
        String companyCode,
        long bankAccountId,
        LocalDate fromDate,
        LocalDate throughDate,
        Path destination,
        boolean overwriteExisting)
{
    public BankStatementExportRequest
    {
        companyCode = requiredText(companyCode, "companyCode");
        if (bankAccountId < 1L)
        {
            throw new IllegalArgumentException("bankAccountId must be positive");
        }
        Objects.requireNonNull(fromDate, "fromDate");
        Objects.requireNonNull(throughDate, "throughDate");
        if (fromDate.isAfter(throughDate))
        {
            throw new IllegalArgumentException("fromDate must be on or before throughDate");
        }
        destination = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
    }

    private static String requiredText(String value, String field)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
