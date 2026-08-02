package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.model.ImportIssue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Strict, non-mutating projection of governed banking and reconciliation extensions. */
final class SclxBankingImportData
{
    private static final String APP = "$.extensions.scaJakartaH2";

    private final List<BankValue> banks;
    private final List<BankAccountValue> bankAccounts;
    private final List<BatchValue> batches;
    private final List<StatementLineValue> statementLines;
    private final List<IssueValue> issues;
    private final List<ClearanceValue> clearances;
    private final List<SessionValue> sessions;
    private final List<MatchValue> matches;

    private SclxBankingImportData(
            List<BankValue> banks,
            List<BankAccountValue> bankAccounts,
            List<BatchValue> batches,
            List<StatementLineValue> statementLines,
            List<IssueValue> issues,
            List<ClearanceValue> clearances,
            List<SessionValue> sessions,
            List<MatchValue> matches)
    {
        this.banks = List.copyOf(banks);
        this.bankAccounts = List.copyOf(bankAccounts);
        this.batches = List.copyOf(batches);
        this.statementLines = List.copyOf(statementLines);
        this.issues = List.copyOf(issues);
        this.clearances = List.copyOf(clearances);
        this.sessions = List.copyOf(sessions);
        this.matches = List.copyOf(matches);
    }

    static SclxBankingImportData parse(JsonNode root)
    {
        JsonNode app = root.path("extensions").path("scaJakartaH2");
        List<BankValue> banks = new ArrayList<>();
        List<BankAccountValue> accounts = new ArrayList<>();
        parseConfiguration(app.get("bankConfiguration"), banks, accounts);

        List<BatchValue> batches = new ArrayList<>();
        List<StatementLineValue> lines = new ArrayList<>();
        List<IssueValue> issues = new ArrayList<>();
        List<ClearanceValue> clearances = new ArrayList<>();
        parseStatementFacts(app.get("bankStatementFacts"), batches, lines, issues, clearances);

        List<SessionValue> sessions = new ArrayList<>();
        List<MatchValue> matches = new ArrayList<>();
        parseReconciliation(app.get("reconciliation"), sessions, matches);

        validateReferences(root, accounts, batches, lines, issues, clearances, sessions, matches);

        banks.sort(Comparator.comparing(BankValue::externalId));
        accounts.sort(Comparator.comparing(BankAccountValue::externalId));
        batches.sort(Comparator.comparing(BatchValue::externalId));
        lines.sort(Comparator.comparing(StatementLineValue::externalId));
        issues.sort(Comparator.comparing(IssueValue::externalId));
        clearances.sort(Comparator.comparing(ClearanceValue::transactionLineId));
        sessions.sort(Comparator.comparing(SessionValue::externalId));
        matches.sort(Comparator.comparing(MatchValue::externalId));
        return new SclxBankingImportData(
                banks, accounts, batches, lines, issues, clearances, sessions, matches);
    }

