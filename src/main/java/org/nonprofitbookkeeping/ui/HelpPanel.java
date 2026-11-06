package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

/** Current production navigation, shortcuts, and authoritative project links. */
public class HelpPanel implements AppPanel
{
    static final String REPOSITORY_URL = "https://github.com/benbaron/sca-jakarta-h2";
    static final String PLAN_URL = REPOSITORY_URL + "/blob/main/doc/PLAN.md";
    static final String UI_RULES_URL = REPOSITORY_URL + "/blob/main/doc/ui_design_rules.md";
    static final String WORKFLOW_URL = REPOSITORY_URL + "/blob/main/doc/workflow/development-workflow.md";

    static final String PANEL_GUIDE_URL = REPOSITORY_URL + "/blob/main/doc/ui/panel-guide.md";

    private final VBox root = new VBox(8);
    private final Label linkStatus = new Label();

    public HelpPanel()
    {
        root.setPadding(new Insets(8));
        root.setMinSize(0.0, 0.0);

        Label title = new Label("Help");
        title.getStyleClass().add("panel-title");

        Label gettingStarted = wrapped("Select a database, company and accounting period, then open a workspace.");

        Label shortcuts = wrapped(GlobalCommandRegistry.shortcutHelpText()
                + "\nCopy/Paste use the focused text control.");

        VBox links = new VBox(4,
                externalLink("Panel and function guide", PANEL_GUIDE_URL),
                externalLink("Project repository", REPOSITORY_URL),
                externalLink("Current execution plan", PLAN_URL),
                externalLink("UI design rules", UI_RULES_URL),
                externalLink("Development workflow", WORKFLOW_URL));
        linkStatus.setWrapText(true);
        linkStatus.getStyleClass().add("help-text");

        VBox content = new VBox(10,
                title,
                new Separator(),
                gettingStarted,
                new Separator(),
                shortcuts,
                new Separator(),
                new Label("Project documentation"),
                links,
                linkStatus);
        content.setPadding(new Insets(4));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setId("helpContentScroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().add(scroll);
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

    private Hyperlink externalLink(String text, String url)
    {
        Hyperlink link = new Hyperlink(text);
        link.setOnAction(event -> openExternal(url));
        return link;
    }

    private static Label wrapped(String text)
    {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }

    private void openExternal(String url)
    {
        if (!Desktop.isDesktopSupported())
        {
            linkStatus.setText("Opening a browser is unavailable on this desktop. Copy this address: " + url);
            return;
        }
        try
        {
            Desktop.getDesktop().browse(URI.create(url));
            linkStatus.setText("Opened " + url);
        }
        catch (IOException | RuntimeException ex)
        {
            linkStatus.setText("Could not open a browser. Copy this address: " + url);
        }
    }
}
