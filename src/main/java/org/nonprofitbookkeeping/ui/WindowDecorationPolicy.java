package org.nonprofitbookkeeping.ui;

import javafx.stage.StageStyle;
import org.nonprofitbookkeeping.model.AppPreferencesState;

/** Resolves the restart-time JavaFX stage decoration from persisted shell preferences. */
final class WindowDecorationPolicy
{
    private WindowDecorationPolicy()
    {
    }

    static StageStyle stageStyle(AppPreferencesState preferences)
    {
        return preferences.useNativeWindowDecorations()
                ? StageStyle.UNIFIED
                : StageStyle.DECORATED;
    }
}