    private static void validateReferences(
            JsonNode root,
            List<BankAccountValue> accounts,
            List<BatchValue> batches,
            List<StatementLineValue> lines,
            List<IssueValue> issues,
            List<ClearanceValue> clearances,
            List<SessionValue> sessions,
            List<MatchValue> matches)
    {
        Set<String> accountIds = identities(root.path("chartOfAccounts"), "accountId");
        Set<String> transactionIds = new HashSet<>();
        Set<String> transactionLineIds = new HashSet<>();
        JsonNode transactions = root.path("transactions");
        if (transactions.isArray())
        {
            for (JsonNode transaction : transactions)
            {
                transactionIds.add(text(transaction, "transactionId", "$.transactions[]"));
                JsonNode transactionLines = transaction.path("lines");
                if (transactionLines.isArray())
                {
                    for (JsonNode line : transactionLines)
                    {
                        transactionLineIds.add(text(line, "lineId", "$.transactions[].lines[]"));
                    }
                }
            }
        }

        Set<String> bankAccountIds = new HashSet<>();
        for (BankAccountValue account : accounts)
        {
            bankAccountIds.add(account.externalId());
            requireReference(account.ledgerAccountId(), accountIds,
                    APP + ".bankConfiguration.accounts[].ledgerAccountId");
        }

        java.util.Map<String, BatchValue> batchesById = new java.util.HashMap<>();
        for (BatchValue batch : batches)
        {
            requireReference(batch.bankAccountId(), bankAccountIds,
                    APP + ".bankStatementFacts.importBatches[].bankAccountId");
            batchesById.put(batch.externalId(), batch);
        }
        java.util.Map<String, StatementLineValue> linesById = new java.util.HashMap<>();
        for (StatementLineValue line : lines)
        {
            requireReference(line.bankAccountId(), bankAccountIds,
                    APP + ".bankStatementFacts.statementLines[].bankAccountId");
            requireReference(line.acceptedTransactionId(), transactionIds,
                    APP + ".bankStatementFacts.statementLines[].acceptedTransactionId");
            requireReference(line.matchedTransactionId(), transactionIds,
                    APP + ".bankStatementFacts.statementLines[].matchedTransactionId");
            BatchValue batch = batchesById.get(line.importBatchId());
            if (batch != null && batch.bankAccountId() != null && line.bankAccountId() != null
                    && !batch.bankAccountId().equals(line.bankAccountId()))
            {
                throw new IllegalStateException(APP
                        + ".bankStatementFacts.statementLines[] uses a different bank account than its batch.");
            }
            linesById.put(line.externalId(), line);
        }
        for (IssueValue issue : issues)
        {
            StatementLineValue line = issue.statementLineId() == null
                    ? null : linesById.get(issue.statementLineId());
            if (line != null && !issue.importBatchId().equals(line.importBatchId()))
            {
                throw new IllegalStateException(APP
                        + ".bankStatementFacts.issues[] references a statement line from another batch.");
            }
        }
        for (BatchValue batch : batches)
        {
            long lineCount = lines.stream()
                    .filter(line -> batch.externalId().equals(line.importBatchId()))
                    .count();
            long issueCount = issues.stream()
                    .filter(issue -> batch.externalId().equals(issue.importBatchId()))
                    .count();
            if (batch.totalLineCount() != lineCount || batch.issueCount() != issueCount)
            {
                throw new IllegalStateException(APP
                        + ".bankStatementFacts batch counts do not match its exported lines and issues.");
            }
        }
        for (ClearanceValue clearance : clearances)
        {
            requireReference(clearance.transactionLineId(), transactionLineIds,
                    APP + ".bankStatementFacts.transactionLineClearance[].lineId");
        }
        for (SessionValue session : sessions)
        {
            requireReference(session.bankAccountId(), bankAccountIds,
                    APP + ".reconciliation.sessions[].bankAccountId");
        }
        Set<String> statementLineIds = linesById.keySet();
        for (MatchValue match : matches)
        {
            requireReference(match.statementLineId(), statementLineIds,
                    APP + ".reconciliation.matches[].statementLineId");
            requireReference(match.transactionLineId(), transactionLineIds,
                    APP + ".reconciliation.matches[].lineId");
        }
    }

    private static Set<String> identities(JsonNode values, String field)
    {
        Set<String> identities = new HashSet<>();
        if (values.isArray())
        {
            for (JsonNode value : values)
            {
                identities.add(text(value, field, "$[]"));
            }
        }
        return identities;
    }

    private static void requireReference(String identity, Set<String> identities, String path)
    {
        if (identity != null && !identities.contains(identity))
        {
            throw new IllegalStateException(path + " does not resolve: " + identity);
        }
    }

