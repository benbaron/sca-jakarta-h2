package org.nonprofitbookkeeping.ui;

import javafx.scene.control.Label;

/** Distinguishes an empty maintenance scope, a filtered list and unavailable data. */
final class MaintenanceListFeedback
{
    private final String records;
    private final String scope;
    private final String createAction;
    private final Label summary = new Label();
    private final Label placeholder = new Label();

    MaintenanceListFeedback(String controlPrefix, String records, String scope, String createAction)
    {
        this.records = records;
        this.scope = scope;
        this.createAction = createAction;
        summary.setId(controlPrefix + "ListSummary");
        summary.setWrapText(true);
        placeholder.setWrapText(true);
        loading();
    }

    Label summary()
    {
        return summary;
    }

    Label placeholder()
    {
        return placeholder;
    }

    void loading()
    {
        summary.setText("Loading " + records + "...");
        placeholder.setText(summary.getText());
    }

    void loaded(int total, int visible)
    {
        summary.setText(visible + " of " + total + " " + records + " shown (including inactive).");
        placeholder.setText(total == 0
                ? "No " + records + " have been created for " + scope + ". Use " + createAction + " and Save to add one."
                : "No matching " + records + ". Choose Show All or clear the search.");
    }

    void failed()
    {
        summary.setText("The " + records + " list could not be refreshed.");
        placeholder.setText("Could not load " + records + ". See the message above.");
    }
}
