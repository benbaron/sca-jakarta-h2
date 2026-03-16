package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.model.ChartOfAccountsTransferFormat;
import org.nonprofitbookkeeping.model.ImportExportState;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.model.UiThemePreference;
import org.nonprofitbookkeeping.model.UserPrivilegeLevel;
import org.nonprofitbookkeeping.model.ViewPresetState;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FileAppStateStoreTest component.
 */
public class FileAppStateStoreTest
{
    @Test
    public void saveThenLoad_roundTripsPreferencesAndCompany(@TempDir Path tempDir)
    {
        FileAppStateStore store = new FileAppStateStore(tempDir.resolve("ui-state.properties"));

        AppPreferencesState prefs = new AppPreferencesState(
                UiThemePreference.DARK,
                true,
                false,
                UserPrivilegeLevel.ADMIN);
        MultiCompanyState company = new MultiCompanyState("BARONY-BLUE", List.of("BARONY-BLUE", "BARONY-RED"));
        DatabaseSelectionState db = new DatabaseSelectionState("/data/barony-blue.mv.db", List.of("/data/barony-blue.mv.db", "/data/barony-red.mv.db"));

        store.savePreferences(prefs);
        store.saveMultiCompany(company);
        store.saveDatabaseSelection(db);

        assertEquals(prefs, store.loadPreferences().orElseThrow());
        assertEquals(company, store.loadMultiCompany().orElseThrow());
        assertEquals(db, store.loadDatabaseSelection().orElseThrow());
    }


    @Test
    public void saveThenLoad_roundTripsViewPresets(@TempDir Path tempDir)
    {
        FileAppStateStore store = new FileAppStateStore(tempDir.resolve("ui-state.properties"));

        List<ViewPresetState> presets = List.of(
                new ViewPresetState("Month Reports", "REPORT_LIBRARY", "2026-03-01", "2026-03-31"),
                new ViewPresetState("Diagnostics", "DIAGNOSTICS", "", ""));

        store.saveViewPresets(presets);

        assertEquals(presets, store.loadViewPresets());
    }


    @Test
    public void saveThenLoad_roundTripsViewPresets_withSpecialCharacters(@TempDir Path tempDir)
    {
        FileAppStateStore store = new FileAppStateStore(tempDir.resolve("ui-state.properties"));

        List<ViewPresetState> presets = List.of(
                new ViewPresetState("Ops | Close\nRun", "PERIOD_CLOSE_RUNS", "2026-04-01", "2026-04-30"),
                new ViewPresetState("Ledger → Drill", "LEDGER_REGISTER", "", ""));

        store.saveViewPresets(presets);

        assertEquals(presets, store.loadViewPresets());
    }

    @Test
    public void importExportState_contractSupportsRequestedFormats()
    {
        ImportExportState state = new ImportExportState(BankingDataFormat.QFX,
                ChartOfAccountsTransferFormat.JSON,
                "imports/bank.qfx",
                "exports/coa.json");

        assertEquals(BankingDataFormat.QFX, state.bankingFormat());
        assertEquals(ChartOfAccountsTransferFormat.JSON, state.chartFormat());
        assertTrue(state.lastImportPath().endsWith(".qfx"));
        assertTrue(state.lastExportPath().endsWith(".json"));
    }
}
