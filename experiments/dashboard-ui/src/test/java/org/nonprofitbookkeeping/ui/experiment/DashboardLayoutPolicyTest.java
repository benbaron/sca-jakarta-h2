package org.nonprofitbookkeeping.ui.experiment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardLayoutPolicyTest
{
    @Test
    void selectsWideLayoutAtWideBreakpoint()
    {
        assertEquals(DashboardLayoutPolicy.LayoutMode.WIDE,
                DashboardLayoutPolicy.modeFor(DashboardLayoutPolicy.WIDE_BREAKPOINT));
    }

    @Test
    void selectsMediumLayoutBetweenBreakpoints()
    {
        assertEquals(DashboardLayoutPolicy.LayoutMode.MEDIUM,
                DashboardLayoutPolicy.modeFor(1000.0));
    }

    @Test
    void selectsNarrowLayoutBelowMediumBreakpoint()
    {
        assertEquals(DashboardLayoutPolicy.LayoutMode.NARROW,
                DashboardLayoutPolicy.modeFor(700.0));
    }
}
