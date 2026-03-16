package org.nonprofitbookkeeping.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AppStateContractsTest component.
 */
public class AppStateContractsTest
{
    @Test
    public void multiCompanyState_preservesActiveAndRecentCompanies()
    {
        MultiCompanyState state = new MultiCompanyState("BARONY-RED", List.of("BARONY-RED", "BARONY-BLUE"));

        assertEquals("BARONY-RED", state.activeCompanyCode());
        assertEquals(List.of("BARONY-RED", "BARONY-BLUE"), state.recentCompanyCodes());
    }

    @Test
    public void appPreferencesState_capturesThemeNativeAndPrivilege()
    {
        AppPreferencesState state = new AppPreferencesState(
                UiThemePreference.SYSTEM_DEFAULT,
                true,
                true,
                UserPrivilegeLevel.MANAGER);

        assertEquals(UiThemePreference.SYSTEM_DEFAULT, state.themePreference());
        assertEquals(true, state.useNativeWindowDecorations());
        assertEquals(true, state.rememberWindowState());
        assertEquals(UserPrivilegeLevel.MANAGER, state.defaultPrivilege());
    }

    @Test
    public void importExportState_supportsOfxQfxAndChartTransferFormats()
    {
        ImportExportState state = new ImportExportState(
                BankingDataFormat.OFX,
                ChartOfAccountsTransferFormat.CSV,
                "imports/bank.ofx",
                "exports/coa.csv");

        assertEquals(BankingDataFormat.OFX, state.bankingFormat());
        assertEquals(ChartOfAccountsTransferFormat.CSV, state.chartFormat());
        assertEquals("imports/bank.ofx", state.lastImportPath());
        assertEquals("exports/coa.csv", state.lastExportPath());
    }
}
