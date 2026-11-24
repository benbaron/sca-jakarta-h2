package org.nonprofitbookkeeping.ui;

import java.util.Objects;

/** Transient literal-name handoff; never a saved master-data identity. */
public record ImportAddSuggestion(String name, String database, String company)
{
    static ImportAddSuggestion capture(String name)
    {
        var session = MainWindow.sharedSessionState();
        return new ImportAddSuggestion(name == null ? "" : name,
                session.databaseSelection().activeDatabasePath(),
                session.multiCompany().activeCompanyCode());
    }

    boolean matchesCurrentScope()
    {
        var session = MainWindow.sharedSessionState();
        return Objects.equals(database, session.databaseSelection().activeDatabasePath())
                && Objects.equals(company, session.multiCompany().activeCompanyCode());
    }
}
