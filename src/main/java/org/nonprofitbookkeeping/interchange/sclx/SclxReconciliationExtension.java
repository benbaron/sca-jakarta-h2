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

/** Typed contract for selected-company bank reconciliation sessions and matches. */
final class SclxReconciliationExtension
{
    static final String KEY = "reconciliation";
    private static final String PATH = "extensions.scaJakartaH2.reconciliation";
    private static final Set<String> ROOT_KEYS = Set.of("sessions", "matches");
    private static final Set<String> SESSION_KEYS = Set.of(
            "reconciliationSessionId", "bankAccountId", "statementStartDate", "statementEndDate",
            "statementEndingBalance", "mismatchPolicy", "status", "notes", "beginningBalance",
            "bookBalanceAll", "bookBalanceCleared", "differenceAmount", "createdAt", "updatedAt");
    private static final Set<String> MATCH_KEYS = Set.of(
            "reconciliationMatchId", "reconciliationSessionId", "statementLineId", "lineId",
            "matchStatus", "resolutionNote", "createdAt", "updatedAt");
    private static final Set<String> POLICIES = Set.of(
            "WARN_ONLY", "OVERWRITE_LEDGER_CLEARED_STATE", "NEVER_OVERWRITE_REQUIRE_MANUAL",
            "DECIDE_PER_IMPORTED_LINE");
    private static final Set<String> SESSION_STATUSES = Set.of(
            "IN_PROGRESS", "UNRESOLVED", "BALANCED", "FINALIZED");
    private static final Set<String> MATCH_STATUSES = Set.of(
            "MATCHED", "UNMATCHED", "AMOUNT_MISMATCH", "DATE_MISMATCH", "DUPLICATE_POSSIBLE",
            "CLEARED_STATE_MISMATCH", "RESOLVED");

    private SclxReconciliationExtension()
    {
    }

    static Map<String, Object> value(
            List<Map<String, Object>> sessions,
            List<Map<String, Object>> matches)
    {
        return Map.of("sessions", List.copyOf(sessions), "matches", List.copyOf(matches));
    }

    static Map<String, Object> sessionEntry(
            String reconciliationSessionId,
            String bankAccountId,
            LocalDate statementStartDate,
            LocalDate statementEndDate,
            BigDecimal statementEndingBalance,
            String mismatchPolicy,
            String status,
            String notes,
            BigDecimal beginningBalance,
            BigDecimal bookBalanceAll,
            BigDecimal bookBalanceCleared,
            BigDecimal differenceAmount,
            Instant createdAt,
            Instant updatedAt)
    {
        requireDates(statementStartDate, statementEndDate);
        requireSupported(mismatchPolicy, POLICIES, "mismatchPolicy");
        requireSupported(status, SESSION_STATUSES, "status");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("reconciliationSessionId", requireText(
                reconciliationSessionId, "reconciliationSessionId"));
        entry.put("bankAccountId", requireText(bankAccountId, "bankAccountId"));
        entry.put("statementStartDate", statementStartDate);
        entry.put("statementEndDate", statementEndDate);
        entry.put("statementEndingBalance", statementEndingBalance);
        entry.put("mismatchPolicy", mismatchPolicy);
        entry.put("status", status);
        entry.put("notes", optionalText(notes));
        entry.put("beginningBalance", Objects.requireNonNull(beginningBalance, "beginningBalance"));
        entry.put("bookBalanceAll", Objects.requireNonNull(bookBalanceAll, "bookBalanceAll"));
        entry.put("bookBalanceCleared", Objects.requireNonNull(bookBalanceCleared, "bookBalanceCleared"));
        entry.put("differenceAmount", Objects.requireNonNull(differenceAmount, "differenceAmount"));
        entry.put("createdAt", Objects.requireNonNull(createdAt, "createdAt"));
        entry.put("updatedAt", Objects.requireNonNull(updatedAt, "updatedAt"));
        return java.util.Collections.unmodifiableMap(entry);
    }

    static Map<String, Object> matchEntry(
            String reconciliationMatchId,
            String reconciliationSessionId,
            String statementLineId,
            String lineId,
            String matchStatus,
            String resolutionNote,
            Instant createdAt,
            Instant updatedAt)
    {
        if (optionalText(statementLineId) == null && optionalText(lineId) == null)
        {
            throw new IllegalArgumentException(
                    "reconciliation match must reference a statement line or transaction line");
        }
        requireSupported(matchStatus, MATCH_STATUSES, "matchStatus");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("reconciliationMatchId", requireText(reconciliationMatchId, "reconciliationMatchId"));
        entry.put("reconciliationSessionId", requireText(
                reconciliationSessionId, "reconciliationSessionId"));
        entry.put("statementLineId", optionalText(statementLineId));
        entry.put("lineId", optionalText(lineId));
        entry.put("matchStatus", matchStatus);
        entry.put("resolutionNote", optionalText(resolutionNote));
        entry.put("createdAt", Objects.requireNonNull(createdAt, "createdAt"));
        entry.put("updatedAt", Objects.requireNonNull(updatedAt, "updatedAt"));
        return java.util.Collections.unmodifiableMap(entry);
    }

    static Data data(SclxExportDocument.Extensions extensions)
    {
        Objects.requireNonNull(extensions, "extensions");
        Object raw = extensions.scaJakartaH2().get(KEY);
        if (raw == null)
        {
            return new Data(List.of(), List.of());
        }
        if (!(raw instanceof Map<?, ?> root))
        {
            throw new IllegalArgumentException(PATH + " must be an object");
        }
        if (!root.keySet().equals(ROOT_KEYS))
        {
            throw new IllegalArgumentException(PATH + " has unsupported fields");
        }
        return new Data(sessions(root), matches(root));
    }

