package org.nonprofitbookkeeping.interchange.sclx;

import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.ImportIssue;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;

import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Maps and ownership-validates the selected-company banking and reconciliation graph. */
final class SclxBankingSnapshotAssembler
{
    Result assemble(
            String companyCode,
            Company company,
            ChartOfAccounts activeChart,
            SclxBankingSnapshot banking,
            Set<Txn> includedTransactions,
            Map<Txn, String> exportedTransactionIds,
            List<TxnSplit> transactionLines,
            Map<TxnSplit, String> exportedLineIds)
    {
        Objects.requireNonNull(banking, "banking");

        Set<Bank> includedBanks = identitySet(banking.banks());
        Set<CompanyBankAccount> includedBankAccounts = identitySet(banking.bankAccounts());
        Set<BankImportBatch> includedBatches = identitySet(banking.importBatches());
        Set<BankStatementLine> includedStatementLines = identitySet(banking.statementLines());

        Map<UUID, CompanyBankAccount> bankAccountByPortableId = new HashMap<>();
        List<Map<String, Object>> banks = banking.banks().stream()
                .peek(bank -> requireBank(bank, company))
                .sorted(Comparator.comparing(bank -> bank.getPortableId().toString()))
                .map(bank -> SclxBankConfigurationExtension.bankEntry(
                        SclxPortableIdentity.bank(companyCode, bank.getPortableId().toString()),
                        bank.getName(),
                        bank.getRoutingNumber(),
                        bank.getAddress(),
                        bank.getWebsite(),
                        bank.getContactName(),
                        bank.getContactPhone(),
                        bank.getContactEmail(),
                        bank.getNotes(),
                        bank.isActive()))
                .toList();

        List<Map<String, Object>> bankAccounts = banking.bankAccounts().stream()
                .peek(account -> requireBankAccount(
                        account, company, activeChart, includedBanks))
                .sorted(Comparator.comparing(account -> account.getPortableId().toString()))
                .map(account ->
                {
                    bankAccountByPortableId.put(account.getPortableId(), account);
                    return SclxBankConfigurationExtension.accountEntry(
                            SclxPortableIdentity.bankAccount(
                                    companyCode, account.getPortableId().toString()),
                            account.getBank() == null ? null : SclxPortableIdentity.bank(
                                    companyCode, account.getBank().getPortableId().toString()),
                            account.getAccount() == null ? null : SclxPortableIdentity.account(
                                    companyCode, account.getAccount().getCode()),
                            account.getName(),
                            account.getNickname(),
                            account.getInstitutionName(),
                            account.getAccountType(),
                            account.getLastFour(),
                            account.getMaskedAccountNumber(),
                            account.getOpeningDate(),
                            account.getStatementImportFormat() == null
                                    ? null : account.getStatementImportFormat().name(),
                            account.getOfxBankId(),
                            account.getOfxAccountId(),
                            Objects.requireNonNull(account.getOpeningBalance(), "bank account opening balance"),
                            account.isActive(),
                            account.getNotes());
                })
                .toList();

        List<Map<String, Object>> importBatches = banking.importBatches().stream()
                .peek(batch -> requireBatch(batch, company, includedBankAccounts))
                .sorted(Comparator.comparing(batch -> batch.getPortableId().toString()))
                .map(batch -> SclxBankStatementFactsExtension.importBatchEntry(
                        SclxPortableIdentity.bankImportBatch(
                                companyCode, batch.getPortableId().toString()),
                        batch.getBankAccount() == null ? null : SclxPortableIdentity.bankAccount(
                                companyCode, batch.getBankAccount().getPortableId().toString()),
                        batch.getSourceName(),
                        batch.getSourceHash(),
                        Objects.requireNonNull(batch.getSourceFormat(), "bank import source format").name(),
                        batch.getSourceVariant(),
                        batch.getSourceVersion(),
                        batch.getSourceEncoding(),
                        batch.getSourceInstitutionId(),
                        batch.getSourceBankId(),
                        batch.getSourceAccountId(),
                        batch.getSourceAccountType(),
                        batch.getCurrency(),
                        batch.getStatementStartDate(),
                        batch.getStatementEndDate(),
                        batch.getLedgerBalance(),
                        batch.getAvailableBalance(),
                        batch.getAccountMatchStatus(),
                        batch.isAccountIdentityConfirmed(),
                        Objects.requireNonNull(batch.getStatus(), "bank import status").name(),
                        Objects.requireNonNull(batch.getImportedAt(), "bank import timestamp"),
                        batch.getCompletedAt(),
                        batch.getTotalLineCount(),
                        batch.getAcceptedLineCount(),
                        batch.getRejectedLineCount(),
                        batch.getIssueCount(),
                        batch.getNotes()))
                .toList();

        List<Map<String, Object>> statementLines = banking.statementLines().stream()
                .peek(line -> requireStatementLine(
                        line,
                        company,
                        includedBankAccounts,
                        includedBatches,
                        includedTransactions))
                .sorted(Comparator.comparing(line -> line.getPortableId().toString()))
                .map(line -> SclxBankStatementFactsExtension.statementLineEntry(
                        SclxPortableIdentity.bankStatementLine(
                                companyCode, line.getPortableId().toString()),
                        SclxPortableIdentity.bankImportBatch(
                                companyCode, line.getBatch().getPortableId().toString()),
                        line.getBankAccount() == null ? null : SclxPortableIdentity.bankAccount(
                                companyCode, line.getBankAccount().getPortableId().toString()),
                        line.getSourceRowNumber(),
                        line.getSourceTransactionId(),
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
                        line.getCorrectionAction(),
                        line.getCorrectedSourceTransactionId(),
                        Objects.requireNonNull(line.getStatus(), "bank statement line status").name(),
                        line.getDispositionNote(),
                        exportedTransactionId(line.getAcceptedTransaction(), exportedTransactionIds),
                        exportedTransactionId(line.getMatchedTransaction(), exportedTransactionIds)))
                .toList();

        List<Map<String, Object>> issues = banking.importIssues().stream()
                .peek(issue -> requireIssue(issue, includedBatches, includedStatementLines))
                .sorted(Comparator.comparing(issue -> issue.getPortableId().toString()))
                .map(issue -> SclxBankStatementFactsExtension.issueEntry(
                        SclxPortableIdentity.bankImportIssue(
                                companyCode, issue.getPortableId().toString()),
                        SclxPortableIdentity.bankImportBatch(
                                companyCode, issue.getBatch().getPortableId().toString()),
                        issue.getStatementLine() == null ? null : SclxPortableIdentity.bankStatementLine(
                                companyCode, issue.getStatementLine().getPortableId().toString()),
                        issue.getSourceRowNumber(),
                        Objects.requireNonNull(issue.getSeverity(), "bank import issue severity").name(),
                        issue.getCode(),
                        issue.getMessage(),
                        Objects.requireNonNull(issue.getCreatedAt(), "bank import issue createdAt")))
                .toList();

        Map<Long, String> exportedLineIdByLocalId = new HashMap<>();
        for (TxnSplit line : transactionLines)
        {
            if (line.getId() != null)
            {
                exportedLineIdByLocalId.put(
                        line.getId(),
                        Objects.requireNonNull(exportedLineIds.get(line), "exported transaction line identity"));
            }
        }

        List<Map<String, Object>> clearance = transactionLines.stream()
                .filter(line -> line.isBankCleared()
                        || line.getBankClearedOn() != null
                        || line.getMatchedBankStatementLine() != null)
                .peek(line -> requireClearance(line, includedStatementLines))
                .map(line -> SclxBankStatementFactsExtension.clearanceEntry(
                        Objects.requireNonNull(exportedLineIds.get(line), "exported transaction line identity"),
                        line.isBankCleared(),
                        line.getBankClearedOn(),
                        line.getMatchedBankStatementLine() == null ? null
                                : SclxPortableIdentity.bankStatementLine(
                                        companyCode,
                                        line.getMatchedBankStatementLine().getPortableId().toString())))
                .sorted(Comparator.comparing(entry -> (String) entry.get("lineId")))
                .toList();

        Set<UUID> sessionPortableIds = new java.util.HashSet<>();
        List<Map<String, Object>> sessions = banking.reconciliationSessions().stream()
                .peek(session -> requireSession(
                        session, bankAccountByPortableId, sessionPortableIds))
                .sorted(Comparator.comparing(session -> session.portableId().toString()))
                .map(session -> SclxReconciliationExtension.sessionEntry(
                        SclxPortableIdentity.reconciliationSession(
                                companyCode, session.portableId().toString()),
                        SclxPortableIdentity.bankAccount(
                                companyCode, session.bankAccountPortableId().toString()),
                        session.statementStartDate(),
                        session.statementEndDate(),
                        session.statementEndingBalance(),
                        session.mismatchPolicy(),
                        session.status(),
                        session.notes(),
                        session.beginningBalance(),
                        session.bookBalanceAll(),
                        session.bookBalanceCleared(),
                        session.differenceAmount(),
                        session.createdAt(),
                        session.updatedAt()))
                .toList();

        List<Map<String, Object>> matches = banking.reconciliationMatches().stream()
                .peek(match -> requireMatch(
                        match,
                        sessionPortableIds,
                        banking.statementLines(),
                        exportedLineIdByLocalId))
                .sorted(Comparator.comparing(match -> match.portableId().toString()))
                .map(match -> SclxReconciliationExtension.matchEntry(
                        SclxPortableIdentity.reconciliationMatch(
                                companyCode, match.portableId().toString()),
                        SclxPortableIdentity.reconciliationSession(
                                companyCode, match.sessionPortableId().toString()),
                        match.statementLinePortableId() == null ? null
                                : SclxPortableIdentity.bankStatementLine(
                                        companyCode, match.statementLinePortableId().toString()),
                        match.transactionSplitLocalId() == null ? null
                                : exportedLineIdByLocalId.get(match.transactionSplitLocalId()),
                        match.matchStatus(),
                        match.resolutionNote(),
                        match.createdAt(),
                        match.updatedAt()))
                .toList();

        return new Result(
                SclxBankConfigurationExtension.value(banks, bankAccounts),
                SclxBankStatementFactsExtension.value(
                        importBatches, statementLines, issues, clearance),
                SclxReconciliationExtension.value(sessions, matches));
    }

