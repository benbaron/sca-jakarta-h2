package org.nonprofitbookkeeping.interchange.bank;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Objects;

/** Exact scope and output profile for one OFX/QFX statement-activity export. */
public record BankStatementOfxExportRequest(
        String companyCode,
        long bankAccountId,
        LocalDate fromDate,
        LocalDate throughDate,
        Path destination,
        boolean overwriteExisting,
        Profile profile)
{
    public BankStatementOfxExportRequest
    {
        companyCode = required(companyCode, "companyCode");
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
        Objects.requireNonNull(profile, "profile");
    }

    BankStatementExportRequest statementRequest()
    {
        return new BankStatementExportRequest(
                companyCode, bankAccountId, fromDate, throughDate, destination, overwriteExisting);
    }

    private static String required(String value, String field)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public enum Profile
    {
        OFX_2_XML,
        QFX_2_XML
    }
}