    private static void parseConfiguration(
            JsonNode value,
            List<BankValue> banks,
            List<BankAccountValue> accounts)
    {
        if (absent(value))
        {
            return;
        }
        String path = APP + ".bankConfiguration";
        requireObject(value, path);
        requireFields(value, Set.of("banks", "accounts"), Set.of("banks", "accounts"), path);
        Set<String> bankIds = new HashSet<>();
        JsonNode bankNodes = array(value, "banks", path);
        for (int index = 0; index < bankNodes.size(); index++)
        {
            JsonNode bank = bankNodes.get(index);
            String itemPath = path + ".banks[" + index + "]";
            requireObject(bank, itemPath);
            requireFields(bank,
                    Set.of("bankId", "name", "routingNumber", "address", "website", "contactName",
                            "contactPhone", "contactEmail", "notes", "active"),
                    Set.of("bankId", "name", "active"), itemPath);
            String externalId = unique(bank, "bankId", itemPath, bankIds, "bank");
            banks.add(new BankValue(
                    externalId,
                    text(bank, "name", itemPath),
                    optionalText(bank, "routingNumber", itemPath),
                    optionalText(bank, "address", itemPath),
                    optionalText(bank, "website", itemPath),
                    optionalText(bank, "contactName", itemPath),
                    optionalText(bank, "contactPhone", itemPath),
                    optionalText(bank, "contactEmail", itemPath),
                    optionalText(bank, "notes", itemPath),
                    flag(bank, "active", itemPath)));
        }

        Set<String> accountIds = new HashSet<>();
        JsonNode accountNodes = array(value, "accounts", path);
        for (int index = 0; index < accountNodes.size(); index++)
        {
            JsonNode account = accountNodes.get(index);
            String itemPath = path + ".accounts[" + index + "]";
            requireObject(account, itemPath);
            requireFields(account,
                    Set.of("bankAccountId", "bankId", "ledgerAccountId", "name", "nickname",
                            "institutionName", "accountType", "lastFour", "maskedAccountNumber",
                            "openingDate", "statementImportFormat", "ofxBankId", "ofxAccountId",
                            "openingBalance", "active", "notes"),
                    Set.of("bankAccountId", "name", "openingBalance", "active"), itemPath);
            String externalId = unique(
                    account, "bankAccountId", itemPath, accountIds, "configured bank account");
            String bankId = optionalText(account, "bankId", itemPath);
            if (bankId != null && !bankIds.contains(bankId))
            {
                throw new IllegalStateException(itemPath + ".bankId does not resolve to an imported bank.");
            }
            accounts.add(new BankAccountValue(
                    externalId,
                    bankId,
                    optionalText(account, "ledgerAccountId", itemPath),
                    text(account, "name", itemPath),
                    optionalText(account, "nickname", itemPath),
                    optionalText(account, "institutionName", itemPath),
                    optionalText(account, "accountType", itemPath),
                    optionalText(account, "lastFour", itemPath),
                    optionalText(account, "maskedAccountNumber", itemPath),
                    optionalDate(account, "openingDate", itemPath),
                    optionalEnum(BankingDataFormat.class,
                            optionalText(account, "statementImportFormat", itemPath),
                            itemPath + ".statementImportFormat"),
                    optionalText(account, "ofxBankId", itemPath),
                    optionalText(account, "ofxAccountId", itemPath),
                    decimal(account, "openingBalance", itemPath, true, true),
                    flag(account, "active", itemPath),
                    optionalText(account, "notes", itemPath)));
        }
    }

