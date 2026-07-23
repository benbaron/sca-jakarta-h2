package org.nonprofitbookkeeping.interchange;

/** Risk and target mode for one interchange operation. */
public enum InterchangeOperationMode
{
    PREVIEW_ONLY,
    VALIDATE_ONLY,
    COMMIT_ACTIVE_COMPANY,
    CREATE_NEW_COMPANY,
    CREATE_NEW_DATABASE,
    EXPORT
}
