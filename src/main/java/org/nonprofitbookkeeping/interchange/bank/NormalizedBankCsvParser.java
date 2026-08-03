package org.nonprofitbookkeeping.interchange.bank;

import org.nonprofitbookkeeping.interchange.InterchangeMessageSeverity;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.BankingDataFormat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Strict bounded reader for the frozen 29-column normalized bank CSV 1.0 format. */
public final class NormalizedBankCsvParser
{
    private static final int COLUMN_COUNT = 29;
    private static final int MAX_EXTERNAL_ID_LENGTH = 200;

    public NormalizedBankCsvDocument parse(Path source)
    {
        if (source == null)
        {
            throw new IllegalArgumentException("Normalized bank CSV source is required.");
        }
        Path exact = source.toAbsolutePath().normalize();
        String csv = BankCsvParser.decode(BankCsvParser.read(exact), "UTF-8");
        List<BankCsvParser.CsvRecord> records = BankCsvParser.records(csv, ',');
        if (records.size() < 2)
        {
            throw new IllegalArgumentException("Normalized bank CSV requires a header and at least one data row.");
        }
        List<String> expected = List.of(NormalizedBankCsvSerializer.HEADER.split(",", -1));
        if (!records.get(0).fields().equals(expected))
        {
            throw new IllegalArgumentException("Normalized bank CSV header must exactly match version 1.0.");
        }

        Map<String, BatchBuilder> batches = new LinkedHashMap<>();
        Set<String> rowExternalIds = new HashSet<>();
        List<BankStatementDocument.Transaction> transactions = new ArrayList<>();
        List<InterchangeValidationMessage> messages = new ArrayList<>();
        String institutionId = null;
        String bankId = null;
        String accountId = null;
        String accountType = null;
        String currency = null;
        LocalDate minimumDate = null;
        LocalDate maximumDate = null;

        for (int index = 1; index < records.size(); index++)
        {
            BankCsvParser.CsvRecord record = records.get(index);
            if (record.fields().stream().allMatch(String::isBlank))
            {
                continue;
            }
            if (record.fields().size() != COLUMN_COUNT)
            {
                throw rowError(record, "must contain exactly 29 columns.");
            }
            List<String> field = record.fields();
            if (!"1.0".equals(field.get(0)))
            {
                throw rowError(record, "uses unsupported record_version: " + field.get(0) + ".");
            }
            String format = enumValue(field.get(1), BankImportBatch.SourceFormat.class, record, "source_format");
            String batchExternalId = externalId(field.get(2), record, "source_batch_external_id");
            String sourceFileName = required(field.get(3), record, "source_file_name", 260);
            String lineExternalId = externalId(field.get(4), record, "statement_line_external_id");
            if (!rowExternalIds.add(lineExternalId))
            {
                throw rowError(record, "duplicates statement_line_external_id " + lineExternalId + ".");
            }
            String rowInstitutionId = optional(field.get(5), record, "institution_id", 120);
            String rowBankId = optional(field.get(6), record, "bank_id", 80);
            String rowAccountId = required(field.get(7), record, "account_id", 160);
            String rowAccountType = optional(field.get(8), record, "account_type", 80);
            LocalDate transactionDate = date(field.get(9), record, "transaction_date");
            LocalDate postedDate = date(field.get(10), record, "posted_date");
            if (transactionDate == null && postedDate == null)
            {
                throw rowError(record, "requires transaction_date or posted_date.");
            }
            BigDecimal amount = decimal(field.get(11), record, "amount", true, false);
            String rowCurrency = required(field.get(12), record, "currency", 3).toUpperCase(Locale.ROOT);
            if (!rowCurrency.matches("[A-Z]{3}"))
            {
                throw rowError(record, "has invalid currency " + rowCurrency + ".");
            }
            String sourceTransactionId = optional(field.get(13), record, "source_transaction_id", 160);
            String transactionType = optional(field.get(14), record, "transaction_type", 40);
            String payeeId = optional(field.get(15), record, "payee_id", 200);
            String payeeName = optional(field.get(16), record, "payee_name", 260);
            String memo = optional(field.get(17), record, "memo", 1000);
            String checkNumber = optional(field.get(18), record, "check_number", 80);
            String reference = optional(field.get(19), record, "reference", 160);
            String correctionAction = optional(field.get(20), record, "correction_action", 20).toUpperCase(Locale.ROOT);
            String correctedSourceId = optional(field.get(21), record, "corrected_source_transaction_id", 160);
            if (correctionAction.isBlank() != correctedSourceId.isBlank())
            {
                throw rowError(record, "must provide correction_action and corrected_source_transaction_id together.");
            }
            if (!correctionAction.isBlank() && !Set.of("DELETE", "REPLACE").contains(correctionAction))
            {
                throw rowError(record, "has unsupported correction_action " + correctionAction + ".");
            }
            LocalDate statementStart = date(field.get(22), record, "statement_start_date");
            LocalDate statementEnd = date(field.get(23), record, "statement_end_date");
            if (statementStart != null && statementEnd != null && statementStart.isAfter(statementEnd))
            {
                throw rowError(record, "has statement_start_date after statement_end_date.");
            }
            BigDecimal ledgerBalance = decimal(field.get(24), record, "ledger_balance", false, true);
            BigDecimal availableBalance = decimal(field.get(25), record, "available_balance", false, true);
            String reviewStatus = enumValue(field.get(26), BankStatementLine.Status.class, record, "review_status");
            String duplicateStatus = optional(field.get(27), record, "duplicate_status", 20).toUpperCase(Locale.ROOT);
            if (!Set.of("", "UNIQUE", "EXACT", "PROBABLE").contains(duplicateStatus))
            {
                throw rowError(record, "has unsupported duplicate_status " + duplicateStatus + ".");
            }
            if (("EXACT".equals(duplicateStatus)) != "DUPLICATE".equals(reviewStatus))
            {
                throw rowError(record, "must pair duplicate_status EXACT with review_status DUPLICATE.");
            }
            String matchedTransactionId = optional(
                    field.get(28), record, "matched_transaction_external_id", MAX_EXTERNAL_ID_LENGTH);
            if ("MATCHED".equals(reviewStatus) != !matchedTransactionId.isBlank())
            {
                throw rowError(record, "must pair review_status MATCHED with matched_transaction_external_id.");
            }

            institutionId = firstNonBlank(institutionId, rowInstitutionId);
            bankId = firstNonBlank(bankId, rowBankId);
            accountId = firstNonBlank(accountId, rowAccountId);
            accountType = firstNonBlank(accountType, rowAccountType);
            currency = firstNonBlank(currency, rowCurrency);
            LocalDate effectiveDate = postedDate == null ? transactionDate : postedDate;
            minimumDate = minimumDate == null || effectiveDate.isBefore(minimumDate) ? effectiveDate : minimumDate;
            maximumDate = maximumDate == null || effectiveDate.isAfter(maximumDate) ? effectiveDate : maximumDate;

            BankStatementExportRow row = new BankStatementExportRow(
                    format, batchExternalId, sourceFileName, lineExternalId,
                    rowInstitutionId, rowBankId, rowAccountId, rowAccountType,
                    transactionDate, postedDate, amount, rowCurrency, sourceTransactionId,
                    transactionType, payeeId, payeeName, memo, checkNumber, reference,
                    correctionAction, correctedSourceId, statementStart, statementEnd,
                    ledgerBalance, availableBalance, reviewStatus, duplicateStatus,
                    matchedTransactionId);
            batches.computeIfAbsent(batchExternalId,
                    ignored -> new BatchBuilder(batchExternalId, format, sourceFileName, row))
                    .add(record.lineNumber(), row);
            transactions.add(new BankStatementDocument.Transaction(
                    record.lineNumber(), transactionDate, postedDate, amount, sourceTransactionId,
                    transactionType, payeeName, memo, checkNumber, reference,
                    correctionAction, correctedSourceId));
        }
        if (transactions.isEmpty())
        {
            throw new IllegalArgumentException("Normalized bank CSV contains no data rows.");
        }
        messages.add(new InterchangeValidationMessage(
                InterchangeMessageSeverity.INFO,
                "NORMALIZED_BANK_CSV_RECOGNIZED",
                "document",
                "Recognized normalized bank CSV 1.0 with " + transactions.size()
                        + " row(s) in " + batches.size() + " source batch(es).",
                false));
        BankStatementDocument statement = new BankStatementDocument(
                exact.getFileName().toString(), BankingDataFormat.CSV,
                BankStatementDocument.Variant.NORMALIZED_CSV, "1.0", "UTF-8",
                new BankStatementDocument.AccountIdentity(
                        empty(institutionId), empty(bankId), accountId, empty(accountType)),
                currency, minimumDate, maximumDate, null, null, transactions, messages);
        return new NormalizedBankCsvDocument(
                statement,
                batches.values().stream().map(BatchBuilder::build).toList(),
                messages);
    }

