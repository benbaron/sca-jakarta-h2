package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.service.ApplicationPermission;
import org.nonprofitbookkeeping.service.AuthorizationPolicy;

import java.util.Locale;

/** Captures the session scope of a deferred company layout save. */
record UiStateWriteContext(UiSessionState session, String company, String database)
{
    static UiStateWriteContext capture(UiSessionState session, String company)
    {
        return new UiStateWriteContext(session, normalize(company), session.databaseSelection().activeDatabasePath());
    }

    boolean canSave()
    {
        return session.authenticatedUser().map(user -> AuthorizationPolicy.allows(
                user.effectiveRoles(), ApplicationPermission.UI_PREFERENCE_WRITE)).orElse(false)
                && company.equals(normalize(session.multiCompany().activeCompanyCode()))
                && database.equals(session.databaseSelection().activeDatabasePath());
    }

    private static String normalize(String company)
    {
        return company == null || company.isBlank() ? "DEFAULT" : company.trim().toUpperCase(Locale.ROOT);
    }
}
