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

    private final VBox root = new VBox(8);
    private final Label linkStatus = new Label();

    public HelpPanel()
    {
        root.setPadding(new Insets(8));
        root.setMinSize(0.0, 0.0);

        Label title = new Label("Help");
        title.getStyleClass().add("panel-title");

        Label gettingStarted = wrapped("Getting Started\n"
                + "1) Select your H2 database in File -> Select Database File… or create one with File -> Create New Database…\n"
                + "2) Choose an existing active company from the toolbar, or use Administration -> Company Admin to create and maintain companies.\n"
                + "3) Choose the accounting month in the toolbar and select Set Active Period.\n"
                + "4) Use Journal for transaction entry/review, Banking and Bank Reconciliation for bank work, and Import Preview before accepting supported imports.");

        Label navigation = wrapped("Production destinations\n"
                + "Accounting: Journal, Banking, Bank Reconciliation, Bank Transactions\n"
                + "Planning: Budget Editor, Budget vs Actual\n"
                + "Assets & Inventory: Asset Register, Depreciation Runs, Inventory\n"
                + "Import & Oversight: Import Preview, Audit History, Period Close\n"
                + "Reports: Report Library\n"
                + "Administration: Chart of Accounts, Funds, Administration, Diagnostics");

        Label shortcuts = wrapped(GlobalCommandRegistry.shortcutHelpText()
                + "\n\nCopy and Paste use the standard shortcuts of the focused text control; "
                + "the workspace does not intercept them.");

        VBox links = new VBox(4,
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
                navigation,
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