    private static String firstNonBlank(String established, String candidate)
    {
        if (established == null || established.isBlank())
        {
            return candidate;
        }
        return established;
    }

    private static LocalDate date(String value, BankCsvParser.CsvRecord record, String field)
    {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank())
        {
            return null;
        }
        try
        {
            return LocalDate.parse(normalized);
        }
        catch (DateTimeParseException ex)
        {
            throw rowError(record, "has invalid " + field + " " + normalized + ".");
        }
    }

    private static BigDecimal decimal(
            String value,
            BankCsvParser.CsvRecord record,
            String field,
            boolean required,
            boolean allowZero)
    {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank())
        {
            if (required)
            {
                throw rowError(record, "is missing " + field + ".");
            }
            return null;
        }
        try
        {
            BigDecimal parsed = new BigDecimal(normalized);
            if ((!allowZero && parsed.signum() == 0)
                    || parsed.scale() > 4 || parsed.precision() - parsed.scale() > 15)
            {
                throw rowError(record, field + " must fit nonzero DECIMAL(19,4).");
            }
            return parsed;
        }
        catch (NumberFormatException ex)
        {
            throw rowError(record, "has invalid " + field + " " + normalized + ".");
        }
    }

    private static <E extends Enum<E>> String enumValue(
            String value, Class<E> type, BankCsvParser.CsvRecord record, String field)
    {
        String normalized = required(value, record, field, 40).toUpperCase(Locale.ROOT);
        try
        {
            return Enum.valueOf(type, normalized).name();
        }
        catch (IllegalArgumentException ex)
        {
            throw rowError(record, "has unsupported " + field + " " + normalized + ".");
        }
    }

    private static String externalId(String value, BankCsvParser.CsvRecord record, String field)
    {
        return required(value, record, field, MAX_EXTERNAL_ID_LENGTH);
    }

    private static String required(
            String value, BankCsvParser.CsvRecord record, String field, int maximumLength)
    {
        String normalized = optional(value, record, field, maximumLength);
        if (normalized.isBlank())
        {
            throw rowError(record, "is missing " + field + ".");
        }
        return normalized;
    }

    private static String optional(
            String value, BankCsvParser.CsvRecord record, String field, int maximumLength)
    {
        String normalized = value == null ? "" : value.trim();
        if (normalized.codePointCount(0, normalized.length()) > maximumLength)
        {
            throw rowError(record, field + " exceeds " + maximumLength + " characters.");
        }
        return normalized;
    }

    private static String empty(String value)
    {
        return value == null ? "" : value;
    }

    private static IllegalArgumentException rowError(BankCsvParser.CsvRecord record, String message)
    {
        return new IllegalArgumentException(
                "Normalized bank CSV row " + record.lineNumber() + " " + message);
    }

    private static final class BatchBuilder
    {
        private final String externalId;
        private final String sourceFormat;
        private final String sourceFileName;
        private final Map<String, String> metadata = new HashMap<>();
        private final List<NormalizedBankCsvDocument.Row> rows = new ArrayList<>();

        private BatchBuilder(
                String externalId, String sourceFormat, String sourceFileName, BankStatementExportRow first)
        {
            this.externalId = externalId;
            this.sourceFormat = sourceFormat;
            this.sourceFileName = sourceFileName;
            remember(first);
        }

        private void add(int sourceRowNumber, BankStatementExportRow row)
        {
            if (!sourceFormat.equals(row.sourceFormat()) || !sourceFileName.equals(row.sourceFileName()))
            {
                throw new IllegalArgumentException(
                        "Normalized bank CSV source batch " + externalId + " has inconsistent source metadata.");
            }
            Map<String, String> candidate = metadata(row);
            if (!metadata.equals(candidate))
            {
                throw new IllegalArgumentException(
                        "Normalized bank CSV source batch " + externalId + " has inconsistent statement metadata.");
            }
            rows.add(new NormalizedBankCsvDocument.Row(sourceRowNumber, row));
        }

        private void remember(BankStatementExportRow row)
        {
            metadata.putAll(metadata(row));
        }

        private static Map<String, String> metadata(BankStatementExportRow row)
        {
            Map<String, String> values = new HashMap<>();
            values.put("institution", row.institutionId());
            values.put("bank", row.bankId());
            values.put("account", row.accountId());
            values.put("type", row.accountType());
            values.put("currency", row.currency());
            values.put("start", row.statementStartDate() == null ? "" : row.statementStartDate().toString());
            values.put("end", row.statementEndDate() == null ? "" : row.statementEndDate().toString());
            values.put("ledger", row.ledgerBalance() == null ? "" : row.ledgerBalance().toPlainString());
            values.put("available", row.availableBalance() == null ? "" : row.availableBalance().toPlainString());
            return values;
        }

        private NormalizedBankCsvDocument.Batch build()
        {
            return new NormalizedBankCsvDocument.Batch(
                    externalId, sourceFormat, sourceFileName, rows);
        }
    }
}
