package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Normalizes bank import rows before durable review persistence. */
public class BankImportNormalizationService
{
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    public BankImportNormalizationResult normalize(List<BankTransactionRecord> records,
                                                   DuplicateContext duplicateContext)
    {
        List<BankTransactionRecord> safeRecords = records == null ? List.of() : List.copyOf(records);
        DuplicateContext context = duplicateContext == null ? DuplicateContext.empty() : duplicateContext;
        Set<String> seenExternalIds = new HashSet<>();
        Set<String> seenFingerprints = new HashSet<>();
        List<NormalizedBankStatementLine> lines = new ArrayList<>();

        for (int i = 0; i < safeRecords.size(); i++)
        {
            BankTransactionRecord record = safeRecords.get(i);
            int rowNumber = i + 1;
            List<ImportRowIssue> issues = new ArrayList<>();
            LocalDate postedDate = parseDate(record == null ? null : record.postedOn(), rowNumber, issues);
            BigDecimal amount = record == null ? null : record.amount();
            if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0)
            {
                issues.add(ImportRowIssue.error(rowNumber, "INVALID_AMOUNT", "Bank statement amount is required and cannot be zero."));
            }

            String externalId = normalizeExternalId(record == null ? null : record.fitId());
            String fingerprint = fingerprint(postedDate, amount, record == null ? "" : record.transactionType(), record == null ? "" : record.name(), record == null ? "" : record.memo());
            boolean exactDuplicate = false;
            if (!externalId.isBlank())
            {
                exactDuplicate = !seenExternalIds.add(externalId) || context.existingExternalIds().contains(externalId);
            }
            if (externalId.isBlank())
            {
                exactDuplicate = !seenFingerprints.add(fingerprint) || context.existingFingerprints().contains(fingerprint);
            }
            else
            {
                seenFingerprints.add(fingerprint);
            }
            if (exactDuplicate)
            {
                issues.add(ImportRowIssue.error(rowNumber, "EXACT_DUPLICATE", "Exact duplicate bank import row."));
            }

            boolean probableDuplicate = !exactDuplicate && context.probableDuplicates().stream()
                    .anyMatch(candidate -> candidate.matches(postedDate, amount, record == null ? "" : record.name(), record == null ? "" : record.memo()));
            if (probableDuplicate)
            {
                issues.add(ImportRowIssue.warning(rowNumber, "PROBABLE_DUPLICATE", "Probable duplicate bank import row."));
            }

            lines.add(new NormalizedBankStatementLine(
                    rowNumber,
                    externalId,
                    fingerprint,
                    postedDate,
                    postedDate,
                    amount,
                    normalizeText(record == null ? null : record.transactionType()),
                    normalizeText(record == null ? null : record.name()),
                    normalizeText(record == null ? null : record.memo()),
                    exactDuplicate,
                    probableDuplicate,
                    List.copyOf(issues)));
        }

        return new BankImportNormalizationResult(lines);
    }

    private static LocalDate parseDate(String raw, int rowNumber, List<ImportRowIssue> issues)
    {
        String value = normalizeText(raw);
        if (value.isBlank())
        {
            issues.add(ImportRowIssue.error(rowNumber, "MISSING_DATE", "Bank statement posted date is required."));
            return null;
        }
        String datePart = value.length() >= 8 ? value.substring(0, 8) : value;
        try
        {
            return datePart.contains("-") ? LocalDate.parse(datePart, ISO_DATE) : LocalDate.parse(datePart, BASIC_DATE);
        }
        catch (DateTimeParseException ex)
        {
            issues.add(ImportRowIssue.error(rowNumber, "INVALID_DATE", "Bank statement posted date is invalid."));
            return null;
        }
    }

    private static String normalizeExternalId(String raw)
    {
        return normalizeText(raw).toUpperCase(Locale.ROOT);
    }

    private static String normalizeText(String raw)
    {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }

    private static String fingerprint(LocalDate postedDate,
                                      BigDecimal amount,
                                      String transactionType,
                                      String name,
                                      String memo)
    {
        String material = String.join("|",
                postedDate == null ? "" : postedDate.toString(),
                amount == null ? "" : amount.stripTrailingZeros().toPlainString(),
                normalizeText(transactionType).toUpperCase(Locale.ROOT),
                normalizeText(name).toUpperCase(Locale.ROOT),
                normalizeText(memo).toUpperCase(Locale.ROOT));
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 is required for bank import fingerprints.", ex);
        }
    }

    public record DuplicateContext(Set<String> existingExternalIds,
                                   Set<String> existingFingerprints,
                                   List<ProbableDuplicateCandidate> probableDuplicates)
    {
        public DuplicateContext
        {
            existingExternalIds = existingExternalIds == null ? Set.of() : Set.copyOf(existingExternalIds);
            existingFingerprints = existingFingerprints == null ? Set.of() : Set.copyOf(existingFingerprints);
            probableDuplicates = probableDuplicates == null ? List.of() : List.copyOf(probableDuplicates);
        }

        public static DuplicateContext empty()
        {
            return new DuplicateContext(Set.of(), Set.of(), List.of());
        }
    }

    public record ProbableDuplicateCandidate(LocalDate postedDate,
                                             BigDecimal amount,
                                             String payee,
                                             String memo,
                                             int dateToleranceDays)
    {
        boolean matches(LocalDate importedDate, BigDecimal importedAmount, String importedPayee, String importedMemo)
        {
            if (postedDate == null || importedDate == null || amount == null || importedAmount == null)
            {
                return false;
            }
            long days = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(postedDate, importedDate));
            return days <= Math.max(0, dateToleranceDays)
                    && amount.compareTo(importedAmount) == 0
                    && normalizeText(payee).equalsIgnoreCase(normalizeText(importedPayee))
                    && (normalizeText(memo).isBlank() || normalizeText(memo).equalsIgnoreCase(normalizeText(importedMemo)));
        }
    }

    public record BankImportNormalizationResult(List<NormalizedBankStatementLine> lines)
    {
        public BankImportNormalizationResult
        {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    public record NormalizedBankStatementLine(int sourceRowNumber,
                                              String sourceTransactionId,
                                              String deterministicFingerprint,
                                              LocalDate transactionDate,
                                              LocalDate postedDate,
                                              BigDecimal amount,
                                              String transactionType,
                                              String name,
                                              String memo,
                                              boolean exactDuplicate,
                                              boolean probableDuplicate,
                                              List<ImportRowIssue> issues)
    {
        public NormalizedBankStatementLine
        {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }

        public boolean hasErrors()
        {
            return issues.stream().anyMatch(issue -> issue.severity() == ImportIssueSeverity.ERROR);
        }
    }

    public record ImportRowIssue(int sourceRowNumber,
                                 ImportIssueSeverity severity,
                                 String code,
                                 String message)
    {
        public static ImportRowIssue error(int rowNumber, String code, String message)
        {
            return new ImportRowIssue(rowNumber, ImportIssueSeverity.ERROR, code, message);
        }

        public static ImportRowIssue warning(int rowNumber, String code, String message)
        {
            return new ImportRowIssue(rowNumber, ImportIssueSeverity.WARNING, code, message);
        }
    }

    public enum ImportIssueSeverity
    {
        INFO,
        WARNING,
        ERROR
    }
}
