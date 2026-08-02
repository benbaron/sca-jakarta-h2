package org.nonprofitbookkeeping.interchange.sclx;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Typed contract for reviewed bank-import, statement, issue, and clearance facts. */
final class SclxBankStatementFactsExtension
{
    static final String KEY = "bankStatementFacts";
    private static final String PATH = "extensions.scaJakartaH2.bankStatementFacts";
    private static final Set<String> ROOT_KEYS = Set.of(
            "importBatches", "statementLines", "issues", "transactionLineClearance");
    private static final Set<String> BATCH_KEYS = Set.of(
            "importBatchId", "bankAccountId", "sourceName", "sourceHash", "sourceFormat",
            "sourceVariant", "sourceVersion", "sourceEncoding", "sourceInstitutionId",
            "sourceBankId", "sourceAccountId", "sourceAccountType", "currency",
            "statementStartDate", "statementEndDate", "ledgerBalance", "availableBalance",
            "accountMatchStatus", "accountIdentityConfirmed",
            "status", "importedAt", "completedAt", "totalLineCount", "acceptedLineCount",
            "rejectedLineCount", "issueCount", "notes");
    private static final Set<String> LINE_KEYS = Set.of(
            "statementLineId", "importBatchId", "bankAccountId", "sourceRowNumber",
            "sourceTransactionId", "deterministicFingerprint", "statementAccountIdentifier",
            "transactionDate", "postedDate", "amount", "transactionType", "name", "memo",
            "checkNumber", "reference", "currency", "correctionAction",
            "correctedSourceTransactionId", "status", "dispositionNote", "acceptedTransactionId",
            "matchedTransactionId");
    private static final Set<String> ISSUE_KEYS = Set.of(
            "issueId", "importBatchId", "statementLineId", "sourceRowNumber", "severity",
            "code", "message", "createdAt");
    private static final Set<String> CLEARANCE_KEYS = Set.of(
            "lineId", "bankCleared", "bankClearedOn", "statementLineId");
    private static final Set<String> SOURCE_FORMATS = Set.of("OFX", "QFX", "QIF", "CSV", "SCLX", "OTHER");
    private static final Set<String> BATCH_STATUSES = Set.of(
            "IMPORTED", "PARTIALLY_ACCEPTED", "ACCEPTED", "REJECTED", "FAILED", "CANCELLED");
    private static final Set<String> LINE_STATUSES = Set.of(
            "IMPORTED", "ACCEPTED", "REJECTED", "MATCHED", "DUPLICATE", "ERROR");
    private static final Set<String> SEVERITIES = Set.of("INFO", "WARNING", "ERROR");

    private SclxBankStatementFactsExtension()
    {
    }

    static Map<String, Object> value(
            List<Map<String, Object>> importBatches,
            List<Map<String, Object>> statementLines,
            List<Map<String, Object>> issues,
            List<Map<String, Object>> transactionLineClearance)
    {
        return Map.of(
                "importBatches", List.copyOf(importBatches),
                "statementLines", List.copyOf(statementLines),
                "issues", List.copyOf(issues),
                "transactionLineClearance", List.copyOf(transactionLineClearance));
    }

