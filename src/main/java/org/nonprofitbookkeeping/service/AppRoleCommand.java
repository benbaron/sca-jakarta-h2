package org.nonprofitbookkeeping.service;

/** Stable-ID global role maintenance command. */
public record AppRoleCommand(
        Long id,
        String code,
        String name,
        String description,
        boolean active,
        String actor)
{
}
