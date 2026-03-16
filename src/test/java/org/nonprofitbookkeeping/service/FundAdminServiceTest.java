package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.FundType;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * FundAdminServiceTest component.
 */
public class FundAdminServiceTest
{
    private final FundAdminService service = new FundAdminService(null);

    @Test
    public void upsert_rejectsBlankCode()
    {
        assertThrows(IllegalArgumentException.class,
                () -> service.upsert(" ", "Fund", FundType.UNRESTRICTED, true));
    }

    @Test
    public void upsert_rejectsBlankName()
    {
        assertThrows(IllegalArgumentException.class,
                () -> service.upsert("GEN", " ", FundType.UNRESTRICTED, true));
    }

    @Test
    public void upsert_rejectsNullFundType()
    {
        assertThrows(IllegalArgumentException.class,
                () -> service.upsert("GEN", "General", null, true));
    }
}
