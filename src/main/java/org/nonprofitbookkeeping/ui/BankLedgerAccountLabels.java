package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.CompanyBankAccount;

import java.util.Objects;

/** Full identity for company-scoped Banking account choices and existing links. */
final class BankLedgerAccountLabels
{
    private BankLedgerAccountLabels()
    {
    }

    static String choice(Account account)
    {
        if (account == null)
        {
            return "";
        }
        var chart = account.getChart();
        return accountIdentity(account) + (chart == null ? " — No chart linked"
                : " — Chart: " + chart.getName() + " (version " + chart.getVersion() + "; chart ID " + chart.getId() + ")");
    }

    static String configured(CompanyBankAccount configured)
    {
        return configured.getAccount() == null ? "No ledger account linked"
                : accountIdentity(configured.getAccount()) + " — " + configuredChart(configured);
    }

    static String configuredChart(CompanyBankAccount configured)
    {
        Account account = configured.getAccount();
        if (account == null)
        {
            return "No ledger account linked";
        }
        var chart = account.getChart();
        if (chart == null || chart.getCompany() == null || configured.getCompany() == null
                || configured.getCompany().getId() == null
                || !Objects.equals(chart.getCompany().getId(), configured.getCompany().getId()))
        {
            return "Chart ownership requires review";
        }
        return chart.getName() + " (version " + chart.getVersion() + "; chart ID " + chart.getId() + ")";
    }

    static String accountIdentity(Account account)
    {
        if (account == null)
        {
            return "No ledger account linked";
        }
        return account.getCode() + " — " + account.getName() + " (account ID " + account.getId() + ")"
                + (account.isActive() ? "" : " [inactive]");
    }
}