    private static void parseStatementFacts(
            JsonNode value,
            List<BatchValue> batches,
            List<StatementLineValue> lines,
            List<IssueValue> issues,
            List<ClearanceValue> clearances)
    {
        if (absent(value))
        {
            return;
        }
        String path = APP + ".bankStatementFacts";
        requireObject(value, path);
        requireFields(value,
                Set.of("importBatches", "statementLines", "issues", "transactionLineClearance"),
                Set.of("importBatches", "statementLines", "issues", "transactionLineClearance"), path);
        Set<String> batchIds = new HashSet<>();
        JsonNode batchNodes = array(value, "importBatches", path);
        for (int index = 0; index < batchNodes.size(); index++)
        {
            JsonNode batch = batchNodes.get(index);
            String itemPath = path + ".importBatches[" + index + "]";
            requireObject(batch, itemPath);
            requireFields(batch,
                    Set.of("importBatchId", "bankAccountId", "sourceName", "sourceHash", "sourceFormat",
                            "sourceVariant", "sourceVersion", "sourceEncoding", "sourceInstitutionId",
                            "sourceBankId", "sourceAccountId", "sourceAccountType", "currency",
                            "statementStartDate", "statementEndDate", "ledgerBalance", "availableBalance",
                            "accountMatchStatus", "accountIdentityConfirmed",
                            "status", "importedAt", "completedAt", "totalLineCount", "acceptedLineCount",
                            "rejectedLineCount", "issueCount", "notes"),
                    Set.of("importBatchId", "sourceName", "sourceFormat", "status", "importedAt",
                            "totalLineCount", "acceptedLineCount", "rejectedLineCount", "issueCount"), itemPath);
            String externalId = unique(batch, "importBatchId", itemPath, batchIds, "bank import batch");
            batches.add(new BatchValue(
                    externalId,
                    optionalText(batch, "bankAccountId", itemPath),
                    text(batch, "sourceName", itemPath),
                    optionalText(batch, "sourceHash", itemPath),
                    enumValue(BankImportBatch.SourceFormat.class,
                            text(batch, "sourceFormat", itemPath), itemPath + ".sourceFormat"),
                    optionalText(batch, "sourceVariant", itemPath),
                    optionalText(batch, "sourceVersion", itemPath),
                    optionalText(batch, "sourceEncoding", itemPath),
                    optionalText(batch, "sourceInstitutionId", itemPath),
                    optionalText(batch, "sourceBankId", itemPath),
                    optionalText(batch, "sourceAccountId", itemPath),
                    optionalText(batch, "sourceAccountType", itemPath),
                    optionalText(batch, "currency", itemPath),
                    optionalDate(batch, "statementStartDate", itemPath),
                    optionalDate(batch, "statementEndDate", itemPath),
                    optionalDecimal(batch, "ledgerBalance", itemPath, true),
                    optionalDecimal(batch, "availableBalance", itemPath, true),
                    optionalEnumName(Set.of("EXACT", "CONFIRMATION_REQUIRED"),
                            optionalText(batch, "accountMatchStatus", itemPath),
                            itemPath + ".accountMatchStatus"),
                    optionalFlag(batch, "accountIdentityConfirmed", itemPath),
                    enumValue(BankImportBatch.Status.class,
                            text(batch, "status", itemPath), itemPath + ".status"),
                    instant(batch, "importedAt", itemPath),
                    optionalInstant(batch, "completedAt", itemPath),
                    nonNegativeInteger(batch, "totalLineCount", itemPath),
                    nonNegativeInteger(batch, "acceptedLineCount", itemPath),
                    nonNegativeInteger(batch, "rejectedLineCount", itemPath),
                    nonNegativeInteger(batch, "issueCount", itemPath),
                    optionalText(batch, "notes", itemPath)));
        }

        Set<String> lineIds = new HashSet<>();
        JsonNode lineNodes = array(value, "statementLines", path);
        for (int index = 0; index < lineNodes.size(); index++)
        {
            JsonNode line = lineNodes.get(index);
            String itemPath = path + ".statementLines[" + index + "]";
            requireObject(line, itemPath);
            requireFields(line,
                    Set.of("statementLineId", "importBatchId", "bankAccountId", "sourceRowNumber",
                            "sourceTransactionId", "deterministicFingerprint", "statementAccountIdentifier",
                            "transactionDate", "postedDate", "amount", "transactionType", "name", "memo",
                            "checkNumber", "reference", "currency", "correctionAction",
                            "correctedSourceTransactionId", "status", "dispositionNote", "acceptedTransactionId",
                            "matchedTransactionId"),
                    Set.of("statementLineId", "importBatchId", "sourceRowNumber",
                            "deterministicFingerprint", "status"), itemPath);
            String externalId = unique(line, "statementLineId", itemPath, lineIds, "bank statement line");
            String batchId = text(line, "importBatchId", itemPath);
            if (!batchIds.contains(batchId))
            {
                throw new IllegalStateException(itemPath + ".importBatchId does not resolve.");
            }
            BankStatementLine.Status status = enumValue(
                    BankStatementLine.Status.class, text(line, "status", itemPath), itemPath + ".status");
            String acceptedTransactionId = optionalText(line, "acceptedTransactionId", itemPath);
            String matchedTransactionId = optionalText(line, "matchedTransactionId", itemPath);
            if (status == BankStatementLine.Status.ACCEPTED && acceptedTransactionId == null)
            {
                throw new IllegalStateException(itemPath + " ACCEPTED status requires acceptedTransactionId.");
            }
            if (status == BankStatementLine.Status.MATCHED && matchedTransactionId == null)
            {
                throw new IllegalStateException(itemPath + " MATCHED status requires matchedTransactionId.");
            }
            lines.add(new StatementLineValue(
                    externalId,
                    batchId,
                    optionalText(line, "bankAccountId", itemPath),
                    nonNegativeInteger(line, "sourceRowNumber", itemPath),
                    optionalText(line, "sourceTransactionId", itemPath),
                    text(line, "deterministicFingerprint", itemPath),
                    optionalText(line, "statementAccountIdentifier", itemPath),
                    optionalDate(line, "transactionDate", itemPath),
                    optionalDate(line, "postedDate", itemPath),
                    optionalDecimal(line, "amount", itemPath, true),
                    optionalText(line, "transactionType", itemPath),
                    optionalText(line, "name", itemPath),
                    optionalText(line, "memo", itemPath),
                    optionalText(line, "checkNumber", itemPath),
                    optionalText(line, "reference", itemPath),
                    optionalText(line, "currency", itemPath),
                    optionalEnumName(Set.of("DELETE", "REPLACE"),
                            optionalText(line, "correctionAction", itemPath),
                            itemPath + ".correctionAction"),
                    optionalText(line, "correctedSourceTransactionId", itemPath),
                    status,
                    optionalText(line, "dispositionNote", itemPath),
                    acceptedTransactionId,
                    matchedTransactionId));
        }

        Set<String> issueIds = new HashSet<>();
        JsonNode issueNodes = array(value, "issues", path);
        for (int index = 0; index < issueNodes.size(); index++)
        {
            JsonNode issue = issueNodes.get(index);
            String itemPath = path + ".issues[" + index + "]";
            requireObject(issue, itemPath);
            requireFields(issue,
                    Set.of("issueId", "importBatchId", "statementLineId", "sourceRowNumber", "severity",
                            "code", "message", "createdAt"),
                    Set.of("issueId", "importBatchId", "severity", "code", "message", "createdAt"), itemPath);
            String externalId = unique(issue, "issueId", itemPath, issueIds, "bank import issue");
            String batchId = text(issue, "importBatchId", itemPath);
            String lineId = optionalText(issue, "statementLineId", itemPath);
            if (!batchIds.contains(batchId) || (lineId != null && !lineIds.contains(lineId)))
            {
                throw new IllegalStateException(itemPath + " has an unresolved batch or statement-line reference.");
            }
            issues.add(new IssueValue(
                    externalId,
                    batchId,
                    lineId,
                    optionalInteger(issue, "sourceRowNumber", itemPath),
                    enumValue(ImportIssue.Severity.class,
                            text(issue, "severity", itemPath), itemPath + ".severity"),
                    text(issue, "code", itemPath),
                    text(issue, "message", itemPath),
                    instant(issue, "createdAt", itemPath)));
        }

        Set<String> clearedLineIds = new HashSet<>();
        JsonNode clearanceNodes = array(value, "transactionLineClearance", path);
        for (int index = 0; index < clearanceNodes.size(); index++)
        {
            JsonNode clearance = clearanceNodes.get(index);
            String itemPath = path + ".transactionLineClearance[" + index + "]";
            requireObject(clearance, itemPath);
            requireFields(clearance, Set.of("lineId", "bankCleared", "bankClearedOn", "statementLineId"),
                    Set.of("lineId", "bankCleared"), itemPath);
            String lineId = unique(clearance, "lineId", itemPath, clearedLineIds, "cleared transaction line");
            if (!flag(clearance, "bankCleared", itemPath))
            {
                throw new IllegalStateException(itemPath + ".bankCleared must be true.");
            }
            String statementLineId = optionalText(clearance, "statementLineId", itemPath);
            if (statementLineId != null && !lineIds.contains(statementLineId))
            {
                throw new IllegalStateException(itemPath + ".statementLineId does not resolve.");
            }
            clearances.add(new ClearanceValue(
                    lineId, optionalDate(clearance, "bankClearedOn", itemPath), statementLineId));
        }
    }

