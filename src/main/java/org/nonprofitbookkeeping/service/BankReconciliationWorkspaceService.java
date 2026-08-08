package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Service boundary for the full Bank Reconciliation workspace. */
public class BankReconciliationWorkspaceService
{
    public enum ClearedStatePolicy
    {
        WARN_ONLY,
        OVERWRITE_LEDGER_CLEARED_STATE,
        NEVER_OVERWRITE_REQUIRE_MANUAL,
        DECIDE_PER_IMPORTED_LINE
    }

    public enum SessionStatus { IN_PROGRESS, UNRESOLVED, BALANCED, FINALIZED }
    public enum DifferenceCategory
    {
        MATCHED,
        UNMATCHED_LEDGER,
        UNMATCHED_STATEMENT,
        AMOUNT_MISMATCH,
        DATE_MISMATCH,
        DUPLICATE_POSSIBLE,
        CLEARED_STATE_MISMATCH,
        BEGINNING_BALANCE_DIFFERENCE,
        ENDING_BALANCE_DIFFERENCE,
        RESOLVED
    }

    public record BankAccountOption(Long id, String label) { }
    public record StartCommand(String companyCode,
                               Long bankAccountId,
                               LocalDate statementEndDate,
                               BigDecimal statementEndingBalance,
                               ClearedStatePolicy policy,
                               String notes) { }
    public record SuccessorCommand(long finalizedSessionId,
                                   LocalDate statementEndDate,
                                   BigDecimal statementEndingBalance,
                                   ClearedStatePolicy policy,
                                   String notes,
                                   String actor,
                                   String reason) { }
    public record ManualStatementLineCommand(long sessionId,
                                             LocalDate date,
                                             BigDecimal amount,
                                             String description,
                                             String reference) { }
    public record BalanceCards(BigDecimal beginningBalance,
                               BigDecimal bookBalanceAllTransactions,
                               BigDecimal bookBalanceClearedOnly,
                               BigDecimal statementEndingBalance,
                               BigDecimal difference) { }
    public record SessionSummary(long id,
                                 String bankAccountLabel,
                                 LocalDate statementStartDate,
                                 LocalDate statementEndDate,
                                 SessionStatus status,
                                 BigDecimal difference) { }
    public record StatementEntryView(Long statementLineId,
                                     LocalDate date,
                                     String description,
                                     String reference,
                                     BigDecimal amount,
                                     String clearedState,
                                     DifferenceCategory matchStatus,
                                     Long matchedLedgerSplitId,
                                     String resolution) { }
    public record LedgerLineView(Long splitId,
                                 Long txnId,
                                 LocalDate date,
                                 String memo,
                                 String transactionNumber,
                                 BigDecimal amount,
                                 boolean cleared,
                                 Long matchedStatementLineId,
                                 DifferenceCategory matchStatus) { }
    public record DifferenceView(DifferenceCategory category,
                                 LocalDate ledgerDate,
                                 LocalDate statementDate,
                                 BigDecimal ledgerAmount,
                                 BigDecimal statementAmount,
                                 String description) { }
    public record Snapshot(long sessionId,
                           String companyCode,
                           Long bankAccountId,
                           String bankAccountLabel,
                           LocalDate statementStartDate,
                           LocalDate statementEndDate,
                           ClearedStatePolicy policy,
                           SessionStatus status,
                           BalanceCards balances,
                           List<StatementEntryView> statementEntries,
                           List<LedgerLineView> ledgerLines,
                           List<DifferenceView> differences,
                           List<SessionSummary> savedSessions) { }

    private final Jpa jpa;
    private final BankStatementManualEntryService manualStatementService;

    public BankReconciliationWorkspaceService(Jpa jpa)
    {
        this(jpa, new BankStatementManualEntryService());
    }

