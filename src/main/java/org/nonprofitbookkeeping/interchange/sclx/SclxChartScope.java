package org.nonprofitbookkeeping.interchange.sclx;

import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.Company;

import java.util.Objects;

/** Persisted chart identity, independent of Hibernate proxy versus implementation object identity. */
final class SclxChartScope
{
    private SclxChartScope()
    {
    }

    static boolean sameCompany(Company actual, Company expected)
    {
        return actual != null && expected != null && (actual == expected
                || actual.getId() != null && actual.getId().equals(expected.getId()));
    }

    static boolean sameChart(ChartOfAccounts actual, ChartOfAccounts expected)
    {
        return actual != null && expected != null && sameCompany(actual.getCompany(), expected.getCompany())
                && (actual == expected || actual.getId() != null && actual.getId().equals(expected.getId()));
    }

    static void requireAccount(Account account, ChartOfAccounts selected, String field, String context)
    {
        requireAccount(account, selected, field, context,
                "Review the entry's account selection in Journal or the chart assignment in Company Admin.");
    }

    static void requireAccount(Account account, ChartOfAccounts selected, String field, String context, String guidance)
    {
        Objects.requireNonNull(account, field);
        if (sameChart(account.getChart(), selected))
        {
            return;
        }
        String message = field + " is outside the selected company's active chart: " + account.getCode()
                + ".\nRecord: " + context;
        if (account.getChart() != null && selected != null
                && sameCompany(account.getChart().getCompany(), selected.getCompany()))
        {
            message += "\nLedger account: " + account.getCode() + " — " + account.getName()
                    + (account.getId() == null ? "" : " (ID " + account.getId() + ")")
                    + "\nReferenced chart: " + describe(account.getChart())
                    + "\nSelected chart: " + describe(selected);
        }
        message += "\n" + guidance + "\nSCLX exports one selected chart; existing history was not changed.";
        throw new IllegalArgumentException(message);
    }

    private static String describe(ChartOfAccounts chart)
    {
        return "chart '" + chart.getName() + "' (version " + chart.getVersion()
                + (chart.getId() == null ? "" : ", ID " + chart.getId()) + ")";
    }
}