    private static void parseReconciliation(
            JsonNode value,
            List<SessionValue> sessions,
            List<MatchValue> matches)
    {
        if (absent(value))
        {
            return;
        }
        String path = APP + ".reconciliation";
        requireObject(value, path);
        requireFields(value, Set.of("sessions", "matches"), Set.of("sessions", "matches"), path);
        Set<String> sessionIds = new HashSet<>();
        JsonNode sessionNodes = array(value, "sessions", path);
        for (int index = 0; index < sessionNodes.size(); index++)
        {
            JsonNode session = sessionNodes.get(index);
            String itemPath = path + ".sessions[" + index + "]";
            requireObject(session, itemPath);
            requireFields(session,
                    Set.of("reconciliationSessionId", "bankAccountId", "statementStartDate",
                            "statementEndDate", "statementEndingBalance", "mismatchPolicy", "status", "notes",
                            "beginningBalance", "bookBalanceAll", "bookBalanceCleared", "differenceAmount",
                            "createdAt", "updatedAt"),
                    Set.of("reconciliationSessionId", "bankAccountId", "statementStartDate",
                            "statementEndDate", "mismatchPolicy", "status", "beginningBalance", "bookBalanceAll",
                            "bookBalanceCleared", "differenceAmount", "createdAt", "updatedAt"), itemPath);
            String externalId = unique(
                    session, "reconciliationSessionId", itemPath, sessionIds, "reconciliation session");
            LocalDate start = date(session, "statementStartDate", itemPath);
            LocalDate end = date(session, "statementEndDate", itemPath);
            if (start.isAfter(end))
            {
                throw new IllegalStateException(itemPath + " statement date range is reversed.");
            }
            String policy = enumName(Set.of("WARN_ONLY", "OVERWRITE_LEDGER_CLEARED_STATE",
                    "NEVER_OVERWRITE_REQUIRE_MANUAL", "DECIDE_PER_IMPORTED_LINE"),
                    text(session, "mismatchPolicy", itemPath), itemPath + ".mismatchPolicy");
            String status = enumName(Set.of("IN_PROGRESS", "UNRESOLVED", "BALANCED", "FINALIZED"),
                    text(session, "status", itemPath), itemPath + ".status");
            sessions.add(new SessionValue(
                    externalId,
                    text(session, "bankAccountId", itemPath),
                    start,
                    end,
                    optionalDecimal(session, "statementEndingBalance", itemPath, true),
                    policy,
                    status,
                    optionalText(session, "notes", itemPath),
                    decimal(session, "beginningBalance", itemPath, true, true),
                    decimal(session, "bookBalanceAll", itemPath, true, true),
                    decimal(session, "bookBalanceCleared", itemPath, true, true),
                    decimal(session, "differenceAmount", itemPath, true, true),
                    instant(session, "createdAt", itemPath),
                    instant(session, "updatedAt", itemPath)));
        }

        Set<String> matchIds = new HashSet<>();
        JsonNode matchNodes = array(value, "matches", path);
        for (int index = 0; index < matchNodes.size(); index++)
        {
            JsonNode match = matchNodes.get(index);
            String itemPath = path + ".matches[" + index + "]";
            requireObject(match, itemPath);
            requireFields(match,
                    Set.of("reconciliationMatchId", "reconciliationSessionId", "statementLineId", "lineId",
                            "matchStatus", "resolutionNote", "createdAt", "updatedAt"),
                    Set.of("reconciliationMatchId", "reconciliationSessionId", "matchStatus", "createdAt",
                            "updatedAt"), itemPath);
            String externalId = unique(
                    match, "reconciliationMatchId", itemPath, matchIds, "reconciliation match");
            String sessionId = text(match, "reconciliationSessionId", itemPath);
            if (!sessionIds.contains(sessionId))
            {
                throw new IllegalStateException(itemPath + ".reconciliationSessionId does not resolve.");
            }
            String statementLineId = optionalText(match, "statementLineId", itemPath);
            String transactionLineId = optionalText(match, "lineId", itemPath);
            if (statementLineId == null && transactionLineId == null)
            {
                throw new IllegalStateException(itemPath + " must reference a statement or transaction line.");
            }
            String status = enumName(Set.of("MATCHED", "UNMATCHED", "AMOUNT_MISMATCH", "DATE_MISMATCH",
                    "DUPLICATE_POSSIBLE", "CLEARED_STATE_MISMATCH", "RESOLVED"),
                    text(match, "matchStatus", itemPath), itemPath + ".matchStatus");
            matches.add(new MatchValue(
                    externalId,
                    sessionId,
                    statementLineId,
                    transactionLineId,
                    status,
                    optionalText(match, "resolutionNote", itemPath),
                    instant(match, "createdAt", itemPath),
                    instant(match, "updatedAt", itemPath)));
        }
    }

