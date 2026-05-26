package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class BudgetCategoryAdminServiceTest
{
    private final BudgetCategoryAdminService service = new BudgetCategoryAdminService(null);

    @Test
    public void upsert_rejectsBlankCode()
    {
        assertThrows(IllegalArgumentException.class,
                () -> service.upsert(" ", "Event Food", true));
    }

    @Test
    public void upsert_rejectsBlankName()
    {
        assertThrows(IllegalArgumentException.class,
                () -> service.upsert("EVENT_FOOD", " ", true));
    }
}
