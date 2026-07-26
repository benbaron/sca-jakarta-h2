package org.nonprofitbookkeeping.interchange.sclx;

import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Fund;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maps the selected company's profile, active chart, accounts, and funds into the immutable SCLX DTO graph.
 * Querying remains outside this class so callers must supply an already bounded company-owned snapshot.
 */
public final class SclxCoreSnapshotAssembler
{
    private final SclxExportDocumentValidator validator = new SclxExportDocumentValidator();

    public SclxExportDocument assemble(
            Company company,
            List<Account> accounts,
            List<Fund> funds,
            Instant exportedAt)
    {
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(accounts, "accounts");
        Objects.requireNonNull(funds, "funds");
        Objects.requireNonNull(exportedAt, "exportedAt");

        ChartOfAccounts activeChart = Objects.requireNonNull(
                company.getActiveChartOfAccounts(), "selected company has no active chart of accounts");
        if (activeChart.getCompany() != company)
        {
            throw new IllegalArgumentException("active chart does not belong to the selected company");
        }

        String companyCode = requireText(company.getCode(), "company code");
        String currency = requireText(company.getDefaultCurrency(), "company currency");
        LocalDate fiscalYearStart = LocalDate.of(
                exportedAt.atZone(ZoneOffset.UTC).getYear(),
                company.getFiscalYearStartMonth(),
                company.getFiscalYearStartDay());

        List<SclxExportDocument.Account> exportedAccounts = accounts.stream()
                .peek(account -> requireAccountOwnership(account, activeChart))
                .sorted(Comparator.comparing(Account::getCode))
                .map(account -> mapAccount(companyCode, currency, account))
                .toList();

        List<SclxExportDocument.Fund> exportedFunds = funds.stream()
                .peek(fund -> requireFundOwnership(fund, company))
                .sorted(Comparator.comparing(Fund::getCode))
                .map(fund -> mapFund(companyCode, fund))
                .toList();

        SclxExportDocument document = SclxExportDocument.version13(
                exportedAt,
                new SclxExportDocument.Organization(
                        SclxPortableIdentity.organization(companyCode),
                        companyCode,
                        requireText(company.getDisplayName(), "company display name"),
                        currency,
                        fiscalYearStart),
                exportedAccounts,
                exportedFunds,
                List.of(),
                List.of(),
                new SclxExportDocument.Extensions(1, Map.of(
                        "activeChartName", activeChart.getName(),
                        "activeChartVersion", activeChart.getVersion())));
        validator.validate(document);
        return document;
    }

    private static SclxExportDocument.Account mapAccount(
            String companyCode, String currency, Account account)
    {
        String accountId = SclxPortableIdentity.account(companyCode, account.getCode());
        String parentId = account.getParent() == null
                ? null
                : SclxPortableIdentity.account(companyCode, account.getParent().getCode());
        return new SclxExportDocument.Account(
                accountId,
                account.getCode(),
                account.getName(),
                account.getAccountType().name(),
                account.getSubtype() == null ? null : account.getSubtype().name(),
                account.getNormalBalance().name(),
                parentId,
                currency,
                account.getOpeningBalance(),
                account.isPosting(),
                account.isActive());
    }

    private static SclxExportDocument.Fund mapFund(String companyCode, Fund fund)
    {
        String parentId = fund.getParent() == null
                ? null
                : SclxPortableIdentity.fund(companyCode, fund.getParent().getCode());
        return new SclxExportDocument.Fund(
                SclxPortableIdentity.fund(companyCode, fund.getCode()),
                fund.getCode(),
                fund.getName(),
                fund.getFundType().name(),
                parentId,
                fund.isActive(),
                fund.getEffectiveFrom(),
                fund.getEffectiveTo(),
                fund.getRestrictionText());
    }

    private static void requireAccountOwnership(Account account, ChartOfAccounts activeChart)
    {
        Objects.requireNonNull(account, "account");
        if (account.getChart() != activeChart)
        {
            throw new IllegalArgumentException("account is outside the selected company's active chart: "
                    + account.getCode());
        }
    }

    private static void requireFundOwnership(Fund fund, Company company)
    {
        Objects.requireNonNull(fund, "fund");
        if (fund.getCompany() != company)
        {
            throw new IllegalArgumentException("fund is outside the selected company: " + fund.getCode());
        }
    }

    private static String requireText(String value, String field)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
