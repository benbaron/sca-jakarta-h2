package org.nonprofitbookkeeping.model;

import java.util.List;

/**
 * Persisted database-file selection context for multi-database workflows.
 */
public record DatabaseSelectionState(String activeDatabasePath, List<String> recentDatabasePaths)
{
}
