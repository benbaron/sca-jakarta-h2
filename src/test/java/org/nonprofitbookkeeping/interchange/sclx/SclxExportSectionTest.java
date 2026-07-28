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
        assertTrue(SclxExportSection.COUNTERPARTIES.deferred());
    }
}
