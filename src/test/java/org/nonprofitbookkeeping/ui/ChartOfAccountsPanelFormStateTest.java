package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.NormalBalance;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ChartOfAccountsPanelFormStateTest component.
 */
public class ChartOfAccountsPanelFormStateTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @Test
    public void formState_roundTripsSubtypeAndParentCode()
    {
        ChartOfAccountsPanel.FormState state = FxTestSupport.onFx(() -> {
            ChartOfAccountsPanel panel = new ChartOfAccountsPanel();
            ChartOfAccountsPanel.FormState expected = new ChartOfAccountsPanel.FormState(
                    "1100",
                    "Accounts Receivable",
                    AccountType.ASSET,
                    NormalBalance.DEBIT,
                    AccountSubtype.RECEIVABLE,
                    "1000",
                    true);
            panel.setFormStateForTests(expected);
            return panel.readFormStateForTests();
        });

        assertEquals("1100", state.code());
        assertEquals("Accounts Receivable", state.name());
        assertEquals(AccountType.ASSET, state.accountType());
        assertEquals(NormalBalance.DEBIT, state.normalBalance());
        assertEquals(AccountSubtype.RECEIVABLE, state.subtype());
        assertEquals("1000", state.parentCode());
    }
}
