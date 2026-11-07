package org.nonprofitbookkeeping.model;

/**
 * Persisted shell divider format. The retired right-divider field remains for file compatibility.
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
