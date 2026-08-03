package org.nonprofitbookkeeping.interchange.bank;

import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Result of one deterministic, atomically committed normalized bank CSV export. */
public record BankStatementExportResult(
        Path destination,
        String companyCode,
        String bankAccountExternalId,
        LocalDate fromDate,
        LocalDate throughDate,
        int rowCount,
        long byteCount,
        String sha256,
        List<InterchangeValidationMessage> messages)
{
    public BankStatementExportResult
    {
        destination = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
        companyCode = required(companyCode, "companyCode");
        bankAccountExternalId = required(bankAccountExternalId, "bankAccountExternalId");
        Objects.requireNonNull(fromDate, "fromDate");
        Objects.requireNonNull(throughDate, "throughDate");
        if (rowCount < 0 || byteCount < 0L)
        {
            throw new IllegalArgumentException("Export counts cannot be negative");
        }
        sha256 = required(sha256, "sha256").toLowerCase(java.util.Locale.ROOT);
        if (!sha256.matches("[0-9a-f]{64}"))
        {
            throw new IllegalArgumentException("sha256 must be a lowercase SHA-256 value");
        }
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
    }

    private static String required(String value, String field)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
