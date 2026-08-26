package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.AccountFunction;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.NormalBalance;

/** Immutable create/update command for one H2-backed Chart of Accounts record. */
public record AccountCommand(
        Long id,
        String code,
        String name,
        AccountType accountType,
        AccountFunction accountFunction,
        NormalBalance normalBalance,
        AccountSubtype subtype,
        String parentCode,
        boolean active)
{
}