    List<BankValue> banks() { return banks; }
    List<BankAccountValue> bankAccounts() { return bankAccounts; }
    List<BatchValue> batches() { return batches; }
    List<StatementLineValue> statementLines() { return statementLines; }
    List<IssueValue> issues() { return issues; }
    List<ClearanceValue> clearances() { return clearances; }
    List<SessionValue> sessions() { return sessions; }
    List<MatchValue> matches() { return matches; }

    private static boolean absent(JsonNode value)
    {
        return value == null || value.isMissingNode() || value.isNull();
    }

    private static JsonNode array(JsonNode value, String field, String path)
    {
        JsonNode result = value.get(field);
        if (result == null || !result.isArray())
        {
            throw new IllegalStateException(path + "." + field + " must be an array.");
        }
        return result;
    }

    private static void requireObject(JsonNode value, String path)
    {
        if (value == null || !value.isObject())
        {
            throw new IllegalStateException(path + " must be an object.");
        }
    }

    private static void requireFields(JsonNode value, Set<String> allowed, Set<String> required, String path)
    {
        Set<String> present = new HashSet<>();
        Iterator<String> names = value.fieldNames();
        while (names.hasNext())
        {
            String name = names.next();
            if (!allowed.contains(name))
            {
                throw new IllegalStateException(path + " has unsupported field " + name + ".");
            }
            present.add(name);
        }
        if (!present.containsAll(required))
        {
            Set<String> missing = new HashSet<>(required);
            missing.removeAll(present);
            throw new IllegalStateException(path + " is missing required fields " + missing + ".");
        }
    }