    private static void requireBank(Bank bank, Company company)
    {
        Objects.requireNonNull(bank, "bank");
        if (bank.getCompany() != company)
        {
            throw new IllegalArgumentException("bank is outside the selected company");
        }
        Objects.requireNonNull(bank.getPortableId(), "bank portableId");
        requireText(bank.getName(), "bank name");
    }

    private static void requireBankAccount(
            CompanyBankAccount account,
            Company company,
            ChartOfAccounts activeChart,
            Set<Bank> includedBanks)
    {
        Objects.requireNonNull(account, "bank account");
        if (account.getCompany() != company)
        {
            throw new IllegalArgumentException("bank account is outside the selected company");
        }
        Objects.requireNonNull(account.getPortableId(), "bank account portableId");
        requireText(account.getName(), "bank account name");
        if (account.getBank() != null)
        {
            requireBank(account.getBank(), company);
            if (!includedBanks.contains(account.getBank()))
            {
                throw new IllegalArgumentException("bank account references an omitted bank");
            }
        }
        Account ledgerAccount = account.getAccount();
        if (ledgerAccount != null && ledgerAccount.getChart() != activeChart)
        {
            throw new IllegalArgumentException(
                    "bank account ledger account is outside the selected active chart");
        }
    }

    private static void requireBatch(
            BankImportBatch batch,
            Company company,
            Set<CompanyBankAccount> includedBankAccounts)
    {
        Objects.requireNonNull(batch, "bank import batch");
        if (batch.getCompany() != company)
        {
            throw new IllegalArgumentException("bank import batch is outside the selected company");
        }
        Objects.requireNonNull(batch.getPortableId(), "bank import batch portableId");
        requireText(batch.getSourceName(), "bank import source name");
        if (batch.getBankAccount() != null && !includedBankAccounts.contains(batch.getBankAccount()))
        {
            throw new IllegalArgumentException("bank import batch references an omitted bank account");
        }
    }

