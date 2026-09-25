package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreferenceConsumerMatrixTest
{
    @Test
    void everySavedSettingsValueHasAnActiveConsumerOrIsExplicitlyDeferred()
    {
        assertEquals(Set.of(
                        "themePreference",
                        "useNativeWindowDecorations",
                        "rememberWindowState",
                        "defaultPrivilege",
                        "correctionMethod",
                        "closedPeriodPolicy",
                        "requireReopenReason",
                        "defaultReopenScope",
                        "confirmEnteredTransactionDeletion",
                        "periodStartDayOfMonth",
                        "currencySymbol",
                        "moneyPrintFormat",
                        "dateDisplayFormat"),
                PreferenceConsumerMatrix.entries().stream()
                        .map(PreferenceConsumerMatrix.Entry::key)
                        .collect(Collectors.toSet()));

        PreferenceConsumerMatrix.entries().forEach(entry ->
        {
            assertFalse(entry.scope().isBlank());
            assertFalse(entry.userMessage().isBlank());
            if (entry.status() == PreferenceConsumerMatrix.Status.ACTIVE)
            {
                assertTrue(entry.enabledInSettings());
                assertFalse(entry.productionConsumer().isBlank());
            }
            else
            {
                assertFalse(entry.enabledInSettings());
                assertTrue(entry.productionConsumer().isBlank());
            }
        });
    }

    @Test
    void compatibilityOnlyValuesAreDisabledAndDeferred()
    {
        assertEquals(PreferenceConsumerMatrix.Status.DEFERRED,
                PreferenceConsumerMatrix.entry("defaultPrivilege").status());
        PreferenceConsumerMatrix.Entry defaultPrivilege = PreferenceConsumerMatrix.entry("defaultPrivilege");
        assertEquals(PreferenceConsumerMatrix.Status.DEFERRED, defaultPrivilege.status());
        assertTrue(defaultPrivilege.userMessage().contains("signed-in account"));
        assertTrue(defaultPrivilege.userMessage().contains("company role assignments"));
        assertFalse(defaultPrivilege.userMessage().contains("not implemented"));
        assertEquals(PreferenceConsumerMatrix.Status.DEFERRED,
                PreferenceConsumerMatrix.entry("defaultReopenScope").status());
    }
}
