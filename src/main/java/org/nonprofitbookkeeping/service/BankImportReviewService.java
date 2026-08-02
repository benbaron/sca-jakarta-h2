package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.ImportIssue;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Persists normalized bank import review facts without creating ledger transactions. */
public class BankImportReviewService
{
    private final Jpa jpa;
    private final BankImportNormalizationService normalizationService;

    public BankImportReviewService(Jpa jpa)
    {
        this(jpa, new BankImportNormalizationService());
    }

    BankImportReviewService(Jpa jpa, BankImportNormalizationService normalizationService)
    {
        this.jpa = jpa;
        this.normalizationService = normalizationService;
    }

    public BankImportReviewResult createReviewBatch(BankImportReviewCommand command)
    {
        if (command == null)
        {
            throw new IllegalArgumentException("Bank import review command is required.");
        }
        if (isBlank(command.sourceName()))
        {
            throw new IllegalArgumentException("Bank import source name is required.");
        }

        try (EntityManager em = jpa.em())
        {
            var tx = em.getTransaction();
            tx.begin();
            try
            {
                Company company = companyByCode(em, command.companyCode());
                CompanyBankAccount bankAccount = bankAccountForCompany(em, command.bankAccountId(), company);
                BankImportNormalizationService.DuplicateContext duplicateContext = duplicateContext(em, company);
                BankImportNormalizationService.BankImportNormalizationResult normalized = normalizationService.normalize(command.records(), duplicateContext);

                BankImportBatch batch = new BankImportBatch();
                batch.setCompany(company);
                batch.setBankAccount(bankAccount);
                batch.setSourceName(command.sourceName().trim());
                batch.setSourcePath(blankToNull(command.sourcePath()));
                batch.setSourceHash(blankToNull(command.sourceHash()));
                batch.setSourceFormat(command.sourceFormat() == null ? BankImportBatch.SourceFormat.OTHER : command.sourceFormat());
                batch.setStatus(BankImportBatch.Status.IMPORTED);
                batch.setTotalLineCount(normalized.lines().size());
                batch.setNotes(blankToNull(command.notes()));
                em.persist(batch);

                int issueCount = 0;
                int errorLineCount = 0;
                int duplicateLineCount = 0;
                for (BankImportNormalizationService.NormalizedBankStatementLine normalizedLine : normalized.lines())
                {
                    BankStatementLine statementLine = new BankStatementLine();
                    statementLine.setBatch(batch);
                    statementLine.setCompany(company);
                    statementLine.setBankAccount(bankAccount);
                    statementLine.setSourceRowNumber(normalizedLine.sourceRowNumber());
                    statementLine.setSourceTransactionId(blankToNull(normalizedLine.sourceTransactionId()));
                    statementLine.setDeterministicFingerprint(normalizedLine.deterministicFingerprint());
                    statementLine.setTransactionDate(normalizedLine.transactionDate());
                    statementLine.setPostedDate(normalizedLine.postedDate());
                    statementLine.setAmount(normalizedLine.amount());
                    statementLine.setTransactionType(blankToNull(normalizedLine.transactionType()));
                    statementLine.setName(blankToNull(normalizedLine.name()));
                    statementLine.setMemo(blankToNull(normalizedLine.memo()));
                    if (normalizedLine.exactDuplicate())
                    {
                        statementLine.setStatus(BankStatementLine.Status.DUPLICATE);
                        duplicateLineCount++;
                    }
                    else if (normalizedLine.hasErrors())
                    {
                        statementLine.setStatus(BankStatementLine.Status.ERROR);
                        errorLineCount++;
                    }
                    else
                    {
                        statementLine.setStatus(BankStatementLine.Status.IMPORTED);
                    }
                    em.persist(statementLine);

                    for (BankImportNormalizationService.ImportRowIssue rowIssue : normalizedLine.issues())
                    {
                        ImportIssue issue = new ImportIssue();
                        issue.setBatch(batch);
                        issue.setStatementLine(statementLine);
                        issue.setSourceRowNumber(rowIssue.sourceRowNumber());
                        issue.setSeverity(mapSeverity(rowIssue.severity()));
                        issue.setCode(rowIssue.code());
                        issue.setMessage(rowIssue.message());
                        em.persist(issue);
                        issueCount++;
                    }
                }
                batch.setIssueCount(issueCount);
                batch.touchUpdatedAt();
                tx.commit();
                return new BankImportReviewResult(batch.getId(), batch.getTotalLineCount(), issueCount, errorLineCount, duplicateLineCount);
            }
            catch (RuntimeException ex)
            {
                rollback(tx);
                throw ex;
            }
        }
    }

