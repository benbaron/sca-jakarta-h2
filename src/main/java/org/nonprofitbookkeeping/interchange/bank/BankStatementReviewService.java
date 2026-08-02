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
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.BankImportNormalizationService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Previews and atomically persists strict OFX/QFX documents as durable review facts. */
public final class BankStatementReviewService
{
    private static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;

    private final Jpa jpa;
    private final BankStatementParser parser;
    private final BankStatementAccountMatcher accountMatcher;
    private final BankImportNormalizationService normalizationService;
    private final Runnable afterPersistHook;

    public BankStatementReviewService(Jpa jpa)
    {
        this(jpa, new BankStatementParser(), new BankStatementAccountMatcher(),
                new BankImportNormalizationService(), () -> { });
    }

    BankStatementReviewService(
            Jpa jpa,
            BankStatementParser parser,
            BankStatementAccountMatcher accountMatcher,
            BankImportNormalizationService normalizationService,
            Runnable afterPersistHook)
    {
        this.jpa = java.util.Objects.requireNonNull(jpa, "jpa");
        this.parser = java.util.Objects.requireNonNull(parser, "parser");
        this.accountMatcher = java.util.Objects.requireNonNull(accountMatcher, "accountMatcher");
        this.normalizationService = java.util.Objects.requireNonNull(normalizationService, "normalizationService");
        this.afterPersistHook = java.util.Objects.requireNonNull(afterPersistHook, "afterPersistHook");
    }

    public BankStatementReviewPreview preview(Path source, String companyCode, long bankAccountId)
    {
        Path exactSource = requireSource(source);
        String sourceHash = sha256(exactSource);
        BankStatementDocument document = parser.parse(exactSource);
        try (EntityManager em = jpa.em())
        {
            Company company = company(em, companyCode);
            CompanyBankAccount account = account(em, company, bankAccountId);
            BankStatementAccountMatcher.Match match = accountMatcher.match(company, account, document);
            BankImportNormalizationService.BankImportNormalizationResult normalized =
                    normalizationService.normalize(document, duplicateContext(em, company, account));
            List<InterchangeValidationMessage> messages = new ArrayList<>(document.messages());
            messages.addAll(match.messages());
            for (BankImportNormalizationService.NormalizedBankStatementLine line : normalized.lines())
            {
                for (BankImportNormalizationService.ImportRowIssue issue : line.issues())
                {
                    messages.add(new InterchangeValidationMessage(
                            interchangeSeverity(issue.severity()),
                            issue.code(),
                            "statement.transactions[" + line.sourceRowNumber() + "]",
                            issue.message(),
                            false));
                }
            }
            return new BankStatementReviewPreview(
                    exactSource,
                    sourceHash,
                    company.getCode(),
                    account.getId(),
                    account.getName(),
                    document,
                    match.status(),
                    normalized.lines(),
                    messages);
        }
    }

