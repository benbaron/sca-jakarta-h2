package org.nonprofitbookkeeping.service;

/** Raised when a write or export would cross or lack authoritative company ownership. */
public class CompanyOwnershipException extends IllegalStateException
{
    public CompanyOwnershipException(String message)
    {
        super(message);
    }
}
