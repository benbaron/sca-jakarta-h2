package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDebugTest
{
    @AfterEach
    void clearProperties()
    {
        System.clearProperty("sca.ui.debug");
        System.clearProperty("sca.ui.debug.ledger-register");
        System.clearProperty("sca.ui.debug.transaction-editor");
    }

    @Test
    void debugLoggingIsEnabledByDefault()
    {
        assertTrue(UiDebug.isEnabled("ledger-register"));
    }

    @Test
    void globalPropertyDisablesAllAreas()
    {
        System.setProperty("sca.ui.debug", "false");

        assertFalse(UiDebug.isEnabled("ledger-register"));
        assertFalse(UiDebug.isEnabled("transaction-editor"));
    }

    @Test
    void areaPropertyOverridesGlobalProperty()
    {
        System.setProperty("sca.ui.debug", "false");
        System.setProperty("sca.ui.debug.ledger-register", "true");

        assertTrue(UiDebug.isEnabled("ledger-register"));
        assertFalse(UiDebug.isEnabled("transaction-editor"));
    }

    @Test
    void areaPropertyCanDisableOneArea()
    {
        System.setProperty("sca.ui.debug.ledger-register", "off");

        assertFalse(UiDebug.isEnabled("ledger-register"));
        assertTrue(UiDebug.isEnabled("transaction-editor"));
    }
}
