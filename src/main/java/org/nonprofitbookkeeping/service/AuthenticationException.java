package org.nonprofitbookkeeping.service;

/** Expected authentication failure with a safe user-facing message. */
public class AuthenticationException extends RuntimeException
{
    public AuthenticationException(String message)
    {
        super(message);
    }
}
