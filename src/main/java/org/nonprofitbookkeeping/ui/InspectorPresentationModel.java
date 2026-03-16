package org.nonprofitbookkeeping.ui;

/**
 * InspectorPresentationModel component.
 */
final class InspectorPresentationModel
{
    private InspectorPresentationModel()
    {
    }

    static String workspaceContext(String activeCompany, String dateRange)
    {
        return "Active company: " + activeCompany + "\nDate range: " + dateRange;
    }

    static String navigationGroupBody(String activeCompany, String dateRange)
    {
        return "Navigation group selected. Choose a workspace item to open details.\n"
                + workspaceContext(activeCompany, dateRange);
    }

    static String panelBody(String label,
                            String panelId,
                            String activeCompany,
                            String dateRange,
                            String capabilities,
                            String openBehavior,
                            String contextHint)
    {
        return "Panel: " + label
                + "\nID: " + panelId
                + "\n" + workspaceContext(activeCompany, dateRange)
                + "\nCapabilities: " + capabilities
                + "\nOpen behavior: " + openBehavior
                + "\nContext: " + contextHint;
    }
}
