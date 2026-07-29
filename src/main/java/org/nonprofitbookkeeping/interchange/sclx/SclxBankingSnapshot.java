package org.nonprofitbookkeeping.interchange.sclx;

import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.ImportIssue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Bounded selected-company banking and reconciliation graph used by SCLX assembly. */
record SclxBankingSnapshot(
        List<Bank> banks,
        List<CompanyBankAccount> bankAccounts,
        List<BankImportBatch> importBatches,
        List<BankStatementLine> statementLines,
        List<ImportIssue> importIssues,
        List<ReconciliationSession> reconciliationSessions,
        List<ReconciliationMatch> reconciliationMatches)
{
    SclxBankingSnapshot
    {
        banks = List.copyOf(banks);
        bankAccounts = List.copyOf(bankAccounts);
        importBatches = List.copyOf(importBatches);
        statementLines = List.copyOf(statementLines);
        importIssues = List.copyOf(importIssues);
        reconciliationSessions = List.copyOf(reconciliationSessions);
        reconciliationMatches = List.copyOf(reconciliationMatches);
    }

    static SclxBankingSnapshot empty()
    {
        return new SclxBankingSnapshot(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    record ReconciliationSession(
            UUID portableId,
            UUID bankAccountPortableId,
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

    record ReconciliationMatch(
            UUID portableId,
            UUID sessionPortableId,
            UUID statementLinePortableId,
            Long transactionSplitLocalId,
            String matchStatus,
            String resolutionNote,
            Instant createdAt,
            Instant updatedAt)
    {
    }
}
