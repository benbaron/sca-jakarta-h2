package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.AccountFunction;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.NormalBalance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    public void formState_roundTripsSubtypeParentCodeAndNoFunction()
    {
        ChartOfAccountsPanel.FormState state = FxTestSupport.onFx(() -> {
            ChartOfAccountsPanel panel = new ChartOfAccountsPanel();
            ChartOfAccountsPanel.FormState expected = new ChartOfAccountsPanel.FormState(
                    "1100",
                    "Accounts Receivable",
                    AccountType.ASSET,
                    null,
                    NormalBalance.DEBIT,
                    AccountSubtype.RECEIVABLE,
                    "1000",
                    true);
            panel.setFormStateForTests(expected);
            assertEquals("None", panel.functionDisplayForTests());
            return panel.readFormStateForTests();
        });

        assertEquals("1100", state.code());
        assertEquals("Accounts Receivable", state.name());
        assertEquals(AccountType.ASSET, state.accountType());
        assertNull(state.accountFunction());
        assertEquals(NormalBalance.DEBIT, state.normalBalance());
        assertEquals(AccountSubtype.RECEIVABLE, state.subtype());
        assertEquals("1000", state.parentCode());
    }

    @Test
    public void functionField_displaysAssignsAndClearsBankWithoutSentinelPersistence()
    {
        ChartOfAccountsPanel.FormState cleared = FxTestSupport.onFx(() -> {
            ChartOfAccountsPanel panel = new ChartOfAccountsPanel();
            panel.setFormStateForTests(new ChartOfAccountsPanel.FormState(
                    "1010",
                    "Operating Checking",
                    AccountType.ASSET,
                    AccountFunction.BANK,
                    NormalBalance.DEBIT,
                    AccountSubtype.CASH,
                    "",
                    true));

            assertEquals("BANK", panel.functionDisplayForTests());
            assertEquals(AccountFunction.BANK, panel.readFormStateForTests().accountFunction());

            panel.clearFunctionForTests();
            assertEquals("None", panel.functionDisplayForTests());
            return panel.readFormStateForTests();
        });

        assertNull(cleared.accountFunction());
    }
}