    static Map<String, Object> importBatchEntry(
            String importBatchId,
            String bankAccountId,
            String sourceName,
            String sourceHash,
            String sourceFormat,
            String sourceVariant,
            String sourceVersion,
            String sourceEncoding,
            String sourceInstitutionId,
            String sourceBankId,
            String sourceAccountId,
            String sourceAccountType,
            String currency,
            LocalDate statementStartDate,
            LocalDate statementEndDate,
            BigDecimal ledgerBalance,
            BigDecimal availableBalance,
            String accountMatchStatus,
            boolean accountIdentityConfirmed,
            String status,
            Instant importedAt,
            Instant completedAt,
            int totalLineCount,
            int acceptedLineCount,
            int rejectedLineCount,
            int issueCount,
            String notes)
    {
        requireSupported(sourceFormat, SOURCE_FORMATS, "sourceFormat");
        requireSupported(status, BATCH_STATUSES, "status");
        requireNonNegative(totalLineCount, "totalLineCount");
        requireNonNegative(acceptedLineCount, "acceptedLineCount");
        requireNonNegative(rejectedLineCount, "rejectedLineCount");
        requireNonNegative(issueCount, "issueCount");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("importBatchId", requireText(importBatchId, "importBatchId"));
        entry.put("bankAccountId", optionalText(bankAccountId));
        entry.put("sourceName", requireText(sourceName, "sourceName"));
        entry.put("sourceHash", optionalText(sourceHash));
        entry.put("sourceFormat", sourceFormat);
        entry.put("sourceVariant", optionalText(sourceVariant));
        entry.put("sourceVersion", optionalText(sourceVersion));
        entry.put("sourceEncoding", optionalText(sourceEncoding));
        entry.put("sourceInstitutionId", optionalText(sourceInstitutionId));
        entry.put("sourceBankId", optionalText(sourceBankId));
        entry.put("sourceAccountId", optionalText(sourceAccountId));
        entry.put("sourceAccountType", optionalText(sourceAccountType));
        entry.put("currency", optionalText(currency));
        entry.put("statementStartDate", statementStartDate);
        entry.put("statementEndDate", statementEndDate);
        entry.put("ledgerBalance", ledgerBalance);
        entry.put("availableBalance", availableBalance);
        entry.put("accountMatchStatus", optionalText(accountMatchStatus));
        entry.put("accountIdentityConfirmed", accountIdentityConfirmed);
        entry.put("status", status);
        entry.put("importedAt", Objects.requireNonNull(importedAt, "importedAt"));
        entry.put("completedAt", completedAt);
        entry.put("totalLineCount", totalLineCount);
        entry.put("acceptedLineCount", acceptedLineCount);
        entry.put("rejectedLineCount", rejectedLineCount);
        entry.put("issueCount", issueCount);
        entry.put("notes", optionalText(notes));
        return java.util.Collections.unmodifiableMap(entry);
    }

    static Map<String, Object> statementLineEntry(
            String statementLineId,
            String importBatchId,
            String bankAccountId,
            int sourceRowNumber,
            String sourceTransactionId,
            String deterministicFingerprint,
            String statementAccountIdentifier,
            LocalDate transactionDate,
            LocalDate postedDate,
            BigDecimal amount,
            String transactionType,
            String name,
            String memo,
            String checkNumber,
            String reference,
            String currency,
            String correctionAction,
            String correctedSourceTransactionId,
            String status,
            String dispositionNote,
            String acceptedTransactionId,
            String matchedTransactionId)
    {
        requireNonNegative(sourceRowNumber, "sourceRowNumber");
        requireSupported(status, LINE_STATUSES, "status");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("statementLineId", requireText(statementLineId, "statementLineId"));
        entry.put("importBatchId", requireText(importBatchId, "importBatchId"));
        entry.put("bankAccountId", optionalText(bankAccountId));
        entry.put("sourceRowNumber", sourceRowNumber);
        entry.put("sourceTransactionId", optionalText(sourceTransactionId));
        entry.put("deterministicFingerprint", requireText(
                deterministicFingerprint, "deterministicFingerprint"));
        entry.put("statementAccountIdentifier", optionalText(statementAccountIdentifier));
        entry.put("transactionDate", transactionDate);
        entry.put("postedDate", postedDate);
        entry.put("amount", amount);
        entry.put("transactionType", optionalText(transactionType));
        entry.put("name", optionalText(name));
        entry.put("memo", optionalText(memo));
        entry.put("checkNumber", optionalText(checkNumber));
        entry.put("reference", optionalText(reference));
        entry.put("currency", optionalText(currency));
        entry.put("correctionAction", optionalText(correctionAction));
        entry.put("correctedSourceTransactionId", optionalText(correctedSourceTransactionId));
        entry.put("status", status);
        entry.put("dispositionNote", optionalText(dispositionNote));
        entry.put("acceptedTransactionId", optionalText(acceptedTransactionId));
        entry.put("matchedTransactionId", optionalText(matchedTransactionId));
        return java.util.Collections.unmodifiableMap(entry);
    }

    static Map<String, Object> issueEntry(
            String issueId,
            String importBatchId,
            String statementLineId,
            Integer sourceRowNumber,
            String severity,
            String code,
            String message,
            Instant createdAt)
    {
        if (sourceRowNumber != null)
        {
            requireNonNegative(sourceRowNumber, "sourceRowNumber");
        }
        requireSupported(severity, SEVERITIES, "severity");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("issueId", requireText(issueId, "issueId"));
        entry.put("importBatchId", requireText(importBatchId, "importBatchId"));
        entry.put("statementLineId", optionalText(statementLineId));
        entry.put("sourceRowNumber", sourceRowNumber);
        entry.put("severity", severity);
        entry.put("code", requireText(code, "code"));
        entry.put("message", requireText(message, "message"));
        entry.put("createdAt", Objects.requireNonNull(createdAt, "createdAt"));
        return java.util.Collections.unmodifiableMap(entry);
    }

