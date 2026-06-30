package org.nonprofitbookkeeping.ui;

import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DashboardWorkspaceLayoutPolicyTest
{
    @Test
    public void selectsResponsiveModesAtBreakpoints()
    {
        assertEquals(
                DashboardWorkspaceLayoutPolicy.LayoutMode.WIDE,
                DashboardWorkspaceLayoutPolicy.modeFor(
                        DashboardWorkspaceLayoutPolicy.WIDE_BREAKPOINT));
        assertEquals(
                DashboardWorkspaceLayoutPolicy.LayoutMode.MEDIUM,
                DashboardWorkspaceLayoutPolicy.modeFor(900));
        assertEquals(
                DashboardWorkspaceLayoutPolicy.LayoutMode.NARROW,
                DashboardWorkspaceLayoutPolicy.modeFor(640));
    }

    @Test
    public void narrowModeStacksAllCardsWithoutOverlap()
    {
        javafx.scene.layout.GridPane grid = gridWithEightCards();

        DashboardWorkspaceLayoutPolicy.apply(grid, 640);

        for (int index = 0; index < 8; index++)
        {
            assertEquals(0, javafx.scene.layout.GridPane.getColumnIndex(grid.getChildren().get(index)));
            assertEquals(index, javafx.scene.layout.GridPane.getRowIndex(grid.getChildren().get(index)));
            assertEquals(4, javafx.scene.layout.GridPane.getColumnSpan(grid.getChildren().get(index)));
        }
    }

    @Test
    public void wideModeSpansTransactionTableAcrossWorkspace()
    {
        javafx.scene.layout.GridPane grid = gridWithEightCards();

        DashboardWorkspaceLayoutPolicy.apply(grid, 1200);

        Pane transactionCard = (Pane) grid.getChildren().get(4);
        assertEquals(0, javafx.scene.layout.GridPane.getColumnIndex(transactionCard));
        assertEquals(1, javafx.scene.layout.GridPane.getRowIndex(transactionCard));
        assertEquals(4, javafx.scene.layout.GridPane.getColumnSpan(transactionCard));
    }

    private static javafx.scene.layout.GridPane gridWithEightCards()
    {
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        for (int index = 0; index < 8; index++)
        {
            grid.getChildren().add(new Pane());
        }
        return grid;
    }
}