    private static String unique(
            JsonNode value, String field, String path, Set<String> identities, String label)
    {
        String identity = text(value, field, path);
        if (!identities.add(identity))
        {
            throw new IllegalStateException("SCLX contains duplicate " + label + " identity " + identity + ".");
        }
        return identity;
    }

    private static String text(JsonNode value, String field, String path)
    {
        String result = optionalText(value, field, path);
        if (result == null)
        {
            throw new IllegalStateException(path + "." + field + " must be a nonblank string.");
        }
        return result;
    }

    private static String optionalText(JsonNode value, String field, String path)
    {
        JsonNode node = value.get(field);
        if (node == null || node.isNull())
        {
            return null;
        }
        if (!node.isTextual())
        {
            throw new IllegalStateException(path + "." + field + " must be text or null.");
        }
        String result = node.textValue().trim();
        return result.isEmpty() ? null : result;
    }

    private static boolean flag(JsonNode value, String field, String path)
    {
        JsonNode node = value.get(field);
        if (node == null || !node.isBoolean())
        {
            throw new IllegalStateException(path + "." + field + " must be boolean.");
        }
        return node.booleanValue();
    }

    private static boolean optionalFlag(JsonNode value, String field, String path)
    {
        return value.has(field) && flag(value, field, path);
    }

    private static int nonNegativeInteger(JsonNode value, String field, String path)
    {
        Integer result = optionalInteger(value, field, path);
        if (result == null)
        {
            throw new IllegalStateException(path + "." + field + " must be an integer.");
        }
        return result;
    }

    private static Integer optionalInteger(JsonNode value, String field, String path)
    {
        JsonNode node = value.get(field);
        if (node == null || node.isNull())
        {
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() < 0)
        {
            throw new IllegalStateException(path + "." + field + " must be a nonnegative integer.");
        }
        return node.intValue();
    }

    private static BigDecimal optionalDecimal(JsonNode value, String field, String path, boolean signed)
    {
        JsonNode node = value.get(field);
        return node == null || node.isNull() ? null : decimal(value, field, path, signed, true);
    }

    private static BigDecimal decimal(
            JsonNode value, String field, String path, boolean signed, boolean required)
    {
        JsonNode node = value.get(field);
        if (node == null || node.isNull())
        {
            if (!required)
            {
                return null;
            }
            throw new IllegalStateException(path + "." + field + " must be a decimal value.");
        }
        if (!node.isTextual() && !node.isNumber())
        {
            throw new IllegalStateException(path + "." + field + " must be a decimal value.");
        }
        try
        {
            BigDecimal amount = new BigDecimal(node.asText());
            if (amount.scale() > 4 || amount.setScale(4, RoundingMode.UNNECESSARY).precision() > 19)
            {
                throw new IllegalStateException(path + "." + field + " exceeds DECIMAL(19,4).");
            }
            if (!signed && amount.signum() < 0)
            {
                throw new IllegalStateException(path + "." + field + " must be nonnegative.");
            }
            return amount.setScale(4, RoundingMode.UNNECESSARY);
        }
        catch (ArithmeticException | NumberFormatException ex)
        {
            throw new IllegalStateException(path + "." + field + " must be DECIMAL(19,4).", ex);
        }
    }

    private static LocalDate date(JsonNode value, String field, String path)
    {
        LocalDate result = optionalDate(value, field, path);
        if (result == null)
        {
            throw new IllegalStateException(path + "." + field + " must be an ISO date.");
        }
        return result;
    }

