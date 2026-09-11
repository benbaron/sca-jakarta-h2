package org.nonprofitbookkeeping.service;

/** Immutable selected-company Activity projection for administration UI and tests. */
public record ActivityView(
        Long id,
        String code,
        String name,
        boolean active)
{
}
