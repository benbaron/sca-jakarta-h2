package org.nonprofitbookkeeping.interchange.sclx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SclxVersionTest
{
    @Test
    void readsGovernedInputVersions()
    {
        assertEquals(SclxVersion.V1_0, SclxVersion.parseReadable("1.0"));
        assertEquals(SclxVersion.V1_2, SclxVersion.parseReadable("1.2"));
        assertEquals(SclxVersion.V1_3, SclxVersion.parseReadable("1.3"));
    }

    @Test
    void writesOnlyVersionOnePointThree()
    {
        assertFalse(SclxVersion.V1_0.writable());
        assertFalse(SclxVersion.V1_2.writable());
        assertTrue(SclxVersion.V1_3.writable());
        assertEquals(SclxVersion.V1_3, SclxVersion.writerVersion());
    }

    @Test
    void rejectsMissingMalformedAndUnsupportedVersions()
    {
        assertThrows(IllegalArgumentException.class, () -> SclxVersion.parseReadable(null));
        assertThrows(IllegalArgumentException.class, () -> SclxVersion.parseReadable(""));
        assertThrows(IllegalArgumentException.class, () -> SclxVersion.parseReadable("1.1"));
        assertThrows(IllegalArgumentException.class, () -> SclxVersion.parseReadable("2.0"));
    }
}
