package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InspectorPresentationModelTest component.
 */
class InspectorPresentationModelTest
{
    @Test
    void panelBody_usesSharedContextFormat()
    {
        String body = InspectorPresentationModel.panelBody(
                "Diagnostics",
                "DIAGNOSTICS",
                "BARONY-RED",
                "ALL",
                "Health checks",
                "Enter",
                "Use Find");

        assertTrue(body.contains("Panel: Diagnostics"));
        assertTrue(body.contains("Active company: BARONY-RED"));
        assertTrue(body.contains("Date range: ALL"));
        assertTrue(body.contains("Capabilities: Health checks"));
    }
}