    /** Recreates reviewed statement facts inside an interchange caller's existing transaction. */
    public ImportedFacts importForInterchange(
            EntityManager em,
            Company company,
            List<BatchImport> batchValues,
            List<StatementLineImport> lineValues,
            List<IssueImport> issueValues,
            Map<String, CompanyBankAccount> bankAccounts,
            Map<String, Txn> transactions)
    {
        Objects.requireNonNull(em, "em");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(batchValues, "batchValues");
        Objects.requireNonNull(lineValues, "lineValues");
        Objects.requireNonNull(issueValues, "issueValues");
        Objects.requireNonNull(bankAccounts, "bankAccounts");
        Objects.requireNonNull(transactions, "transactions");
        if (!em.getTransaction().isActive())
        {
            throw new IllegalStateException("Bank-fact import requires an active caller-owned transaction");
        }

        Map<String, BankImportBatch> batches = new LinkedHashMap<>();
        for (BatchImport value : batchValues)
        {
            CompanyBankAccount bankAccount = optional(bankAccounts, value.bankAccountId(), "configured bank account");
            requireCompany(company, bankAccount == null ? null : bankAccount.getCompany(), "Configured bank account");
            BankImportBatch batch = new BankImportBatch();
            batch.setCompany(company);
            batch.setBankAccount(bankAccount);
            batch.setSourceName(value.sourceName());
            batch.setSourcePath(null);
            batch.setSourceHash(value.sourceHash());
            batch.setSourceFormat(value.sourceFormat());
            batch.setSourceVariant(value.sourceVariant());
            batch.setSourceVersion(value.sourceVersion());
            batch.setSourceEncoding(value.sourceEncoding());
            batch.setSourceInstitutionId(value.sourceInstitutionId());
            batch.setSourceBankId(value.sourceBankId());
            batch.setSourceAccountId(value.sourceAccountId());
            batch.setSourceAccountType(value.sourceAccountType());
            batch.setCurrency(value.currency());
            batch.setStatementStartDate(value.statementStartDate());
            batch.setStatementEndDate(value.statementEndDate());
            batch.setLedgerBalance(value.ledgerBalance());
            batch.setAvailableBalance(value.availableBalance());
            batch.setAccountMatchStatus(value.accountMatchStatus());
            batch.setAccountIdentityConfirmed(value.accountIdentityConfirmed());
            batch.setStatus(value.status());
            batch.setCompletedAt(value.completedAt());
            batch.setTotalLineCount(value.totalLineCount());
            batch.setAcceptedLineCount(value.acceptedLineCount());
            batch.setRejectedLineCount(value.rejectedLineCount());
            batch.setIssueCount(value.issueCount());
            batch.setNotes(value.notes());
            batch.initializeImportMetadata(value.portableId(), value.importedAt());
            em.persist(batch);
            batches.put(value.externalId(), batch);
        }
        em.flush();

        Map<String, BankStatementLine> lines = new LinkedHashMap<>();
        for (StatementLineImport value : lineValues)
        {
            BankImportBatch batch = required(batches, value.importBatchId(), "bank import batch");
            CompanyBankAccount bankAccount = optional(bankAccounts, value.bankAccountId(), "configured bank account");
            if (bankAccount != null)
            {
                requireCompany(company, bankAccount.getCompany(), "Configured bank account");
            }
            if (batch.getBankAccount() != null && bankAccount != null
                    && !batch.getBankAccount().getId().equals(bankAccount.getId()))
            {
                throw new IllegalArgumentException("Statement-line bank account differs from its import batch");
            }
            Txn accepted = optional(transactions, value.acceptedTransactionId(), "accepted transaction");
            Txn matched = optional(transactions, value.matchedTransactionId(), "matched transaction");
            requireCompany(company, accepted == null ? null : accepted.getCompany(), "Accepted transaction");
            requireCompany(company, matched == null ? null : matched.getCompany(), "Matched transaction");
            BankStatementLine line = new BankStatementLine();
            line.setBatch(batch);
            line.setCompany(company);
            line.setBankAccount(bankAccount);
            line.setSourceRowNumber(value.sourceRowNumber());
            line.setSourceTransactionId(value.sourceTransactionId());
            line.setDeterministicFingerprint(value.deterministicFingerprint());
            line.setStatementAccountIdentifier(value.statementAccountIdentifier());
            line.setTransactionDate(value.transactionDate());
            line.setPostedDate(value.postedDate());
            line.setAmount(value.amount());
            line.setTransactionType(value.transactionType());
            line.setName(value.name());
            line.setMemo(value.memo());
            line.setCheckNumber(value.checkNumber());
            line.setReference(value.reference());
            line.setCurrency(value.currency());
            line.setCorrectionAction(value.correctionAction());
            line.setCorrectedSourceTransactionId(value.correctedSourceTransactionId());
            line.setStatus(value.status());
            line.setDispositionNote(value.dispositionNote());
            line.setAcceptedTransaction(accepted);
            line.setMatchedTransaction(matched);
            line.initializeImportMetadata(value.portableId());
            em.persist(line);
            lines.put(value.externalId(), line);
        }
        em.flush();

        Map<String, ImportIssue> issues = new LinkedHashMap<>();
        for (IssueImport value : issueValues)
        {
            BankImportBatch batch = required(batches, value.importBatchId(), "bank import batch");
            BankStatementLine line = optional(lines, value.statementLineId(), "bank statement line");
            if (line != null && !line.getBatch().getId().equals(batch.getId()))
            {
                throw new IllegalArgumentException("Import issue statement line belongs to another batch");
            }
            ImportIssue issue = new ImportIssue();
            issue.setBatch(batch);
            issue.setStatementLine(line);
            issue.setSourceRowNumber(value.sourceRowNumber());
            issue.setSeverity(value.severity());
            issue.setCode(value.code());
            issue.setMessage(value.message());
            issue.initializeImportMetadata(value.portableId(), value.createdAt());
            em.persist(issue);
            issues.put(value.externalId(), issue);
        }
        return new ImportedFacts(batches, lines, issues);
    }

