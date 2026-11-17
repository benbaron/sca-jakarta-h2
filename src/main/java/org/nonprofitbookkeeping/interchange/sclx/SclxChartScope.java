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
                "Review the entry's account selection in Journal or the chart assignment on the Chart of Accounts page.");
    }

    static void requireAccount(Account account, ChartOfAccounts selected, String field, String context, String guidance)
    {
        Objects.requireNonNull(account, field);
        if (account.getChart() != null && selected != null
                && sameCompany(account.getChart().getCompany(), selected.getCompany()))
        {
            return;
        }
        throw new IllegalArgumentException(field + " is outside the selected company: " + account.getCode()
                + ".\nRecord: " + context + "\n" + guidance
                + "\nSCLX includes all company-owned charts; foreign-company references cannot be exported.");
    }
}
