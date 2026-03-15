package org.nonprofitbookkeeping.ui;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class DrillThroughCoordinator
{
    private static final AtomicReference<String> CONTEXT = new AtomicReference<>();
    private static volatile Consumer<AppPanelId> opener = id -> {};

    private DrillThroughCoordinator()
    {
    }

    static void configureOpener(Consumer<AppPanelId> panelOpener)
    {
        opener = panelOpener == null ? (id -> {}) : panelOpener;
    }

    static void openLedgerWithContext(String context)
    {
        CONTEXT.set(context == null ? "" : context);
        opener.accept(AppPanelId.LEDGER_REGISTER);
    }

    static String consumeContext()
    {
        String context = CONTEXT.getAndSet("");
        return context == null ? "" : context;
    }
}
