package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public enum StatementSource { MANUAL, CSV, OFX, QIF }
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
    public record ManualStatementLineCommand(long sessionId,
                                             LocalDate date,
                                             BigDecimal amount,
                                             String description,
                                             String reference) { }
    public record ImportStatementCommand(long sessionId,
                                         StatementSource source,
                                         String sourceName,
                                         String sourceText) { }
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

    private static final Pattern OFX_TRANSACTION = Pattern.compile("<STMTTRN>(.*?)</STMTTRN>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final Jpa jpa;

    public BankReconciliationWorkspaceService(Jpa jpa)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
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

    public Snapshot load(long sessionId)
    {
        recalculateBalances(sessionId);
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
                SessionRow session = session(em, command.sessionId());
                CompanyBankAccount bankAccount = configuredBankAccount(em, session.bankAccountId(), session.company());
                persistStatementLines(em,
                        session.company(),
                        bankAccount,
                        BankImportBatch.SourceFormat.OTHER,
                        "Manual reconciliation entry",
                        List.of(new ParsedStatementLine(command.date(), command.amount(), command.description(), command.reference())));
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

    public Snapshot importStatementText(ImportStatementCommand command)
    {
        if (command == null || command.source() == null || isBlank(command.sourceText()))
        {
            throw new IllegalArgumentException("Statement import source and text are required.");
        }
        List<ParsedStatementLine> parsed = parse(command.source(), command.sourceText());
        if (parsed.isEmpty())
        {
            throw new IllegalArgumentException("No statement lines could be parsed from the selected source.");
        }
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                SessionRow session = session(em, command.sessionId());
                CompanyBankAccount bankAccount = configuredBankAccount(em, session.bankAccountId(), session.company());
                persistStatementLines(em,
                        session.company(),
                        bankAccount,
                        sourceFormat(command.source()),
                        isBlank(command.sourceName()) ? command.source().name() + " statement import" : command.sourceName(),
                        parsed);
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
        if (statementLineId == null && splitId == null)
        {
            throw new IllegalArgumentException("Select a statement entry or ledger line to unmatch.");
        }
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                if (statementLineId != null)
                {
                    BankStatementLine line = em.find(BankStatementLine.class, statementLineId);
                    if (line != null)
                    {
                        line.setMatchedTransaction(null);
                        line.setStatus(BankStatementLine.Status.IMPORTED);
                        line.touchUpdatedAt();
                    }
                    em.createNativeQuery("delete from bank_reconciliation_match where session_id = ? and statement_line_id = ?")
                            .setParameter(1, sessionId)
                            .setParameter(2, statementLineId)
                            .executeUpdate();
                }
                if (splitId != null)
                {
                    TxnSplit split = em.find(TxnSplit.class, splitId);
                    if (split != null)
                    {
                        split.setMatchedBankStatementLine(null);
                    }
                    em.createNativeQuery("delete from bank_reconciliation_match where session_id = ? and txn_split_id = ?")
                            .setParameter(1, sessionId)
                            .setParameter(2, splitId)
                            .executeUpdate();
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
                SessionRow session = session(em, sessionId);
                TxnSplit split = required(em, TxnSplit.class, splitId, "Ledger line");
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

    public Snapshot resolveDifference(long sessionId, Long statementLineId, Long splitId, String note)
    {
        if (statementLineId == null && splitId == null)
        {
            throw new IllegalArgumentException("Select a statement entry or ledger line to resolve.");
        }
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                insertMatch(em, sessionId, statementLineId, splitId, DifferenceCategory.RESOLVED, isBlank(note) ? "Resolved by user." : note);
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

    public Snapshot save(long sessionId, boolean finalize)
    {
        Snapshot current = load(sessionId);
        SessionStatus status = finalize && isBalanced(current)
                ? SessionStatus.FINALIZED
                : (isBalanced(current) ? SessionStatus.BALANCED : SessionStatus.UNRESOLVED);
        try (EntityManager em = jpa.em())
        {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try
            {
                em.createNativeQuery("update bank_reconciliation_session set status = ?, updated_at = CURRENT_TIMESTAMP where id = ?")
                        .setParameter(1, status.name())
                        .setParameter(2, sessionId)
                        .executeUpdate();
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
        SessionRow session = session(em, sessionId);
        BankStatementLine statement = required(em, BankStatementLine.class, statementLineId, "Statement line");
        TxnSplit split = required(em, TxnSplit.class, splitId, "Ledger line");
        CompanyBankAccount bankAccount = configuredBankAccount(em, session.bankAccountId(), session.company());
        CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
        if (statement.getCompany() == null || !Objects.equals(statement.getCompany().getId(), session.company().getId()))
        {
            throw new IllegalArgumentException("Statement line belongs to a different company.");
        }
        ownership.ensureOwnedBy(em, session.company(), split.getTxn(), "Ledger transaction");
        ownership.ensureOwnedBy(em, session.company(), split.getAccount(), "Ledger line account");
        ownership.ensureOwnedBy(em, session.company(), split.getFund(), "Ledger line fund");
        ownership.ensureOwnedBy(em, session.company(), split.getBudgetCategory(), "Ledger line budget category");
        ownership.ensureOwnedBy(em, session.company(), split.getActivity(), "Ledger line activity");
        ownership.ensureOwnedBy(em, session.company(), split.getMerchant(), "Ledger line merchant");
        if (statement.getBankAccount() == null || !Objects.equals(statement.getBankAccount().getId(), bankAccount.getId()))
        {
            throw new IllegalArgumentException("Statement line does not belong to the reconciliation bank account.");
        }
        if (split.getAccount() == null || !Objects.equals(split.getAccount().getId(), bankAccount.getAccount().getId()))
        {
            throw new IllegalArgumentException("Ledger line does not belong to the reconciliation bank account.");
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

    private void persistStatementLines(EntityManager em, Company company, CompanyBankAccount bankAccount, BankImportBatch.SourceFormat format, String sourceName, List<ParsedStatementLine> lines)
    {
        BankImportBatch batch = new BankImportBatch();
        batch.setCompany(company);
        batch.setBankAccount(bankAccount);
        batch.setSourceFormat(format);
        batch.setSourceName(sourceName);
        batch.setTotalLineCount(lines.size());
        em.persist(batch);
        int row = 1;
        for (ParsedStatementLine parsed : lines)
        {
            BankStatementLine line = new BankStatementLine();
            line.setBatch(batch);
            line.setCompany(company);
            line.setBankAccount(bankAccount);
            line.setSourceRowNumber(row);
            line.setSourceTransactionId(firstNonBlank(parsed.reference(), sourceName + "-" + row));
            line.setDeterministicFingerprint(UUID.nameUUIDFromBytes((sourceName + row + parsed.date() + parsed.amount() + parsed.description()).getBytes(StandardCharsets.UTF_8)).toString());
            line.setStatementAccountIdentifier(bankAccount.getMaskedAccountNumber());
            line.setTransactionDate(parsed.date());
            line.setPostedDate(parsed.date());
            line.setAmount(amount(parsed.amount()));
            line.setName(parsed.description());
            line.setReference(parsed.reference());
            line.setStatus(BankStatementLine.Status.IMPORTED);
            em.persist(line);
            row++;
        }
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
        return new HashSet<>(statementMatches(em, sessionId).keySet());
    }

    private Set<Long> matchedSplitIds(EntityManager em, long sessionId)
    {
        return new HashSet<>(splitMatches(em, sessionId).keySet());
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

    private static List<ParsedStatementLine> parse(StatementSource source, String text)
    {
        return switch (source)
        {
            case MANUAL -> throw new IllegalArgumentException("Manual lines are added through the manual entry form.");
            case CSV -> parseCsv(text);
            case OFX -> parseOfx(text);
            case QIF -> parseQif(text);
        };
    }

    private static List<ParsedStatementLine> parseCsv(String text)
    {
        List<String> lines = text.lines().filter(line -> !line.isBlank()).toList();
        if (lines.isEmpty())
        {
            return List.of();
        }
        String[] header = splitCsv(lines.get(0));
        boolean hasHeader = contains(header, "date") && contains(header, "amount");
        Map<String, Integer> index = hasHeader ? headerIndex(header) : Map.of();
        List<ParsedStatementLine> output = new ArrayList<>();
        for (int i = hasHeader ? 1 : 0; i < lines.size(); i++)
        {
            String[] cells = splitCsv(lines.get(i));
            LocalDate date = parseDate(cell(cells, hasHeader ? index.getOrDefault("date", 0) : 0));
            BigDecimal amount = parseAmount(cell(cells, hasHeader ? index.getOrDefault("amount", 1) : 1));
            String description = cell(cells, hasHeader ? index.getOrDefault("description", index.getOrDefault("memo", 2)) : 2);
            String reference = cell(cells, hasHeader ? index.getOrDefault("reference", index.getOrDefault("check", 3)) : 3);
            output.add(new ParsedStatementLine(date, amount, description, reference));
        }
        return output;
    }

    private static List<ParsedStatementLine> parseOfx(String text)
    {
        List<ParsedStatementLine> output = new ArrayList<>();
        Matcher matcher = OFX_TRANSACTION.matcher(text);
        while (matcher.find())
        {
            String block = matcher.group(1);
            output.add(new ParsedStatementLine(
                    parseOfxDate(tag(block, "DTPOSTED")),
                    parseAmount(tag(block, "TRNAMT")),
                    firstNonBlank(tag(block, "NAME"), tag(block, "MEMO"), tag(block, "TRNTYPE")),
                    firstNonBlank(tag(block, "FITID"), tag(block, "CHECKNUM"))));
        }
        return output;
    }

    private static List<ParsedStatementLine> parseQif(String text)
    {
        List<ParsedStatementLine> output = new ArrayList<>();
        LocalDate date = null;
        BigDecimal amount = null;
        String payee = "";
        String memo = "";
        String reference = "";
        for (String raw : text.split("\\R"))
        {
            String line = raw.trim();
            if (line.equals("^"))
            {
                if (date != null && amount != null)
                {
                    output.add(new ParsedStatementLine(date, amount, firstNonBlank(payee, memo), reference));
                }
                date = null;
                amount = null;
                payee = "";
                memo = "";
                reference = "";
            }
            else if (line.startsWith("D"))
            {
                date = parseDate(line.substring(1));
            }
            else if (line.startsWith("T"))
            {
                amount = parseAmount(line.substring(1));
            }
            else if (line.startsWith("P"))
            {
                payee = line.substring(1);
            }
            else if (line.startsWith("M"))
            {
                memo = line.substring(1);
            }
            else if (line.startsWith("N"))
            {
                reference = line.substring(1);
            }
        }
        return output;
    }

    private static BankImportBatch.SourceFormat sourceFormat(StatementSource source)
    {
        return switch (source)
        {
            case CSV -> BankImportBatch.SourceFormat.CSV;
            case OFX -> BankImportBatch.SourceFormat.OFX;
            case QIF -> BankImportBatch.SourceFormat.QIF;
            case MANUAL -> BankImportBatch.SourceFormat.OTHER;
        };
    }

    private static String[] splitCsv(String line)
    {
        return line.split(",", -1);
    }

    private static boolean contains(String[] values, String text)
    {
        for (String value : values)
        {
            if (value.trim().equalsIgnoreCase(text))
            {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Integer> headerIndex(String[] header)
    {
        Map<String, Integer> output = new HashMap<>();
        for (int i = 0; i < header.length; i++)
        {
            output.put(header[i].trim().toLowerCase(Locale.ROOT), i);
        }
        return output;
    }

    private static String cell(String[] cells, int index)
    {
        return index >= 0 && index < cells.length ? cells[index].trim().replaceAll("^\"|\"$", "") : "";
    }

    private static String tag(String block, String tag)
    {
        Matcher matcher = Pattern.compile("<" + tag + ">(.*?)(?=<[A-Z/]|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(block);
        return matcher.find() ? matcher.group(1).replaceAll("<.*", "").trim() : "";
    }

    private static LocalDate parseOfxDate(String raw)
    {
        String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (digits.length() < 8)
        {
            throw new IllegalArgumentException("OFX date is missing or invalid.");
        }
        return LocalDate.parse(digits.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static LocalDate parseDate(String raw)
    {
        if (isBlank(raw))
        {
            throw new IllegalArgumentException("Statement date is required.");
        }
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("M/d/uuuu"),
                DateTimeFormatter.ofPattern("M-d-uuuu"),
                DateTimeFormatter.ofPattern("MM/dd/uuuu"),
                DateTimeFormatter.ofPattern("MM-dd-uuuu")))
        {
            try
            {
                return LocalDate.parse(raw.trim(), formatter);
            }
            catch (DateTimeParseException ignored)
            {
                // Try the next common bank statement date format.
            }
        }
        throw new IllegalArgumentException("Statement date is invalid: " + raw);
    }

    private static BigDecimal parseAmount(String raw)
    {
        if (isBlank(raw))
        {
            throw new IllegalArgumentException("Statement amount is required.");
        }
        return amount(new BigDecimal(raw.trim().replace("$", "").replace(",", "")));
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

    private record ParsedStatementLine(LocalDate date, BigDecimal amount, String description, String reference) { }
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
