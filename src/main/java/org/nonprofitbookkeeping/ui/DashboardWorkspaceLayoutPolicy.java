package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.layout.GridPane;

/** Applies responsive placement and geometry rules to the dashboard workspace. */
public final class DashboardWorkspaceLayoutPolicy
{
    public static final double WIDE_BREAKPOINT = 1060.0;
    public static final double MEDIUM_BREAKPOINT = 760.0;
    public static final double GRID_HORIZONTAL_INSETS = 28.0;
    public static final double GRID_GAP = 12.0;

    private DashboardWorkspaceLayoutPolicy()
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
        Node budgetPerformance = grid.getChildren().get(2);
        Node openItems = grid.getChildren().get(3);
        Node transactions = grid.getChildren().get(4);
        Node reconciliation = grid.getChildren().get(5);
        Node budgetActual = grid.getChildren().get(6);
        Node quickLinks = grid.getChildren().get(7);

        switch (modeFor(width))
        {
            case WIDE ->
            {
                position(cash, 0, 0, 1);
                position(surplus, 1, 0, 1);
                position(budgetPerformance, 2, 0, 1);
                position(openItems, 3, 0, 1);
                position(transactions, 0, 1, 4);
                position(reconciliation, 0, 2, 2);
                position(budgetActual, 2, 2, 1);
                position(quickLinks, 3, 2, 1);
            }
            case MEDIUM ->
            {
                position(cash, 0, 0, 2);
                position(surplus, 2, 0, 2);
                position(budgetPerformance, 0, 1, 2);
                position(openItems, 2, 1, 2);
                position(transactions, 0, 2, 4);
                position(reconciliation, 0, 3, 2);
                position(budgetActual, 2, 3, 2);
                position(quickLinks, 0, 4, 4);
            }
            case NARROW ->
            {
                position(cash, 0, 0, 4);
                position(surplus, 0, 1, 4);
                position(budgetPerformance, 0, 2, 4);
                position(openItems, 0, 3, 4);
                position(transactions, 0, 4, 4);
                position(reconciliation, 0, 5, 4);
                position(budgetActual, 0, 6, 4);
                position(quickLinks, 0, 7, 4);
            }
        }
    }

    /**
     * Evaluates the complete workspace geometry without starting the JavaFX toolkit.
     *
     * @param workspaceWidth outer split-pane width
     * @param viewportHeight center viewport height available to the dashboard
     * @param navigationWidth current navigation width, or zero when collapsed
     * @param inspectorWidth current inspector width, or zero when collapsed
     * @param visibleSidebarCount number of visible sidebars, from zero through two
     * @param dividerWidth draggable divider width
     * @param dashboardPreferredHeight preferred height of all dashboard rows
     * @param cardMinimumWidth minimum usable width of a KPI card
     * @param transactionTableMinimumWidth minimum usable table viewport width
     * @param transactionTablePreferredWidth preferred width of all transaction columns
     * @return immutable geometry assessment used by headless policy tests
     */
    public static GeometryAssessment assess(
            double workspaceWidth,
            double viewportHeight,
            double navigationWidth,
            double inspectorWidth,
            int visibleSidebarCount,
            double dividerWidth,
            double dashboardPreferredHeight,
            double cardMinimumWidth,
            double transactionTableMinimumWidth,
            double transactionTablePreferredWidth)
    {
        requireNonNegative(workspaceWidth, "workspaceWidth");
        requireNonNegative(viewportHeight, "viewportHeight");
        requireNonNegative(navigationWidth, "navigationWidth");
        requireNonNegative(inspectorWidth, "inspectorWidth");
        requireNonNegative(dividerWidth, "dividerWidth");
        requireNonNegative(dashboardPreferredHeight, "dashboardPreferredHeight");
        requireNonNegative(cardMinimumWidth, "cardMinimumWidth");
        requireNonNegative(transactionTableMinimumWidth, "transactionTableMinimumWidth");
        requireNonNegative(transactionTablePreferredWidth, "transactionTablePreferredWidth");
        if (visibleSidebarCount < 0 || visibleSidebarCount > 2)
        {
            throw new IllegalArgumentException("visibleSidebarCount must be between zero and two");
        }

        double centerViewportWidth = Math.max(
                0.0,
                workspaceWidth
                        - navigationWidth
                        - inspectorWidth
                        - visibleSidebarCount * dividerWidth);
        LayoutMode mode = modeFor(centerViewportWidth);
        int cardColumns = switch (mode)
        {
            case WIDE -> 4;
            case MEDIUM -> 2;
            case NARROW -> 1;
        };

        double cardWidth = Math.max(
                0.0,
                (centerViewportWidth
                        - GRID_HORIZONTAL_INSETS
                        - (cardColumns - 1) * GRID_GAP)
                        / cardColumns);
        double transactionViewportWidth = Math.max(
                0.0,
                centerViewportWidth - GRID_HORIZONTAL_INSETS);

        return new GeometryAssessment(
                centerViewportWidth,
                mode,
                cardColumns,
                cardWidth,
                cardWidth >= cardMinimumWidth,
                transactionViewportWidth,
                transactionViewportWidth >= transactionTableMinimumWidth,
                transactionTablePreferredWidth > transactionViewportWidth,
                dashboardPreferredHeight > viewportHeight);
    }

    private static void position(Node node, int column, int row, int columnSpan)
    {
        GridPane.setColumnIndex(node, column);
        GridPane.setRowIndex(node, row);
        GridPane.setColumnSpan(node, columnSpan);
    }

    private static void requireNonNegative(double value, String name)
    {
        if (!Double.isFinite(value) || value < 0.0)
        {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    public enum LayoutMode
    {
        WIDE,
        MEDIUM,
        NARROW
    }

    /** Complete geometry result for viewport, child, scrolling, and divider tests. */
    public record GeometryAssessment(
            double centerViewportWidth,
            LayoutMode layoutMode,
            int cardColumns,
            double cardWidth,
            boolean cardMinimumSatisfied,
            double transactionViewportWidth,
            boolean transactionMinimumSatisfied,
            boolean transactionHorizontalScrollRequired,
            boolean outerVerticalScrollRequired)
    {
    }
}
