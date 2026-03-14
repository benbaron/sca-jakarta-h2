package org.nonprofitbookkeeping.feature;

/**
 * Minimal cross-layer coverage contract for a capability.
 */
public record CapabilityCoverage(
        Capability capability,
        String uiSurface,
        String modelContract,
        String actionContract,
        String testContract)
{
}
