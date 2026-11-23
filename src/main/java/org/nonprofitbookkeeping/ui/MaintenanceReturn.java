package org.nonprofitbookkeeping.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;

/** A maintenance panel's navigation-only return to its actual workspace caller. */
final class MaintenanceReturn
{
    private final AppPanelId owner;
    private final Button button = new Button();
    private AppPanelId destination = AppPanelId.JOURNAL_PANE;

    MaintenanceReturn(AppPanelId owner, String controlId)
    {
        this.owner = owner;
        button.setId(controlId);
        describe("Journal");
        button.setOnAction(event -> DrillThroughCoordinator.openPanelWithContext(destination, ""));
    }

    Button button()
    {
        return button;
    }

    void shownFrom(AppPanelId origin, String title)
    {
        if (origin != null && AppPanelId.canonical(origin) != owner)
        {
            destination = AppPanelId.canonical(origin);
            describe(title == null || title.isBlank() ? "previous workspace" : title);
        }
    }

    private void describe(String title)
    {
        button.setText("Return to " + title);
        button.setTooltip(new Tooltip("Return to " + title + " without saving or discarding editor changes."));
    }
}
