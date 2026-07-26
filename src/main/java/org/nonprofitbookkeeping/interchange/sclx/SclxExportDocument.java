package org.nonprofitbookkeeping.interchange.sclx;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, JPA-independent DTO graph for deterministic SCLX 1.3 export. */
public record SclxExportDocument(
        String format,
        String version,
        Instant exportedAt,
        Organization organization,
        List<Account> chartOfAccounts,
        List<Fund> funds,
        List<Budget> budgets,
        List<Transaction> transactions,
        Extensions extensions)
{
    public SclxExportDocument
    {
        if (!"SCLX".equals(format))
        {
            throw new IllegalArgumentException("format must be SCLX");
        }
        if (!SclxVersion.writerVersion().externalValue().equals(version))
        {
            throw new IllegalArgumentException("version must be the governed writer version");
        }
        Objects.requireNonNull(exportedAt, "exportedAt");
        Objects.requireNonNull(organization, "organization");
        chartOfAccounts = List.copyOf(Objects.requireNonNull(chartOfAccounts, "chartOfAccounts"));
        funds = List.copyOf(Objects.requireNonNull(funds, "funds"));
        budgets = List.copyOf(Objects.requireNonNull(budgets, "budgets"));
        transactions = List.copyOf(Objects.requireNonNull(transactions, "transactions"));
        Objects.requireNonNull(extensions, "extensions");
    }

    public static SclxExportDocument version13(
            Instant exportedAt,
            Organization organization,
            List<Account> accounts,
            List<Fund> funds,
            List<Budget> budgets,
            List<Transaction> transactions,
            Extensions extensions)
    {
        return new SclxExportDocument(
                "SCLX",
                SclxVersion.writerVersion().externalValue(),
                exportedAt,
                organization,
                accounts,
                funds,
                budgets,
                transactions,
                extensions);
    }

    public record Organization(
            String organizationId,
            String code,
            String name,
            String baseCurrency,
            LocalDate fiscalYearStart)
    {
        public Organization
        {
            requireText(organizationId, "organizationId");
            requireText(code, "code");
            requireText(name, "name");
            requireText(baseCurrency, "baseCurrency");
            Objects.requireNonNull(fiscalYearStart, "fiscalYearStart");
        }
    }

    public record Account(
            String accountId,
            String code,
            String name,
            String type,
            String subtype,
            String increaseSide,
            String parentAccountId,
            String currency,
            BigDecimal openingBalance,
            boolean posting,
            boolean active)
    {
        public Account
        {
            requireText(accountId, "accountId");
            requireText(code, "code");
            requireText(name, "name");
            requireText(type, "type");
            requireText(increaseSide, "increaseSide");
            requireText(currency, "currency");
            Objects.requireNonNull(openingBalance, "openingBalance");
        }
    }

    public record Fund(
            String fundId,
            String code,
            String name,
            String type,
            String parentFundId,
            boolean active,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String restrictionText)
    {
        public Fund
        {
            requireText(fundId, "fundId");
            requireText(code, "code");
            requireText(name, "name");
            requireText(type, "type");
        }
    }

    public record Budget(
            String budgetId,
            String name,
            int fiscalYear,
            String version,
            boolean active,
            List<BudgetLine> lines)
    {
        public Budget
        {
            requireText(budgetId, "budgetId");
            requireText(name, "name");
            requireText(version, "version");
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        }
    }

    public record BudgetLine(
            String lineId,
            String accountId,
            String fundId,
            String categoryCode,
            BigDecimal amount)
    {
        public BudgetLine
        {
            requireText(lineId, "lineId");
            requireText(categoryCode, "categoryCode");
            Objects.requireNonNull(amount, "amount");
        }
    }

    public record Transaction(
            String transactionId,
            LocalDate transactionDate,
            String description,
            String reference,
            String correctionOfTransactionId,
            List<TransactionLine> lines)
    {
        public Transaction
        {
            requireText(transactionId, "transactionId");
            Objects.requireNonNull(transactionDate, "transactionDate");
            requireText(description, "description");
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        }
    }

    public record TransactionLine(
            String lineId,
            String accountId,
            String fundId,
            String activityId,
            String counterpartyId,
            BigDecimal debit,
            BigDecimal credit,
            String memo)
    {
        public TransactionLine
        {
            requireText(lineId, "lineId");
            requireText(accountId, "accountId");
            Objects.requireNonNull(debit, "debit");
            Objects.requireNonNull(credit, "credit");
            boolean hasDebit = debit.signum() != 0;
            boolean hasCredit = credit.signum() != 0;
            if (hasDebit == hasCredit)
            {
                throw new IllegalArgumentException("transaction line must contain either debit or credit, but not both");
            }
        }
    }

    public record Extensions(int version, Map<String, Object> scaJakartaH2)
    {
        public Extensions
        {
            if (version < 1)
            {
                throw new IllegalArgumentException("extension version must be positive");
            }
            scaJakartaH2 = Map.copyOf(Objects.requireNonNull(scaJakartaH2, "scaJakartaH2"));
        }
    }

    private static void requireText(String value, String field)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