    public BankStatementReviewResult commit(
            BankStatementReviewPreview approvedPreview,
            boolean accountIdentityConfirmed,
            String actor)
    {
        if (approvedPreview == null)
        {
            throw new IllegalArgumentException("Approved bank-statement preview is required.");
        }
        if (actor == null || actor.isBlank())
        {
            throw new IllegalArgumentException("Audit actor is required.");
        }
        BankStatementReviewPreview current = preview(
                approvedPreview.source(),
                approvedPreview.companyCode(),
                approvedPreview.bankAccountId());
        if (!approvedPreview.sourceHash().equals(current.sourceHash()))
        {
            throw new IllegalArgumentException("Bank statement changed after preview; preview it again.");
        }
        if (!current.commitAllowed(accountIdentityConfirmed))
        {
            throw new IllegalArgumentException("Bank statement preview has unresolved blocking or account-identity issues.");
        }

        try (EntityManager em = jpa.em())
        {
            var transaction = em.getTransaction();
            transaction.begin();
            try
            {
                Company company = company(em, current.companyCode());
                CompanyBankAccount account = account(em, company, current.bankAccountId());
                BankStatementAccountMatcher.Match match = accountMatcher.match(company, account, current.document());
                if (!match.commitAllowed(accountIdentityConfirmed))
                {
                    throw new IllegalArgumentException("Configured bank-account identity changed after preview.");
                }

                BankImportBatch existing = identicalBatch(
                        em, company, account, current.document(), current.sourceHash());
                if (existing != null)
                {
                    BankStatementReviewResult result = existingResult(em, existing);
                    transaction.commit();
                    return result;
                }

                BankImportNormalizationService.BankImportNormalizationResult normalized =
                        normalizationService.normalize(current.document(), duplicateContext(em, company, account));
                BankImportBatch batch = batch(company, account, current, accountIdentityConfirmed);
                em.persist(batch);

                int issues = persistBatchIssues(em, batch, current.document().messages(), match.messages());
                int errors = 0;
                int duplicates = 0;
                int reviewable = 0;
                for (BankImportNormalizationService.NormalizedBankStatementLine value : normalized.lines())
                {
                    BankStatementLine line = statementLine(batch, company, account, current.document(), value);
                    if (value.exactDuplicate())
                    {
                        line.setStatus(BankStatementLine.Status.DUPLICATE);
                        duplicates++;
                    }
                    else if (value.hasErrors())
                    {
                        line.setStatus(BankStatementLine.Status.ERROR);
                        errors++;
                    }
                    else
                    {
                        line.setStatus(BankStatementLine.Status.IMPORTED);
                        reviewable++;
                    }
                    em.persist(line);
                    for (BankImportNormalizationService.ImportRowIssue valueIssue : value.issues())
                    {
                        em.persist(issue(batch, line, valueIssue));
                        issues++;
                    }
                }
                batch.setRejectedLineCount(errors + duplicates);
                batch.setIssueCount(issues);
                batch.touchUpdatedAt();
                em.flush();
                afterPersistHook.run();

                AuditEvent audit = new AuditEvent();
                audit.setCompany(company);
                audit.setActor(actor.trim());
                audit.setActionType("BANK_STATEMENT_REVIEW_IMPORTED");
                audit.setEntityType("BANK_IMPORT_BATCH");
                audit.setEntityId(batch.getPortableId().toString());
                audit.setSummary("Imported " + batch.getTotalLineCount()
                        + " bank statement row(s) from " + batch.getSourceName() + " for durable review.");
                audit.setAfterValue("hash=" + batch.getSourceHash()
                        + ";format=" + batch.getSourceFormat()
                        + ";account=" + account.getPortableId()
                        + ";reviewable=" + reviewable
                        + ";duplicates=" + duplicates
                        + ";errors=" + errors);
                audit.setReason("Explicit bank-statement review import");
                em.persist(audit);
                transaction.commit();
                return new BankStatementReviewResult(
                        batch.getId(), true, batch.getTotalLineCount(), reviewable,
                        errors, duplicates, issues);
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

    private static BankImportBatch batch(
            Company company,
            CompanyBankAccount account,
            BankStatementReviewPreview preview,
            boolean accountIdentityConfirmed)
    {
        BankStatementDocument document = preview.document();
        BankImportBatch batch = new BankImportBatch();
        batch.setCompany(company);
        batch.setBankAccount(account);
        batch.setSourceName(document.sourceName());
        batch.setSourcePath(preview.source().toString());
        batch.setSourceHash(preview.sourceHash());
        batch.setSourceFormat(sourceFormat(document));
        batch.setSourceVariant(document.variant().name());
        batch.setSourceVersion(document.version());
        batch.setSourceEncoding(document.encoding());
        batch.setSourceInstitutionId(blankToNull(document.account().institutionId()));
        batch.setSourceBankId(blankToNull(document.account().bankId()));
        batch.setSourceAccountId(document.account().accountId());
        batch.setSourceAccountType(blankToNull(document.account().accountType()));
        batch.setCurrency(document.currency());
        batch.setStatementStartDate(document.statementStartDate());
        batch.setStatementEndDate(document.statementEndDate());
        batch.setLedgerBalance(document.ledgerBalance());
        batch.setAvailableBalance(document.availableBalance());
        batch.setAccountMatchStatus(preview.accountMatchStatus().name());
        batch.setAccountIdentityConfirmed(accountIdentityConfirmed);
        batch.setStatus(BankImportBatch.Status.IMPORTED);
        batch.setTotalLineCount(preview.lines().size());
        batch.setNotes("Strict content-first " + document.variant() + " import");
        return batch;
    }

    private static BankStatementLine statementLine(
            BankImportBatch batch,
            Company company,
            CompanyBankAccount account,
            BankStatementDocument document,
            BankImportNormalizationService.NormalizedBankStatementLine value)
    {
        BankStatementLine line = new BankStatementLine();
        line.setBatch(batch);
        line.setCompany(company);
        line.setBankAccount(account);
        line.setSourceRowNumber(value.sourceRowNumber());
        line.setSourceTransactionId(blankToNull(value.sourceTransactionId()));
        line.setDeterministicFingerprint(value.deterministicFingerprint());
        line.setStatementAccountIdentifier(document.account().accountId());
        line.setTransactionDate(value.transactionDate());
        line.setPostedDate(value.postedDate());
        line.setAmount(value.amount());
        line.setTransactionType(blankToNull(value.transactionType()));
        line.setName(blankToNull(value.name()));
        line.setMemo(blankToNull(value.memo()));
        line.setCheckNumber(blankToNull(value.checkNumber()));
        line.setReference(blankToNull(value.reference()));
        line.setCurrency(blankToNull(value.currency()));
        line.setCorrectionAction(blankToNull(value.correctionAction()));
        line.setCorrectedSourceTransactionId(blankToNull(value.correctedSourceTransactionId()));
        return line;
    }

    private static int persistBatchIssues(
            EntityManager em,
            BankImportBatch batch,
            List<InterchangeValidationMessage> parserMessages,
            List<InterchangeValidationMessage> matchMessages)
    {
        int count = 0;
        List<InterchangeValidationMessage> values = new ArrayList<>(parserMessages);
        values.addAll(matchMessages);
        for (InterchangeValidationMessage value : values)
        {
            ImportIssue issue = new ImportIssue();
            issue.setBatch(batch);
            issue.setSeverity(issueSeverity(value.severity()));
            issue.setCode(value.code());
            issue.setMessage(value.message());
            em.persist(issue);
            count++;
        }
        return count;
    }

    private static ImportIssue issue(
            BankImportBatch batch,
            BankStatementLine line,
            BankImportNormalizationService.ImportRowIssue value)
    {
        ImportIssue issue = new ImportIssue();
        issue.setBatch(batch);
        issue.setStatementLine(line);
        issue.setSourceRowNumber(value.sourceRowNumber());
        issue.setSeverity(issueSeverity(value.severity()));
        issue.setCode(value.code());
        issue.setMessage(value.message());
        return issue;
    }

    private static BankImportNormalizationService.DuplicateContext duplicateContext(
            EntityManager em,
            Company company,
            CompanyBankAccount account)
    {
        Set<String> sourceIds = new HashSet<>(em.createQuery("""
                        select upper(l.sourceTransactionId)
                          from BankStatementLine l
                         where l.company = :company
                           and l.bankAccount = :account
                           and l.sourceTransactionId is not null
                        """, String.class)
                .setParameter("company", company)
                .setParameter("account", account)
                .getResultList());
        Set<String> fingerprints = new HashSet<>(em.createQuery("""
                        select l.deterministicFingerprint
                          from BankStatementLine l
                         where l.company = :company
                           and l.bankAccount = :account
                        """, String.class)
                .setParameter("company", company)
                .setParameter("account", account)
                .getResultList());
        List<BankImportNormalizationService.ProbableDuplicateCandidate> probable = em.createQuery("""
                        select l.postedDate, l.amount, l.name, l.memo
                          from BankStatementLine l
                         where l.company = :company
                           and l.bankAccount = :account
                        """, Object[].class)
                .setParameter("company", company)
                .setParameter("account", account)
                .setMaxResults(1_000_000)
                .getResultList().stream()
                .map(row -> new BankImportNormalizationService.ProbableDuplicateCandidate(
                        (LocalDate) row[0],
                        (java.math.BigDecimal) row[1],
                        (String) row[2],
                        (String) row[3],
                        3))
                .toList();
        return new BankImportNormalizationService.DuplicateContext(sourceIds, fingerprints, probable);
    }

    private static BankImportBatch identicalBatch(
            EntityManager em,
            Company company,
            CompanyBankAccount account,
            BankStatementDocument document,
            String sourceHash)
    {
        return em.createQuery("""
                        select b
                          from BankImportBatch b
                         where b.company = :company
                           and b.bankAccount = :account
                           and b.sourceFormat = :format
                           and b.sourceHash = :sourceHash
                         order by b.id
                        """, BankImportBatch.class)
                .setParameter("company", company)
                .setParameter("account", account)
                .setParameter("format", sourceFormat(document))
                .setParameter("sourceHash", sourceHash)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    private static BankStatementReviewResult existingResult(EntityManager em, BankImportBatch batch)
    {
        List<BankStatementLine.Status> statuses = em.createQuery("""
                        select l.status from BankStatementLine l where l.batch = :batch
                        """, BankStatementLine.Status.class)
                .setParameter("batch", batch)
                .getResultList();
        int errors = (int) statuses.stream().filter(status -> status == BankStatementLine.Status.ERROR).count();
        int duplicates = (int) statuses.stream().filter(status -> status == BankStatementLine.Status.DUPLICATE).count();
        int reviewable = (int) statuses.stream().filter(status -> status == BankStatementLine.Status.IMPORTED).count();
        return new BankStatementReviewResult(
                batch.getId(), false, statuses.size(), reviewable, errors, duplicates, batch.getIssueCount());
    }

    private static Company company(EntityManager em, String companyCode)
    {
        if (companyCode == null || companyCode.isBlank())
        {
            throw new IllegalArgumentException("Company code is required.");
        }
        return em.createQuery("select c from Company c where c.code = :code", Company.class)
                .setParameter("code", companyCode.trim())
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Company does not exist: " + companyCode + "."));
    }

    private static CompanyBankAccount account(EntityManager em, Company company, long accountId)
    {
        CompanyBankAccount account = em.createQuery("""
                        select a from CompanyBankAccount a
                        join fetch a.company
                        left join fetch a.bank
                        left join fetch a.account
                        where a.id = :id
                        """, CompanyBankAccount.class)
                .setParameter("id", accountId)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Configured bank account does not exist: " + accountId + "."));
        if (!company.getId().equals(account.getCompany().getId()))
        {
            throw new IllegalArgumentException("Configured bank account belongs to another company.");
        }
        return account;
    }

    private static Path requireSource(Path source)
    {
        if (source == null)
        {
            throw new IllegalArgumentException("Bank statement source is required.");
        }
        Path exact = source.toAbsolutePath().normalize();
        try
        {
            long size = Files.size(exact);
            if (size <= 0 || size > MAX_FILE_BYTES)
            {
                throw new IllegalArgumentException("Bank statement must contain 1 to 67108864 bytes.");
            }
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException("Cannot read bank statement: " + exact, ex);
        }
        return exact;
    }

    private static String sha256(Path path)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path))
            {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0)
                {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (IOException | NoSuchAlgorithmException ex)
        {
            throw new IllegalArgumentException("Cannot hash bank statement: " + path, ex);
        }
    }

    private static BankImportBatch.SourceFormat sourceFormat(BankStatementDocument document)
    {
        return document.format() == org.nonprofitbookkeeping.model.BankingDataFormat.QFX
                ? BankImportBatch.SourceFormat.QFX : BankImportBatch.SourceFormat.OFX;
    }

    private static InterchangeMessageSeverity interchangeSeverity(
            BankImportNormalizationService.ImportIssueSeverity severity)
    {
        return switch (severity)
        {
            case INFO -> InterchangeMessageSeverity.INFO;
            case WARNING -> InterchangeMessageSeverity.WARNING;
            // Row errors remain durable review facts and do not block the whole valid document.
            case ERROR -> InterchangeMessageSeverity.WARNING;
        };
    }

    private static ImportIssue.Severity issueSeverity(InterchangeMessageSeverity severity)
    {
        return switch (severity)
        {
            case INFO -> ImportIssue.Severity.INFO;
            case WARNING -> ImportIssue.Severity.WARNING;
            case ERROR -> ImportIssue.Severity.ERROR;
        };
    }

    private static ImportIssue.Severity issueSeverity(
            BankImportNormalizationService.ImportIssueSeverity severity)
    {
        return switch (severity)
        {
            case INFO -> ImportIssue.Severity.INFO;
            case WARNING -> ImportIssue.Severity.WARNING;
            case ERROR -> ImportIssue.Severity.ERROR;
        };
    }

    private static String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
