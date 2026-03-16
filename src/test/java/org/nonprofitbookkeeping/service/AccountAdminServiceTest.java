package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.NormalBalance;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AccountAdminServiceTest component.
 */
public class AccountAdminServiceTest
{
    private final AccountAdminService service = new AccountAdminService(null);

    @Test
    public void upsert_rejectsBlankCode()
    {
        assertThrows(IllegalArgumentException.class,
                () -> service.upsert(" ", "Name", AccountType.ASSET, NormalBalance.DEBIT, null, null, true));
    }

    @Test
    public void upsert_rejectsBlankName()
    {
        assertThrows(IllegalArgumentException.class,
                () -> service.upsert("1000", " ", AccountType.ASSET, NormalBalance.DEBIT, null, null, true));
    }

    @Test
    public void upsert_rejectsNullType()
    {
        assertThrows(IllegalArgumentException.class,
                () -> service.upsert("1000", "Cash", null, NormalBalance.DEBIT, null, null, true));
    }

    @Test
    public void upsert_rejectsNullNormalBalance()
    {
        assertThrows(IllegalArgumentException.class,
                () -> service.upsert("1000", "Cash", AccountType.ASSET, null, null, null, true));
    }
}