    private static List<SessionEntry> sessions(Map<?, ?> root)
    {
        List<Map<?, ?>> maps = SclxExtensionValueReader.objects(
                SclxExtensionValueReader.array(root, "sessions", PATH),
                PATH + ".sessions", SESSION_KEYS);
        List<SessionEntry> result = new ArrayList<>(maps.size());
        for (int index = 0; index < maps.size(); index++)
        {
            Map<?, ?> map = maps.get(index);
            String path = PATH + ".sessions[" + index + ']';
            LocalDate start = SclxExtensionValueReader.date(map, "statementStartDate", path, false);
            LocalDate end = SclxExtensionValueReader.date(map, "statementEndDate", path, false);
            requireDates(start, end);
            String policy = SclxExtensionValueReader.text(map, "mismatchPolicy", path);
            String status = SclxExtensionValueReader.text(map, "status", path);
            requireSupported(policy, POLICIES, path + ".mismatchPolicy");
            requireSupported(status, SESSION_STATUSES, path + ".status");
            result.add(new SessionEntry(
                    SclxExtensionValueReader.text(map, "reconciliationSessionId", path),
                    SclxExtensionValueReader.text(map, "bankAccountId", path),
                    start,
                    end,
                    SclxExtensionValueReader.decimal(map, "statementEndingBalance", path, true),
                    policy,
                    status,
                    SclxExtensionValueReader.optionalText(map, "notes", path),
                    SclxExtensionValueReader.decimal(map, "beginningBalance", path, false),
                    SclxExtensionValueReader.decimal(map, "bookBalanceAll", path, false),
                    SclxExtensionValueReader.decimal(map, "bookBalanceCleared", path, false),
                    SclxExtensionValueReader.decimal(map, "differenceAmount", path, false),
                    SclxExtensionValueReader.instant(map, "createdAt", path, false),
                    SclxExtensionValueReader.instant(map, "updatedAt", path, false)));
        }
        return List.copyOf(result);
    }

    private static List<MatchEntry> matches(Map<?, ?> root)
    {
        List<Map<?, ?>> maps = SclxExtensionValueReader.objects(
                SclxExtensionValueReader.array(root, "matches", PATH),
                PATH + ".matches", MATCH_KEYS);
        List<MatchEntry> result = new ArrayList<>(maps.size());
        for (int index = 0; index < maps.size(); index++)
        {
            Map<?, ?> map = maps.get(index);
            String path = PATH + ".matches[" + index + ']';
            String statementLineId = SclxExtensionValueReader.optionalText(map, "statementLineId", path);
            String lineId = SclxExtensionValueReader.optionalText(map, "lineId", path);
            if (statementLineId == null && lineId == null)
            {
                throw new IllegalArgumentException(path + " must reference a statement line or transaction line");
            }
            String status = SclxExtensionValueReader.text(map, "matchStatus", path);
            requireSupported(status, MATCH_STATUSES, path + ".matchStatus");
            result.add(new MatchEntry(
                    SclxExtensionValueReader.text(map, "reconciliationMatchId", path),
                    SclxExtensionValueReader.text(map, "reconciliationSessionId", path),
                    statementLineId,
                    lineId,
                    status,
                    SclxExtensionValueReader.optionalText(map, "resolutionNote", path),
                    SclxExtensionValueReader.instant(map, "createdAt", path, false),
                    SclxExtensionValueReader.instant(map, "updatedAt", path, false)));
        }
        return List.copyOf(result);
    }

    static Set<String> uniqueSessionIds(Data data)
    {
        Set<String> ids = new HashSet<>();
        data.sessions().forEach(entry -> requireUnique(ids, entry.reconciliationSessionId(), "reconciliation session"));
        return ids;
    }

    static Set<String> uniqueMatchIds(Data data)
    {
        Set<String> ids = new HashSet<>();
        data.matches().forEach(entry -> requireUnique(ids, entry.reconciliationMatchId(), "reconciliation match"));
        return ids;
    }

    private static void requireDates(LocalDate start, LocalDate end)
    {
        Objects.requireNonNull(start, "statementStartDate");
        Objects.requireNonNull(end, "statementEndDate");
        if (start.isAfter(end))
        {
            throw new IllegalArgumentException("statementStartDate must be on or before statementEndDate");
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

    record Data(List<SessionEntry> sessions, List<MatchEntry> matches)
    {
        Data
        {
            sessions = List.copyOf(sessions);
            matches = List.copyOf(matches);
        }
    }

    record SessionEntry(
            String reconciliationSessionId,
            String bankAccountId,
            LocalDate statementStartDate,
            LocalDate statementEndDate,
            BigDecimal statementEndingBalance,
            String mismatchPolicy,
            String status,
            String notes,
            BigDecimal beginningBalance,
            BigDecimal bookBalanceAll,
            BigDecimal bookBalanceCleared,
            BigDecimal differenceAmount,
            Instant createdAt,
            Instant updatedAt)
    {
    }

    record MatchEntry(
            String reconciliationMatchId,
            String reconciliationSessionId,
            String statementLineId,
            String lineId,
            String matchStatus,
            String resolutionNote,
            Instant createdAt,
            Instant updatedAt)
    {
    }
}
