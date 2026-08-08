package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.ClosedPeriodPolicy;
import org.nonprofitbookkeeping.model.CorrectionMethod;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.model.ReopenScope;
import org.nonprofitbookkeeping.model.UiThemePreference;
import org.nonprofitbookkeeping.model.UserPrivilegeLevel;
import org.nonprofitbookkeeping.model.ViewPresetState;
import org.nonprofitbookkeeping.model.WorkspaceDividerState;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Properties-file backed app state persistence.
 */
public class FileAppStateStore implements AppStateStore
{
    private static final String K_THEME = "preferences.theme";
    private static final String K_NATIVE = "preferences.nativeDecorations";
    private static final String K_REMEMBER = "preferences.rememberWindowState";
    private static final String K_PRIV = "preferences.defaultPrivilege";
    private static final String K_CORRECTION = "preferences.correctionMethod";
    private static final String K_CLOSED_PERIOD = "preferences.closedPeriodPolicy";
    private static final String K_REQUIRE_REOPEN_REASON = "preferences.requireReopenReason";
    private static final String K_REOPEN_SCOPE = "preferences.defaultReopenScope";
    private static final String K_CONFIRM_DELETE = "preferences.confirmEnteredTransactionDeletion";
    private static final String K_PERIOD_START_DAY = "preferences.periodStartDayOfMonth";

    private static final String K_ACTIVE_COMPANY = "multiCompany.active";
    private static final String K_RECENTS = "multiCompany.recents";

    private static final String K_ACTIVE_DB = "database.active";
    private static final String K_DB_RECENTS = "database.recents";

    private static final String K_VIEW_PRESET_ROWS = "viewPresets.rows";
    private static final String K_WORKSPACE_LEFT_DIVIDER = "workspace.divider.left";
    private static final String K_WORKSPACE_RIGHT_DIVIDER = "workspace.divider.right";

    private final Path file;

    public FileAppStateStore(Path file)
    {
        this.file = file;
    }

    @Override
    public Optional<AppPreferencesState> loadPreferences()
    {
        Properties p = read();
        if (!p.containsKey(K_THEME))
        {
            return Optional.empty();
        }

        UiThemePreference theme = enumValue(p, K_THEME, UiThemePreference.SYSTEM_DEFAULT);
        boolean nativeDecorations = Boolean.parseBoolean(p.getProperty(K_NATIVE, "false"));
        boolean remember = Boolean.parseBoolean(p.getProperty(K_REMEMBER, "true"));
        UserPrivilegeLevel privilege = enumValue(p, K_PRIV, UserPrivilegeLevel.ACCOUNTANT);
        CorrectionMethod correction = enumValue(p, K_CORRECTION, CorrectionMethod.DIRECT_EDIT);
        ClosedPeriodPolicy closedPeriod = enumValue(p, K_CLOSED_PERIOD, ClosedPeriodPolicy.WARN_AND_REOPEN);
        boolean requireReason = Boolean.parseBoolean(p.getProperty(K_REQUIRE_REOPEN_REASON, "false"));
        ReopenScope reopenScope = enumValue(p, K_REOPEN_SCOPE, ReopenScope.UNTIL_MANUALLY_CLOSED);
        boolean confirmDelete = Boolean.parseBoolean(p.getProperty(K_CONFIRM_DELETE, "true"));
        int periodStartDay = intValue(p, K_PERIOD_START_DAY, 1, 1, 28);

        return Optional.of(new AppPreferencesState(
                theme,
                nativeDecorations,
                remember,
                privilege,
                correction,
                closedPeriod,
                requireReason,
                reopenScope,
                confirmDelete,
                periodStartDay));
    }

    @Override
    public Optional<MultiCompanyState> loadMultiCompany()
    {
        Properties p = read();
        String active = p.getProperty(K_ACTIVE_COMPANY);
        if (active == null || active.isBlank())
        {
            return Optional.empty();
        }
        String recentsRaw = p.getProperty(K_RECENTS, active);
        List<String> recents = Arrays.stream(recentsRaw.split(","))
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .toList();
        return Optional.of(new MultiCompanyState(active, recents.isEmpty() ? List.of(active) : recents));
    }

    @Override
    public Optional<DatabaseSelectionState> loadDatabaseSelection()
    {
        Properties p = read();
        String active = p.getProperty(K_ACTIVE_DB);
        if (active == null || active.isBlank())
        {
            return Optional.empty();
        }

        String recentsRaw = p.getProperty(K_DB_RECENTS, active);
        List<String> recents = Arrays.stream(recentsRaw.split(","))
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .toList();
        return Optional.of(new DatabaseSelectionState(active, recents.isEmpty() ? List.of(active) : recents));
    }

    @Override
    public Optional<WorkspaceDividerState> loadWorkspaceDividers()
    {
        Properties p = read();
        String leftRaw = p.getProperty(K_WORKSPACE_LEFT_DIVIDER);
        String rightRaw = p.getProperty(K_WORKSPACE_RIGHT_DIVIDER);
        if (leftRaw == null || rightRaw == null)
        {
            return Optional.empty();
        }
        try
        {
            return Optional.of(new WorkspaceDividerState(
                    Double.parseDouble(leftRaw),
                    Double.parseDouble(rightRaw)));
        }
        catch (IllegalArgumentException ex)
        {
            return Optional.empty();
        }
    }

