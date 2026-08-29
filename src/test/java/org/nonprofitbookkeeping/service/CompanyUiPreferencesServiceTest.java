package org.nonprofitbookkeeping.service;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.CompanyUiPreferences;
import org.nonprofitbookkeeping.model.DateDisplayFormat;
import org.nonprofitbookkeeping.model.MoneyPrintFormat;
import org.nonprofitbookkeeping.report.ReportDefinition;
import org.nonprofitbookkeeping.repository.JdbcCompanyUiPreferenceRepository;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyUiPreferencesServiceTest
{
    private CompanyUiPreferencesService service;

    @BeforeEach
    void setUp() throws Exception
    {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:company-ui-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement())
        {
            statement.execute("""
                    CREATE TABLE company_ui_preference (
                        company_code VARCHAR(64) PRIMARY KEY,
                        currency_symbol VARCHAR(8) NOT NULL,
                        money_print_format VARCHAR(32) NOT NULL,
                        date_display_format VARCHAR(32) NOT NULL,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
                    """);
            statement.execute("""
                    CREATE TABLE company_ui_state (
                        company_code VARCHAR(64) NOT NULL,
                        state_key VARCHAR(240) NOT NULL,
                        state_value CLOB NOT NULL,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY(company_code, state_key))
                    """);
        }
        service = new CompanyUiPreferencesService(new JdbcCompanyUiPreferenceRepository(dataSource));
    }

    @Test
    void preferencesAndWorkspaceStateRoundTripByCompany()
    {
        assertEquals(CompanyUiPreferences.defaults(), service.load("alpha"));

        CompanyUiPreferences alpha = new CompanyUiPreferences("€", MoneyPrintFormat.SYMBOL_SUFFIX, DateDisplayFormat.DAY_MONTH_YEAR);
        service.save("alpha", alpha);
        service.saveState("alpha", Map.of(
                "journal.table.lines.order", "account,fund,debit,credit",
                "journal.divider.outer.0", "0.41"));

        assertEquals(alpha, service.load("ALPHA"));
        Map<String, String> state = service.loadState("alpha", "journal.");
        assertEquals("account,fund,debit,credit", state.get("journal.table.lines.order"));
        assertEquals("0.41", state.get("journal.divider.outer.0"));
        assertTrue(service.loadState("beta", "journal.").isEmpty());
        assertEquals(CompanyUiPreferences.defaults(), service.load("beta"));
    }

    @Test
    void reportingDefaultsRoundTripByCompanyAndIgnoreStaleSavedValues()
    {
        assertEquals(CompanyReportingDefaults.defaults(), service.loadReportingDefaults("alpha"));

        CompanyReportingDefaults alpha = new CompanyReportingDefaults(
                ReportDefinition.BALANCE_SHEET,
                FinancialReportExportFormat.PDF);
        service.saveReportingDefaults("alpha", alpha);

        assertEquals(alpha, service.loadReportingDefaults("ALPHA"));
        assertEquals(CompanyReportingDefaults.defaults(), service.loadReportingDefaults("beta"));

        service.saveState("alpha", Map.of(
                "reportingDefaults.defaultReportId", "removed-report",
                "reportingDefaults.defaultExportFormat", "REMOVED_FORMAT"));
        assertEquals(CompanyReportingDefaults.defaults(), service.loadReportingDefaults("alpha"));
    }
}
