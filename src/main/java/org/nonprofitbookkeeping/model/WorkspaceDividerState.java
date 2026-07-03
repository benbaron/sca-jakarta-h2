package org.nonprofitbookkeeping.model;

/**
 * Persisted safe divider positions for the production three-pane workspace.
 */
public record WorkspaceDividerState(double leftDividerPosition,
                                    double rightDividerPosition)
{
    public WorkspaceDividerState
    {
        if (!Double.isFinite(leftDividerPosition)
                || !Double.isFinite(rightDividerPosition)
                || leftDividerPosition <= 0.0
                || rightDividerPosition >= 1.0
                || leftDividerPosition >= rightDividerPosition)
        {
            throw new IllegalArgumentException("Divider positions must be finite, ordered fractions inside the workspace");
        }
    }
}
