package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.LockModeType;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.ImportIssue;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Explicit, atomic acceptance of one durable reviewed statement row into the
 * canonical transaction ledger.
 *
 * <p>Import/review remains non-posting. This service is the only reviewed-row
 * acceptance boundary: preview freezes source identity, commit revalidates it,
 * canonical transaction entry runs inside the caller-owned transaction, and the
 * statement link/status/audit fact commit together.</p>
 */
public final class ReviewedStatementAcceptanceService
{
    private static final String PROBABLE_DUPLICATE = "PROBABLE_DUPLICATE";

    @FunctionalInterface
    interface CommitHook
    {
        void afterTransactionPersisted();
    }

    private final Jpa jpa;
    private final TransactionEntryService transactionEntry;
    private final Supplier<String> companyCodeSupplier;
    private final CommitHook commitHook;
    private final AuthorizationGuard authorizationGuard;

    public ReviewedStatementAcceptanceService(
            Jpa jpa,
            TransactionEntryService transactionEntry,
            Supplier<String> companyCodeSupplier)
    {
        this(jpa, transactionEntry, companyCodeSupplier, () -> { }, null);
    }

    public ReviewedStatementAcceptanceService(
            Jpa jpa,
            TransactionEntryService transactionEntry,
            Supplier<String> companyCodeSupplier,
            AuthorizationGuard authorizationGuard)
    {
        this(jpa, transactionEntry, companyCodeSupplier, () -> { }, authorizationGuard);
    }

    ReviewedStatementAcceptanceService(
            Jpa jpa,
            TransactionEntryService transactionEntry,
            Supplier<String> companyCodeSupplier,
            CommitHook commitHook)
    {
        this(jpa, transactionEntry, companyCodeSupplier, commitHook, null);
    }

