package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.model.ChartOfAccountsTransferFormat;
import org.nonprofitbookkeeping.model.ClosedPeriodPolicy;
import org.nonprofitbookkeeping.model.CorrectionMethod;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.ImportExportState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.model.ReopenScope;
import org.nonprofitbookkeeping.model.UiThemePreference;
import org.nonprofitbookkeeping.model.UserPrivilegeLevel;
import org.nonprofitbookkeeping.model.ViewPresetState;
import org.nonprofitbookkeeping.model.WorkspaceDividerState;
import org.nonprofitbookkeeping.model.WorkspaceWindowState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                UserPrivilegeLevel.ADMIN,
                CorrectionMethod.REVERSAL_AND_REPLACEMENT,
                ClosedPeriodPolicy.REQUIRE_REASON,
                true,
                ReopenScope.CURRENT_SESSION,
                false,
                4);
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
    public void saveDatabaseSession_roundTripsDatabaseAndResolvedCompanyTogether(@TempDir Path tempDir)
    {
        FileAppStateStore store = new FileAppStateStore(tempDir.resolve("ui-state.properties"));
        DatabaseSelectionState database = new DatabaseSelectionState(
                "/data/target.mv.db",
                List.of("/data/target.mv.db", "/data/source.mv.db"));
        MultiCompanyState company = new MultiCompanyState(
                "TARGET",
                List.of("TARGET", "OTHER"));

        store.saveDatabaseSession(database, company);

        assertEquals(database, store.loadDatabaseSelection().orElseThrow());
        assertEquals(company, store.loadMultiCompany().orElseThrow());
    }

    @Test
    public void loadPreferences_oldFileUsesProductionDefaults(@TempDir Path tempDir) throws IOException
    {
        Path file = tempDir.resolve("ui-state.properties");
        Files.writeString(file, "preferences.theme=LIGHT\n"
                + "preferences.nativeDecorations=false\n"
                + "preferences.rememberWindowState=true\n"
                + "preferences.defaultPrivilege=ACCOUNTANT\n");

        AppPreferencesState state = new FileAppStateStore(file).loadPreferences().orElseThrow();

        assertEquals(CorrectionMethod.DIRECT_EDIT, state.correctionMethod());
        assertEquals(ClosedPeriodPolicy.WARN_AND_REOPEN, state.closedPeriodPolicy());
        assertFalse(state.requireReopenReason());
        assertEquals(ReopenScope.UNTIL_MANUALLY_CLOSED, state.defaultReopenScope());
        assertTrue(state.confirmEnteredTransactionDeletion());
        assertEquals(1, state.periodStartDayOfMonth());
    }

    @Test
    public void loadPreferences_invalidEnumUsesSafeDefault(@TempDir Path tempDir) throws IOException
    {
        Path file = tempDir.resolve("ui-state.properties");
        Files.writeString(file, "preferences.theme=LIGHT\n"
                + "preferences.correctionMethod=UNKNOWN\n"
                + "preferences.closedPeriodPolicy=UNKNOWN\n"
                + "preferences.defaultReopenScope=UNKNOWN\n"
                + "preferences.periodStartDayOfMonth=99\n");

        AppPreferencesState state = new FileAppStateStore(file).loadPreferences().orElseThrow();

        assertEquals(CorrectionMethod.DIRECT_EDIT, state.correctionMethod());
        assertEquals(ClosedPeriodPolicy.WARN_AND_REOPEN, state.closedPeriodPolicy());
        assertEquals(ReopenScope.UNTIL_MANUALLY_CLOSED, state.defaultReopenScope());
        assertEquals(1, state.periodStartDayOfMonth());
    }

    @Test
    public void saveThenLoad_roundTripsWorkspaceDividers(@TempDir Path tempDir)
    {
        FileAppStateStore store = new FileAppStateStore(tempDir.resolve("ui-state.properties"));
        WorkspaceDividerState dividers = new WorkspaceDividerState(0.21, 0.79);

        store.saveWorkspaceDividers(dividers);

        assertEquals(dividers, store.loadWorkspaceDividers().orElseThrow());
    }

    @Test
    public void loadWorkspaceDividers_invalidValuesAreIgnored(@TempDir Path tempDir) throws IOException
    {
        Path file = tempDir.resolve("ui-state.properties");
        Files.writeString(file, "workspace.divider.left=0.90\n"
                + "workspace.divider.right=0.20\n");

        assertTrue(new FileAppStateStore(file).loadWorkspaceDividers().isEmpty());
    }

    @Test
    public void saveThenLoad_roundTripsAndClearsWindowAndDividerState(@TempDir Path tempDir)
    {
        FileAppStateStore store = new FileAppStateStore(tempDir.resolve("ui-state.properties"));
        WorkspaceWindowState window = new WorkspaceWindowState(40.0, 50.0, 1200.0, 760.0, true);
        WorkspaceDividerState dividers = new WorkspaceDividerState(0.22, 0.80);

        store.saveWindowState(window);
        store.saveWorkspaceDividers(dividers);

        assertEquals(window, store.loadWindowState().orElseThrow());
        assertEquals(dividers, store.loadWorkspaceDividers().orElseThrow());

        store.clearWindowState();
        store.clearWorkspaceDividers();

        assertTrue(store.loadWindowState().isEmpty());
        assertTrue(store.loadWorkspaceDividers().isEmpty());
    }

    @Test
    public void loadWindowState_invalidValuesAreIgnored(@TempDir Path tempDir) throws IOException
    {
        Path file = tempDir.resolve("ui-state.properties");
        Files.writeString(file, "window.x=10\n"
                + "window.y=20\n"
                + "window.width=-1\n"
                + "window.height=700\n");

        assertTrue(new FileAppStateStore(file).loadWindowState().isEmpty());
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
