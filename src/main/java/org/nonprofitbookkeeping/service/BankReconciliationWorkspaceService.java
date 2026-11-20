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
                                     String resolution,
                                     boolean manual)
    {
        public StatementEntryView(Long statementLineId, LocalDate date, String description, String reference,
                BigDecimal amount, String clearedState, DifferenceCategory matchStatus, Long matchedLedgerSplitId, String resolution)
        {
            this(statementLineId,date,description,reference,amount,clearedState,matchStatus,matchedLedgerSplitId,resolution,false);
        }
    }
    public record LedgerLineView(Long splitId,
                                 Long txnId,
                                 LocalDate date,
                                 String memo,
                                 String transactionNumber,
                                 BigDecimal amount,
                                 boolean cleared,
                                 Long matchedStatementLineId,
                                 DifferenceCategory matchStatus,
                                 String paymentReference, String correctionHistory) { }
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
    private final AuthorizationGuard authorizationGuard;

    public BankReconciliationWorkspaceService(Jpa jpa)
    {
        this(jpa, new BankStatementManualEntryService(), null);
    }

    public BankReconciliationWorkspaceService(Jpa jpa, AuthorizationGuard authorizationGuard)
    {
        this(jpa, new BankStatementManualEntryService(), authorizationGuard);
    }

    BankReconciliationWorkspaceService(Jpa jpa, BankStatementManualEntryService manualStatementService)
    {
        this(jpa, manualStatementService, null);
    }

    BankReconciliationWorkspaceService(
            Jpa jpa,
            BankStatementManualEntryService manualStatementService,
            AuthorizationGuard authorizationGuard)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.manualStatementService = Objects.requireNonNull(manualStatementService, "manualStatementService");
        this.authorizationGuard = authorizationGuard;
    }

    public Snapshot updateStatementBalance(long sessionId, BigDecimal endingBalance)
    {
        String actor = bookkeepingActorForSession(sessionId, "update statement ending balance", "system");
        if (endingBalance == null || endingBalance.stripTrailingZeros().scale() > 2)
        {
            throw new IllegalArgumentException("Enter a known statement ending balance with at most two decimal places.");
        }
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                SessionRow row = requireMutableSession(em, sessionId);
                em.createNativeQuery("update bank_reconciliation_session set statement_ending_balance = ?, updated_at = CURRENT_TIMESTAMP where id = ?")
                .setParameter(1, endingBalance).setParameter(2, sessionId).executeUpdate();
                recordAudit(em, row.company(), actor, "RECONCILIATION_STATEMENT_BALANCE_CHANGED", "BANK_RECONCILIATION_SESSION",
                            String.valueOf(sessionId), "Updated bank statement ending balance.", String.valueOf(row.statementEndingBalance()),
                            endingBalance.toPlainString(), "Explicit statement header save");
                CompanyBankAccount account = configuredBankAccount(em, row.bankAccountId(), row.company());
                recalculateBalances(em, sessionId, account, row.startDate(), row.endDate(), endingBalance);
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

    public record OpeningView(BigDecimal clearedOpening, BigDecimal bookOpening,
                              LocalDate startDate, Long sourceSessionId, LocalDate sourceEndingDate,
                              boolean confirmed, String source) { }

    /** Opening facts are account/session scoped; reading never invents a zero opening. */

    public OpeningView opening(long sessionId)
    {
        try (EntityManager em = jpa.em())
        {
            SessionRow row = session(em, sessionId);
            CompanyBankAccount account = configuredBankAccount(em, row.bankAccountId(), row.company());
            Object[] facts = (Object[])
                             em.createNativeQuery("select opening_amount, opening_source_session_id from bank_reconciliation_session where id = ?")
                             .setParameter(1, sessionId).getSingleResult();
            BigDecimal value = amountOrNull(facts[0]);
            Long source = facts[1] == null ? null : ((Number) facts[1]).longValue();
            LocalDate sourceEnd = source == null ? null : session(em, source).endDate();
            BigDecimal book = ledgerLinesThrough(em, row.company(), account.getAccount(), row.endDate()).stream()
                              .filter(split -> split.getTxn().getTxnDate().isBefore(row.startDate()))
                              .map(TxnSplit::getAmountSigned).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new OpeningView(value, amount(book), row.startDate(), source, sourceEnd, value != null,
                                   source != null ? "Completed reconciliation ending " + sourceEnd
                                   : value != null ? "Confirmed first statement opening"
                                   : row.status() == SessionStatus.FINALIZED ? "Recorded historical reconciliation" : "First statement opening required");
        }
    }

    public Snapshot confirmOpening(long sessionId, LocalDate beginningDate, BigDecimal opening, boolean reviewed)
    {
        String actor = bookkeepingActorForSession(sessionId, "confirm reconciliation opening", "system");
        if (!reviewed || beginningDate == null || opening == null || opening.stripTrailingZeros().scale() > 2)
        {
            throw new IllegalArgumentException("Confirm the beginning date, whole-cent opening balance and review of the journal starting position/outstanding items.");
        }
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                SessionRow row = requireMutableSession(em, sessionId);
                Object[] facts = (Object[])
                                 em.createNativeQuery("select opening_amount, opening_source_session_id from bank_reconciliation_session where id = ?")
                                 .setParameter(1, sessionId).getSingleResult();
                if (facts[1] != null)
                {
                    throw new IllegalStateException("Opening comes from a completed reconciliation and cannot be replaced.");
                }
                if (!matches(em, sessionId, true).isEmpty())
                {
                    throw new IllegalStateException("Remove existing matches before correcting the first opening.");
                }
                if (beginningDate.isAfter(row.endDate()))
                {
                    throw new IllegalArgumentException("Beginning date must not follow statement end.");
                }
                CompanyBankAccount account = configuredBankAccount(em, row.bankAccountId(), row.company());
                if (account.getOpeningDate() != null && beginningDate.isBefore(account.getOpeningDate()))
                {
                    throw new IllegalArgumentException("Beginning date precedes the configured bank opening date.");
                }
                em.createNativeQuery("update bank_reconciliation_session set statement_start_date = ?, opening_amount = ?, updated_at = CURRENT_TIMESTAMP where id = ?")
                .setParameter(1, beginningDate).setParameter(2, opening).setParameter(3, sessionId).executeUpdate();
                recordAudit(em, row.company(), actor, "RECONCILIATION_OPENING_CONFIRMED", "BANK_RECONCILIATION_SESSION",
                            String.valueOf(sessionId), "Confirmed first bank statement opening and reviewed starting journal/outstanding position.",
                            String.valueOf(facts[0]), "date=" + beginningDate + "; opening=" + opening, "User reviewed opening position");
                recalculateBalances(em, sessionId, account, beginningDate, row.endDate(), row.statementEndingBalance());
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

    private void inheritOpening(EntityManager em, long sessionId, CompanyBankAccount account, LocalDate start)
    {
        @SuppressWarnings("unchecked")
        List<Object[]> previous =
            em.createNativeQuery("select id, statement_ending_balance from bank_reconciliation_session where company_id = ? and bank_account_id = ? and status = 'FINALIZED' and statement_end_date = ? and statement_ending_balance is not null order by id desc")
            .setParameter(1, account.getCompany().getId()).setParameter(2, account.getId()).setParameter(3, start.minusDays(1))
            .setMaxResults(1).getResultList();
        if (!previous.isEmpty())
        {
            if (((BigDecimal) previous.get(0)[1]).stripTrailingZeros().scale() > 2)
            {
                throw new IllegalStateException("Previous statement ending balance contains fractional cents; review it before starting a successor.");
            }
            em.createNativeQuery("update bank_reconciliation_session set opening_amount = ?, opening_source_session_id = ? where id = ?")
            .setParameter(1, previous.get(0)[1]).setParameter(2, previous.get(0)[0]).setParameter(3, sessionId).executeUpdate();
        }
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

    public List<SessionSummary> listSessionsForAccount(String companyCode, long bankAccountId)
    {
        try (EntityManager em = jpa.em())
        {
            Company company = companyByCode(em, companyCode);
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery("""
                    select s.id, cba.name, a.code, s.statement_start_date, s.statement_end_date, s.status, s.difference_amount
                    from bank_reconciliation_session s join company_bank_account cba on cba.id=s.bank_account_id
                    left join account a on a.id=cba.account_id
                    where s.company_id=? and s.bank_account_id=? order by s.statement_end_date desc,s.id desc
                    """).setParameter(1,company.getId()).setParameter(2,bankAccountId).getResultList();
            return rows.stream().map(BankReconciliationWorkspaceService::sessionSummary).toList();
        }
    }

    public Snapshot start(StartCommand command)
    {
        String actor = ServiceAuthorization.actor(authorizationGuard, ApplicationPermission.BOOKKEEPING_WRITE,
                command == null ? null : command.companyCode(), "start reconciliation", "system");
        validateStart(command);
        long sessionId;
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                Company company = companyByCode(em, command.companyCode());
                em.lock(company, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                CompanyBankAccount bankAccount = configuredBankAccount(em, command.bankAccountId(), company);
                requireActive(bankAccount);
                LocalDate startDate = reconciliationStartDate(em, bankAccount, command.statementEndDate());
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
                inheritOpening(em, sessionId, bankAccount, startDate);
                recalculateBalances(em, sessionId, bankAccount, startDate, command.statementEndDate(), command.statementEndingBalance());
                recordAudit(em,company,actor,"RECONCILIATION_STARTED","BANK_RECONCILIATION_SESSION",String.valueOf(sessionId),
                        "Started bank reconciliation.",null,"account="+bankAccount.getId()+"; through="+command.statementEndDate()+"; statementEnding="+command.statementEndingBalance(),"User started statement");
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
        String authorizedActor = bookkeepingActorForSession(
                command == null ? null : command.finalizedSessionId(),
                "start reconciliation successor",
                command == null ? null : command.actor());
        if (command == null || command.finalizedSessionId() <= 0 || command.statementEndDate() == null)
        {
            throw new IllegalArgumentException("Finalized session and successor statement ending date are required.");
        }
        String actor = requireText(authorizedActor, "Successor actor");
        String reason = requireText(command.reason(), "Successor reason");
        long successorId;
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                SessionRow predecessor = session(em, command.finalizedSessionId());
                em.lock(predecessor.company(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                if (predecessor.status() != SessionStatus.FINALIZED)
                {
                    throw new IllegalStateException("A successor can be started only from a finalized reconciliation session.");
                }
                CompanyBankAccount bankAccount = configuredBankAccount(
                        em, predecessor.bankAccountId(), predecessor.company());
                requireActive(bankAccount);
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
                inheritOpening(em, successorId, bankAccount, startDate);
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

    /** Recorded session state for a read-only packet; deliberately does not recalculate or persist balances. */
    public Snapshot readForPacket(long sessionId, String companyCode)
    {
        try (EntityManager em = jpa.em())
        {
            SessionRow row = session(em, sessionId);
            if (!row.company().getCode().equalsIgnoreCase(companyCode))
            {
                throw new IllegalArgumentException("Reconciliation session belongs to another company.");
            }
            return snapshot(em, sessionId);
        }
    }

    public Snapshot addManualLine(ManualStatementLineCommand command)
    {
        String actor = bookkeepingActorForSession(command == null ? null : command.sessionId(),
                "add reconciliation statement line", "system");
        if (command == null || command.date() == null || command.amount() == null || command.amount().stripTrailingZeros().scale() > 2 || compare(command.amount(), BigDecimal.ZERO) == 0)
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
                BankStatementLine created = manualStatementService.addLine(
                        em,
                        session.company(),
                        bankAccount,
                        command.date(),
                        command.amount(),
                        command.description(),
                        command.reference());
                recordAudit(em, session.company(), actor, "MANUAL_STATEMENT_ENTERED", "BANK_STATEMENT_LINE", String.valueOf(created.getId()),
                        "Entered reviewed manual bank statement fact.", null, "date="+command.date()+"; amount="+command.amount()+"; reference="+command.reference(), "User entered statement fact");
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

    /** Retires an erroneous manual statement fact without deleting its source/audit history. */
    public Snapshot removeManualLine(long sessionId, long statementId)
    {
        String actor = bookkeepingActorForSession(sessionId, "remove manual statement entry", "system");
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                SessionRow row = requireMutableSession(em, sessionId);
                var bank = configuredBankAccount(em, row.bankAccountId(), row.company());
                var statement = requireStatementLine(em, row, bank, statementId);
                var batch = statement.getBatch();
                if (batch.getSourceFormat() != org.nonprofitbookkeeping.model.BankImportBatch.SourceFormat.OTHER
                        || !"Manual reconciliation entry".equals(batch.getSourceName()) || batch.getTotalLineCount() != 1)
                {
                    throw new IllegalStateException("Imported statement source facts are retained. Only an unlinked manual entry can be removed here.");
                }
                long links = ((Number)
                              em.createNativeQuery("select count(*) from bank_reconciliation_match where statement_line_id = ? and txn_split_id is not null")
                              .setParameter(1, statementId).getSingleResult()).longValue();
                if (links != 0 || statement.getMatchedTransaction() != null || statement.getAcceptedTransaction() != null)
                {
                    throw new IllegalStateException("Unmatch this statement entry before removing it; accepted journal entries require their correction workflow.");
                }
                statement.setStatus(BankStatementLine.Status.REJECTED);
                statement.touchUpdatedAt();
                batch.setStatus(org.nonprofitbookkeeping.model.BankImportBatch.Status.REJECTED);
                batch.setRejectedLineCount(1);
                recordAudit(em, row.company(), actor, "MANUAL_STATEMENT_REMOVED", "BANK_STATEMENT_LINE", String.valueOf(statementId),
                            "Removed erroneous manual statement entry from reconciliation; retained source fact.", "IMPORTED", "REJECTED",
                            "User confirmed removal");
                em.flush();
                recalculateBalances(em, sessionId, bank, row.startDate(), row.endDate(), row.statementEndingBalance());
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

    public Snapshot autoMatch(long sessionId)
    {
        requireBookkeepingWriteForSession(sessionId, "auto-match reconciliation");
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
                    boolean autoClears = current.policy() == ClearedStatePolicy.OVERWRITE_LEDGER_CLEARED_STATE;
                    if (usedSplits.contains(ledger.splitId()) || (!ledger.cleared() && !autoClears))
                    {
                        continue;
                    }
                    List<StatementEntryView> candidates = current.statementEntries().stream()
                            .filter(statement -> !usedStatements.contains(statement.statementLineId()))
                            .filter(statement -> Objects.equals(em.find(BankStatementLine.class,statement.statementLineId()).getTransactionDate(), ledger.date()))
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

    /** Explicit reviewed bank-item match: all Fund allocations of one instrument clear atomically. */
    public Snapshot matchBankItem(long sessionId, long statementLineId, long anchorSplitId)
    {
        String actor = bookkeepingActorForSession(sessionId, "match and clear bank item", "system");
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                SessionRow row = requireMutableSession(em, sessionId);
                if (em.createNativeQuery("select opening_amount from bank_reconciliation_session where id = ?")
                        .setParameter(1, sessionId).getSingleResult() == null)
                {
                    throw new IllegalStateException("Confirm the statement opening before matching.");
                }
                CompanyBankAccount bank = configuredBankAccount(em, row.bankAccountId(), row.company());
                BankStatementLine statement = requireStatementLine(em, row, bank, statementLineId);
                TxnSplit anchor = requireLedgerSplit(em, row, bank, anchorSplitId);
                List<TxnSplit> group = bankItemSplits(em, anchor);
                for (TxnSplit member : group)
                {
                    requireLedgerSplit(em, row, bank, member.getId());
                }
                BigDecimal total = group.stream().map(TxnSplit::getAmountSigned).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (compare(total, statement.getAmount()) != 0)
                {
                    throw new IllegalArgumentException("Selected bank item and statement amounts differ; no clearing changes were made.");
                }
                LocalDate clearedDate = statement.getPostedDate() == null ? statement.getTransactionDate() : statement.getPostedDate();
                requireStatementDateInRange(row, clearedDate);
                boolean alreadyMatched = Objects.equals(statement.getMatchedTransaction(), anchor.getTxn())
                                         && group.stream().allMatch(split -> hasRelationshipMatch(em, sessionId, statementLineId, split.getId())
                                                 && split.getMatchedBankStatementLine() != null
                                                 && Objects.equals(split.getMatchedBankStatementLine().getId(), statementLineId));
                if (!alreadyMatched)
                {
                    if (statement.getMatchedTransaction() != null || hasAnyRelationshipMatchForStatement(em, sessionId, statementLineId)
                            || group.stream().anyMatch(split -> split.getMatchedBankStatementLine() != null
                                                       || hasAnyRelationshipMatchForSplit(em, sessionId, split.getId())))
                    {
                        throw new IllegalStateException("Bank item or statement entry already has another match.");
                    }
                    for (TxnSplit split : group)
                    {
                        insertMatch(em, sessionId, statementLineId, split.getId(), DifferenceCategory.MATCHED,
                                    "Reviewed bank item; journal date " + split.getTxn().getTxnDate() + "; bank clear date " + clearedDate);
                        em.createNativeQuery("update bank_reconciliation_match set prior_cleared = ?, prior_cleared_on = ? where session_id = ? and statement_line_id = ? and txn_split_id = ?")
                        .setParameter(1, split.isBankCleared()).setParameter(2, split.getBankClearedOn())
                        .setParameter(3, sessionId).setParameter(4, statementLineId).setParameter(5, split.getId()).executeUpdate();
                        split.setBankCleared(true);
                        split.setBankClearedOn(clearedDate);
                        split.setMatchedBankStatementLine(statement);
                    }
                    statement.setMatchedTransaction(anchor.getTxn());
                    statement.setStatus(BankStatementLine.Status.MATCHED);
                    statement.touchUpdatedAt();
                    recordAudit(em, row.company(), actor, "BANK_ITEM_MATCHED_AND_CLEARED", "BANK_RECONCILIATION_SESSION",
                                String.valueOf(sessionId),
                                "Matched one bank instrument across " + group.size() + " Fund allocations.", null,
                                "statement=" + statementLineId + "; amount=" + total + "; clearDate=" + clearedDate, "Explicit reviewed match");
                }
                em.flush();
                recalculateBalances(em, sessionId, bank, row.startDate(), row.endDate(), row.statementEndingBalance());
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

    public Snapshot unmatchBankItem(long sessionId, long statementLineId, long anchorSplitId)
    {
        String actor = bookkeepingActorForSession(sessionId, "unmatch bank item", "system");
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                SessionRow row = requireMutableSession(em, sessionId);
                CompanyBankAccount bank = configuredBankAccount(em, row.bankAccountId(), row.company());
                BankStatementLine statement = requireStatementLine(em, row, bank, statementLineId);
                TxnSplit anchor = requireLedgerSplit(em, row, bank, anchorSplitId);
                List<TxnSplit> group = bankItemSplits(em, anchor);
                for (TxnSplit split : group)
                {
                    if (!hasRelationshipMatch(em, sessionId, statementLineId, split.getId())
                            || statement.getMatchedTransaction() == null
                            || !Objects.equals(statement.getMatchedTransaction().getId(), split.getTxn().getId())
                            || split.getMatchedBankStatementLine() == null
                            || !Objects.equals(split.getMatchedBankStatementLine().getId(), statementLineId))
                    {
                        throw new IllegalStateException("The whole bank item must have one symmetric match in this session.");
                    }
                }
                for (TxnSplit split : group)
                {
                    Object[] previous = (Object[])
                                        em.createNativeQuery("select prior_cleared, prior_cleared_on from bank_reconciliation_match where session_id = ? and statement_line_id = ? and txn_split_id = ?")
                                        .setParameter(1, sessionId).setParameter(2, statementLineId).setParameter(3, split.getId()).getSingleResult();
                    split.setMatchedBankStatementLine(null);
                    if (previous[0] != null)
                    {
                        split.setBankCleared(Boolean.TRUE.equals(previous[0]));
                        split.setBankClearedOn(dateOrNull(previous[1]));
                    }
                }
                em.createNativeQuery("delete from bank_reconciliation_match where session_id = ? and statement_line_id = ?")
                .setParameter(1, sessionId).setParameter(2, statementLineId).executeUpdate();
                statement.setMatchedTransaction(null);
                statement.setStatus(statement.getAcceptedTransaction() == null
                                    ? BankStatementLine.Status.IMPORTED : BankStatementLine.Status.ACCEPTED);
                statement.touchUpdatedAt();
                recordAudit(em, row.company(), actor, "BANK_ITEM_UNMATCHED", "BANK_RECONCILIATION_SESSION", String.valueOf(sessionId),
                            "Removed whole bank-item match and restored prior clearing facts.", "statement=" + statementLineId, null,
                            "Explicit unmatch");
                em.flush();
                recalculateBalances(em, sessionId, bank, row.startDate(), row.endDate(), row.statementEndingBalance());
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

    private static List<TxnSplit> bankItemSplits(EntityManager em, TxnSplit anchor)
    {
        return em.createQuery("select s from TxnSplit s where s.txn = :txn and s.account = :account order by s.id",
                              TxnSplit.class)
               .setParameter("txn", anchor.getTxn()).setParameter("account", anchor.getAccount()).getResultList().stream()
               .filter(split -> Objects.equals(split.getPaymentMethod(), anchor.getPaymentMethod())
                       && Objects.equals(split.getPaymentReference(), anchor.getPaymentReference())
                       && Objects.equals(split.getPaymentIssuedOn(), anchor.getPaymentIssuedOn())).toList();
    }

    public Snapshot matchSelected(long sessionId, Long statementLineId, Long splitId, boolean overwriteCleared)
    {
        requireBookkeepingWriteForSession(sessionId, "match reconciliation lines");
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
        requireBookkeepingWriteForSession(sessionId, "unmatch reconciliation lines");
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
        requireBookkeepingWriteForSession(sessionId, "mark reconciliation ledger line cleared");
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
        requireBookkeepingWriteForSession(sessionId, "record reconciliation difference explanation");
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
        return saveStatement(sessionId, null, finalize);
    }

    /** Header save and progress/completion status share one serialized transaction. */
    public Snapshot saveStatement(long sessionId, BigDecimal endingBalance, boolean finalize)
    {
        if (endingBalance != null && endingBalance.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException("Statement balance requires whole cents.");
        String actor = bookkeepingActorForSession(sessionId,
                finalize ? "finalize reconciliation" : "save reconciliation", "system");
        Snapshot current = load(sessionId);
        if (current.status() == SessionStatus.FINALIZED)
        {
            if (finalize && (endingBalance == null || current.balances().statementEndingBalance() != null
                    && endingBalance.compareTo(current.balances().statementEndingBalance()) == 0))
            {
                return current;
            }
            throw finalizedMutation(sessionId);
        }
        if (finalize && endingBalance == null && !isBalanced(current))
        {
            throw new IllegalStateException(
                    "Reconciliation cannot be finalized until balances and differences are resolved.");
        }
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                SessionRow locked = requireMutableSession(em, sessionId);
                CompanyBankAccount account = configuredBankAccount(em, locked.bankAccountId(), locked.company());
                if (endingBalance != null)
                    em.createNativeQuery("update bank_reconciliation_session set statement_ending_balance = ? where id = ?")
                            .setParameter(1, endingBalance).setParameter(2, sessionId).executeUpdate();
                recalculateBalances(em, sessionId, account, locked.startDate(), locked.endDate(),
                        endingBalance == null ? locked.statementEndingBalance() : endingBalance);
                Snapshot fresh = snapshot(em, sessionId);
                if (finalize && !isBalanced(fresh))
                    throw new IllegalStateException("Reconciliation changed; resolve the current opening, matches and difference before completion.");
                SessionStatus currentStatus = finalize ? SessionStatus.FINALIZED
                        : isBalanced(fresh) ? SessionStatus.BALANCED : SessionStatus.UNRESOLVED;
                int updated = em.createNativeQuery("""
                        update bank_reconciliation_session
                           set status = ?, updated_at = CURRENT_TIMESTAMP
                         where id = ? and status <> 'FINALIZED'
                        """)
                        .setParameter(1, currentStatus.name())
                        .setParameter(2, sessionId)
                        .executeUpdate();
                if (updated != 1)
                {
                    throw finalizedMutation(sessionId);
                }
                recordAudit(em, locked.company(), actor, finalize ? "RECONCILIATION_COMPLETED" : "RECONCILIATION_SAVED",
                        "BANK_RECONCILIATION_SESSION", String.valueOf(sessionId),
                        "Saved reviewed bank reconciliation state.", "status="+locked.status()+"; statementEnding="+locked.statementEndingBalance(),
                        "status="+currentStatus+"; statementEnding="+fresh.balances().statementEndingBalance(),
                        finalize ? "Confirmed completion" : "Saved progress");
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

    private void requireBookkeepingWriteForCompany(String companyCode, String operation)
    {
        ServiceAuthorization.require(
                authorizationGuard,
                ApplicationPermission.BOOKKEEPING_WRITE,
                companyCode,
                operation);
    }

    private void requireBookkeepingWriteForSession(Long sessionId, String operation)
    {
        if (authorizationGuard == null)
        {
            return;
        }
        if (sessionId == null || sessionId <= 0)
        {
            ServiceAuthorization.require(
                    authorizationGuard,
                    ApplicationPermission.BOOKKEEPING_WRITE,
                    operation);
            return;
        }
        String companyCode;
        try (EntityManager em = jpa.em())
        {
            companyCode = session(em, sessionId).company().getCode();
        }
        ServiceAuthorization.require(
                authorizationGuard,
                ApplicationPermission.BOOKKEEPING_WRITE,
                companyCode,
                operation);
    }

    private String bookkeepingActorForSession(
            Long sessionId,
            String operation,
            String fallbackActor)
    {
        if (authorizationGuard == null)
        {
            return fallbackActor;
        }
        if (sessionId == null || sessionId <= 0)
        {
            return ServiceAuthorization.actor(
                    authorizationGuard,
                    ApplicationPermission.BOOKKEEPING_WRITE,
                    null,
                    operation,
                    fallbackActor);
        }
        String companyCode;
        try (EntityManager em = jpa.em())
        {
            companyCode = session(em, sessionId).company().getCode();
        }
        return ServiceAuthorization.actor(
                authorizationGuard,
                ApplicationPermission.BOOKKEEPING_WRITE,
                companyCode,
                operation,
                fallbackActor);
    }

    private Snapshot snapshot(EntityManager em, long sessionId)
    {
        SessionRow session = session(em, sessionId);
        CompanyBankAccount bankAccount = configuredBankAccount(em, session.bankAccountId(), session.company());
        List<TxnSplit> ledger = ledgerLines(em, session);
        List<BankStatementLine> statements = statementLines(em, bankAccount, session.startDate(), session.endDate());
        Map<Long, MatchRow> statementMatches = statementMatches(em, sessionId);
        Map<Long, MatchRow> splitMatches = splitMatches(em, sessionId);
        List<DifferenceView> differences = differences(ledger, statements, statementMatches, splitMatches, session);
        Object established = em.createNativeQuery("select opening_amount from bank_reconciliation_session where id = ?")
                .setParameter(1, sessionId).getSingleResult();
        if (established == null && session.status() != SessionStatus.FINALIZED)
        {
            differences = new ArrayList<>(differences);
            differences.add(new DifferenceView(DifferenceCategory.BEGINNING_BALANCE_DIFFERENCE,
                    session.startDate(), null, null, null, "Confirm the first statement opening and starting journal position."));
        }
        if (established != null)
        {
            differences = new ArrayList<>(differences);
            for (BankStatementLine statement : statements)
            {
                List<MatchRow> members = splitMatches.values().stream()
                        .filter(match -> Objects.equals(match.statementLineId(), statement.getId())).toList();
                if (members.isEmpty()) continue;
                List<TxnSplit> splits = members.stream().map(match -> em.find(TxnSplit.class,match.splitId())).toList();
                TxnSplit anchor = splits.get(0);
                Set<Long> expected = bankItemSplits(em,anchor).stream().map(TxnSplit::getId).collect(java.util.stream.Collectors.toSet());
                Set<Long> actual = splits.stream().map(TxnSplit::getId).collect(java.util.stream.Collectors.toSet());
                BigDecimal total = splits.stream().map(TxnSplit::getAmountSigned).reduce(BigDecimal.ZERO,BigDecimal::add);
                boolean valid = expected.equals(actual) && total.compareTo(statement.getAmount()) == 0
                        && members.stream().allMatch(match -> match.status() == DifferenceCategory.MATCHED)
                        && splits.stream().allMatch(split -> split.isBankCleared() && split.getBankClearedOn() != null
                            && !split.getBankClearedOn().isBefore(session.startDate()) && !split.getBankClearedOn().isAfter(session.endDate())
                            && split.getMatchedBankStatementLine() != null && Objects.equals(split.getMatchedBankStatementLine().getId(),statement.getId())
                            && Objects.equals(split.getAccount().getId(),bankAccount.getAccount().getId())
                            && Objects.equals(split.getTxn().getId(),anchor.getTxn().getId()))
                        && statement.getMatchedTransaction() != null && Objects.equals(statement.getMatchedTransaction().getId(),anchor.getTxn().getId());
                if (!valid) differences.add(new DifferenceView(DifferenceCategory.CLEARED_STATE_MISMATCH,
                        anchor.getTxn().getTxnDate(), statement.getPostedDate() == null ? statement.getTransactionDate() : statement.getPostedDate(),
                        total, statement.getAmount(), "Matched bank instrument is incomplete or its clearing evidence changed; review and unmatch before completion."));
            }
        }
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
        return snapshot.balances().statementEndingBalance() != null
                && snapshot.statementEntries().stream().allMatch(line -> line.matchedLedgerSplitId() != null)
                && compare(snapshot.balances().difference(), BigDecimal.ZERO) == 0
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
        boolean clearOnMatch = session.policy() == ClearedStatePolicy.OVERWRITE_LEDGER_CLEARED_STATE
                || overwriteCleared;
        if (!split.isBankCleared() && !clearOnMatch)
        {
            throw new IllegalStateException(
                    "The selected ledger line is not cleared. Policy " + session.policy().name()
                            + " preserves ledger cleared state; choose Mark Cleared before matching.");
        }
        insertMatch(em, sessionId, statementLineId, splitId, status, note);
        statement.setMatchedTransaction(split.getTxn());
        statement.setStatus(BankStatementLine.Status.MATCHED);
        statement.touchUpdatedAt();
        split.setMatchedBankStatementLine(statement);
        if (clearOnMatch)
        {
            split.setBankCleared(true);
            split.setBankClearedOn(statement.getPostedDate() == null ? statement.getTransactionDate() : statement.getPostedDate());
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
        return output.stream().filter(difference -> difference.category() != DifferenceCategory.UNMATCHED_LEDGER
                && difference.category() != DifferenceCategory.DATE_MISMATCH
                && difference.category() != DifferenceCategory.AMOUNT_MISMATCH
                && difference.category() != DifferenceCategory.DUPLICATE_POSSIBLE).toList();
    }

    private static StatementEntryView statementView(BankStatementLine line, MatchRow match)
    {
        return new StatementEntryView(
                line.getId(),
                line.getPostedDate() == null ? line.getTransactionDate() : line.getPostedDate(),
                firstNonBlank(line.getName(), line.getMemo(), line.getTransactionType(), "Statement line " + line.getId()),
                firstNonBlank(line.getReference(), line.getCheckNumber(), line.getSourceTransactionId(), ""),
                amount(line.getAmount()),
                line.getStatus() == BankStatementLine.Status.ERROR ? "Import error — review source" : line.getStatus().name(),
                match == null ? DifferenceCategory.UNMATCHED_STATEMENT : match.status(),
                match == null ? null : match.splitId(),
                match == null ? "" : match.note(),
                line.getBatch().getSourceFormat() == org.nonprofitbookkeeping.model.BankImportBatch.SourceFormat.OTHER
                        && "Manual reconciliation entry".equals(line.getBatch().getSourceName()));
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
                match == null ? DifferenceCategory.UNMATCHED_LEDGER : match.status(),
                split.getPaymentReference(),
                split.getTxn().getReversalOf() != null ? "Reversal of #" + split.getTxn().getReversalOf().getId()
                        : split.getTxn().getReplacementFor() != null ? "Replacement of #" + split.getTxn().getReplacementFor().getId()
                        : "REVERSED".equals(split.getTxn().getStatus()) ? "Cancelled original; linked reversal retained" : "");
    }

    private void recalculateBalances(EntityManager em, long sessionId, CompanyBankAccount bankAccount, LocalDate start, LocalDate end, BigDecimal statementEndingBalance)
    {
        Object supplied = em.createNativeQuery("select opening_amount from bank_reconciliation_session where id = ?")
                .setParameter(1, sessionId).getSingleResult();
        BigDecimal beginning = amount(supplied == null ? null : (BigDecimal) supplied);
        BigDecimal all = ledgerLinesThrough(em, bankAccount.getCompany(), bankAccount.getAccount(), end).stream()
                .map(TxnSplit::getAmountSigned).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Only this session's evidence advances its confirmed opening. A later session's
        // cleared flag/date cannot retroactively change this period's result.
        @SuppressWarnings("unchecked")
        List<Number> splitIds = em.createNativeQuery("select distinct txn_split_id from bank_reconciliation_match where session_id = ? and statement_line_id is not null and txn_split_id is not null and match_status = 'MATCHED'")
                .setParameter(1, sessionId).getResultList();
        BigDecimal cleared = beginning;
        for (Number id : splitIds)
        {
            TxnSplit split = em.find(TxnSplit.class, id.longValue());
            LocalDate clearDate = split.getBankClearedOn();
            if (split.isBankCleared() && clearDate != null && !clearDate.isBefore(start) && !clearDate.isAfter(end))
            {
                cleared = cleared.add(split.getAmountSigned());
            }
        }
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
            Map<String, TxnSplit> transactionLines,
            Map<String, Long> existingSessions,
            Map<String, Long> existingMatches)
    {
        Objects.requireNonNull(em, "em");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(sessionValues, "sessionValues");
        Objects.requireNonNull(matchValues, "matchValues");
        Objects.requireNonNull(existingSessions, "existingSessions");
        Objects.requireNonNull(existingMatches, "existingMatches");
        if (!em.getTransaction().isActive())
        {
            throw new IllegalStateException("Reconciliation import requires an active caller-owned transaction");
        }
        Map<String, Long> sessions = new LinkedHashMap<>(existingSessions);
        for (SessionImport value : sessionValues)
        {
            if (sessions.containsKey(value.externalId()))
            {
                throw new IllegalArgumentException(
                        "Duplicate reconciliation session identity: " + value.externalId());
            }
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

        Map<String, Long> matches = new LinkedHashMap<>(existingMatches);
        for (MatchImport value : matchValues)
        {
            if (matches.containsKey(value.externalId()))
            {
                throw new IllegalArgumentException(
                        "Duplicate reconciliation match identity: " + value.externalId());
            }
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

    private List<TxnSplit> ledgerLines(EntityManager em, SessionRow session)
    {
        CompanyBankAccount bankAccount = configuredBankAccount(em, session.bankAccountId(), session.company());
        Map<Long, MatchRow> matches = splitMatches(em, session.id());
        return ledgerLinesThrough(em, session.company(), bankAccount.getAccount(), session.endDate()).stream()
                .filter(split -> !split.getTxn().getTxnDate().isBefore(session.startDate())
                        || outstanding(split) || matches.containsKey(split.getId()))
                .toList();
    }

    private static boolean outstanding(TxnSplit split)
    {
        return !split.isBankCleared() || split.getMatchedBankStatementLine() == null;
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
                  and coalesce(l.postedDate,l.transactionDate) >= :start
                  and coalesce(l.postedDate,l.transactionDate) <= :end
                  and l.status not in (:excluded)
                order by l.transactionDate, l.id
                """, BankStatementLine.class)
                .setParameter("bankAccount", bankAccount)
                .setParameter("start", start)
                .setParameter("end", end)
                .setParameter("excluded", List.of(BankStatementLine.Status.DUPLICATE, BankStatementLine.Status.REJECTED))
                .getResultList();
    }

    private LocalDate reconciliationStartDate(EntityManager em, CompanyBankAccount bankAccount, LocalDate statementEnd)
    {
        if (bankAccount.getOpeningDate() != null && statementEnd.isBefore(bankAccount.getOpeningDate()))
        {
            throw new IllegalArgumentException("Statement ending date precedes the configured bank opening date.");
        }
        Object previous = em.createNativeQuery("""
                select max(statement_end_date) from bank_reconciliation_session
                 where company_id = ? and bank_account_id = ?
                   and status = 'FINALIZED' and statement_end_date < ?
                """)
                .setParameter(1, bankAccount.getCompany().getId())
                .setParameter(2, bankAccount.getId())
                .setParameter(3, statementEnd)
                .getSingleResult();
        LocalDate previousEnd = dateOrNull(previous);
        if (previousEnd != null)
        {
            return previousEnd.plusDays(1);
        }
        if (bankAccount.getOpeningDate() != null)
        {
            return bankAccount.getOpeningDate();
        }
        LocalDate start = statementEnd.withDayOfMonth(1);
        LocalDate firstLedger = em.createQuery("""
                select min(s.txn.txnDate) from TxnSplit s
                 where s.txn.company = :company and s.account = :account and s.txn.txnDate <= :end
                """, LocalDate.class)
                .setParameter("company", bankAccount.getCompany())
                .setParameter("account", bankAccount.getAccount())
                .setParameter("end", statementEnd)
                .getSingleResult();
        LocalDate firstStatement = em.createQuery("""
                select min(coalesce(l.postedDate,l.transactionDate)) from BankStatementLine l
                 where l.bankAccount = :bankAccount and coalesce(l.postedDate,l.transactionDate) <= :end
                   and l.status not in (:excluded)
                """, LocalDate.class)
                .setParameter("bankAccount", bankAccount)
                .setParameter("end", statementEnd)
                .setParameter("excluded", List.of(BankStatementLine.Status.ERROR, BankStatementLine.Status.DUPLICATE, BankStatementLine.Status.REJECTED))
                .getSingleResult();
        if (firstLedger != null && firstLedger.isBefore(start))
        {
            start = firstLedger;
        }
        if (firstStatement != null && firstStatement.isBefore(start))
        {
            start = firstStatement;
        }
        return start;
    }

    private SessionRow requireMutableSession(EntityManager em, long sessionId)
    {
        SessionRow session = session(em, sessionId);
        em.lock(session.company(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        session = session(em, sessionId);
        if (session.status() == SessionStatus.FINALIZED)
        {
            throw finalizedMutation(sessionId);
        }
        requireActive(configuredBankAccount(em, session.bankAccountId(), session.company()));
        return session;
    }

    private static void requireActive(CompanyBankAccount account)
    {
        if (!account.isActive()) throw new IllegalStateException("This configured bank account is inactive; its reconciliation history is read-only.");
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
        requireStatementDateInRange(session, statement.getPostedDate() == null ? statement.getTransactionDate() : statement.getPostedDate());
        if (statement.getStatus() == BankStatementLine.Status.ERROR
                || statement.getStatus() == BankStatementLine.Status.DUPLICATE
                || statement.getStatus() == BankStatementLine.Status.REJECTED)
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
        if (txnDate == null || txnDate.isAfter(session.endDate())
                || (txnDate.isBefore(session.startDate()) && !outstanding(split)
                    && !splitMatches(em, session.id()).containsKey(splitId)))
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
        if (bankAccount.getBank() == null || bankAccount.getAccount() == null)
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
        if (command.statementEndingBalance() != null && command.statementEndingBalance().stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException("Statement ending balance requires whole cents.");
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
