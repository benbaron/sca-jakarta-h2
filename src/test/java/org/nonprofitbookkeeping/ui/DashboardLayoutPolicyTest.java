package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DashboardLayoutPolicyTest
{
    @Test
    public void selectsWideLayoutAtWideBreakpoint()
    {
        assertEquals(
                DashboardLayoutPolicy.LayoutMode.WIDE,
                DashboardLayoutPolicy.modeFor(DashboardLayoutPolicy.WIDE_BREAKPOINT));
    }

    @Test
    public void selectsMediumLayoutBetweenBreakpoints()
    {
        assertEquals(
                DashboardLayoutPolicy.LayoutMode.MEDIUM,
                DashboardLayoutPolicy.modeFor(1000.0));
    }

    @Test
    public void selectsNarrowLayoutBelowMediumBreakpoint()
    {
        assertEquals(
                DashboardLayoutPolicy.LayoutMode.NARROW,
                DashboardLayoutPolicy.modeFor(700.0));
    }
}
