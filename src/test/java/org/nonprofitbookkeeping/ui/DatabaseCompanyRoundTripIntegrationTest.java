package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.AccountAdminService;
import org.nonprofitbookkeeping.service.AccountLookupService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DatabaseCompanyRoundTripIntegrationTest component.
 */
public class DatabaseCompanyRoundTripIntegrationTest
{
    @Test
    public void roundTrip_databaseAndCompanyContext_reopensAndReadsBackData() throws Exception
    {
        Path db = Files.createTempFile("db-roundtrip", ".mv.db");
        Path stateFile = Files.createTempFile("ui-state-roundtrip", ".properties");

        DatabaseBootstrap.migrate(db);

        FileAppStateStore store = new FileAppStateStore(stateFile);
        store.saveDatabaseSelection(new DatabaseSelectionState(db.toString(), List.of(db.toString())));
        store.saveMultiCompany(new MultiCompanyState("COMPANY_A", List.of("COMPANY_A")));

        Jpa first = new Jpa(db);
        try
        {
            seedActiveChart(first);
            AccountAdminService admin = new AccountAdminService(first);
            admin.upsert("A-1000", "COMPANY_A Cash", AccountType.ASSET, NormalBalance.DEBIT, null, null, true);
        }
        finally
        {
            first.close();
        }

        store.saveMultiCompany(new MultiCompanyState("COMPANY_B", List.of("COMPANY_B", "COMPANY_A")));
        MultiCompanyState switched = store.loadMultiCompany().orElseThrow();
        assertEquals("COMPANY_B", switched.activeCompanyCode());

        store.saveMultiCompany(new MultiCompanyState("COMPANY_A", List.of("COMPANY_A", "COMPANY_B")));
        MultiCompanyState reopened = store.loadMultiCompany().orElseThrow();
        assertEquals("COMPANY_A", reopened.activeCompanyCode());

        Jpa second = new Jpa(db);
        try
        {
            AccountLookupService lookup = new AccountLookupService(second);
            List<String> names = lookup.listPostingAccountsIncludingInactive().stream().map(a -> a.getName()).toList();
            assertTrue(names.contains("COMPANY_A Cash"));
        }
        finally
        {
            second.close();
        }
    }

    private static void seedActiveChart(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setName("Default Chart");
            chart.setVersion("v1");
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);
            em.getTransaction().commit();
        }
    }
}