    ReviewedStatementAcceptanceService(
            Jpa jpa,
            TransactionEntryService transactionEntry,
            Supplier<String> companyCodeSupplier,
            CommitHook commitHook,
            AuthorizationGuard authorizationGuard)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.transactionEntry = Objects.requireNonNull(transactionEntry, "transactionEntry");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
        this.commitHook = Objects.requireNonNull(commitHook, "commitHook");
        this.authorizationGuard = authorizationGuard;
    }

    /** Creates a non-mutating frozen preview for one currently reviewable row. */
    public AcceptancePreview preview(long statementLineId)
    {
        return preview(requiredCompanyCode(), statementLineId);
    }

    /** Creates a non-mutating frozen preview for one explicit company. */
    public AcceptancePreview preview(String companyCode, long statementLineId)
    {
        if (statementLineId <= 0)
        {
            throw new IllegalArgumentException("Select one durable reviewed statement row.");
        }
        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa).requireCompany(em, companyCode);
            BankStatementLine line = requireLine(em, company, statementLineId, LockModeType.NONE);
            return preview(em, company, line);
        }
    }

    /**
     * Commits one approved preview atomically. A successful retry returns the
     * already-linked canonical transaction instead of creating another one.
     */
    public AcceptanceResult accept(
            AcceptancePreview approvedPreview,
            TransactionCommand command,
            boolean probableDuplicateConfirmed,
            String actor)
    {
        requireBookkeepingWrite();
        Objects.requireNonNull(approvedPreview, "approvedPreview");
        Objects.requireNonNull(command, "command");
        String selectedCompanyCode = requiredCompanyCode();
        if (!selectedCompanyCode.equalsIgnoreCase(approvedPreview.companyCode()))
        {
            throw new IllegalStateException("Active company changed after preview; preview the row again.");
        }

        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                Company company = new CompanyOwnershipService(jpa).requireCompany(em, selectedCompanyCode);
                BankStatementLine line = requireLine(
                        em, company, approvedPreview.statementLineId(), LockModeType.PESSIMISTIC_WRITE);
                requireSameSource(approvedPreview, line);

                if (line.getAcceptedTransaction() != null)
                {
                    if (line.getStatus() != BankStatementLine.Status.ACCEPTED)
                    {
                        throw new IllegalStateException(
                                "Reviewed row has an accepted transaction link but is not in ACCEPTED state.");
                    }
                    Txn existing = line.getAcceptedTransaction();
                    new CompanyOwnershipService(jpa).requireOwnedBy(company, existing, "Accepted transaction");
                    em.flush();
                    tx.commit();
                    return new AcceptanceResult(
                            line.getId(), existing.getId(), existing.getPortableId(), true,
                            "Reviewed row was already accepted; reused the existing canonical transaction.");
                }

                validateEligibility(em, company, line, probableDuplicateConfirmed);
                validateCommandAgainstSource(em, company, line, command);

                UUID transactionPortableId = UUID.randomUUID();
                String reason = probableDuplicateConfirmed && hasProbableDuplicate(em, line)
                        ? "Explicit reviewed-row acceptance with probable-duplicate confirmation."
                        : "Explicit reviewed-row acceptance.";
                Txn txn = transactionEntry.enter(
                        em,
                        company,
                        command,
                        transactionPortableId,
                        normalizedActor(actor),
                        reason);
                em.flush();
                commitHook.afterTransactionPersisted();

                line.setAcceptedTransaction(txn);
                line.setStatus(BankStatementLine.Status.ACCEPTED);
                line.setDispositionNote("Accepted explicitly into canonical transaction " + txn.getPortableId() + ".");
                line.touchUpdatedAt();
                updateBatchDisposition(em, line.getBatch());
                em.persist(acceptanceAudit(company, line, txn, actor, probableDuplicateConfirmed));
                em.flush();
                tx.commit();
                return new AcceptanceResult(
                        line.getId(), txn.getId(), txn.getPortableId(), false,
                        "Created canonical transaction " + txn.getId() + " from reviewed statement row " + line.getId() + ".");
            }
            catch (RuntimeException ex)
            {
                rollback(tx);
                throw ex;
            }
        }
    }

    private void requireBookkeepingWrite()
    {
        ServiceAuthorization.require(
                authorizationGuard,
                ApplicationPermission.BOOKKEEPING_WRITE,
                companyCodeSupplier.get(),
                "accept reviewed bank statement row");
    }

    private AcceptancePreview preview(EntityManager em, Company company, BankStatementLine line)
    {
        CompanyBankAccount bankAccount = requireConfiguredAccount(company, line);
        Account ledgerAccount = requireLedgerAccount(company, bankAccount);
        boolean probableDuplicate = hasProbableDuplicate(em, line);
        List<String> issues = em.createQuery(
                        "select i.code, i.message from ImportIssue i where i.statementLine = :line order by i.id",
                        Object[].class)
                .setParameter("line", line)
                .getResultList().stream()
                .map(row -> Objects.toString(row[0], "") + ": " + Objects.toString(row[1], ""))
                .toList();
        boolean eligible = line.getStatus() == BankStatementLine.Status.IMPORTED
                && line.getMatchedTransaction() == null
                && line.getAcceptedTransaction() == null
                && company.isActive()
                && bankAccount.isActive()
                && currencyCompatible(company, line)
                && !hasBlockingIssue(em, line)
                && !inFinalizedReconciliation(em, line, bankAccount, effectiveSourceDate(line));
        String eligibility = eligible
                ? (probableDuplicate
                    ? "Eligible only with explicit probable-duplicate confirmation."
                    : "Eligible for explicit canonical transaction acceptance.")
                : eligibilityReason(em, line, bankAccount);

        return new AcceptancePreview(
                company.getCode(),
                line.getId(),
                line.getPortableId(),
                line.getBatch().getId(),
                bankAccount.getId(),
                bankAccount.getPortableId(),
                bankAccount.getName(),
                ledgerAccount.getId(),
                ledgerAccount.getCode(),
                ledgerAccount.getName(),
                ledgerAccount.getNormalBalance(),
                line.getSourceRowNumber(),
                line.getSourceTransactionId(),
                line.getSourceExternalId(),
                line.getSourcePayeeId(),
                line.getDeterministicFingerprint(),
                line.getStatementAccountIdentifier(),
                line.getTransactionDate(),
                line.getPostedDate(),
                line.getAmount(),
                line.getTransactionType(),
                line.getName(),
                line.getMemo(),
                line.getCheckNumber(),
                line.getReference(),
                line.getCurrency(),
                line.getStatus(),
                probableDuplicate,
                issues,
                eligible,
                eligibility);
    }

    private void validateEligibility(
            EntityManager em,
            Company company,
            BankStatementLine line,
            boolean probableDuplicateConfirmed)
    {
        CompanyBankAccount bankAccount = requireConfiguredAccount(company, line);
        requireLedgerAccount(company, bankAccount);
        if (!company.isActive())
        {
            throw new IllegalStateException("The active company is inactive.");
        }
        if (!bankAccount.isActive())
        {
            throw new IllegalStateException("The configured bank account is inactive.");
        }
        if (line.getStatus() == BankStatementLine.Status.DUPLICATE)
        {
            throw new IllegalStateException("Exact duplicate statement rows cannot be accepted into the ledger.");
        }
        if (line.getStatus() != BankStatementLine.Status.IMPORTED)
        {
            throw new IllegalStateException(
                    "Reviewed row is not eligible for acceptance in state " + line.getStatus() + ".");
        }
        if (line.getMatchedTransaction() != null || hasAnyReconciliationMatch(em, line))
        {
            throw new IllegalStateException("Reviewed row is already matched in reconciliation.");
        }
        if (hasBlockingIssue(em, line))
        {
            throw new IllegalStateException("Reviewed row has a blocking import issue and cannot be accepted.");
        }
        if (hasProbableDuplicate(em, line) && !probableDuplicateConfirmed)
        {
            throw new IllegalStateException(
                    "Reviewed row has a probable-duplicate warning; explicit duplicate confirmation is required.");
        }
        if (inFinalizedReconciliation(em, line, bankAccount, effectiveSourceDate(line)))
        {
            throw new IllegalStateException(
                    "Reviewed row falls within a finalized reconciliation and cannot create a new ledger transaction.");
        }
    }

    private void validateCommandAgainstSource(EntityManager em, Company company, BankStatementLine line, TransactionCommand command)
    {
        if (command.date() == null)
        {
            throw new IllegalArgumentException("Transaction date is required.");
        }
        CompanyBankAccount bankAccount = line.getBankAccount();
        Account ledgerAccount = bankAccount.getAccount();
        if (!Objects.equals(command.bankAccountId(), ledgerAccount.getId()))
        {
            throw new IllegalArgumentException(
                    "Acceptance transaction must identify the reviewed row's configured ledger bank account.");
        }
        String sourceCurrency = normalizedCurrency(line.getCurrency());
        if (!currencyCompatible(company, line))
        {
            throw new IllegalStateException(
                    "Reviewed row currency " + sourceCurrency + " does not match company currency "
                            + company.getDefaultCurrency() + "; currency conversion is not part of reviewed-row acceptance.");
        }
        if (inFinalizedReconciliation(em, line, bankAccount, command.date()))
        {
            throw new IllegalStateException(
                    "Acceptance transaction date falls within a finalized reconciliation for the configured bank account.");
        }

        BigDecimal sourceAmount = amount(line.getAmount());
        BigDecimal bankSigned = BigDecimal.ZERO;
        int bankLines = 0;
        for (TransactionLineCommand value : command.lines())
        {
            if (value != null && Objects.equals(value.accountId(), ledgerAccount.getId()))
            {
                bankLines++;
                BigDecimal debit = amount(value.debit());
                BigDecimal credit = amount(value.credit());
                bankSigned = bankSigned.add(ledgerAccount.getNormalBalance() == NormalBalance.DEBIT
                        ? debit.subtract(credit)
                        : credit.subtract(debit));
            }
        }
        if (bankLines != 1 || bankSigned.compareTo(sourceAmount) != 0)
        {
            throw new IllegalArgumentException(
                    "Acceptance transaction must contain exactly one bank split equal to the reviewed source amount "
                            + sourceAmount.toPlainString() + ".");
        }
    }

    private static BankStatementLine requireLine(
            EntityManager em,
            Company company,
            long statementLineId,
            LockModeType lock)
    {
        BankStatementLine line = lock == LockModeType.NONE
                ? em.find(BankStatementLine.class, statementLineId)
                : em.find(BankStatementLine.class, statementLineId, lock);
        if (line == null)
        {
            throw new IllegalArgumentException("Reviewed statement row does not exist: " + statementLineId + ".");
        }
        if (line.getCompany() == null || !Objects.equals(company.getId(), line.getCompany().getId()))
        {
            throw new IllegalArgumentException("Reviewed statement row does not belong to the active company.");
        }
        return line;
    }

    private static CompanyBankAccount requireConfiguredAccount(Company company, BankStatementLine line)
    {
        CompanyBankAccount bankAccount = line.getBankAccount();
        if (bankAccount == null || bankAccount.getId() == null)
        {
            throw new IllegalStateException("Reviewed statement row is not bound to a configured bank account.");
        }
        if (bankAccount.getCompany() == null || !Objects.equals(company.getId(), bankAccount.getCompany().getId()))
        {
            throw new IllegalStateException("Reviewed row's configured bank account belongs to another company.");
        }
        return bankAccount;
    }

    private static Account requireLedgerAccount(Company company, CompanyBankAccount bankAccount)
    {
        Account account = bankAccount.getAccount();
        if (account == null || account.getId() == null)
        {
            throw new IllegalStateException("Configured bank account has no canonical ledger account.");
        }
        if (account.getChart() == null || account.getChart().getCompany() == null
                || !Objects.equals(company.getId(), account.getChart().getCompany().getId()))
        {
            throw new IllegalStateException("Configured ledger bank account belongs to another company.");
        }
        if (!account.isActive() || !account.isPosting())
        {
            throw new IllegalStateException("Configured ledger bank account must be an active posting account.");
        }
        return account;
    }

    private static boolean hasProbableDuplicate(EntityManager em, BankStatementLine line)
    {
        return count(em, """
                select count(i) from ImportIssue i
                where i.statementLine = :line and upper(i.code) = :code
                """, line, PROBABLE_DUPLICATE) > 0;
    }

    private static boolean hasBlockingIssue(EntityManager em, BankStatementLine line)
    {
        return em.createQuery("""
                        select count(i) from ImportIssue i
                        where i.statementLine = :line
                          and (i.severity = :error or upper(i.code) = 'EXACT_DUPLICATE')
                        """, Long.class)
                .setParameter("line", line)
                .setParameter("error", ImportIssue.Severity.ERROR)
                .getSingleResult() > 0;
    }

    private static long count(EntityManager em, String jpql, BankStatementLine line, String code)
    {
        return em.createQuery(jpql, Long.class)
                .setParameter("line", line)
                .setParameter("code", code)
                .getSingleResult();
    }

    private static boolean hasAnyReconciliationMatch(EntityManager em, BankStatementLine line)
    {
        Number value = (Number) em.createNativeQuery("""
                select count(*) from bank_reconciliation_match where statement_line_id = ?
                """)
                .setParameter(1, line.getId())
                .getSingleResult();
        return value.longValue() > 0;
    }

    private static boolean inFinalizedReconciliation(
            EntityManager em,
            BankStatementLine line,
            CompanyBankAccount bankAccount,
            LocalDate effectiveDate)
    {
        if (effectiveDate == null)
        {
            return false;
        }
        Number value = (Number) em.createNativeQuery("""
                select count(*)
                  from bank_reconciliation_session s
                 where s.company_id = ?
                   and s.bank_account_id = ?
                   and s.status = 'FINALIZED'
                   and ? between s.statement_start_date and s.statement_end_date
                """)
                .setParameter(1, line.getCompany().getId())
                .setParameter(2, bankAccount.getId())
                .setParameter(3, effectiveDate)
                .getSingleResult();
        return value.longValue() > 0;
    }


    private static LocalDate effectiveSourceDate(BankStatementLine line)
    {
        return line.getPostedDate() == null ? line.getTransactionDate() : line.getPostedDate();
    }

    private static boolean currencyCompatible(Company company, BankStatementLine line)
    {
        String sourceCurrency = normalizedCurrency(line.getCurrency());
        return sourceCurrency.isBlank()
                || sourceCurrency.equals(normalizedCurrency(company.getDefaultCurrency()));
    }

    private static String eligibilityReason(
            EntityManager em,
            BankStatementLine line,
            CompanyBankAccount bankAccount)
    {
        if (!line.getCompany().isActive()) return "The active company is inactive.";
        if (!bankAccount.isActive()) return "The configured bank account is inactive.";
        if (!currencyCompatible(line.getCompany(), line)) return "The reviewed row currency does not match the company currency.";
        if (line.getAcceptedTransaction() != null) return "Already accepted into a canonical transaction.";
        if (line.getMatchedTransaction() != null || hasAnyReconciliationMatch(em, line)) return "Already matched in reconciliation.";
        if (line.getStatus() == BankStatementLine.Status.DUPLICATE) return "Exact duplicate rows are blocked.";
        if (line.getStatus() != BankStatementLine.Status.IMPORTED) return "Review state " + line.getStatus() + " is not acceptance-eligible.";
        if (hasBlockingIssue(em, line)) return "A blocking import issue is unresolved.";
        if (inFinalizedReconciliation(em, line, bankAccount, effectiveSourceDate(line))) return "The statement date is protected by a finalized reconciliation.";
        return "Reviewed row is not currently eligible.";
    }

    private static void requireSameSource(AcceptancePreview approved, BankStatementLine current)
    {
        CompanyBankAccount bankAccount = current.getBankAccount();
        if (!Objects.equals(approved.statementPortableId(), current.getPortableId())
                || !Objects.equals(approved.batchId(), current.getBatch().getId())
                || bankAccount == null
                || !Objects.equals(approved.bankAccountId(), bankAccount.getId())
                || !Objects.equals(approved.bankAccountPortableId(), bankAccount.getPortableId())
                || approved.sourceRowNumber() != current.getSourceRowNumber()
                || !Objects.equals(approved.sourceTransactionId(), current.getSourceTransactionId())
                || !Objects.equals(approved.sourceExternalId(), current.getSourceExternalId())
                || !Objects.equals(approved.sourcePayeeId(), current.getSourcePayeeId())
                || !Objects.equals(approved.deterministicFingerprint(), current.getDeterministicFingerprint())
                || !Objects.equals(approved.statementAccountIdentifier(), current.getStatementAccountIdentifier())
                || !Objects.equals(approved.transactionDate(), current.getTransactionDate())
                || !Objects.equals(approved.postedDate(), current.getPostedDate())
                || amount(approved.amount()).compareTo(amount(current.getAmount())) != 0
                || !Objects.equals(approved.transactionType(), current.getTransactionType())
                || !Objects.equals(approved.payeeName(), current.getName())
                || !Objects.equals(approved.memo(), current.getMemo())
                || !Objects.equals(approved.checkNumber(), current.getCheckNumber())
                || !Objects.equals(approved.reference(), current.getReference())
                || !Objects.equals(normalizedCurrency(approved.currency()), normalizedCurrency(current.getCurrency())))
        {
            throw new IllegalStateException("Reviewed statement source changed after preview; preview the row again.");
        }
    }

    private static void updateBatchDisposition(EntityManager em, BankImportBatch batch)
    {
        List<BankStatementLine.Status> states = em.createQuery(
                        "select l.status from BankStatementLine l where l.batch = :batch",
                        BankStatementLine.Status.class)
                .setParameter("batch", batch)
                .getResultList();
        int accepted = (int) states.stream()
                .filter(value -> value == BankStatementLine.Status.ACCEPTED || value == BankStatementLine.Status.MATCHED)
                .count();
        int rejected = (int) states.stream()
                .filter(value -> value == BankStatementLine.Status.REJECTED
                        || value == BankStatementLine.Status.DUPLICATE
                        || value == BankStatementLine.Status.ERROR)
                .count();
        batch.setAcceptedLineCount(accepted);
        batch.setRejectedLineCount(rejected);
        if (!states.isEmpty() && accepted == states.size())
        {
            batch.setStatus(BankImportBatch.Status.ACCEPTED);
        }
        else if (accepted > 0)
        {
            batch.setStatus(BankImportBatch.Status.PARTIALLY_ACCEPTED);
        }
        else
        {
            batch.setStatus(BankImportBatch.Status.IMPORTED);
        }
        batch.touchUpdatedAt();
    }

    private static AuditEvent acceptanceAudit(
            Company company,
            BankStatementLine line,
            Txn txn,
            String actor,
            boolean probableDuplicateConfirmed)
    {
        AuditEvent event = new AuditEvent();
        event.setCompany(company);
        event.setActor(normalizedActor(actor));
        event.setActionType("BANK_STATEMENT_ROW_ACCEPTED");
        event.setEntityType("BANK_STATEMENT_LINE");
        event.setEntityId(line.getPortableId().toString());
        event.setSummary("Accepted reviewed bank statement row " + line.getId()
                + " into canonical transaction " + txn.getId() + ".");
        event.setBeforeValue("status=IMPORTED;acceptedTxnId=null;fingerprint=" + line.getDeterministicFingerprint());
        event.setAfterValue("status=ACCEPTED;acceptedTxnId=" + txn.getId()
                + ";acceptedTxnPortableId=" + txn.getPortableId());
        event.setReason(probableDuplicateConfirmed
                ? "User explicitly confirmed the probable-duplicate warning before acceptance."
                : "User explicitly accepted the reviewed statement row into the ledger.");
        return event;
    }

    private String requiredCompanyCode()
    {
        String value = companyCodeSupplier.get();
        if (value == null || value.isBlank())
        {
            throw new IllegalStateException("Active company is required for reviewed-row acceptance.");
        }
        return value.trim();
    }

    private static String normalizedActor(String actor)
    {
        return actor == null || actor.isBlank() ? "ui" : actor.trim();
    }

    private static String normalizedCurrency(String value)
    {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal amount(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static void rollback(EntityTransaction tx)
    {
        if (tx != null && tx.isActive())
        {
            tx.rollback();
        }
    }

    public record AcceptancePreview(
            String companyCode,
            long statementLineId,
            UUID statementPortableId,
            Long batchId,
            Long bankAccountId,
            UUID bankAccountPortableId,
            String bankAccountName,
            Long ledgerAccountId,
            String ledgerAccountCode,
            String ledgerAccountName,
            NormalBalance ledgerAccountNormalBalance,
            int sourceRowNumber,
            String sourceTransactionId,
            String sourceExternalId,
            String sourcePayeeId,
            String deterministicFingerprint,
            String statementAccountIdentifier,
            LocalDate transactionDate,
            LocalDate postedDate,
            BigDecimal amount,
            String transactionType,
            String payeeName,
            String memo,
            String checkNumber,
            String reference,
            String currency,
            BankStatementLine.Status status,
            boolean probableDuplicate,
            List<String> issues,
            boolean eligible,
            String eligibilityMessage)
    {
        public AcceptancePreview
        {
            companyCode = Objects.requireNonNull(companyCode, "companyCode");
            statementPortableId = Objects.requireNonNull(statementPortableId, "statementPortableId");
            bankAccountPortableId = Objects.requireNonNull(bankAccountPortableId, "bankAccountPortableId");
            ledgerAccountId = Objects.requireNonNull(ledgerAccountId, "ledgerAccountId");
            ledgerAccountNormalBalance = Objects.requireNonNull(ledgerAccountNormalBalance, "ledgerAccountNormalBalance");
            deterministicFingerprint = Objects.requireNonNull(deterministicFingerprint, "deterministicFingerprint");
            amount = Objects.requireNonNull(amount, "amount");
            status = Objects.requireNonNull(status, "status");
            issues = List.copyOf(issues == null ? List.of() : issues);
            eligibilityMessage = eligibilityMessage == null ? "" : eligibilityMessage;
        }

        public LocalDate effectiveSourceDate()
        {
            return postedDate == null ? transactionDate : postedDate;
        }
    }

    public record AcceptanceResult(
            long statementLineId,
            long transactionId,
            UUID transactionPortableId,
            boolean reusedExisting,
            String message)
    {
        public AcceptanceResult
        {
            transactionPortableId = Objects.requireNonNull(transactionPortableId, "transactionPortableId");
            message = Objects.requireNonNull(message, "message");
        }
    }
}
