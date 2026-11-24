package org.nonprofitbookkeeping.ui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Coordinates cross-panel navigation and one-time context handoff. */
final class DrillThroughCoordinator
{
    private static final AtomicReference<String> CONTEXT = new AtomicReference<>();
    private static final Map<AppPanelId, String> PANEL_CONTEXT = new ConcurrentHashMap<>();
    private static final Map<AppPanelId, ImportAddSuggestion> IMPORT_ADD_CONTEXT = new ConcurrentHashMap<>();
    private static volatile Consumer<AppPanelId> opener = id -> {};

    private DrillThroughCoordinator()
    {
    }

    static void configureOpener(Consumer<AppPanelId> panelOpener)
    {
        opener = panelOpener == null ? (id -> {}) : panelOpener;
        if (panelOpener == null)
        {
            IMPORT_ADD_CONTEXT.clear();
        }
        debug("Configured opener: " + (panelOpener == null ? "no-op" : panelOpener));
    }

    static void openLedgerWithContext(String context)
    {
        CONTEXT.set(context == null ? "" : context);
        debug("Stored legacy ledger context '" + safeContext(context) + "'.");
        openPanelWithContext(AppPanelId.JOURNAL_PANE, context);
    }

    static void openTransactionEditorWithContext(String context)
    {
        openPanelWithContext(AppPanelId.JOURNAL_PANE, context);
    }

    static void openPanelWithContext(AppPanelId requestedPanelId, String context)
    {
        openPanelWithContext(requestedPanelId, context, null);
    }

    private static void openPanelWithContext(AppPanelId requestedPanelId, String context, ImportAddSuggestion suggestion)
    {
        if (requestedPanelId == null)
        {
            debug("Ignored open request with null panel id and context '" + safeContext(context) + "'.");
            return;
        }
        AppPanelId panelId = AppPanelId.canonical(requestedPanelId);
        String normalizedContext = context == null ? "" : context;
        if (requestedPanelId == AppPanelId.RECONCILIATION_RUNS
                && !normalizedContext.startsWith("reconciliation:session=")
                && !normalizedContext.startsWith("bank-import-return:reconciliation="))
        {
            normalizedContext = "banking:reconcile";
        }
        else if (requestedPanelId == AppPanelId.BANK_TRANSACTIONS)
        {
            normalizedContext = normalizedContext.toLowerCase(java.util.Locale.ROOT).contains("review")
                    ? "banking:statements" : "banking:journal";
        }
        if (suggestion == null)
        {
            IMPORT_ADD_CONTEXT.remove(panelId);
        }
        else
        {
            IMPORT_ADD_CONTEXT.put(panelId, suggestion);
        }
        PANEL_CONTEXT.put(panelId, normalizedContext);
        debug("Opening " + panelId + " with context '" + safeContext(normalizedContext) + "'.");
        opener.accept(panelId);
        debug("Open request dispatched for " + panelId + ".");
    }

    static String consumeContext(AppPanelId requestedPanelId)
    {
        if (requestedPanelId == null)
        {
            return "";
        }
        AppPanelId panelId = AppPanelId.canonical(requestedPanelId);
        String context = PANEL_CONTEXT.remove(panelId);
        debug("Consumed context for " + panelId + ": '" + safeContext(context) + "'.");
        return context == null ? "" : context;
    }

    static void openForImportAdd(AppPanelId destination, String literalName)
    {
        AppPanelId id = AppPanelId.canonical(destination);
        openPanelWithContext(id, "", ImportAddSuggestion.capture(literalName));
    }

    static ImportAddSuggestion consumeImportAdd(AppPanelId destination)
    {
        return IMPORT_ADD_CONTEXT.remove(AppPanelId.canonical(destination));
    }

    static String consumeContext()
    {
        String context = CONTEXT.getAndSet("");
        debug("Consumed legacy context: '" + safeContext(context) + "'.");
        return context == null ? "" : context;
    }

    private static void debug(String message)
    {
        UiDebug.log("drill-through", message);
    }

    private static String safeContext(String context)
    {
        return context == null ? "" : context;
    }
}
