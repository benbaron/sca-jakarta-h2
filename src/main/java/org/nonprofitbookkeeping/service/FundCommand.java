package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.FundType;

import java.time.LocalDate;

/** Immutable create/update command for one H2-backed fund. */
public record FundCommand(
        Long id,
        String code,
        String name,
        FundType fundType,
        boolean active,
        Long parentFundId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String restrictionText)
{
}
