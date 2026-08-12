package org.nonprofitbookkeeping.service;

/** Stable-ID application-user maintenance command. */
public record AppUserCommand(
        Long id,
        String username,
        String displayName,
        String email,
        boolean active,
        String actor)
{
}
