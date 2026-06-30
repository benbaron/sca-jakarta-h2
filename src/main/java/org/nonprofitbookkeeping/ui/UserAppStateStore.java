package org.nonprofitbookkeeping.ui;

import java.nio.file.Path;

final class UserAppStateStore
{
    private UserAppStateStore()
    {
    }

    static AppStateStore create()
    {
        Path statePath = Path.of(System.getProperty("user.home"), ".sca-ledger", "ui-state.properties");
        return new FileAppStateStore(statePath);
    }
}
