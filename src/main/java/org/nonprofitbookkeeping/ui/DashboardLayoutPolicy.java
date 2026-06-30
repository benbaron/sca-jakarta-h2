package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.layout.GridPane;

/**
 * Applies responsive layout breakpoints to the production dashboard.
 */
public final class DashboardLayoutPolicy
{
    public static final double WIDE_BREAKPOINT = 1180.0;
    public static final double MEDIUM_BREAKPOINT = 820.0;

    private DashboardLayoutPolicy()
    {
    }

    public static LayoutMode modeFor(double width)
    {
        if (width >= WIDE_BREAKPOINT)
        {
            return LayoutMode.WIDE;
        }
        if (width >= MEDIUM_BREAKPOINT)
        {
            return LayoutMode.MEDIUM;
        }
        return LayoutMode.NARROW;
    }

    public static void apply(GridPane grid, double width)
    {
        if (grid.getChildren().size() < 8)
        {
            return;
        }

        Node cash = grid.getChildren().get(0);
        Node surplus = grid.getChildren().get(1);
        Node fundChart = grid.getChildren().get(2);
        Node openItems = grid.getChildren().get(3);
        Node transactions = grid.getChildren().get(4);
        Node reconciliation = grid.getChildren().get(5);
        Node fundTotals = grid.getChildren().get(6);
        Node quickLinks = grid.getChildren().get(7);

        switch (modeFor(width))
        {
            case WIDE ->
            {
                position(cash, 0, 0, 1);
                position(surplus, 1, 0, 1);
                position(fundChart, 2, 0, 1);
                position(openItems, 3, 0, 1);
                position(transactions, 0, 1, 4);
                position(reconciliation, 0, 2, 2);
                position(fundTotals, 2, 2, 1);
                position(quickLinks, 3, 2, 1);
            }
            case MEDIUM ->
            {
                position(cash, 0, 0, 2);
                position(surplus, 2, 0, 2);
                position(fundChart, 0, 1, 2);
                position(openItems, 2, 1, 2);
                position(transactions, 0, 2, 4);
                position(reconciliation, 0, 3, 2);
                position(fundTotals, 2, 3, 2);
                position(quickLinks, 0, 4, 4);
            }
            case NARROW ->
            {
                position(cash, 0, 0, 4);
                position(surplus, 0, 1, 4);
                position(fundChart, 0, 2, 4);
                position(openItems, 0, 3, 4);
                position(transactions, 0, 4, 4);
                position(reconciliation, 0, 5, 4);
                position(fundTotals, 0, 6, 4);
                position(quickLinks, 0, 7, 4);
            }
        }
    }

    private static void position(Node node, int column, int row, int columnSpan)
    {
        GridPane.setColumnIndex(node, column);
        GridPane.setRowIndex(node, row);
        GridPane.setColumnSpan(node, columnSpan);
    }

    public enum LayoutMode
    {
        WIDE,
        MEDIUM,
        NARROW
    }
}