    @Override
    public List<ViewPresetState> loadViewPresets()
    {
        Properties p = read();
        String raw = p.getProperty(K_VIEW_PRESET_ROWS, "");
        if (raw.isBlank())
        {
            return List.of();
        }

        List<ViewPresetState> out = new ArrayList<>();
        for (String row : raw.split("\\n"))
        {
            if (row.isBlank())
            {
                continue;
            }
            String[] parts = row.split("\\|", -1);
            if (parts.length != 4)
            {
                continue;
            }
            out.add(new ViewPresetState(decodeToken(parts[0]), decodeToken(parts[1]), decodeToken(parts[2]), decodeToken(parts[3])));
        }
        return out;
    }

    @Override
    public void saveViewPresets(List<ViewPresetState> presets)
    {
        Properties p = read();
        List<ViewPresetState> safe = presets == null ? List.of() : presets;
        StringBuilder out = new StringBuilder();
        for (ViewPresetState preset : safe)
        {
            if (preset == null)
            {
                continue;
            }
            if (out.length() > 0)
            {
                out.append("\n");
            }
            out.append(encodeToken(preset.name())).append("|")
                    .append(encodeToken(preset.panelId())).append("|")
                    .append(encodeToken(preset.startDateIso())).append("|")
                    .append(encodeToken(preset.endDateIso()));
        }
        p.setProperty(K_VIEW_PRESET_ROWS, out.toString());
        write(p);
    }

    @Override
    public void savePreferences(AppPreferencesState state)
    {
        Properties p = read();
        p.setProperty(K_THEME, state.themePreference().name());
        p.setProperty(K_NATIVE, Boolean.toString(state.useNativeWindowDecorations()));
        p.setProperty(K_REMEMBER, Boolean.toString(state.rememberWindowState()));
        p.setProperty(K_PRIV, state.defaultPrivilege().name());
        p.setProperty(K_CORRECTION, state.correctionMethod().name());
        p.setProperty(K_CLOSED_PERIOD, state.closedPeriodPolicy().name());
        p.setProperty(K_REQUIRE_REOPEN_REASON, Boolean.toString(state.requireReopenReason()));
        p.setProperty(K_REOPEN_SCOPE, state.defaultReopenScope().name());
        p.setProperty(K_CONFIRM_DELETE, Boolean.toString(state.confirmEnteredTransactionDeletion()));
        p.setProperty(K_PERIOD_START_DAY, Integer.toString(state.periodStartDayOfMonth()));
        write(p);
    }

    @Override
    public void saveWorkspaceDividers(WorkspaceDividerState state)
    {
        Properties p = read();
        p.setProperty(K_WORKSPACE_LEFT_DIVIDER, Double.toString(state.leftDividerPosition()));
        p.setProperty(K_WORKSPACE_RIGHT_DIVIDER, Double.toString(state.rightDividerPosition()));
        write(p);
    }

    @Override
    public void saveMultiCompany(MultiCompanyState state)
    {
        Properties p = read();
        p.setProperty(K_ACTIVE_COMPANY, state.activeCompanyCode());
        p.setProperty(K_RECENTS, String.join(",", state.recentCompanyCodes()));
        write(p);
    }

    @Override
    public void saveDatabaseSelection(DatabaseSelectionState state)
    {
        Properties p = read();
        putDatabaseSelection(p, state);
        write(p);
    }

    @Override
    public void saveDatabaseSession(DatabaseSelectionState database, MultiCompanyState company)
    {
        Properties p = read();
        putDatabaseSelection(p, database);
        p.setProperty(K_ACTIVE_COMPANY, company.activeCompanyCode());
        p.setProperty(K_RECENTS, String.join(",", company.recentCompanyCodes()));
        write(p);
    }

    private static void putDatabaseSelection(Properties properties, DatabaseSelectionState state)
    {
        properties.setProperty(K_ACTIVE_DB, state.activeDatabasePath());
        properties.setProperty(K_DB_RECENTS, String.join(",", state.recentDatabasePaths()));
    }

    private static <E extends Enum<E>> E enumValue(Properties properties, String key, E fallback)
    {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank())
        {
            return fallback;
        }
        try
        {
            return Enum.valueOf(fallback.getDeclaringClass(), raw);
        }
        catch (IllegalArgumentException ex)
        {
            return fallback;
        }
    }

    private static int intValue(Properties properties, String key, int fallback, int min, int max)
    {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank())
        {
            return fallback;
        }
        try
        {
            int parsed = Integer.parseInt(raw.trim());
            return parsed < min || parsed > max ? fallback : parsed;
        }
        catch (NumberFormatException ex)
        {
            return fallback;
        }
    }

    private static String encodeToken(String value)
    {
        String safe = value == null ? "" : value;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(safe.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeToken(String token)
    {
        if (token == null || token.isEmpty())
        {
            return "";
        }
        try
        {
            return new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        }
        catch (IllegalArgumentException ex)
        {
            return token;
        }
    }

    private Properties read()
    {
        Properties p = new Properties();
        if (!Files.exists(file))
        {
            return p;
        }
        try (InputStream in = Files.newInputStream(file))
        {
            p.load(in);
            return p;
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not read app state file: " + file, ex);
        }
    }

    private void write(Properties p)
    {
        try
        {
            Path parent = file.getParent();
            if (parent != null)
            {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(file))
            {
                p.store(out, "SCA Ledger UI state");
            }
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not write app state file: " + file, ex);
        }
    }
}
