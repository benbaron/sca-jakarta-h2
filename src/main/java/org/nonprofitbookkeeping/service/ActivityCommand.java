package org.nonprofitbookkeeping.service;

/** Immutable create/update command for one H2-backed Activity. */
public record ActivityCommand(
        Long id,
        String code,
        String name,
        boolean active)
{
}