    private static void requireStatementLine(
            BankStatementLine line,
            Company company,
            Set<CompanyBankAccount> includedBankAccounts,
            Set<BankImportBatch> includedBatches,
            Set<Txn> includedTransactions)
    {
        Objects.requireNonNull(line, "bank statement line");
        if (line.getCompany() != company)
        {
            throw new IllegalArgumentException("bank statement line is outside the selected company");
        }
        Objects.requireNonNull(line.getPortableId(), "bank statement line portableId");
        if (!includedBatches.contains(line.getBatch()))
        {
            throw new IllegalArgumentException("bank statement line references an omitted import batch");
        }
        if (line.getBankAccount() != null && !includedBankAccounts.contains(line.getBankAccount()))
        {
            throw new IllegalArgumentException("bank statement line references an omitted bank account");
        }
        requireTransaction(line.getAcceptedTransaction(), includedTransactions, "accepted transaction");
        requireTransaction(line.getMatchedTransaction(), includedTransactions, "matched transaction");
    }

    private static void requireTransaction(Txn transaction, Set<Txn> includedTransactions, String field)
    {
        if (transaction != null && !includedTransactions.contains(transaction))
        {
            throw new IllegalArgumentException("bank statement line " + field + " is outside the exported snapshot");
        }
    }

    private static void requireIssue(
            ImportIssue issue,
            Set<BankImportBatch> includedBatches,
            Set<BankStatementLine> includedStatementLines)
    {
        Objects.requireNonNull(issue, "bank import issue");
        Objects.requireNonNull(issue.getPortableId(), "bank import issue portableId");
        if (!includedBatches.contains(issue.getBatch()))
        {
            throw new IllegalArgumentException("bank import issue references an omitted import batch");
        }
        if (issue.getStatementLine() != null)
        {
            if (!includedStatementLines.contains(issue.getStatementLine()))
            {
                throw new IllegalArgumentException("bank import issue references an omitted statement line");
            }
            if (issue.getStatementLine().getBatch() != issue.getBatch())
            {
                throw new IllegalArgumentException("bank import issue statement line belongs to another batch");
            }
        }
    }

