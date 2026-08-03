package org.nonprofitbookkeeping.interchange.bank;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.interchange.InterchangeMessageSeverity;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.ImportIssue;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.BankImportNormalizationService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Strict normalized-CSV preview and one-transaction durable-review restoration authority. */
public final class NormalizedBankCsvReviewService
{
    private static final String SOURCE_VARIANT = "NORMALIZED_CSV_1_0";

    private final Jpa jpa;
    private final NormalizedBankCsvParser parser;
    private final BankStatementAccountMatcher accountMatcher;
    private final Runnable afterPersistHook;

    public NormalizedBankCsvReviewService(Jpa jpa)
    {
        this(jpa, new NormalizedBankCsvParser(), new BankStatementAccountMatcher(), () -> { });
    }

    NormalizedBankCsvReviewService(
            Jpa jpa,
            NormalizedBankCsvParser parser,
            BankStatementAccountMatcher accountMatcher,
            Runnable afterPersistHook)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.accountMatcher = Objects.requireNonNull(accountMatcher, "accountMatcher");
        this.afterPersistHook = Objects.requireNonNull(afterPersistHook, "afterPersistHook");
    }

    public NormalizedBankCsvReviewPreview preview(
            Path source, String companyCode, long bankAccountId)
    {
        Path exact = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        String hash = sha256(BankCsvParser.read(exact));
        NormalizedBankCsvDocument document = parser.parse(exact);
        try (EntityManager em = jpa.em())
        {
            Company company = company(em, companyCode);
            CompanyBankAccount account = account(em, company, bankAccountId);
            BankStatementAccountMatcher.Match match = matchAll(company, account, document);
            List<InterchangeValidationMessage> messages = new ArrayList<>(document.messages());
            messages.addAll(match.messages());
            if (!identicalImportExists(em, company, account, hash))
            {
                validateExternalIdentityConflicts(em, company, account, document, messages);
                validateMatchedTransactions(em, company, document, messages);
            }
            return new NormalizedBankCsvReviewPreview(
                    exact, hash, company.getCode(), account.getId(), account.getName(),
                    document, match.status(), previewLines(document), messages);
        }
    }

    private static List<BankImportNormalizationService.NormalizedBankStatementLine> previewLines(
            NormalizedBankCsvDocument document)
    {
        Map<Integer, BankStatementExportRow> sourceRows = new HashMap<>();
        document.batches().stream()
                .flatMap(batch -> batch.rows().stream())
                .forEach(row -> sourceRows.put(row.sourceRowNumber(), row.value()));
        return new BankImportNormalizationService()
                .normalize(
                        document.statement(),
                        BankImportNormalizationService.DuplicateContext.empty())
                .lines().stream()
                .map(line ->
                {
                    BankStatementExportRow source = sourceRows.get(line.sourceRowNumber());
                    boolean exact = source != null && "EXACT".equals(source.duplicateStatus());
                    boolean probable = source != null && "PROBABLE".equals(source.duplicateStatus());
                    return new BankImportNormalizationService.NormalizedBankStatementLine(
                            line.sourceRowNumber(),
                            line.sourceTransactionId(),
                            line.deterministicFingerprint(),
                            line.transactionDate(),
                            line.postedDate(),
                            line.amount(),
                            line.transactionType(),
                            line.name(),
                            line.memo(),
                            line.checkNumber(),
                            line.reference(),
                            line.currency(),
                            line.correctionAction(),
                            line.correctedSourceTransactionId(),
                            exact,
                            probable,
                            line.issues());
                })
                .toList();
    }

    public NormalizedBankCsvReviewResult commit(
            NormalizedBankCsvReviewPreview approvedPreview,
            boolean accountIdentityConfirmed,
            String actor)
    {
        if (approvedPreview == null)
        {
            throw new IllegalArgumentException("Approved normalized bank CSV preview is required.");
        }
        if (actor == null || actor.isBlank())
        {
            throw new IllegalArgumentException("Audit actor is required.");
        }
        NormalizedBankCsvReviewPreview current = preview(
                approvedPreview.source(), approvedPreview.companyCode(), approvedPreview.bankAccountId());
        if (!approvedPreview.sourceHash().equals(current.sourceHash()))
        {
            throw new IllegalArgumentException("Normalized bank CSV changed after preview; preview it again.");
        }
        if (!current.commitAllowed(accountIdentityConfirmed))
        {
            throw new IllegalArgumentException(
                    "Normalized bank CSV preview has unresolved blocking or account-identity issues.");
        }

        try (EntityManager em = jpa.em())
        {
            var transaction = em.getTransaction();
            transaction.begin();
            try
            {
                Company company = company(em, current.companyCode());
                CompanyBankAccount account = account(em, company, current.bankAccountId());
                BankStatementAccountMatcher.Match match = matchAll(company, account, current.document());
                if (!match.commitAllowed(accountIdentityConfirmed))
                {
                    throw new IllegalArgumentException(
                            "Configured bank-account identity changed after normalized CSV preview.");
                }
                List<BankImportBatch> existing = identicalImports(
                        em, company, account, current.sourceHash());
                if (!existing.isEmpty())
                {
                    NormalizedBankCsvReviewResult result = existingResult(em, existing);
                    transaction.commit();
                    return result;
                }

                List<InterchangeValidationMessage> identityMessages = new ArrayList<>();
                validateExternalIdentityConflicts(
                        em, company, account, current.document(), identityMessages);
                Map<String, Txn> matchedTransactions = validateMatchedTransactions(
                        em, company, current.document(), identityMessages);
                if (identityMessages.stream().anyMatch(InterchangeValidationMessage::blocking))
                {
                    throw new IllegalArgumentException(
                            "Normalized bank CSV identities changed after preview; preview it again.");
                }

                Instant importedAt = Instant.now();
                List<Long> batchIds = new ArrayList<>();
                int reviewable = 0;
                int matched = 0;
                int duplicates = 0;
                int issues = 0;
                for (NormalizedBankCsvDocument.Batch sourceBatch : current.document().batches())
                {
                    BankStatementExportRow first = sourceBatch.rows().get(0).value();
                    BankImportBatch batch = new BankImportBatch();
                    batch.initializeImportMetadata(
                            portableIdentity(company, account, "BATCH", sourceBatch.externalId()), importedAt);
                    batch.setCompany(company);
                    batch.setBankAccount(account);
                    batch.setSourceExternalId(sourceBatch.externalId());
                    batch.setSourceName(sourceBatch.sourceFileName());
                    batch.setSourcePath(current.source().toString());
                    batch.setSourceHash(current.sourceHash());
                    batch.setSourceFormat(BankImportBatch.SourceFormat.valueOf(sourceBatch.sourceFormat()));
                    batch.setSourceVariant(SOURCE_VARIANT);
                    batch.setSourceVersion("1.0");
                    batch.setSourceEncoding("UTF-8");
                    batch.setSourceInstitutionId(blankToNull(first.institutionId()));
                    batch.setSourceBankId(blankToNull(first.bankId()));
                    batch.setSourceAccountId(first.accountId());
                    batch.setSourceAccountType(blankToNull(first.accountType()));
                    batch.setCurrency(first.currency());
                    batch.setStatementStartDate(first.statementStartDate());
                    batch.setStatementEndDate(first.statementEndDate());
                    batch.setLedgerBalance(first.ledgerBalance());
                    batch.setAvailableBalance(first.availableBalance());
                    batch.setAccountMatchStatus(match.status().name());
                    batch.setAccountIdentityConfirmed(accountIdentityConfirmed);
                    batch.setStatus(BankImportBatch.Status.IMPORTED);
                    batch.setTotalLineCount(sourceBatch.rows().size());
                    batch.setNotes("Strict normalized bank CSV 1.0 semantic import");
                    em.persist(batch);

                    int rejected = 0;
                    int accepted = 0;
                    for (NormalizedBankCsvDocument.Row sourceRow : sourceBatch.rows())
                    {
                        BankStatementExportRow value = sourceRow.value();
                        BankStatementLine line = new BankStatementLine();
                        line.initializeImportMetadata(portableIdentity(
                                company, account, "LINE", value.statementLineExternalId()));
                        line.setBatch(batch);
                        line.setCompany(company);
                        line.setBankAccount(account);
                        line.setSourceRowNumber(sourceRow.sourceRowNumber());
                        line.setSourceExternalId(value.statementLineExternalId());
                        line.setSourceTransactionId(blankToNull(value.sourceTransactionId()));
                        line.setSourcePayeeId(blankToNull(value.payeeId()));
                        line.setDeterministicFingerprint(fingerprint(value));
                        line.setStatementAccountIdentifier(value.accountId());
                        line.setTransactionDate(value.transactionDate());
                        line.setPostedDate(value.postedDate());
                        line.setAmount(value.amount());
                        line.setTransactionType(blankToNull(value.transactionType()));
                        line.setName(blankToNull(value.payeeName()));
                        line.setMemo(blankToNull(value.memo()));
                        line.setCheckNumber(blankToNull(value.checkNumber()));
                        line.setReference(blankToNull(value.reference()));
                        line.setCurrency(value.currency());
                        line.setCorrectionAction(blankToNull(value.correctionAction()));
                        line.setCorrectedSourceTransactionId(
                                blankToNull(value.correctedSourceTransactionId()));
                        BankStatementLine.Status status = BankStatementLine.Status.valueOf(value.reviewStatus());
                        line.setStatus(status);
                        if (!value.matchedTransactionExternalId().isBlank())
                        {
                            line.setMatchedTransaction(matchedTransactions.get(
                                    value.matchedTransactionExternalId()));
                        }
                        em.persist(line);

                        if ("PROBABLE".equals(value.duplicateStatus()))
                        {
                            em.persist(issue(batch, line, sourceRow.sourceRowNumber(),
                                    ImportIssue.Severity.WARNING, "PROBABLE_DUPLICATE",
                                    "Probable duplicate retained from normalized bank CSV."));
                            issues++;
                        }
                        else if ("EXACT".equals(value.duplicateStatus()))
                        {
                            em.persist(issue(batch, line, sourceRow.sourceRowNumber(),
                                    ImportIssue.Severity.ERROR, "EXACT_DUPLICATE",
                                    "Exact duplicate retained from normalized bank CSV."));
                            issues++;
                        }
                        switch (status)
                        {
                            case MATCHED -> { matched++; accepted++; }
                            case ACCEPTED -> accepted++;
                            case DUPLICATE, ERROR, REJECTED -> { duplicates += status == BankStatementLine.Status.DUPLICATE ? 1 : 0; rejected++; }
                            default -> reviewable++;
                        }
                    }
                    batch.setAcceptedLineCount(accepted);
                    batch.setRejectedLineCount(rejected);
                    batch.setIssueCount((int) em.createQuery(
                                    "select count(i) from ImportIssue i where i.batch = :batch", Long.class)
                            .setParameter("batch", batch).getSingleResult().longValue());
                    batch.touchUpdatedAt();
                    em.flush();
                    batchIds.add(batch.getId());
                }
                afterPersistHook.run();
                AuditEvent audit = new AuditEvent();
                audit.setCompany(company);
                audit.setActor(actor.trim());
                audit.setActionType("NORMALIZED_BANK_CSV_IMPORTED");
                audit.setEntityType("BANK_IMPORT_BATCH_SET");
                audit.setEntityId(current.sourceHash());
                audit.setSummary("Imported " + current.document().batches().size()
                        + " source batch(es) and " + current.document().statement().transactions().size()
                        + " bank statement row(s) from normalized CSV for durable review.");
                audit.setAfterValue("hash=" + current.sourceHash()
                        + ";account=" + account.getPortableId()
                        + ";batches=" + current.document().batches().size()
                        + ";rows=" + current.document().statement().transactions().size());
                audit.setReason("Explicit normalized bank CSV semantic import");
                em.persist(audit);
                transaction.commit();
                return new NormalizedBankCsvReviewResult(
                        batchIds, true, batchIds.size(),
                        current.document().statement().transactions().size(),
                        reviewable, matched, duplicates, issues);
            }
            catch (RuntimeException ex)
            {
                if (transaction.isActive())
                {
                    transaction.rollback();
                }
                throw ex;
            }
        }
    }

    private static void validateExternalIdentityConflicts(
            EntityManager em,
            Company company,
            CompanyBankAccount account,
            NormalizedBankCsvDocument document,
            List<InterchangeValidationMessage> messages)
    {
        for (NormalizedBankCsvDocument.Batch batch : document.batches())
        {
            UUID batchPortableId = portableIdentity(company, account, "BATCH", batch.externalId());
            long batchCount = em.createQuery("""
                            select count(b) from BankImportBatch b
                             where b.portableId = :portableId
                                or (b.company = :company and b.bankAccount = :account
                                    and b.sourceExternalId = :externalId)
                            """, Long.class)
                    .setParameter("portableId", batchPortableId)
                    .setParameter("company", company)
                    .setParameter("account", account)
                    .setParameter("externalId", batch.externalId())
                    .getSingleResult();
            if (batchCount > 0)
            {
                blocking(messages, "NORMALIZED_BANK_BATCH_ID_CONFLICT", "batches." + batch.externalId(),
                        "Source batch external ID already exists for the selected account.");
            }
            for (NormalizedBankCsvDocument.Row row : batch.rows())
            {
                UUID linePortableId = portableIdentity(
                        company, account, "LINE", row.value().statementLineExternalId());
                long lineCount = em.createQuery("""
                                select count(l) from BankStatementLine l
                                 where l.portableId = :portableId
                                    or (l.company = :company and l.bankAccount = :account
                                        and l.sourceExternalId = :externalId)
                                """, Long.class)
                        .setParameter("portableId", linePortableId)
                        .setParameter("company", company)
                        .setParameter("account", account)
                        .setParameter("externalId", row.value().statementLineExternalId())
                        .getSingleResult();
                if (lineCount > 0)
                {
                    blocking(messages, "NORMALIZED_BANK_LINE_ID_CONFLICT",
                            "rows." + row.value().statementLineExternalId(),
                            "Statement-line external ID already exists for the selected account.");
                }
            }
        }
    }

    private BankStatementAccountMatcher.Match matchAll(
            Company company,
            CompanyBankAccount account,
            NormalizedBankCsvDocument document)
    {
        List<InterchangeValidationMessage> messages = new ArrayList<>();
        BankStatementAccountMatcher.Status combined = BankStatementAccountMatcher.Status.EXACT;
        for (NormalizedBankCsvDocument.Batch batch : document.batches())
        {
            BankStatementAccountMatcher.Match match = accountMatcher.match(
                    company, account, statementForBatch(document.statement().sourceName(), batch));
            messages.addAll(match.messages());
            if (match.status() == BankStatementAccountMatcher.Status.BLOCKING)
            {
                combined = BankStatementAccountMatcher.Status.BLOCKING;
            }
            else if (match.status() == BankStatementAccountMatcher.Status.CONFIRMATION_REQUIRED
                    && combined == BankStatementAccountMatcher.Status.EXACT)
            {
                combined = BankStatementAccountMatcher.Status.CONFIRMATION_REQUIRED;
            }
        }
        return new BankStatementAccountMatcher.Match(combined, messages);
    }

    private static BankStatementDocument statementForBatch(
            String normalizedFileName, NormalizedBankCsvDocument.Batch batch)
    {
        BankStatementExportRow first = batch.rows().get(0).value();
        List<BankStatementDocument.Transaction> transactions = batch.rows().stream()
                .map(row -> {
                    BankStatementExportRow value = row.value();
                    return new BankStatementDocument.Transaction(
                            row.sourceRowNumber(), value.transactionDate(), value.postedDate(),
                            value.amount(), value.sourceTransactionId(), value.transactionType(),
                            value.payeeName(), value.memo(), value.checkNumber(), value.reference(),
                            value.correctionAction(), value.correctedSourceTransactionId());
                }).toList();
        return new BankStatementDocument(
                normalizedFileName,
                org.nonprofitbookkeeping.model.BankingDataFormat.CSV,
                BankStatementDocument.Variant.NORMALIZED_CSV,
                "1.0",
                "UTF-8",
                new BankStatementDocument.AccountIdentity(
                        first.institutionId(), first.bankId(), first.accountId(), first.accountType()),
                first.currency(),
                first.statementStartDate(),
                first.statementEndDate(),
                first.ledgerBalance(),
                first.availableBalance(),
                transactions,
                List.of());
    }

    private static Map<String, Txn> validateMatchedTransactions(
            EntityManager em,
            Company company,
            NormalizedBankCsvDocument document,
            List<InterchangeValidationMessage> messages)
    {
        Map<String, Txn> matches = new HashMap<>();
        document.batches().stream().flatMap(batch -> batch.rows().stream())
                .map(row -> row.value().matchedTransactionExternalId())
                .filter(value -> !value.isBlank())
                .distinct()
                .forEach(externalId -> {
                    UUID portableId;
                    try
                    {
                        portableId = UUID.fromString(externalId);
                    }
                    catch (IllegalArgumentException ex)
                    {
                        blocking(messages, "NORMALIZED_BANK_MATCH_ID_INVALID", "rows.matched_transaction_external_id",
                                "Matched transaction identity is not a portable UUID: " + externalId + ".");
                        return;
                    }
                    Txn matched = em.createQuery("""
                                    select t from Txn t where t.company = :company and t.portableId = :portableId
                                    """, Txn.class)
                            .setParameter("company", company)
                            .setParameter("portableId", portableId)
                            .getResultStream().findFirst().orElse(null);
                    if (matched == null)
                    {
                        blocking(messages, "NORMALIZED_BANK_MATCH_TARGET_MISSING",
                                "rows.matched_transaction_external_id",
                                "Matched canonical transaction is not present in the selected company: "
                                        + externalId + ".");
                    }
                    else
                    {
                        matches.put(externalId, matched);
                    }
                });
        return matches;
    }

    private static boolean identicalImportExists(
            EntityManager em, Company company, CompanyBankAccount account, String sourceHash)
    {
        return !identicalImports(em, company, account, sourceHash).isEmpty();
    }

    private static List<BankImportBatch> identicalImports(
            EntityManager em, Company company, CompanyBankAccount account, String sourceHash)
    {
        return em.createQuery("""
                        select b from BankImportBatch b
                         where b.company = :company and b.bankAccount = :account
                           and b.sourceHash = :sourceHash and b.sourceVariant = :variant
                         order by b.id
                        """, BankImportBatch.class)
                .setParameter("company", company)
                .setParameter("account", account)
                .setParameter("sourceHash", sourceHash)
                .setParameter("variant", SOURCE_VARIANT)
                .getResultList();
    }

    private static NormalizedBankCsvReviewResult existingResult(
            EntityManager em, List<BankImportBatch> batches)
    {
        List<Long> ids = batches.stream().map(BankImportBatch::getId).toList();
        List<BankStatementLine.Status> statuses = em.createQuery("""
                        select l.status from BankStatementLine l where l.batch in :batches
                        """, BankStatementLine.Status.class)
                .setParameter("batches", batches)
                .getResultList();
        int reviewable = (int) statuses.stream()
                .filter(value -> value == BankStatementLine.Status.IMPORTED).count();
        int matched = (int) statuses.stream()
                .filter(value -> value == BankStatementLine.Status.MATCHED).count();
        int duplicates = (int) statuses.stream()
                .filter(value -> value == BankStatementLine.Status.DUPLICATE).count();
        int issues = batches.stream().mapToInt(BankImportBatch::getIssueCount).sum();
        return new NormalizedBankCsvReviewResult(
                ids, false, ids.size(), statuses.size(), reviewable, matched, duplicates, issues);
    }

    private static Company company(EntityManager em, String companyCode)
    {
        if (companyCode == null || companyCode.isBlank())
        {
            throw new IllegalArgumentException("Company code is required.");
        }
        Company company = em.createQuery(
                        "select c from Company c where c.code = :code", Company.class)
                .setParameter("code", companyCode.trim())
                .getResultStream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Company does not exist: " + companyCode + "."));
        if (!company.isActive())
        {
            throw new IllegalArgumentException("Selected company is inactive.");
        }
        return company;
    }

    private static CompanyBankAccount account(
            EntityManager em, Company company, long bankAccountId)
    {
        CompanyBankAccount account = em.createQuery("""
                        select a from CompanyBankAccount a
                        join fetch a.company join fetch a.bank join fetch a.account
                        where a.id = :id
                        """, CompanyBankAccount.class)
                .setParameter("id", bankAccountId)
                .getResultStream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Configured bank account does not exist: " + bankAccountId + "."));
        if (!company.getId().equals(account.getCompany().getId()))
        {
            throw new IllegalArgumentException("Configured bank account belongs to another company.");
        }
        return account;
    }

    private static ImportIssue issue(
            BankImportBatch batch,
            BankStatementLine line,
            int rowNumber,
            ImportIssue.Severity severity,
            String code,
            String message)
    {
        ImportIssue issue = new ImportIssue();
        issue.setBatch(batch);
        issue.setStatementLine(line);
        issue.setSourceRowNumber(rowNumber);
        issue.setSeverity(severity);
        issue.setCode(code);
        issue.setMessage(message);
        return issue;
    }

    private static UUID portableIdentity(
            Company company, CompanyBankAccount account, String type, String externalId)
    {
        try
        {
            return UUID.fromString(externalId);
        }
        catch (IllegalArgumentException ex)
        {
            String material = "SCA|NORMALIZED_BANK_CSV|" + company.getCode() + "|"
                    + account.getPortableId() + "|" + type + "|" + externalId;
            return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String fingerprint(BankStatementExportRow value)
    {
        String material = String.join("|",
                value.statementLineExternalId(),
                date(value.transactionDate()),
                date(value.postedDate()),
                value.amount().stripTrailingZeros().toPlainString(),
                value.sourceTransactionId().toUpperCase(Locale.ROOT),
                value.transactionType().toUpperCase(Locale.ROOT),
                value.payeeName().toUpperCase(Locale.ROOT),
                value.memo().toUpperCase(Locale.ROOT));
        return sha256(material.getBytes(StandardCharsets.UTF_8));
    }

    private static String date(LocalDate value)
    {
        return value == null ? "" : value.toString();
    }

    private static String sha256(byte[] bytes)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private static void blocking(
            List<InterchangeValidationMessage> messages, String code, String path, String message)
    {
        messages.add(new InterchangeValidationMessage(
                InterchangeMessageSeverity.ERROR, code, path, message, true));
    }

    private static String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