    private static void requireCompany(Company expected, Company actual, String label)
    {
        if (actual != null && !expected.getId().equals(actual.getId()))
        {
            throw new IllegalArgumentException(label + " belongs to another company");
        }
    }

    private static <T> T required(Map<String, T> values, String identity, String label)
    {
        T value = values.get(identity);
        if (value == null)
        {
            throw new IllegalArgumentException("Unresolved " + label + ": " + identity);
        }
        return value;
    }

    private static <T> T optional(Map<String, T> values, String identity, String label)
    {
        return identity == null ? null : required(values, identity, label);
    }

    public record BatchImport(
            String externalId,
            UUID portableId,
            String bankAccountId,
            String sourceName,
            String sourceHash,
            BankImportBatch.SourceFormat sourceFormat,
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
            BankImportBatch.Status status,
            Instant importedAt,
            Instant completedAt,
            int totalLineCount,
            int acceptedLineCount,
            int rejectedLineCount,
            int issueCount,
            String notes)
    {
    }

    public record StatementLineImport(
            String externalId,
            UUID portableId,
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
            BankStatementLine.Status status,
            String dispositionNote,
            String acceptedTransactionId,
            String matchedTransactionId)
    {
    }

    public record IssueImport(
            String externalId,
            UUID portableId,
            String importBatchId,
            String statementLineId,
            Integer sourceRowNumber,
            ImportIssue.Severity severity,
            String code,
            String message,
            Instant createdAt)
    {
    }

    public record ImportedFacts(
            Map<String, BankImportBatch> batches,
            Map<String, BankStatementLine> lines,
            Map<String, ImportIssue> issues)
    {
        public ImportedFacts
        {
            batches = Map.copyOf(batches);
            lines = Map.copyOf(lines);
            issues = Map.copyOf(issues);
        }
    }

    private static BankImportNormalizationService.DuplicateContext duplicateContext(EntityManager em, Company company)
    {
        Set<String> sourceIds = new HashSet<>(em.createQuery("""
                        select l.sourceTransactionId
                        from BankStatementLine l
                        where l.company = :company
                          and l.sourceTransactionId is not null
                        """, String.class)
                .setParameter("company", company)
                .getResultList());
        Set<String> fingerprints = new HashSet<>(em.createQuery("""
                        select l.deterministicFingerprint
                        from BankStatementLine l
                        where l.company = :company
                        """, String.class)
                .setParameter("company", company)
                .getResultList());
        return new BankImportNormalizationService.DuplicateContext(sourceIds, fingerprints, java.util.List.of());
    }

    private static CompanyBankAccount bankAccountForCompany(EntityManager em, Long bankAccountId, Company company)
    {
        if (bankAccountId == null)
        {
            return null;
        }
        CompanyBankAccount account = em.find(CompanyBankAccount.class, bankAccountId);
        if (account == null || account.getCompany() == null || !account.getCompany().getId().equals(company.getId()))
        {
            throw new IllegalArgumentException("Configured bank account does not exist for company: " + bankAccountId + ".");
        }
        return account;
    }

    private static Company companyByCode(EntityManager em, String code)
    {
        if (isBlank(code))
        {
            throw new IllegalArgumentException("Company code is required.");
        }
        return em.createQuery("""
                        select c
                        from Company c
                        where c.code = :code
                        """, Company.class)
                .setParameter("code", code.trim())
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Company does not exist: " + code + "."));
    }

    private static ImportIssue.Severity mapSeverity(BankImportNormalizationService.ImportIssueSeverity severity)
    {
        if (severity == BankImportNormalizationService.ImportIssueSeverity.ERROR)
        {
            return ImportIssue.Severity.ERROR;
        }
        if (severity == BankImportNormalizationService.ImportIssueSeverity.WARNING)
        {
            return ImportIssue.Severity.WARNING;
        }
        return ImportIssue.Severity.INFO;
    }

    private static void rollback(jakarta.persistence.EntityTransaction tx)
    {
        if (tx.isActive())
        {
            tx.rollback();
        }
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value)
    {
        return isBlank(value) ? null : value.trim();
    }
}
