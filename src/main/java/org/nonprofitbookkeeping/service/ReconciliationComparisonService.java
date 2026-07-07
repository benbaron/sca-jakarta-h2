package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.repository.ReconciliationRunRecord;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Compares configured bank-account statement facts with canonical ledger bank lines.
 */
public class ReconciliationComparisonService
{
    private final Jpa jpa;
    private final ReconciliationService reconciliationService;

    public ReconciliationComparisonService(Jpa jpa, ReconciliationService reconciliationService)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.reconciliationService = Objects.requireNonNull(reconciliationService, "reconciliationService");
    }

    public ReconciliationComparisonReport compare(ReconciliationComparisonCommand command)
    {
        if (command == null)
        {
            throw new IllegalArgumentException("Reconciliation comparison command is required.");
        }
        if (isBlank(command.companyCode()))
        {
            throw new IllegalArgumentException("Company code is required.");
        }
        if (command.bankAccountId() == null)
        {
            throw new IllegalArgumentException("Configured bank account is required.");
        }
        if (command.statementEndingOn() == null)
        {
            throw new IllegalArgumentException("Statement ending date is required.");
        }

        try (EntityManager em = jpa.em())
        {
            Company company = companyByCode(em, command.companyCode());
            CompanyBankAccount bankAccount = configuredBankAccount(em, command.bankAccountId(), company);
            Account ledgerAccount = bankAccount.getAccount();
            LocalDate fromDate = command.fromDate() == null
                    ? defaultFromDate(bankAccount, command.statementEndingOn())
                    : command.fromDate();
            if (fromDate.isAfter(command.statementEndingOn()))
            {
                throw new IllegalArgumentException("Reconciliation from date must be on or before the statement ending date.");
            }

            List<TxnSplit> allLedgerLines = ledgerLines(em, ledgerAccount, command.statementEndingOn());
            List<TxnSplit> periodLedgerLines = new ArrayList<>();
            BigDecimal opening = amount(bankAccount.getOpeningBalance());
            BigDecimal activity = BigDecimal.ZERO;
            BigDecimal cleared = amount(bankAccount.getOpeningBalance());
            for (TxnSplit split : allLedgerLines)
            {
                LocalDate txnDate = split.getTxn().getTxnDate();
                BigDecimal signed = amount(split.getAmountSigned());
                if (txnDate.isBefore(fromDate))
                {
                    opening = opening.add(signed);
                }
                else if (!txnDate.isAfter(command.statementEndingOn()))
                {
                    activity = activity.add(signed);
                    periodLedgerLines.add(split);
                }
                if (split.isBankCleared() && !txnDate.isAfter(command.statementEndingOn()))
                {
                    cleared = cleared.add(signed);
                }
            }

            List<BankStatementLine> statementLines = statementLines(em, bankAccount, fromDate, command.statementEndingOn());
            List<ReconciliationComparisonLine> lines = compareLines(periodLedgerLines, statementLines);
            java.util.UUID savedRunId = null;
            int unresolved = unresolvedCount(lines);
            if (command.saveUnresolvedReport() && unresolved > 0)
            {
                ReconciliationRunRecord run = reconciliationService.recordCompletedRun(
                        command.companyCode().trim(),
                        command.statementEndingOn(),
                        persistedRunFormat(bankAccount.getStatementImportFormat()),
                        statementLines.size(),
                        savedReportNotes(bankAccount, fromDate, command.statementEndingOn(), lines));
                savedRunId = run.id();
            }

            return new ReconciliationComparisonReport(
                    savedRunId,
                    command.companyCode().trim(),
                    bankAccount.getId(),
                    bankAccount.getName(),
                    fromDate,
                    command.statementEndingOn(),
                    opening,
                    activity,
                    opening.add(activity),
                    cleared,
                    periodLedgerLines.size(),
                    statementLines.size(),
                    matchedCount(lines),
                    List.copyOf(lines));
        }
    }

    private static List<ReconciliationComparisonLine> compareLines(List<TxnSplit> ledgerLines,
                                                                   List<BankStatementLine> statementLines)
    {
        List<ReconciliationComparisonLine> output = new ArrayList<>();
        Set<Long> matchedSplitIds = new HashSet<>();
        Set<Long> matchedStatementLineIds = new HashSet<>();

        for (TxnSplit split : ledgerLines)
        {
            BankStatementLine match = exactMatch(split, statementLines, matchedStatementLineIds);
            if (match != null)
            {
                matchedSplitIds.add(split.getId());
                matchedStatementLineIds.add(match.getId());
                output.add(new ReconciliationComparisonLine(
                        ReconciliationComparisonLine.Kind.MATCHED,
                        split.getTxn().getId(),
                        split.getId(),
                        match.getId(),
                        split.getTxn().getTxnDate(),
                        match.getTransactionDate(),
                        amount(split.getAmountSigned()),
                        amount(match.getAmount()),
                        "Ledger line and statement line match by date and amount."));
                if (!split.isBankCleared())
                {
                    output.add(new ReconciliationComparisonLine(
                            ReconciliationComparisonLine.Kind.CLEARED_STATE_MISMATCH,
                            split.getTxn().getId(),
                            split.getId(),
                            match.getId(),
                            split.getTxn().getTxnDate(),
                            match.getTransactionDate(),
                            amount(split.getAmountSigned()),
                            amount(match.getAmount()),
                            "Statement line matches the ledger amount/date, but the ledger bank line is not marked cleared."));
                }
            }
        }

        for (TxnSplit split : ledgerLines)
        {
            if (!matchedSplitIds.contains(split.getId()))
            {
                BankStatementLine sameDate = sameDateCandidate(split, statementLines, matchedStatementLineIds);
                BankStatementLine sameAmount = sameAmountCandidate(split, statementLines, matchedStatementLineIds);
                if (sameDate != null)
                {
                    output.add(new ReconciliationComparisonLine(
                            ReconciliationComparisonLine.Kind.AMOUNT_MISMATCH,
                            split.getTxn().getId(),
                            split.getId(),
                            sameDate.getId(),
                            split.getTxn().getTxnDate(),
                            sameDate.getTransactionDate(),
                            amount(split.getAmountSigned()),
                            amount(sameDate.getAmount()),
                            "Ledger and statement dates match, but amounts differ."));
                }
                else if (sameAmount != null)
                {
                    output.add(new ReconciliationComparisonLine(
                            ReconciliationComparisonLine.Kind.DATE_MISMATCH,
                            split.getTxn().getId(),
                            split.getId(),
                            sameAmount.getId(),
                            split.getTxn().getTxnDate(),
                            sameAmount.getTransactionDate(),
                            amount(split.getAmountSigned()),
                            amount(sameAmount.getAmount()),
                            "Ledger and statement amounts match, but dates differ."));
                }
                else
                {
                    output.add(new ReconciliationComparisonLine(
                            ReconciliationComparisonLine.Kind.UNMATCHED_LEDGER,
                            split.getTxn().getId(),
                            split.getId(),
                            null,
                            split.getTxn().getTxnDate(),
                            null,
                            amount(split.getAmountSigned()),
                            null,
                            "Ledger bank line has no matching statement line."));
                }
            }
        }

        for (BankStatementLine line : statementLines)
        {
            if (!matchedStatementLineIds.contains(line.getId()))
            {
                output.add(new ReconciliationComparisonLine(
                        ReconciliationComparisonLine.Kind.UNMATCHED_STATEMENT,
                        null,
                        null,
                        line.getId(),
                        null,
                        line.getTransactionDate(),
                        null,
                        amount(line.getAmount()),
                        "Statement line has no matching ledger bank line."));
            }
        }
        return output;
    }

    private static BankStatementLine exactMatch(TxnSplit split,
                                                List<BankStatementLine> statementLines,
                                                Set<Long> used)
    {
        for (BankStatementLine line : statementLines)
        {
            if (!used.contains(line.getId())
                    && Objects.equals(split.getTxn().getTxnDate(), line.getTransactionDate())
                    && amount(split.getAmountSigned()).compareTo(amount(line.getAmount())) == 0)
            {
                return line;
            }
        }
        return null;
    }

    private static BankStatementLine sameDateCandidate(TxnSplit split,
                                                       List<BankStatementLine> statementLines,
                                                       Set<Long> used)
    {
        for (BankStatementLine line : statementLines)
        {
            if (!used.contains(line.getId())
                    && Objects.equals(split.getTxn().getTxnDate(), line.getTransactionDate()))
            {
                return line;
            }
        }
        return null;
    }

    private static BankStatementLine sameAmountCandidate(TxnSplit split,
                                                         List<BankStatementLine> statementLines,
                                                         Set<Long> used)
    {
        for (BankStatementLine line : statementLines)
        {
            if (!used.contains(line.getId())
                    && amount(split.getAmountSigned()).compareTo(amount(line.getAmount())) == 0)
            {
                return line;
            }
        }
        return null;
    }

    private static List<TxnSplit> ledgerLines(EntityManager em, Account account, LocalDate toDate)
    {
        return em.createQuery("""
                        select s
                        from TxnSplit s
                        join fetch s.txn t
                        join fetch s.account a
                        where a = :account
                          and t.txnDate <= :toDate
                        order by t.txnDate, s.id
                        """, TxnSplit.class)
                .setParameter("account", account)
                .setParameter("toDate", toDate)
                .getResultList();
    }

    private static List<BankStatementLine> statementLines(EntityManager em,
                                                          CompanyBankAccount bankAccount,
                                                          LocalDate fromDate,
                                                          LocalDate toDate)
    {
        return em.createQuery("""
                        select l
                        from BankStatementLine l
                        where l.bankAccount = :bankAccount
                          and l.transactionDate >= :fromDate
                          and l.transactionDate <= :toDate
                          and l.status not in (:excluded)
                        order by l.transactionDate, l.id
                        """, BankStatementLine.class)
                .setParameter("bankAccount", bankAccount)
                .setParameter("fromDate", fromDate)
                .setParameter("toDate", toDate)
                .setParameter("excluded", List.of(BankStatementLine.Status.ERROR, BankStatementLine.Status.DUPLICATE))
                .getResultList();
    }

    private static CompanyBankAccount configuredBankAccount(EntityManager em, Long id, Company company)
    {
        CompanyBankAccount account = em.createQuery("""
                        select cba
                        from CompanyBankAccount cba
                        left join fetch cba.bank
                        left join fetch cba.account
                        where cba.id = :id
                          and cba.company = :company
                        """, CompanyBankAccount.class)
                .setParameter("id", id)
                .setParameter("company", company)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Configured bank account does not exist for company: " + id + "."));
        if (!account.isActive() || account.getBank() == null || account.getAccount() == null)
        {
            throw new IllegalArgumentException("Reconciliation requires an active configured bank account linked to a Bank and chart account.");
        }
        BankConfigurationService.validateBankLedgerAccount(account.getAccount());
        return account;
    }

    private static Company companyByCode(EntityManager em, String code)
    {
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

    private static LocalDate defaultFromDate(CompanyBankAccount bankAccount, LocalDate statementEndingOn)
    {
        if (bankAccount.getOpeningDate() != null)
        {
            return bankAccount.getOpeningDate();
        }
        return statementEndingOn.withDayOfMonth(1);
    }

    private static BankingDataFormat persistedRunFormat(BankingDataFormat format)
    {
        if (format == BankingDataFormat.QFX)
        {
            return BankingDataFormat.QFX;
        }
        return BankingDataFormat.OFX;
    }

    private static String savedReportNotes(CompanyBankAccount bankAccount,
                                           LocalDate fromDate,
                                           LocalDate statementEndingOn,
                                           List<ReconciliationComparisonLine> lines)
    {
        return "UNRESOLVED reconciliation report; bankAccount=" + bankAccount.getName()
                + "; from=" + fromDate
                + "; through=" + statementEndingOn
                + "; matched=" + matchedCount(lines)
                + "; unresolved=" + unresolvedCount(lines)
                + "; unmatchedLedger=" + count(lines, ReconciliationComparisonLine.Kind.UNMATCHED_LEDGER)
                + "; unmatchedStatement=" + count(lines, ReconciliationComparisonLine.Kind.UNMATCHED_STATEMENT)
                + "; amountMismatch=" + count(lines, ReconciliationComparisonLine.Kind.AMOUNT_MISMATCH)
                + "; dateMismatch=" + count(lines, ReconciliationComparisonLine.Kind.DATE_MISMATCH)
                + "; clearedStateMismatch=" + count(lines, ReconciliationComparisonLine.Kind.CLEARED_STATE_MISMATCH);
    }

    private static int matchedCount(List<ReconciliationComparisonLine> lines)
    {
        return count(lines, ReconciliationComparisonLine.Kind.MATCHED);
    }

    private static int unresolvedCount(List<ReconciliationComparisonLine> lines)
    {
        int count = 0;
        for (ReconciliationComparisonLine line : lines)
        {
            if (line.kind() != ReconciliationComparisonLine.Kind.MATCHED)
            {
                count++;
            }
        }
        return count;
    }

    private static int count(List<ReconciliationComparisonLine> lines, ReconciliationComparisonLine.Kind kind)
    {
        int count = 0;
        for (ReconciliationComparisonLine line : lines)
        {
            if (line.kind() == kind)
            {
                count++;
            }
        }
        return count;
    }

    private static BigDecimal amount(BigDecimal amount)
    {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.isBlank();
    }
}
