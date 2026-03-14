package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.model.UiThemePreference;
import org.nonprofitbookkeeping.model.UserPrivilegeLevel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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

    private static final String K_ACTIVE_COMPANY = "multiCompany.active";
    private static final String K_RECENTS = "multiCompany.recents";

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
