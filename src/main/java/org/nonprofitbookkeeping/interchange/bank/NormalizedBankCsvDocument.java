package org.nonprofitbookkeeping.interchange.bank;

import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;

import java.util.List;

/** Strict normalized bank CSV 1.0 projection retaining source batch and row identities. */
public record NormalizedBankCsvDocument(
        BankStatementDocument statement,
        List<Batch> batches,
        List<InterchangeValidationMessage> messages)
{
    public NormalizedBankCsvDocument
    {
        if (statement == null)
        {
            throw new IllegalArgumentException("Normalized bank CSV statement projection is required.");
        }
        batches = batches == null ? List.of() : List.copyOf(batches);
        messages = messages == null ? List.of() : List.copyOf(messages);
        if (batches.isEmpty())
        {
            throw new IllegalArgumentException("Normalized bank CSV requires at least one source batch.");
        }
    }

    public record Batch(String externalId, String sourceFormat, String sourceFileName, List<Row> rows)
    {
        public Batch
        {
            externalId = required(externalId, "source batch external ID");
            sourceFormat = required(sourceFormat, "source format");
            sourceFileName = required(sourceFileName, "source file name");
            rows = rows == null ? List.of() : List.copyOf(rows);
            if (rows.isEmpty())
            {
                throw new IllegalArgumentException("Normalized bank CSV source batch has no rows.");
            }
        }
    }

    public record Row(int sourceRowNumber, BankStatementExportRow value)
    {
        public Row
        {
            if (sourceRowNumber < 2 || value == null)
            {
                throw new IllegalArgumentException("Normalized bank CSV row and value are required.");
            }
        }
    }

    private static String required(String value, String label)
    {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank())
        {
            throw new IllegalArgumentException("Normalized bank CSV " + label + " is required.");
        }
        return normalized;
    }
}
