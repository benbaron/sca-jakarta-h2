package org.nonprofitbookkeeping.interchange.bank;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** Deterministic UTF-8 RFC 4180 serializer for the governed normalized bank CSV 1.0 schema. */
final class NormalizedBankCsvSerializer
{
    static final String HEADER = String.join(",",
            "record_version", "source_format", "source_batch_external_id", "source_file_name",
            "statement_line_external_id", "institution_id", "bank_id", "account_id", "account_type",
            "transaction_date", "posted_date", "amount", "currency", "source_transaction_id",
            "transaction_type", "payee_id", "payee_name", "memo", "check_number", "reference",
            "correction_action", "corrected_source_transaction_id", "statement_start_date",
            "statement_end_date", "ledger_balance", "available_balance", "review_status",
            "duplicate_status", "matched_transaction_external_id");

    byte[] serialize(List<BankStatementExportRow> rows)
    {
        List<BankStatementExportRow> values = List.copyOf(rows);
        StringBuilder output = new StringBuilder(Math.max(1024, values.size() * 256));
        output.append(HEADER).append('\n');
        for (BankStatementExportRow row : values)
        {
            append(output, List.of(
                    "1.0",
                    row.sourceFormat(),
                    row.sourceBatchExternalId(),
                    row.sourceFileName(),
                    row.statementLineExternalId(),
                    row.institutionId(),
                    row.bankId(),
                    row.accountId(),
                    row.accountType(),
                    date(row.transactionDate()),
                    date(row.postedDate()),
                    decimal(row.amount()),
                    row.currency(),
                    row.sourceTransactionId(),
                    row.transactionType(),
                    row.payeeId(),
                    row.payeeName(),
                    row.memo(),
                    row.checkNumber(),
                    row.reference(),
                    row.correctionAction(),
                    row.correctedSourceTransactionId(),
                    date(row.statementStartDate()),
                    date(row.statementEndDate()),
                    decimal(row.ledgerBalance()),
                    decimal(row.availableBalance()),
                    row.reviewStatus(),
                    row.duplicateStatus(),
                    row.matchedTransactionExternalId()));
        }
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void append(StringBuilder output, List<String> fields)
    {
        for (int index = 0; index < fields.size(); index++)
        {
            if (index > 0)
            {
                output.append(',');
            }
            output.append(escape(fields.get(index)));
        }
        output.append('\n');
    }

    private static String escape(String value)
    {
        String normalized = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.indexOf(',') < 0 && normalized.indexOf('"') < 0 && normalized.indexOf('\n') < 0)
        {
            return normalized;
        }
        return '"' + normalized.replace("\"", "\"\"") + '"';
    }

    private static String date(LocalDate value)
    {
        return value == null ? "" : value.toString();
    }

    private static String decimal(BigDecimal value)
    {
        if (value == null)
        {
            return "";
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0).toPlainString() : normalized.toPlainString();
    }
}
