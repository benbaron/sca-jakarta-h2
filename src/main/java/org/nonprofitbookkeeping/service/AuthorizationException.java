package org.nonprofitbookkeeping.service;

/** Safe failure raised when an authenticated session lacks a required runtime permission. */
public class AuthorizationException extends SecurityException
{
    private final ApplicationPermission requiredPermission;

    public AuthorizationException(ApplicationPermission requiredPermission, String message)
    {
        super(message);
        this.requiredPermission = requiredPermission;
    }

    public ApplicationPermission requiredPermission()
    {
        return requiredPermission;
    }
}
