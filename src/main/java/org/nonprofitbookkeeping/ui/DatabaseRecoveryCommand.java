package org.nonprofitbookkeeping.ui;

/** Explicit operator commands available while a selected database cannot be opened. */
enum DatabaseRecoveryCommand
{
    RETRY_CURRENT,
    SELECT_EXISTING,
    CREATE_NEW
}