    BankReconciliationWorkspaceService(Jpa jpa, BankStatementManualEntryService manualStatementService)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.manualStatementService = Objects.requireNonNull(manualStatementService, "manualStatementService");
    }

    public List<BankAccountOption> listConfiguredBankAccounts(String companyCode)
    {
        try (EntityManager em = jpa.em())
        {
            Company company = companyByCode(em, companyCode);
            return em.createQuery("""
                    select cba from CompanyBankAccount cba
                    left join fetch cba.bank
                    left join fetch cba.account
                    where cba.company = :company
                      and cba.active = true
                      and cba.bank is not null
                      and cba.account is not null
                    order by cba.name, cba.id
                    """, CompanyBankAccount.class)
                    .setParameter("company", company)
                    .getResultList()
                    .stream()
                    .peek(bankAccount -> BankConfigurationService.validateBankLedgerAccount(bankAccount.getAccount()))
                    .map(bankAccount -> new BankAccountOption(bankAccount.getId(), bankAccountLabel(bankAccount)))
                    .toList();
        }
    }

    public List<SessionSummary> listSessions(String companyCode)
    {
        try (EntityManager em = jpa.em())
        {
            Company company = companyByCode(em, companyCode);
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery("""
                    select s.id, cba.name, a.code, s.statement_start_date, s.statement_end_date, s.status, s.difference_amount
                      from bank_reconciliation_session s
                      join company_bank_account cba on cba.id = s.bank_account_id
                      left join account a on a.id = cba.account_id
                     where s.company_id = ?
                     order by s.statement_end_date desc, s.id desc
                    """)
                    .setParameter(1, company.getId())
                    .getResultList();
            return rows.stream().map(BankReconciliationWorkspaceService::sessionSummary).toList();
        }
    }

    public Snapshot start(StartCommand command)
    {
        validateStart(command);
        long sessionId;
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                Company company = companyByCode(em, command.companyCode());
                CompanyBankAccount bankAccount = configuredBankAccount(em, command.bankAccountId(), company);
                LocalDate startDate = reconciliationStartDate(em, company.getCode(), bankAccount, command.statementEndDate());
                sessionId = positiveId();
                em.createNativeQuery("""
                        insert into bank_reconciliation_session
                            (id, company_id, bank_account_id, statement_start_date, statement_end_date,
                             statement_ending_balance, mismatch_policy, status, notes)
                        values (?, ?, ?, ?, ?, ?, ?, 'IN_PROGRESS', ?)
                        """)
                        .setParameter(1, sessionId)
                        .setParameter(2, company.getId())
                        .setParameter(3, bankAccount.getId())
                        .setParameter(4, startDate)
                        .setParameter(5, command.statementEndDate())
                        .setParameter(6, amount(command.statementEndingBalance()))
                        .setParameter(7, policy(command.policy()).name())
                        .setParameter(8, blankToNull(command.notes()))
                        .executeUpdate();
                recalculateBalances(em, sessionId, bankAccount, startDate, command.statementEndDate(), command.statementEndingBalance());
                tx.commit();
            }
            catch (RuntimeException ex)
            {
                rollback(tx);
                throw ex;
            }
        }
        return load(sessionId);
    }

    /**
     * Starts a new mutable reconciliation after an immutable finalized session.
     * The finalized predecessor is never updated; the durable audit event records
     * the predecessor/successor relationship and the operator's reason.
     */
    public Snapshot startSuccessor(SuccessorCommand command)
    {
        if (command == null || command.finalizedSessionId() <= 0 || command.statementEndDate() == null)
        {
            throw new IllegalArgumentException("Finalized session and successor statement ending date are required.");
        }
        String actor = requireText(command.actor(), "Successor actor");
        String reason = requireText(command.reason(), "Successor reason");
        long successorId;
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                SessionRow predecessor = session(em, command.finalizedSessionId());
                if (predecessor.status() != SessionStatus.FINALIZED)
                {
                    throw new IllegalStateException("A successor can be started only from a finalized reconciliation session.");
                }
                CompanyBankAccount bankAccount = configuredBankAccount(
                        em, predecessor.bankAccountId(), predecessor.company());
                LocalDate startDate = predecessor.endDate().plusDays(1);
                if (command.statementEndDate().isBefore(startDate))
                {
                    throw new IllegalArgumentException(
                            "Successor statement ending date must be on or after " + startDate + ".");
                }
                successorId = positiveId();
                BigDecimal endingBalance = command.statementEndingBalance() == null
                        ? null
                        : amount(command.statementEndingBalance());
                ClearedStatePolicy successorPolicy = command.policy() == null
                        ? predecessor.policy()
                        : command.policy();
                em.createNativeQuery("""
                        insert into bank_reconciliation_session
                            (id, company_id, bank_account_id, statement_start_date, statement_end_date,
                             statement_ending_balance, mismatch_policy, status, notes)
                        values (?, ?, ?, ?, ?, ?, ?, 'IN_PROGRESS', ?)
                        """)
                        .setParameter(1, successorId)
                        .setParameter(2, predecessor.company().getId())
                        .setParameter(3, bankAccount.getId())
                        .setParameter(4, startDate)
                        .setParameter(5, command.statementEndDate())
                        .setParameter(6, endingBalance)
                        .setParameter(7, successorPolicy.name())
                        .setParameter(8, blankToNull(command.notes()))
                        .executeUpdate();
                recalculateBalances(em, successorId, bankAccount, startDate,
                        command.statementEndDate(), endingBalance);
                recordAudit(
                        em, predecessor.company(), actor,
                        "RECONCILIATION_SUCCESSOR_STARTED",
                        "BANK_RECONCILIATION_SESSION",
                        String.valueOf(successorId),
                        "Started successor reconciliation " + successorId
                                + " from finalized session " + predecessor.id() + ".",
                        "finalizedSession=" + predecessor.id() + "; status=FINALIZED",
                        "successorSession=" + successorId + "; status=IN_PROGRESS",
                        reason);
                tx.commit();
            }
            catch (RuntimeException ex)
            {
                rollback(tx);
                throw ex;
            }
        }
        return load(successorId);
    }

    public Snapshot load(long sessionId)
    {
        boolean finalized;
        try (EntityManager em = jpa.em())
        {
            finalized = session(em, sessionId).status() == SessionStatus.FINALIZED;
        }
        if (!finalized)
        {
            recalculateBalances(sessionId);
        }
        try (EntityManager em = jpa.em())
        {
            return snapshot(em, sessionId);
     }
    }

    public Snapshot addManualLine(ManualStatementLineCommand command)
    {
        if (command == null || command.date() == null || command.amount() == null || compare(command.amount(), BigDecimal.ZERO) == 0)
        {
            throw new IllegalArgumentException("Manual statement date and nonzero amount are required.");
        }
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                SessionRow session = requireMutableSession(em, command.sessionId());
                CompanyBankAccount bankAccount = configuredBankAccount(em, session.bankAccountId(), session.company());
                requireStatementDateInRange(session, command.date());
                manualStatementService.addLine(
                        em,
                        session.company(),
                        bankAccount,
                        command.date(),
                        command.amount(),
                        command.description(),
                        command.reference());
                tx.commit();
            }
            catch (RuntimeException ex)
            {
                rollback(tx);
                throw ex;
            }
        }
        return load(command.sessionId());
    }

    public Snapshot autoMatch(long sessionId)
    {
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                requireMutableSession(em, sessionId);
                Snapshot current = snapshot(em, sessionId);
                Set<Long> usedStatements = matchedStatementIds(em, sessionId);
                Set<Long> usedSplits = matchedSplitIds(em, sessionId);
                for (LedgerLineView ledger : current.ledgerLines())
                {
                    if (usedSplits.contains(ledger.splitId()))
                    {
                        continue;
                    }
                    List<StatementEntryView> candidates = current.statementEntries().stream()
                            .filter(statement -> !usedStatements.contains(statement.statementLineId()))
                            .filter(statement -> Objects.equals(statement.date(), ledger.date()))
                            .filter(statement -> compare(statement.amount(), ledger.amount()) == 0)
                            .toList();
                    if (candidates.size() == 1)
                    {
                        match(em, sessionId, candidates.get(0).statementLineId(), ledger.splitId(), "Auto matched by exact date and amount.", false);
                        usedStatements.add(candidates.get(0).statementLineId());
                        usedSplits.add(ledger.splitId());
                    }
                }
                tx.commit();
            }
            catch (RuntimeException ex)
            {
                rollback(tx);
                throw ex;
            }
        }
        return load(sessionId);
    }

    public Snapshot matchSelected(long sessionId, Long statementLineId, Long splitId, boolean overwriteCleared)
    {
        if (statementLineId == null || splitId == null)
        {
            throw new IllegalArgumentException("Select one statement entry and one ledger line to match.");
        }
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                match(em, sessionId, statementLineId, splitId, "Matched by user.", overwriteCleared);
                tx.commit();
            }
            catch (RuntimeException ex)
            {
                rollback(tx);
                throw ex;
            }
        }
        return load(sessionId);
    }

    public Snapshot unmatchSelected(long sessionId, Long statementLineId, Long splitId)
    {
        if (statementLineId == null || splitId == null)
        {
            throw new IllegalArgumentException("Select the exact matched statement entry and ledger line to unmatch.");
        }
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                SessionRow session = requireMutableSession(em, sessionId);
                CompanyBankAccount bankAccount = configuredBankAccount(
                        em, session.bankAccountId(), session.company());
                BankStatementLine statement = requireStatementLine(
                        em, session, bankAccount, statementLineId);
                TxnSplit split = requireLedgerSplit(em, session, bankAccount, splitId);
                if (!hasRelationshipMatch(em, sessionId, statementLineId, splitId)
                        || statement.getMatchedTransaction() == null
                        || !Objects.equals(statement.getMatchedTransaction().getId(), split.getTxn().getId())
                        || split.getMatchedBankStatementLine() == null
                        || !Objects.equals(split.getMatchedBankStatementLine().getId(), statementLineId))
                {
                    throw new IllegalStateException(
                            "The selected reconciliation pair is not a complete symmetric match; no changes were made.");
                }

                em.createNativeQuery("""
                        delete from bank_reconciliation_match
                         where session_id = ?
                           and statement_line_id = ?
                           and txn_split_id = ?
                           and match_status in ('MATCHED', 'AMOUNT_MISMATCH', 'DATE_MISMATCH', 'CLEARED_STATE_MISMATCH')
                        """)
                        .setParameter(1, sessionId)
                        .setParameter(2, statementLineId)
                        .setParameter(3, splitId)
                        .executeUpdate();
                statement.setMatchedTransaction(null);
                statement.setStatus(statement.getAcceptedTransaction() == null
                        ? BankStatementLine.Status.IMPORTED
                        : BankStatementLine.Status.ACCEPTED);
                statement.touchUpdatedAt();
                split.setMatchedBankStatementLine(null);
                tx.commit();
            }
            catch (RuntimeException ex)
            {
                rollback(tx);
                throw ex;
            }
        }
        return load(sessionId);
    }

    public Snapshot markCleared(long sessionId, Long splitId)
    {
        if (splitId == null)
        {
            throw new IllegalArgumentException("Select a ledger line to mark cleared.");
        }
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                SessionRow session = requireMutableSession(em, sessionId);
                CompanyBankAccount bankAccount = configuredBankAccount(
                        em, session.bankAccountId(), session.company());
                TxnSplit split = requireLedgerSplit(em, session, bankAccount, splitId);
                split.setBankCleared(true);
                split.setBankClearedOn(session.endDate());
                tx.commit();
            }
            catch (RuntimeException ex)
            {
                rollback(tx);
                throw ex;
            }
        }
        return load(sessionId);
    }

    /** Records factual reconciliation context only; no canonical accounting write occurs. */
    public Snapshot recordDifferenceExplanation(long sessionId, Long statementLineId, Long splitId, String note)
    {
        if (statementLineId == null && splitId == null)
        {
            throw new IllegalArgumentException("Select a statement entry or ledger line to explain.");
        }
        String explanation = requireText(note, "Difference explanation");
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                SessionRow session = requireMutableSession(em, sessionId);
                CompanyBankAccount bankAccount = configuredBankAccount(
                        em, session.bankAccountId(), session.company());
                if (statementLineId != null)
                {
                    requireStatementLine(em, session, bankAccount, statementLineId);
                }
                if (splitId != null)
                {
                    requireLedgerSplit(em, session, bankAccount, splitId);
                }
                insertMatch(em, sessionId, statementLineId, splitId,
                        DifferenceCategory.RESOLVED, explanation);
                tx.commit();
            }
            catch (RuntimeException ex)
            {
                rollback(tx);
                throw ex;
            }
        }
        return load(sessionId);
    }

    /** Compatibility alias; production UI uses the factual explanation name. */
    @Deprecated
    public Snapshot resolveDifference(long sessionId, Long statementLineId, Long splitId, String note)
    {
        return recordDifferenceExplanation(sessionId, statementLineId, splitId, note);
    }

    public Snapshot save(long sessionId, boolean finalize)
    {
        Snapshot current = load(sessionId);
        if (current.status() == SessionStatus.FINALIZED)
        {
            if (finalize)
            {
                return current;
            }
            throw finalizedMutation(sessionId);
        }
        if (finalize && !isBalanced(current))
        {
            throw new IllegalStateException(
                    "Reconciliation cannot be finalized until balances and differences are resolved.");
        }
        SessionStatus status = finalize
                ? SessionStatus.FINALIZED
                : (isBalanced(current) ? SessionStatus.BALANCED : SessionStatus.UNRESOLVED);
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                requireMutableSession(em, sessionId);
                int updated = em.createNativeQuery("""
                        update bank_reconciliation_session
                           set status = ?, updated_at = CURRENT_TIMESTAMP
                         where id = ? and status <> 'FINALIZED'
                        """)
                        .setParameter(1, status.name())
                        .setParameter(2, sessionId)
                        .executeUpdate();
                if (updated != 1)
                {
                    throw finalizedMutation(sessionId);
                }
                tx.commit();
            }
            catch (RuntimeException ex)
            {
                rollback(tx);
                throw ex;
            }
        }
        return load(sessionId);
    }

    private Snapshot snapshot(EntityManager em, long sessionId)
    {
        SessionRow session = session(em, sessionId);
        CompanyBankAccount bankAccount = configuredBankAccount(em, session.bankAccountId(), session.company());
        List<TxnSplit> ledger = ledgerLines(em, session.company(), bankAccount.getAccount(), session.startDate(), session.endDate());
        List<BankStatementLine> statements = statementLines(em, bankAccount, session.startDate(), session.endDate());
        Map<Long, MatchRow> statementMatches = statementMatches(em, sessionId);
        Map<Long, MatchRow> splitMatches = splitMatches(em, sessionId);
        List<DifferenceView> differences = differences(ledger, statements, statementMatches, splitMatches, session);
        return new Snapshot(
                session.id(),
                session.company().getCode(),
                bankAccount.getId(),
                bankAccountLabel(bankAccount),
                session.startDate(),
                session.endDate(),
                session.policy(),
                session.status(),
                new BalanceCards(session.beginning(), session.bookAll(), session.bookCleared(), session.statementEndingBalance(), session.difference()),
                statements.stream().map(line -> statementView(line, statementMatches.get(line.getId()))).toList(),
                ledger.stream().map(split -> ledgerView(split, splitMatches.get(split.getId()))).toList(),
                differences,
                listSessions(session.company().getCode()));
    }

    private static boolean isBalanced(Snapshot snapshot)
    {
        return compare(snapshot.balances().difference(), BigDecimal.ZERO) == 0
                && snapshot.differences().stream().noneMatch(difference -> unresolved(difference.category()));
    }

    private void recalculateBalances(long sessionId)
    {
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                SessionRow session = session(em, sessionId);
                if (session.status() == SessionStatus.FINALIZED)
                {
                    tx.commit();
                    return;
                }
                CompanyBankAccount bankAccount = configuredBankAccount(em, session.bankAccountId(), session.company());
                recalculateBalances(em, sessionId, bankAccount, session.startDate(), session.endDate(), session.statementEndingBalance());
                tx.commit();
            }
            catch (RuntimeException ex)
            {
                rollback(tx);
                throw ex;
            }
        }
    }

    private void match(EntityManager em, long sessionId, long statementLineId, long splitId, String note, boolean overwriteCleared)
    {
        SessionRow session = requireMutableSession(em, sessionId);
        CompanyBankAccount bankAccount = configuredBankAccount(
                em, session.bankAccountId(), session.company());
        BankStatementLine statement = requireStatementLine(
                em, session, bankAccount, statementLineId);
        TxnSplit split = requireLedgerSplit(em, session, bankAccount, splitId);
        if (statement.getMatchedTransaction() != null
                || split.getMatchedBankStatementLine() != null
                || hasAnyRelationshipMatchForStatement(em, sessionId, statementLineId)
                || hasAnyRelationshipMatchForSplit(em, sessionId, splitId))
        {
            throw new IllegalStateException(
                    "The selected statement entry or ledger line is already matched in this reconciliation scope.");
        }
        DifferenceCategory status = compare(statement.getAmount(), split.getAmountSigned()) == 0
                ? (Objects.equals(statement.getTransactionDate(), split.getTxn().getTxnDate()) ? DifferenceCategory.MATCHED : DifferenceCategory.DATE_MISMATCH)
                : DifferenceCategory.AMOUNT_MISMATCH;
        insertMatch(em, sessionId, statementLineId, splitId, status, note);
        statement.setMatchedTransaction(split.getTxn());
        statement.setStatus(BankStatementLine.Status.MATCHED);
        statement.touchUpdatedAt();
        split.setMatchedBankStatementLine(statement);
        if (session.policy() == ClearedStatePolicy.OVERWRITE_LEDGER_CLEARED_STATE || overwriteCleared)
        {
            split.setBankCleared(true);
            split.setBankClearedOn(statement.getPostedDate() == null ? statement.getTransactionDate() : statement.getPostedDate());
        }
        else if (!split.isBankCleared())
        {
            insertMatch(em, sessionId, statementLineId, splitId, DifferenceCategory.CLEARED_STATE_MISMATCH,
                    "Matched statement line exists, but ledger line is not cleared under policy " + session.policy().name() + ".");
        }
    }

    private static List<DifferenceView> differences(List<TxnSplit> ledger,
                                                    List<BankStatementLine> statements,
                                                    Map<Long, MatchRow> statementMatches,
                                                    Map<Long, MatchRow> splitMatches,
                                                    SessionRow session)
    {
        List<DifferenceView> output = new ArrayList<>();
        for (TxnSplit split : ledger)
        {
            MatchRow match = splitMatches.get(split.getId());
            if (match != null && match.status() != DifferenceCategory.CLEARED_STATE_MISMATCH)
            {
                continue;
            }
            List<BankStatementLine> exact = statements.stream()
                    .filter(line -> !statementMatches.containsKey(line.getId()))
                    .filter(line -> Objects.equals(line.getTransactionDate(), split.getTxn().getTxnDate()))
                    .filter(line -> compare(line.getAmount(), split.getAmountSigned()) == 0)
                    .toList();
            if (exact.size() > 1)
            {
                output.add(new DifferenceView(DifferenceCategory.DUPLICATE_POSSIBLE,
                        split.getTxn().getTxnDate(), exact.get(0).getTransactionDate(),
                        amount(split.getAmountSigned()), amount(exact.get(0).getAmount()),
                        "Multiple statement lines could match this ledger line."));
            }
            else if (exact.isEmpty())
            {
                BankStatementLine sameDate = statements.stream()
                        .filter(line -> !statementMatches.containsKey(line.getId()))
                        .filter(line -> Objects.equals(line.getTransactionDate(), split.getTxn().getTxnDate()))
                        .findFirst()
                        .orElse(null);
                BankStatementLine sameAmount = statements.stream()
                        .filter(line -> !statementMatches.containsKey(line.getId()))
                        .filter(line -> compare(line.getAmount(), split.getAmountSigned()) == 0)
                        .findFirst()
                        .orElse(null);
                if (sameDate != null)
                {
                    output.add(new DifferenceView(DifferenceCategory.AMOUNT_MISMATCH,
                            split.getTxn().getTxnDate(), sameDate.getTransactionDate(),
                            amount(split.getAmountSigned()), amount(sameDate.getAmount()),
                            "Ledger and statement dates match, but amounts differ."));
                }
                else if (sameAmount != null)
                {
                    output.add(new DifferenceView(DifferenceCategory.DATE_MISMATCH,
                            split.getTxn().getTxnDate(), sameAmount.getTransactionDate(),
                            amount(split.getAmountSigned()), amount(sameAmount.getAmount()),
                            "Ledger and statement amounts match, but dates differ."));
                }
                else
                {
                    output.add(new DifferenceView(DifferenceCategory.UNMATCHED_LEDGER,
                            split.getTxn().getTxnDate(), null,
                            amount(split.getAmountSigned()), null,
                            "Ledger bank-account line has no matching statement entry."));
                }
            }
            if (!split.isBankCleared() && split.getMatchedBankStatementLine() != null)
            {
                output.add(new DifferenceView(DifferenceCategory.CLEARED_STATE_MISMATCH,
                        split.getTxn().getTxnDate(), split.getMatchedBankStatementLine().getTransactionDate(),
                        amount(split.getAmountSigned()), amount(split.getMatchedBankStatementLine().getAmount()),
                        "Ledger line is matched but not marked cleared."));
            }
        }
        for (BankStatementLine line : statements)
        {
            if (!statementMatches.containsKey(line.getId()))
            {
                output.add(new DifferenceView(DifferenceCategory.UNMATCHED_STATEMENT,
                        null, line.getTransactionDate(), null, amount(line.getAmount()),
                        "Statement entry has no matching ledger bank-account line."));
            }
        }
        if (session.statementEndingBalance() != null && compare(session.difference(), BigDecimal.ZERO) != 0)
        {
            output.add(new DifferenceView(DifferenceCategory.ENDING_BALANCE_DIFFERENCE,
                    null, session.endDate(), session.bookCleared(), session.statementEndingBalance(),
                    "Statement ending balance and cleared book balance differ."));
        }
        return output;
    }

    private static StatementEntryView statementView(BankStatementLine line, MatchRow match)
    {
        return new StatementEntryView(
                line.getId(),
                line.getTransactionDate(),
                firstNonBlank(line.getName(), line.getMemo(), line.getTransactionType(), "Statement line " + line.getId()),
                firstNonBlank(line.getReference(), line.getCheckNumber(), line.getSourceTransactionId(), ""),
                amount(line.getAmount()),
                line.getStatus() == BankStatementLine.Status.MATCHED ? "matched" : "not cleared",
                match == null ? DifferenceCategory.UNMATCHED_STATEMENT : match.status(),
                match == null ? null : match.splitId(),
                match == null ? "" : match.note());
    }

    private static LedgerLineView ledgerView(TxnSplit split, MatchRow match)
    {
        return new LedgerLineView(
                split.getId(),
                split.getTxn().getId(),
                split.getTxn().getTxnDate(),
                firstNonBlank(split.getTxn().getMemo(), split.getNotes(), "Transaction " + split.getTxn().getId()),
                String.valueOf(split.getTxn().getId()),
                amount(split.getAmountSigned()),
                split.isBankCleared(),
                match == null ? (split.getMatchedBankStatementLine() == null ? null : split.getMatchedBankStatementLine().getId()) : match.statementLineId(),
                match == null ? DifferenceCategory.UNMATCHED_LEDGER : match.status());
    }

    private void recalculateBalances(EntityManager em, long sessionId, CompanyBankAccount bankAccount, LocalDate start, LocalDate end, BigDecimal statementEndingBalance)
    {
        BigDecimal beginning = amount(bankAccount.getOpeningBalance());
        BigDecimal periodActivity = BigDecimal.ZERO;
        BigDecimal cleared = amount(bankAccount.getOpeningBalance());
        for (TxnSplit split : ledgerLinesThrough(em, bankAccount.getCompany(), bankAccount.getAccount(), end))
        {
            LocalDate transactionDate = split.getTxn().getTxnDate();
            BigDecimal signed = amount(split.getAmountSigned());
            if (transactionDate.isBefore(start))
            {
                beginning = beginning.add(signed);
            }
            else if (!transactionDate.isAfter(end))
            {
                periodActivity = periodActivity.add(signed);
            }
            if (split.isBankCleared() && !transactionDate.isAfter(end))
            {
                cleared = cleared.add(signed);
            }
        }
        BigDecimal all = beginning.add(periodActivity);
        BigDecimal difference = statementEndingBalance == null ? BigDecimal.ZERO : amount(statementEndingBalance).subtract(cleared);
        em.createNativeQuery("""
                update bank_reconciliation_session
                   set beginning_balance = ?, book_balance_all = ?, book_balance_cleared = ?, difference_amount = ?, updated_at = CURRENT_TIMESTAMP
                 where id = ?
                """)
                .setParameter(1, amount(beginning))
                .setParameter(2, amount(all))
                .setParameter(3, amount(cleared))
                .setParameter(4, amount(difference))
                .setParameter(5, sessionId)
                .executeUpdate();
    }

    private SessionRow session(EntityManager em, long sessionId)
    {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                select s.id, s.company_id, s.bank_account_id, s.statement_start_date, s.statement_end_date,
                       s.statement_ending_balance, s.mismatch_policy, s.status, s.beginning_balance,
                       s.book_balance_all, s.book_balance_cleared, s.difference_amount
                  from bank_reconciliation_session s
                 where s.id = ?
                """)
                .setParameter(1, sessionId)
                .getResultList();
        if (rows.isEmpty())
        {
            throw new IllegalArgumentException("Reconciliation session does not exist: " + sessionId);
        }
        Object[] row = rows.get(0);
        Company company = em.find(Company.class, ((Number) row[1]).longValue());
        return new SessionRow(
                ((Number) row[0]).longValue(),
                company,
                ((Number) row[2]).longValue(),
                date(row[3]),
                date(row[4]),
                amountOrNull(row[5]),
                ClearedStatePolicy.valueOf((String) row[6]),
                SessionStatus.valueOf((String) row[7]),
                amount(row[8]),
                amount(row[9]),
                amount(row[10]),
                amount(row[11]));
    }

    private static SessionSummary sessionSummary(Object[] row)
    {
        return new SessionSummary(
                ((Number) row[0]).longValue(),
                (row[2] == null ? "" : String.valueOf(row[2]) + " — ") + row[1],
                date(row[3]),
                date(row[4]),
                SessionStatus.valueOf((String) row[5]),
                amount(row[6]));
    }

    private Map<Long, MatchRow> statementMatches(EntityManager em, long sessionId)
    {
        return matches(em, sessionId, true);
    }

    private Map<Long, MatchRow> splitMatches(EntityManager em, long sessionId)
    {
        return matches(em, sessionId, false);
    }

    private Map<Long, MatchRow> matches(EntityManager em, long sessionId, boolean byStatement)
    {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("select statement_line_id, txn_split_id, match_status, resolution_note from bank_reconciliation_match where session_id = ? order by id")
                .setParameter(1, sessionId)
                .getResultList();
        Map<Long, MatchRow> output = new LinkedHashMap<>();
        for (Object[] row : rows)
        {
            Long statementId = row[0] == null ? null : ((Number) row[0]).longValue();
            Long splitId = row[1] == null ? null : ((Number) row[1]).longValue();
            Long key = byStatement ? statementId : splitId;
            if (key != null)
            {
                output.put(key, new MatchRow(statementId, splitId, DifferenceCategory.valueOf((String) row[2]), row[3] == null ? "" : String.valueOf(row[3])));
            }
        }
        return output;
    }

    private Set<Long> matchedStatementIds(EntityManager em, long sessionId)
    {
        @SuppressWarnings("unchecked")
        List<Number> rows = em.createNativeQuery("""
                select distinct statement_line_id
                  from bank_reconciliation_match
                 where session_id = ?
                   and statement_line_id is not null
                   and txn_split_id is not null
                   and match_status in ('MATCHED', 'AMOUNT_MISMATCH', 'DATE_MISMATCH', 'CLEARED_STATE_MISMATCH')
                """)
                .setParameter(1, sessionId)
                .getResultList();
        Set<Long> ids = new HashSet<>();
        rows.forEach(value -> ids.add(value.longValue()));
        return ids;
    }

    private Set<Long> matchedSplitIds(EntityManager em, long sessionId)
    {
        @SuppressWarnings("unchecked")
        List<Number> rows = em.createNativeQuery("""
                select distinct txn_split_id
                  from bank_reconciliation_match
                 where session_id = ?
                   and statement_line_id is not null
                   and txn_split_id is not null
                   and match_status in ('MATCHED', 'AMOUNT_MISMATCH', 'DATE_MISMATCH', 'CLEARED_STATE_MISMATCH')
                """)
                .setParameter(1, sessionId)
                .getResultList();
        Set<Long> ids = new HashSet<>();
        rows.forEach(value -> ids.add(value.longValue()));
        return ids;
    }

    private void insertMatch(EntityManager em, long sessionId, Long statementLineId, Long splitId, DifferenceCategory status, String note)
    {
        em.createNativeQuery("insert into bank_reconciliation_match (session_id, statement_line_id, txn_split_id, match_status, resolution_note) values (?, ?, ?, ?, ?)")
                .setParameter(1, sessionId)
                .setParameter(2, statementLineId)
                .setParameter(3, splitId)
                .setParameter(4, status.name())
                .setParameter(5, note)
                .executeUpdate();
    }

    /** Recreates reconciliation sessions and matches inside an interchange caller's transaction. */
    public ImportedReconciliation importForInterchange(
            EntityManager em,
            Company company,
            List<SessionImport> sessionValues,
            List<MatchImport> matchValues,
            Map<String, CompanyBankAccount> bankAccounts,
            Map<String, BankStatementLine> statementLines,
            Map<String, TxnSplit> transactionLines)
    {
        Objects.requireNonNull(em, "em");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(sessionValues, "sessionValues");
        Objects.requireNonNull(matchValues, "matchValues");
        if (!em.getTransaction().isActive())
        {
            throw new IllegalStateException("Reconciliation import requires an active caller-owned transaction");
        }
        Map<String, Long> sessions = new LinkedHashMap<>();
        for (SessionImport value : sessionValues)
        {
            CompanyBankAccount bankAccount = required(bankAccounts, value.bankAccountId(), "configured bank account");
            if (bankAccount.getCompany() == null
                    || !company.getId().equals(bankAccount.getCompany().getId()))
            {
                throw new IllegalArgumentException("Reconciliation bank account belongs to another company");
            }
            em.createNativeQuery("""
                    insert into bank_reconciliation_session
                        (portable_id, company_id, bank_account_id, statement_start_date,
                         statement_end_date, statement_ending_balance, mismatch_policy, status,
                         notes, beginning_balance, book_balance_all, book_balance_cleared,
                         difference_amount, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)
                    .setParameter(1, value.portableId())
                    .setParameter(2, company.getId())
                    .setParameter(3, bankAccount.getId())
                    .setParameter(4, value.statementStartDate())
                    .setParameter(5, value.statementEndDate())
                    .setParameter(6, value.statementEndingBalance())
                    .setParameter(7, value.mismatchPolicy())
                    .setParameter(8, value.status())
                    .setParameter(9, value.notes())
                    .setParameter(10, value.beginningBalance())
                    .setParameter(11, value.bookBalanceAll())
                    .setParameter(12, value.bookBalanceCleared())
                    .setParameter(13, value.differenceAmount())
                    .setParameter(14, value.createdAt())
                    .setParameter(15, value.updatedAt())
                    .executeUpdate();
            long id = ((Number) em.createNativeQuery(
                            "select id from bank_reconciliation_session where portable_id = ?")
                    .setParameter(1, value.portableId())
                    .getSingleResult()).longValue();
            sessions.put(value.externalId(), id);
        }

        Map<String, Long> matches = new LinkedHashMap<>();
        for (MatchImport value : matchValues)
        {
            long sessionId = required(sessions, value.reconciliationSessionId(), "reconciliation session");
            BankStatementLine statementLine = optional(
                    statementLines, value.statementLineId(), "bank statement line");
            TxnSplit transactionLine = optional(
                    transactionLines, value.transactionLineId(), "transaction line");
            if (statementLine == null && transactionLine == null)
            {
                throw new IllegalArgumentException("Reconciliation match requires a statement or transaction line");
            }
            if (statementLine != null && (statementLine.getCompany() == null
                    || !company.getId().equals(statementLine.getCompany().getId())))
            {
                throw new IllegalArgumentException("Reconciliation statement line belongs to another company");
            }
            if (transactionLine != null && (transactionLine.getTxn() == null
                    || transactionLine.getTxn().getCompany() == null
                    || !company.getId().equals(transactionLine.getTxn().getCompany().getId())))
            {
                throw new IllegalArgumentException("Reconciliation transaction line belongs to another company");
            }
            em.createNativeQuery("""
                    insert into bank_reconciliation_match
                        (portable_id, session_id, statement_line_id, txn_split_id, match_status,
                         resolution_note, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """)
                    .setParameter(1, value.portableId())
                    .setParameter(2, sessionId)
                    .setParameter(3, statementLine == null ? null : statementLine.getId())
                    .setParameter(4, transactionLine == null ? null : transactionLine.getId())
                    .setParameter(5, value.matchStatus())
                    .setParameter(6, value.resolutionNote())
                    .setParameter(7, value.createdAt())
                    .setParameter(8, value.updatedAt())
                    .executeUpdate();
            long id = ((Number) em.createNativeQuery(
                            "select id from bank_reconciliation_match where portable_id = ?")
                    .setParameter(1, value.portableId())
                    .getSingleResult()).longValue();
            matches.put(value.externalId(), id);
        }
        return new ImportedReconciliation(sessions, matches);
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

    public record SessionImport(
            String externalId,
            UUID portableId,
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

    public record MatchImport(
            String externalId,
            UUID portableId,
            String reconciliationSessionId,
            String statementLineId,
            String transactionLineId,
            String matchStatus,
            String resolutionNote,
            Instant createdAt,
            Instant updatedAt)
    {
    }

    public record ImportedReconciliation(
            Map<String, Long> sessions,
            Map<String, Long> matches)
    {
        public ImportedReconciliation
        {
            sessions = Map.copyOf(sessions);
            matches = Map.copyOf(matches);
        }
    }

    private static List<TxnSplit> ledgerLines(EntityManager em, Company company, Account account, LocalDate start, LocalDate end)
    {
        return em.createQuery("""
                select s from TxnSplit s
                join fetch s.txn t
                join fetch s.account a
                left join fetch s.matchedBankStatementLine
                where t.company = :company
                  and a = :account
                  and t.txnDate >= :start
                  and t.txnDate <= :end
                order by t.txnDate, s.id
                """, TxnSplit.class)
                .setParameter("company", company)
                .setParameter("account", account)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }

    private static List<TxnSplit> ledgerLinesThrough(EntityManager em, Company company, Account account, LocalDate end)
    {
        return em.createQuery("""
                select s from TxnSplit s
                join fetch s.txn t
                join fetch s.account a
                left join fetch s.matchedBankStatementLine
                where t.company = :company
                  and a = :account
                  and t.txnDate <= :end
                order by t.txnDate, s.id
                """, TxnSplit.class)
                .setParameter("company", company)
                .setParameter("account", account)
                .setParameter("end", end)
                .getResultList();
    }

    private static List<BankStatementLine> statementLines(EntityManager em, CompanyBankAccount bankAccount, LocalDate start, LocalDate end)
    {
        return em.createQuery("""
                select l from BankStatementLine l
                where l.bankAccount = :bankAccount
                  and l.transactionDate >= :start
                  and l.transactionDate <= :end
                  and l.status not in (:excluded)
                order by l.transactionDate, l.id
                """, BankStatementLine.class)
                .setParameter("bankAccount", bankAccount)
                .setParameter("start", start)
                .setParameter("end", end)
                .setParameter("excluded", List.of(BankStatementLine.Status.ERROR, BankStatementLine.Status.DUPLICATE))
                .getResultList();
    }

    private LocalDate reconciliationStartDate(EntityManager em, String companyCode, CompanyBankAccount bankAccount, LocalDate statementEnd)
    {
        Object close = em.createNativeQuery("select max(close_date) from period_close_run where group_code = ? and status = 'COMPLETED' and close_date < ?")
                .setParameter(1, companyCode)
                .setParameter(2, statementEnd)
                .getSingleResult();
        LocalDate closeDate = dateOrNull(close);
        if (closeDate != null)
        {
            return closeDate.plusDays(1);
        }
        return bankAccount.getOpeningDate() == null ? statementEnd.withDayOfMonth(1) : bankAccount.getOpeningDate();
    }

    private SessionRow requireMutableSession(EntityManager em, long sessionId)
    {
        SessionRow session = session(em, sessionId);
        if (session.status() == SessionStatus.FINALIZED)
        {
            throw finalizedMutation(sessionId);
        }
        return session;
    }

    private static IllegalStateException finalizedMutation(long sessionId)
    {
        return new IllegalStateException(
                "Finalized reconciliation session " + sessionId
                        + " is read-only. Start a successor reconciliation to continue.");
    }

    private BankStatementLine requireStatementLine(
            EntityManager em, SessionRow session, CompanyBankAccount bankAccount, Long statementLineId)
    {
        BankStatementLine statement = required(
                em, BankStatementLine.class, statementLineId, "Statement line");
        if (statement.getCompany() == null
                || !Objects.equals(statement.getCompany().getId(), session.company().getId()))
        {
            throw new IllegalArgumentException("Statement line belongs to a different company.");
        }
        if (statement.getBankAccount() == null
                || !Objects.equals(statement.getBankAccount().getId(), bankAccount.getId()))
        {
            throw new IllegalArgumentException(
                    "Statement line does not belong to the reconciliation bank account.");
        }
        requireStatementDateInRange(session, statement.getTransactionDate());
        if (statement.getStatus() == BankStatementLine.Status.ERROR
                || statement.getStatus() == BankStatementLine.Status.DUPLICATE)
        {
            throw new IllegalArgumentException(
                    "Statement line is not eligible for reconciliation: " + statement.getStatus() + ".");
        }
        return statement;
    }

    private TxnSplit requireLedgerSplit(
            EntityManager em, SessionRow session, CompanyBankAccount bankAccount, Long splitId)
    {
        TxnSplit split = required(em, TxnSplit.class, splitId, "Ledger line");
        CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
        ownership.ensureOwnedBy(em, session.company(), split.getTxn(), "Ledger transaction");
        ownership.ensureOwnedBy(em, session.company(), split.getAccount(), "Ledger line account");
        ownership.ensureOwnedBy(em, session.company(), split.getFund(), "Ledger line fund");
        ownership.ensureOwnedBy(em, session.company(), split.getBudgetCategory(), "Ledger line budget category");
        ownership.ensureOwnedBy(em, session.company(), split.getActivity(), "Ledger line activity");
        ownership.ensureOwnedBy(em, session.company(), split.getMerchant(), "Ledger line merchant");
        if (split.getAccount() == null
                || !Objects.equals(split.getAccount().getId(), bankAccount.getAccount().getId()))
        {
            throw new IllegalArgumentException(
                    "Ledger line does not belong to the reconciliation bank account.");
        }
        LocalDate txnDate = split.getTxn() == null ? null : split.getTxn().getTxnDate();
        if (txnDate == null || txnDate.isBefore(session.startDate()) || txnDate.isAfter(session.endDate()))
        {
            throw new IllegalArgumentException(
                    "Ledger line is outside the reconciliation statement date range.");
        }
        return split;
    }

    private static void requireStatementDateInRange(SessionRow session, LocalDate date)
    {
        if (date == null || date.isBefore(session.startDate()) || date.isAfter(session.endDate()))
        {
            throw new IllegalArgumentException(
                    "Statement line is outside the reconciliation statement date range.");
        }
    }

    private static boolean hasRelationshipMatch(
            EntityManager em, long sessionId, long statementLineId, long splitId)
    {
        Number count = (Number) em.createNativeQuery("""
                select count(*) from bank_reconciliation_match
                 where session_id = ? and statement_line_id = ? and txn_split_id = ?
                   and match_status in ('MATCHED', 'AMOUNT_MISMATCH', 'DATE_MISMATCH', 'CLEARED_STATE_MISMATCH')
                """)
                .setParameter(1, sessionId)
                .setParameter(2, statementLineId)
                .setParameter(3, splitId)
                .getSingleResult();
        return count.longValue() > 0;
    }

    private static boolean hasAnyRelationshipMatchForStatement(
            EntityManager em, long sessionId, long statementLineId)
    {
        Number count = (Number) em.createNativeQuery("""
                select count(*) from bank_reconciliation_match
                 where session_id = ? and statement_line_id = ? and txn_split_id is not null
                   and match_status in ('MATCHED', 'AMOUNT_MISMATCH', 'DATE_MISMATCH', 'CLEARED_STATE_MISMATCH')
                """)
                .setParameter(1, sessionId)
                .setParameter(2, statementLineId)
                .getSingleResult();
        return count.longValue() > 0;
    }

    private static boolean hasAnyRelationshipMatchForSplit(
            EntityManager em, long sessionId, long splitId)
    {
        Number count = (Number) em.createNativeQuery("""
                select count(*) from bank_reconciliation_match
                 where session_id = ? and txn_split_id = ? and statement_line_id is not null
                   and match_status in ('MATCHED', 'AMOUNT_MISMATCH', 'DATE_MISMATCH', 'CLEARED_STATE_MISMATCH')
                """)
                .setParameter(1, sessionId)
                .setParameter(2, splitId)
                .getSingleResult();
        return count.longValue() > 0;
    }

    private static void recordAudit(
            EntityManager em, Company company, String actor, String actionType, String entityType,
            String entityId, String summary, String before, String after, String reason)
    {
        AuditEvent event = new AuditEvent();
        event.setCompany(company);
        event.setActor(actor);
        event.setActionType(actionType);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setSummary(summary);
        event.setBeforeValue(before);
        event.setAfterValue(after);
        event.setReason(reason);
        em.persist(event);
    }

    private CompanyBankAccount configuredBankAccount(EntityManager em, Long id, Company company)
    {
        CompanyBankAccount bankAccount = em.createQuery("""
                select cba from CompanyBankAccount cba
                left join fetch cba.bank
                left join fetch cba.account
                where cba.id = :id
                  and cba.company = :company
                """, CompanyBankAccount.class)
                .setParameter("id", id)
                .setParameter("company", company)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Configured bank account does not exist for company: " + id));
        if (!bankAccount.isActive() || bankAccount.getBank() == null || bankAccount.getAccount() == null)
        {
            throw new IllegalArgumentException("Reconciliation requires an active configured bank account linked to a Bank and chart account.");
        }
        BankConfigurationService.validateBankLedgerAccount(bankAccount.getAccount());
        new CompanyOwnershipService(jpa).ensureOwnedBy(em, company, bankAccount.getAccount(), "Configured bank ledger account");
        return bankAccount;
    }

    private static <T> T required(EntityManager em, Class<T> type, Long id, String label)
    {
        if (id == null)
        {
            throw new IllegalArgumentException(label + " is required.");
        }
        T value = em.find(type, id);
        if (value == null)
        {
            throw new IllegalArgumentException(label + " does not exist: " + id);
        }
        return value;
    }

    private static Company companyByCode(EntityManager em, String code)
    {
        if (isBlank(code))
        {
            throw new IllegalArgumentException("Company code is required.");
        }
        return em.createQuery("select c from Company c where c.code = :code", Company.class)
                .setParameter("code", code.trim())
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Company does not exist: " + code));
    }

    private static void validateStart(StartCommand command)
    {
        if (command == null || isBlank(command.companyCode()) || command.bankAccountId() == null || command.statementEndDate() == null)
        {
            throw new IllegalArgumentException("Company, configured bank account, and statement ending date are required.");
        }
    }

    private static ClearedStatePolicy policy(ClearedStatePolicy value)
    {
        return value == null ? ClearedStatePolicy.WARN_ONLY : value;
    }

    private static String requireText(String value, String label)
    {
        if (isBlank(value))
        {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    private static boolean unresolved(DifferenceCategory category)
    {
        return category != DifferenceCategory.MATCHED && category != DifferenceCategory.RESOLVED;
    }

    private static BigDecimal amount(Object value)
    {
        if (value == null)
        {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        if (value instanceof BigDecimal bd)
        {
            return bd.setScale(4, RoundingMode.HALF_UP);
        }
        if (value instanceof Number number)
        {
            return BigDecimal.valueOf(number.doubleValue()).setScale(4, RoundingMode.HALF_UP);
        }
        return new BigDecimal(String.valueOf(value)).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal amountOrNull(Object value)
    {
        return value == null ? null : amount(value);
    }

    private static int compare(BigDecimal a, BigDecimal b)
    {
        return amount(a).compareTo(amount(b));
    }

    private static LocalDate date(Object value)
    {
        LocalDate date = dateOrNull(value);
        if (date == null)
        {
            throw new IllegalArgumentException("Date value is required.");
        }
        return date;
    }

    private static LocalDate dateOrNull(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof LocalDate localDate)
        {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate)
        {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private static long positiveId()
    {
        long value = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        return value == 0 ? 1 : value;
    }

    private static String bankAccountLabel(CompanyBankAccount bankAccount)
    {
        String bank = bankAccount.getBank() == null ? "" : bankAccount.getBank().getName() + " • ";
        String chart = bankAccount.getAccount() == null ? "" : " — " + bankAccount.getAccount().getCode();
        return bank + bankAccount.getName() + chart;
    }

    private static String firstNonBlank(String... values)
    {
        for (String value : values)
        {
            if (!isBlank(value))
            {
                return value.trim();
            }
        }
        return "";
    }

    private static String blankToNull(String value)
    {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.isBlank();
    }

    private static void rollback(EntityTransaction tx)
    {
        if (tx.isActive())
        {
            tx.rollback();
        }
    }

    private record MatchRow(Long statementLineId, Long splitId, DifferenceCategory status, String note) { }
    private record SessionRow(long id,
                              Company company,
                              long bankAccountId,
                              LocalDate startDate,
                              LocalDate endDate,
                              BigDecimal statementEndingBalance,
                              ClearedStatePolicy policy,
                              SessionStatus status,
                              BigDecimal beginning,
                              BigDecimal bookAll,
                              BigDecimal bookCleared,
                              BigDecimal difference) { }
}