    private static LocalDate optionalDate(JsonNode value, String field, String path)
    {
        String text = optionalText(value, field, path);
        if (text == null)
        {
            return null;
        }
        try
        {
            return LocalDate.parse(text);
        }
        catch (DateTimeParseException ex)
        {
            throw new IllegalStateException(path + "." + field + " must use ISO date format.", ex);
        }
    }

    private static Instant instant(JsonNode value, String field, String path)
    {
        Instant result = optionalInstant(value, field, path);
        if (result == null)
        {
            throw new IllegalStateException(path + "." + field + " must be an ISO instant.");
        }
        return result;
    }

    private static Instant optionalInstant(JsonNode value, String field, String path)
    {
        String text = optionalText(value, field, path);
        if (text == null)
        {
            return null;
        }
        try
        {
            return Instant.parse(text);
        }
        catch (DateTimeParseException ex)
        {
            throw new IllegalStateException(path + "." + field + " must use ISO instant format.", ex);
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String path)
    {
        try
        {
            return Enum.valueOf(type, value);
        }
        catch (IllegalArgumentException ex)
        {
            throw new IllegalStateException(path + " has unsupported value " + value + ".", ex);
        }
    }

    private static <E extends Enum<E>> E optionalEnum(Class<E> type, String value, String path)
    {
        return value == null ? null : enumValue(type, value, path);
    }

    private static String enumName(Set<String> values, String value, String path)
    {
        if (!values.contains(value))
        {
            throw new IllegalStateException(path + " has unsupported value " + value + ".");
        }
        return value;
    }

    private static String optionalEnumName(Set<String> values, String value, String path)
    {
        return value == null ? null : enumName(values, value, path);
    }

    record BankValue(
            String externalId, String name, String routingNumber, String address, String website,
            String contactName, String contactPhone, String contactEmail, String notes, boolean active) { }

    record BankAccountValue(
            String externalId, String bankId, String ledgerAccountId, String name, String nickname,
            String institutionName, String accountType, String lastFour, String maskedAccountNumber,
            LocalDate openingDate, BankingDataFormat statementImportFormat, String ofxBankId,
            String ofxAccountId, BigDecimal openingBalance, boolean active, String notes) { }

    record BatchValue(
            String externalId, String bankAccountId, String sourceName, String sourceHash,
            BankImportBatch.SourceFormat sourceFormat, String sourceVariant, String sourceVersion,
            String sourceEncoding, String sourceInstitutionId, String sourceBankId, String sourceAccountId,
            String sourceAccountType, String currency, LocalDate statementStartDate,
            LocalDate statementEndDate, BigDecimal ledgerBalance, BigDecimal availableBalance,
            String accountMatchStatus, boolean accountIdentityConfirmed,
            BankImportBatch.Status status, Instant importedAt,
            Instant completedAt, int totalLineCount, int acceptedLineCount, int rejectedLineCount,
            int issueCount, String notes) { }

    record StatementLineValue(
            String externalId, String importBatchId, String bankAccountId, int sourceRowNumber,
            String sourceTransactionId, String deterministicFingerprint, String statementAccountIdentifier,
            LocalDate transactionDate, LocalDate postedDate, BigDecimal amount, String transactionType,
            String name, String memo, String checkNumber, String reference, String currency,
            String correctionAction, String correctedSourceTransactionId, BankStatementLine.Status status,
            String dispositionNote, String acceptedTransactionId, String matchedTransactionId) { }

    record IssueValue(
            String externalId, String importBatchId, String statementLineId, Integer sourceRowNumber,
            ImportIssue.Severity severity, String code, String message, Instant createdAt) { }

    record ClearanceValue(String transactionLineId, LocalDate clearedOn, String statementLineId) { }

    record SessionValue(
            String externalId, String bankAccountId, LocalDate statementStartDate, LocalDate statementEndDate,
            BigDecimal statementEndingBalance, String mismatchPolicy, String status, String notes,
            BigDecimal beginningBalance, BigDecimal bookBalanceAll, BigDecimal bookBalanceCleared,
            BigDecimal differenceAmount, Instant createdAt, Instant updatedAt) { }

    record MatchValue(
            String externalId, String reconciliationSessionId, String statementLineId,
            String transactionLineId, String matchStatus, String resolutionNote,
            Instant createdAt, Instant updatedAt) { }
}
