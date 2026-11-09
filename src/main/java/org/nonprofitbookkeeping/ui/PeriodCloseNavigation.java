package org.nonprofitbookkeeping.ui;

import javafx.scene.control.Hyperlink;
import org.nonprofitbookkeeping.service.ClosedPeriodRangeException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/** One-time exact-scope navigation only; never retries or reopens an accounting operation. */
final class PeriodCloseNavigation
{
    private PeriodCloseNavigation() { }

    record Request(String company, String database, UUID rangeId, LocalDate start, LocalDate end) { }

    static String database()
    {
        return MainWindow.sharedSessionState().databaseSelection().activeDatabasePath();
    }

    static String context(String company, UUID rangeId, LocalDate start, LocalDate end)
    {
        return "period-close|" + encode(company) + "|" + encode(database()) + "|"
                + (rangeId == null ? "" : rangeId) + "|" + start + "|" + end;
    }

    static Optional<Request> parse(String context)
    {
        if (context == null || !context.startsWith("period-close|")) return Optional.empty();
        try
        {
            String[] parts = context.split("\\|", -1);
            if (parts.length != 6) return Optional.empty();
            LocalDate start = LocalDate.parse(parts[4]), end = LocalDate.parse(parts[5]);
            if (end.isBefore(start)) return Optional.empty();
            return Optional.of(new Request(decode(parts[1]), decode(parts[2]),
                    parts[3].isEmpty() ? null : UUID.fromString(parts[3]), start, end));
        }
        catch (IllegalArgumentException | java.time.DateTimeException ex)
        {
            return Optional.empty();
        }
    }

    static Hyperlink failureLink(String id)
    {
        Hyperlink link = new Hyperlink("View Closed Range");
        link.setId(id);
        link.setTooltip(new javafx.scene.control.Tooltip("View the blocking close range. Reopening is a separate action."));
        link.setVisible(false);
        link.managedProperty().bind(link.visibleProperty());
        return link;
    }

    static void showFailure(Hyperlink link, Throwable error, String operationDatabase)
    {
        link.setVisible(false);
        if (!database().equals(operationDatabase)) return;
        for (Throwable cause = error; cause != null; cause = cause.getCause())
        {
            if (cause instanceof ClosedPeriodRangeException closed)
            {
                String captured = context(closed.getCompanyCode(), closed.getCloseRangeId(),
                        closed.getTransactionDate(), closed.getTransactionDate());
                link.setOnAction(event -> DrillThroughCoordinator.openPanelWithContext(AppPanelId.PERIOD_CLOSE_RUNS, captured));
                link.setVisible(true);
                return;
            }
            if (cause == cause.getCause()) break;
        }
    }

    private static String encode(String value)
    {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value)
    {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
