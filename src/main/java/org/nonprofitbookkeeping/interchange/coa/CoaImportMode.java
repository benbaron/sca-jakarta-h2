package org.nonprofitbookkeeping.interchange.coa;

/** Supported Chart of Accounts JSON commit targets. */
public enum CoaImportMode
{
    CREATE_NEW_CHART,
    MERGE_BY_CODE,
    MAP_CODES
}
