package org.nonprofitbookkeeping.ui;

import javafx.scene.control.DialogPane;

/** Applies shared production UI policy to modal dialogs owned by a production destination. */
final class CompanyDialogUiCompliance
{
    private CompanyDialogUiCompliance()
    {
    }

    static void install(DialogPane dialogPane, AppPanelId owner)
    {
        if (dialogPane == null || owner == null)
        {
            return;
        }
        FullTextTooltipInstaller.install(dialogPane);
        CompanyTableStateBinder.applyProductionPanel(dialogPane, owner);
    }
}