    static Map<String, Object> clearanceEntry(
            String lineId,
            boolean bankCleared,
            LocalDate bankClearedOn,
            String statementLineId)
    {
        if (!bankCleared)
        {
            throw new IllegalArgumentException("transaction-line clearance entries must represent cleared state");
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("lineId", requireText(lineId, "lineId"));
        entry.put("bankCleared", true);
        entry.put("bankClearedOn", bankClearedOn);
        entry.put("statementLineId", optionalText(statementLineId));
        return java.util.Collections.unmodifiableMap(entry);
    }

    static Data data(SclxExportDocument.Extensions extensions)
    {
        Objects.requireNonNull(extensions, "extensions");
        Object raw = extensions.scaJakartaH2().get(KEY);
        if (raw == null)
        {
            return new Data(List.of(), List.of(), List.of(), List.of());
        }
        if (!(raw instanceof Map<?, ?> root))
        {
            throw new IllegalArgumentException(PATH + " must be an object");
        }
        if (!root.keySet().equals(ROOT_KEYS))
        {
            throw new IllegalArgumentException(PATH + " has unsupported fields");
        }
        return new Data(
                batches(root),
                lines(root),
                issues(root),
                clearances(root));
    }

    private static List<ImportBatchEntry> batches(Map<?, ?> root)
    {
        List<Map<?, ?>> maps = SclxExtensionValueReader.objects(
                SclxExtensionValueReader.array(root, "importBatches", PATH),
                PATH + ".importBatches", BATCH_KEYS);
        List<ImportBatchEntry> result = new ArrayList<>(maps.size());
        for (int index = 0; index < maps.size(); index++)
        {
            Map<?, ?> map = maps.get(index);
            String path = PATH + ".importBatches[" + index + ']';
            String format = SclxExtensionValueReader.text(map, "sourceFormat", path);
            String status = SclxExtensionValueReader.text(map, "status", path);
            requireSupported(format, SOURCE_FORMATS, path + ".sourceFormat");
            requireSupported(status, BATCH_STATUSES, path + ".status");
            int total = nonNegative(map, "totalLineCount", path);
            int accepted = nonNegative(map, "acceptedLineCount", path);
            int rejected = nonNegative(map, "rejectedLineCount", path);
            int issueCount = nonNegative(map, "issueCount", path);
            result.add(new ImportBatchEntry(
                    SclxExtensionValueReader.text(map, "importBatchId", path),
                    SclxExtensionValueReader.optionalText(map, "bankAccountId", path),
                    SclxExtensionValueReader.text(map, "sourceName", path),
                    SclxExtensionValueReader.optionalText(map, "sourceHash", path),
                    format,
                    SclxExtensionValueReader.optionalText(map, "sourceVariant", path),
                    SclxExtensionValueReader.optionalText(map, "sourceVersion", path),
                    SclxExtensionValueReader.optionalText(map, "sourceEncoding", path),
                    SclxExtensionValueReader.optionalText(map, "sourceInstitutionId", path),
                    SclxExtensionValueReader.optionalText(map, "sourceBankId", path),
                    SclxExtensionValueReader.optionalText(map, "sourceAccountId", path),
                    SclxExtensionValueReader.optionalText(map, "sourceAccountType", path),
                    SclxExtensionValueReader.optionalText(map, "currency", path),
                    SclxExtensionValueReader.date(map, "statementStartDate", path, true),
                    SclxExtensionValueReader.date(map, "statementEndDate", path, true),
                    SclxExtensionValueReader.decimal(map, "ledgerBalance", path, true),
                    SclxExtensionValueReader.decimal(map, "availableBalance", path, true),
                    SclxExtensionValueReader.optionalText(map, "accountMatchStatus", path),
                    optionalFlag(map, "accountIdentityConfirmed", path),
                    status,
                    SclxExtensionValueReader.instant(map, "importedAt", path, false),
                    SclxExtensionValueReader.instant(map, "completedAt", path, true),
                    total,
                    accepted,
                    rejected,
                    issueCount,
                    SclxExtensionValueReader.optionalText(map, "notes", path)));
        }
        return List.copyOf(result);
    }

    private static List<StatementLineEntry> lines(Map<?, ?> root)
    {
        List<Map<?, ?>> maps = SclxExtensionValueReader.objects(
                SclxExtensionValueReader.array(root, "statementLines", PATH),
                PATH + ".statementLines", LINE_KEYS);
        List<StatementLineEntry> result = new ArrayList<>(maps.size());
        for (int index = 0; index < maps.size(); index++)
        {
            Map<?, ?> map = maps.get(index);
            String path = PATH + ".statementLines[" + index + ']';
            String status = SclxExtensionValueReader.text(map, "status", path);
            requireSupported(status, LINE_STATUSES, path + ".status");
            result.add(new StatementLineEntry(
                    SclxExtensionValueReader.text(map, "statementLineId", path),
                    SclxExtensionValueReader.text(map, "importBatchId", path),
                    SclxExtensionValueReader.optionalText(map, "bankAccountId", path),
                    nonNegative(map, "sourceRowNumber", path),
                    SclxExtensionValueReader.optionalText(map, "sourceTransactionId", path),
                    SclxExtensionValueReader.text(map, "deterministicFingerprint", path),
                    SclxExtensionValueReader.optionalText(map, "statementAccountIdentifier", path),
                    SclxExtensionValueReader.date(map, "transactionDate", path, true),
                    SclxExtensionValueReader.date(map, "postedDate", path, true),
                    SclxExtensionValueReader.decimal(map, "amount", path, true),
                    SclxExtensionValueReader.optionalText(map, "transactionType", path),
                    SclxExtensionValueReader.optionalText(map, "name", path),
                    SclxExtensionValueReader.optionalText(map, "memo", path),
                    SclxExtensionValueReader.optionalText(map, "checkNumber", path),
                    SclxExtensionValueReader.optionalText(map, "reference", path),
                    SclxExtensionValueReader.optionalText(map, "currency", path),
                    SclxExtensionValueReader.optionalText(map, "correctionAction", path),
                    SclxExtensionValueReader.optionalText(map, "correctedSourceTransactionId", path),
                    status,
                    SclxExtensionValueReader.optionalText(map, "dispositionNote", path),
                    SclxExtensionValueReader.optionalText(map, "acceptedTransactionId", path),
                    SclxExtensionValueReader.optionalText(map, "matchedTransactionId", path)));
        }
        return List.copyOf(result);
    }

    private static List<IssueEntry> issues(Map<?, ?> root)
    {
        List<Map<?, ?>> maps = SclxExtensionValueReader.objects(
                SclxExtensionValueReader.array(root, "issues", PATH),
                PATH + ".issues", ISSUE_KEYS);
        List<IssueEntry> result = new ArrayList<>(maps.size());
        for (int index = 0; index < maps.size(); index++)
        {
            Map<?, ?> map = maps.get(index);
            String path = PATH + ".issues[" + index + ']';
            String severity = SclxExtensionValueReader.text(map, "severity", path);
            requireSupported(severity, SEVERITIES, path + ".severity");
            Object sourceRow = map.get("sourceRowNumber");
            Integer sourceRowNumber = sourceRow == null ? null : nonNegative(map, "sourceRowNumber", path);
            result.add(new IssueEntry(
                    SclxExtensionValueReader.text(map, "issueId", path),
                    SclxExtensionValueReader.text(map, "importBatchId", path),
                    SclxExtensionValueReader.optionalText(map, "statementLineId", path),
                    sourceRowNumber,
                    severity,
                    SclxExtensionValueReader.text(map, "code", path),
                    SclxExtensionValueReader.text(map, "message", path),
                    SclxExtensionValueReader.instant(map, "createdAt", path, false)));
        }
        return List.copyOf(result);
    }

    private static List<TransactionLineClearance> clearances(Map<?, ?> root)
    {
        List<Map<?, ?>> maps = SclxExtensionValueReader.objects(
                SclxExtensionValueReader.array(root, "transactionLineClearance", PATH),
                PATH + ".transactionLineClearance", CLEARANCE_KEYS);
        List<TransactionLineClearance> result = new ArrayList<>(maps.size());
        for (int index = 0; index < maps.size(); index++)
        {
            Map<?, ?> map = maps.get(index);
            String path = PATH + ".transactionLineClearance[" + index + ']';
            boolean cleared = SclxExtensionValueReader.flag(map, "bankCleared", path);
            if (!cleared)
            {
                throw new IllegalArgumentException(path + ".bankCleared must be true");
            }
            result.add(new TransactionLineClearance(
                    SclxExtensionValueReader.text(map, "lineId", path),
                    true,
                    SclxExtensionValueReader.date(map, "bankClearedOn", path, true),
                    SclxExtensionValueReader.optionalText(map, "statementLineId", path)));
        }
        return List.copyOf(result);
    }

    static Set<String> uniqueImportBatchIds(Data data)
    {
        Set<String> ids = new HashSet<>();
        data.importBatches().forEach(entry -> requireUnique(ids, entry.importBatchId(), "bank import batch"));
        return ids;
    }

    static Set<String> uniqueStatementLineIds(Data data)
    {
        Set<String> ids = new HashSet<>();
        data.statementLines().forEach(entry -> requireUnique(ids, entry.statementLineId(), "bank statement line"));
        return ids;
    }

    static Set<String> uniqueIssueIds(Data data)
    {
        Set<String> ids = new HashSet<>();
        data.issues().forEach(entry -> requireUnique(ids, entry.issueId(), "bank import issue"));
        return ids;
    }

    private static int nonNegative(Map<?, ?> map, String field, String path)
    {
        int value = SclxExtensionValueReader.integer(map, field, path);
        requireNonNegative(value, path + '.' + field);
        return value;
    }

    private static boolean optionalFlag(Map<?, ?> map, String field, String path)
    {
        return map.containsKey(field) && SclxExtensionValueReader.flag(map, field, path);
    }

    private static void requireNonNegative(int value, String field)
    {
        if (value < 0)
        {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }

    private static void requireSupported(String value, Set<String> supported, String field)
    {
        String text = requireText(value, field);
        if (!supported.contains(text))
        {
            throw new IllegalArgumentException(field + " is unsupported: " + value);
        }
    }

    private static void requireUnique(Set<String> ids, String identity, String type)
    {
        if (!ids.add(identity))
        {
            throw new IllegalArgumentException("duplicate " + type + " portable identity: " + identity);
        }
    }

    private static String requireText(String value, String field)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String optionalText(String value)
    {
        return value == null || value.isBlank() ? null : value.strip();
    }

    record Data(
            List<ImportBatchEntry> importBatches,
            List<StatementLineEntry> statementLines,
            List<IssueEntry> issues,
            List<TransactionLineClearance> transactionLineClearance)
    {
        Data
        {
            importBatches = List.copyOf(importBatches);
            statementLines = List.copyOf(statementLines);
            issues = List.copyOf(issues);
            transactionLineClearance = List.copyOf(transactionLineClearance);
        }
    }

    record ImportBatchEntry(
            String importBatchId,
            String bankAccountId,
            String sourceName,
            String sourceHash,
            String sourceFormat,
            String sourceVariant,
            String sourceVersion,
            String sourceEncoding,
            String sourceInstitutionId,
            String sourceBankId,
            String sourceAccountId,
            String sourceAccountType,
            String currency,
            LocalDate statementStartDate,
            LocalDate statementEndDate,
            BigDecimal ledgerBalance,
            BigDecimal availableBalance,
            String accountMatchStatus,
            boolean accountIdentityConfirmed,
            String status,
            Instant importedAt,
            Instant completedAt,
            int totalLineCount,
            int acceptedLineCount,
            int rejectedLineCount,
            int issueCount,
            String notes)
    {
    }

    record StatementLineEntry(
            String statementLineId,
            String importBatchId,
            String bankAccountId,
            int sourceRowNumber,
            String sourceTransactionId,
            String deterministicFingerprint,
            String statementAccountIdentifier,
            LocalDate transactionDate,
            LocalDate postedDate,
            BigDecimal amount,
            String transactionType,
            String name,
            String memo,
            String checkNumber,
            String reference,
            String currency,
            String correctionAction,
            String correctedSourceTransactionId,
            String status,
            String dispositionNote,
            String acceptedTransactionId,
            String matchedTransactionId)
    {
    }

    record IssueEntry(
            String issueId,
            String importBatchId,
            String statementLineId,
            Integer sourceRowNumber,
            String severity,
            String code,
            String message,
            Instant createdAt)
    {
    }

    record TransactionLineClearance(
            String lineId,
            boolean bankCleared,
            LocalDate bankClearedOn,
            String statementLineId)
    {
    }
}
