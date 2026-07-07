package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.ImportIssue;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.HashSet;
import java.util.Set;

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
