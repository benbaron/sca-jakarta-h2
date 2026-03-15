package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

/**
 * Structured help panel with quick-start topics and links.
 */
public class HelpPanel implements AppPanel
{
    private final VBox root = new VBox(8);

    public HelpPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Help");
        title.getStyleClass().add("panel-title");

        Label gettingStarted = new Label("Getting Started\n"
                + "1) Select your database file in File -> Select Database File…\n"
                + "2) Open Settings to choose theme, company, and defaults\n"
                + "3) Use Tools -> Import actions for COA CSV and OFX/QFX workflows");

        Label shortcuts = new Label("Common shortcuts\n"
                + "- Ctrl+S: Save session state\n"
                + "- Ctrl+F: Open Search pane\n"
                + "- Ctrl+N: New item in active panel");

        Hyperlink migrationGuide = new Hyperlink("Build guide: docs/repo-local-build.md");
        migrationGuide.setOnAction(e -> openExternal("https://github.com/nonprofitbookkeeping/sca-jakarta-h2/blob/main/docs/repo-local-build.md"));

        Hyperlink importGuide = new Hyperlink("Progress report: docs/progress-report-next-pass.md");
        importGuide.setOnAction(e -> openExternal("https://github.com/nonprofitbookkeeping/sca-jakarta-h2/blob/main/docs/progress-report-next-pass.md"));

        root.getChildren().addAll(title, new Separator(), gettingStarted, new Separator(), shortcuts, migrationGuide, importGuide);
    }

    @Override
    public String title()
    {
        return "Help";
    }

    @Override
    public Node root()
    {
        return root;
    }

    private void openExternal(String url)
    {
        if (!Desktop.isDesktopSupported())
        {
            return;
        }
        try
        {
            Desktop.getDesktop().browse(URI.create(url));
        }
        catch (IOException ignored)
        {
            // Best effort only.
        }
    }
}
