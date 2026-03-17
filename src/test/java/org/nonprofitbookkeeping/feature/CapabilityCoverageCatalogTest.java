package org.nonprofitbookkeeping.feature;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CapabilityCoverageCatalogTest component.
 */
public class CapabilityCoverageCatalogTest
{
    @Test
    public void allCapabilitiesHaveCrossLayerCoverageEntries()
    {
        Map<Capability, CapabilityCoverage> coverage = CapabilityCoverageCatalog.all();

        assertEquals(EnumSet.allOf(Capability.class), coverage.keySet());

        for (Capability capability : Capability.values())
        {
            CapabilityCoverage row = coverage.get(capability);
            assertNotNull(row);
            assertEquals(capability, row.capability());
            assertFalse(row.uiSurface().isBlank());
            assertFalse(row.modelContract().isBlank());
            assertFalse(row.actionContract().isBlank());
            assertFalse(row.testContract().isBlank());
        }
    }

    @Test
    public void coverageContractsReferenceLoadableModelAndActionTypes() throws Exception
    {
        for (CapabilityCoverage row : CapabilityCoverageCatalog.all().values())
        {
            String modelClassName = row.modelContract();
            int actionEnumTypeEnd = row.actionContract().lastIndexOf('.');
            String actionEnumType = row.actionContract().substring(0, actionEnumTypeEnd);

            Class.forName(modelClassName);
            Class<?> actionType = Class.forName(actionEnumType);
            assertEquals("AppActionId", actionType.getSimpleName());
        }
    }
}
