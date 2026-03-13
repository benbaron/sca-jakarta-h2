package org.nonprofitbookkeeping.model;

import java.util.List;

/**
 * Plugin manager state.
 */
public record PluginState(List<String> enabledPluginIds, List<String> blockedPluginIds)
{
}