    private static void requireClearance(
            TxnSplit line,
            Set<BankStatementLine> includedStatementLines)
    {
        if (!line.isBankCleared())
        {
            throw new IllegalArgumentException(
                    "transaction line has clearance details without bank-cleared state");
        }
        if (line.getMatchedBankStatementLine() != null
                && !includedStatementLines.contains(line.getMatchedBankStatementLine()))
        {
            throw new IllegalArgumentException(
                    "transaction line references a statement line outside the exported snapshot");
        }
    }

    private static void requireSession(
            SclxBankingSnapshot.ReconciliationSession session,
            Map<UUID, CompanyBankAccount> bankAccountByPortableId,
            Set<UUID> sessionPortableIds)
    {
        Objects.requireNonNull(session, "reconciliation session");
        Objects.requireNonNull(session.portableId(), "reconciliation session portableId");
        if (!sessionPortableIds.add(session.portableId()))
        {
            throw new IllegalArgumentException(
                    "duplicate reconciliation session portable identity: " + session.portableId());
        }
        if (!bankAccountByPortableId.containsKey(session.bankAccountPortableId()))
        {
            throw new IllegalArgumentException("reconciliation session references an omitted bank account");
        }
    }

    private static void requireMatch(
            SclxBankingSnapshot.ReconciliationMatch match,
            Set<UUID> sessionPortableIds,
            List<BankStatementLine> statementLines,
            Map<Long, String> exportedLineIdByLocalId)
    {
        Objects.requireNonNull(match, "reconciliation match");
        Objects.requireNonNull(match.portableId(), "reconciliation match portableId");
        if (!sessionPortableIds.contains(match.sessionPortableId()))
        {
            throw new IllegalArgumentException("reconciliation match references an omitted session");
        }
        if (match.statementLinePortableId() != null
                && statementLines.stream().noneMatch(line -> match.statementLinePortableId().equals(line.getPortableId())))
        {
            throw new IllegalArgumentException("reconciliation match references an omitted statement line");
        }
        if (match.transactionSplitLocalId() != null
                && !exportedLineIdByLocalId.containsKey(match.transactionSplitLocalId()))
        {
            throw new IllegalArgumentException("reconciliation match references an omitted transaction line");
        }
        if (match.statementLinePortableId() == null && match.transactionSplitLocalId() == null)
        {
            throw new IllegalArgumentException(
                    "reconciliation match must reference a statement line or transaction line");
        }
    }

    private static String exportedTransactionId(
            Txn transaction,
            Map<Txn, String> exportedTransactionIds)
    {
        return transaction == null ? null : Objects.requireNonNull(
                exportedTransactionIds.get(transaction),
                "bank statement referenced transaction identity");
    }

    private static String requireText(String value, String field)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static <T> Set<T> identitySet(List<T> values)
    {
        Set<T> set = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        set.addAll(values);
        return set;
    }

    record Result(
            Map<String, Object> bankConfiguration,
            Map<String, Object> bankStatementFacts,
            Map<String, Object> reconciliation)
    {
    }
}
