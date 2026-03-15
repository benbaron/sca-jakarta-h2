package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.model.UiThemePreference;
import org.nonprofitbookkeeping.model.UserPrivilegeLevel;
import org.nonprofitbookkeeping.model.ViewPresetState;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.ArrayList;
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

    private static final String K_ACTIVE_COMPANY = "multiCompany.active";
    private static final String K_RECENTS = "multiCompany.recents";

    private static final String K_ACTIVE_DB = "database.active";
    private static final String K_DB_RECENTS = "database.recents";

    private static final String K_VIEW_PRESET_ROWS = "viewPresets.rows";

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

        UiThemePreference theme = UiThemePreference.valueOf(p.getProperty(K_THEME));
        boolean nativeDecorations = Boolean.parseBoolean(p.getProperty(K_NATIVE, "false"));
        boolean remember = Boolean.parseBoolean(p.getProperty(K_REMEMBER, "true"));
        UserPrivilegeLevel privilege = UserPrivilegeLevel.valueOf(p.getProperty(K_PRIV, UserPrivilegeLevel.ACCOUNTANT.name()));
        return Optional.of(new AppPreferencesState(theme, nativeDecorations, remember, privilege));
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
        p.setProperty(K_ACTIVE_DB, state.activeDatabasePath());
        p.setProperty(K_DB_RECENTS, String.join(",", state.recentDatabasePaths()));
        write(p);
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
            // Backward-compatibility for old plaintext token rows.
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
