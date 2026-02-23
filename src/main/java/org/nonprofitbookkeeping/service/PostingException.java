package org.nonprofitbookkeeping.service;

/**
 * Represents the PostingException component in the nonprofit bookkeeping application.
 */
public class PostingException extends RuntimeException
{
    public PostingException(String message)
    {
        super(message);
    }
}
