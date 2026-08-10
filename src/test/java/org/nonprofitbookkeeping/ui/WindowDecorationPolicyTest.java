package org.nonprofitbookkeeping.ui;

import javafx.stage.StageStyle;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.AppPreferencesState;
import org.nonprofitbookkeeping.model.UiThemePreference;
import org.nonprofitbookkeeping.model.UserPrivilegeLevel;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WindowDecorationPolicyTest
{
    @Test
    void resolvesRestartTimeDecorationChoice()
    {
        assertEquals(StageStyle.UNIFIED, WindowDecorationPolicy.stageStyle(preferences(true)));
        assertEquals(StageStyle.DECORATED, WindowDecorationPolicy.stageStyle(preferences(false)));
    }

    private static AppPreferencesState preferences(boolean nativeDecorations)
    {
        return new AppPreferencesState(
                UiThemePreference.SYSTEM_DEFAULT,
                nativeDecorations,
                true,
                UserPrivilegeLevel.ACCOUNTANT);
    }
}
