package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxExportSectionTest
{
    @Test
    void everyExportedSectionHasAStableOutputPath()
    {
        Arrays.stream(SclxExportSection.values())
                .filter(SclxExportSection::exported)
                .forEach(section -> assertNotNull(section.outputPath(), section.name()));
    }

    @Test
    void excludedSectionsHaveNoOutputPath()
    {
        Arrays.stream(SclxExportSection.values())
                .filter(section -> !section.exported())
                .forEach(section -> assertNull(section.outputPath(), section.name()));

        assertFalse(SclxExportSection.USERS_AND_AUTHENTICATION.exported());
        assertFalse(SclxExportSection.COMPATIBILITY_LEDGER.exported());
        assertTrue(SclxExportSection.TRANSACTIONS.exported());
        assertTrue(SclxExportSection.ACTIVITIES.includedByCurrentSnapshot());
        assertFalse(SclxExportSection.ACTIVITIES.deferred());
        assertTrue(SclxExportSection.COUNTERPARTIES.includedByCurrentSnapshot());
        assertFalse(SclxExportSection.COUNTERPARTIES.deferred());
        assertTrue(SclxExportSection.SUPPLEMENTAL_DETAILS.includedByCurrentSnapshot());
        assertFalse(SclxExportSection.SUPPLEMENTAL_DETAILS.deferred());
        assertTrue(SclxExportSection.BANK_CONFIGURATION.includedByCurrentSnapshot());
        assertFalse(SclxExportSection.BANK_CONFIGURATION.deferred());
        assertTrue(SclxExportSection.BANK_STATEMENT_FACTS.includedByCurrentSnapshot());
        assertFalse(SclxExportSection.BANK_STATEMENT_FACTS.deferred());
        assertTrue(SclxExportSection.RECONCILIATION.includedByCurrentSnapshot());
        assertFalse(SclxExportSection.RECONCILIATION.deferred());
        assertTrue(SclxExportSection.FIXED_ASSETS.includedByCurrentSnapshot());
        assertFalse(SclxExportSection.FIXED_ASSETS.deferred());
    }
}
