package org.nonprofitbookkeeping.ui;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Executable inventory of persisted Settings values and their production consumers. */
public final class PreferenceConsumerMatrix
{
    public enum Status
    {
        ACTIVE,
        DEFERRED
    }

    public record Entry(
            String key,
            String scope,
            Status status,
            boolean enabledInSettings,
            String productionConsumer,
            String userMessage)
    {
    }

    private static final List<Entry> ENTRIES = List.of(
            active("themePreference", "user machine", "ProductionWorkspaceWindow theme classes", "Applies immediately."),
            active("useNativeWindowDecorations", "user machine", "MainApp StageStyle", "Applies after restart."),
            active("rememberWindowState", "user machine", "MainApp window geometry and shell dividers", "Applies after restart."),
            deferred("defaultPrivilege", "compatibility", "Authentication and effective authorization are not implemented."),
            active("correctionMethod", "desktop session", "Journal correction/delete action", "Applies to subsequent Journal actions."),
            active("closedPeriodPolicy", "desktop session", "PeriodCloseRunsPanel reopening defaults", "Applies when Period Close opens or refreshes."),
            active("requireReopenReason", "desktop session", "PeriodCloseRunsPanel reopening defaults", "Applies when Period Close opens or refreshes."),
            deferred("defaultReopenScope", "compatibility", "Calculated/custom close ranges have no session-scoped reopen mode."),
            active("confirmEnteredTransactionDeletion", "desktop session", "Journal direct-delete confirmation", "Applies to subsequent direct deletes."),
            active("periodStartDayOfMonth", "desktop session", "ActivePeriodContext and toolbar period selection", "Applies to subsequently selected periods."),
            active("currencySymbol", "company H2", "CompanyUiFormat money presentation", "Applies to the selected company."),
            active("moneyPrintFormat", "company H2", "CompanyUiFormat money presentation", "Applies to the selected company."),
            active("dateDisplayFormat", "company H2", "CompanyUiFormat date presentation", "Applies to the selected company."));

    private static final Map<String, Entry> BY_KEY = ENTRIES.stream()
            .collect(Collectors.toUnmodifiableMap(Entry::key, Function.identity()));

    private PreferenceConsumerMatrix()
    {
    }

    public static List<Entry> entries()
    {
        return ENTRIES;
    }

    public static Entry entry(String key)
    {
        Entry entry = BY_KEY.get(key);
        if (entry == null)
        {
            throw new IllegalArgumentException("Unknown preference key: " + key);
        }
        return entry;
    }

    private static Entry active(String key, String scope, String consumer, String message)
    {
        return new Entry(key, scope, Status.ACTIVE, true, consumer, message);
    }

    private static Entry deferred(String key, String scope, String message)
    {
        return new Entry(key, scope, Status.DEFERRED, false, "", message);
    }
}
