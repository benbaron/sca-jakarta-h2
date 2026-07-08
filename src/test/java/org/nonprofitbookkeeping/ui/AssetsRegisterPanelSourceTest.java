package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.Fund;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Guardrails for Asset Register selector display labels. */
class AssetsRegisterPanelSourceTest
{
    @Test
    void accountAndFundLabelsUseBusinessCodesAndNames()
    {
        Account account = new Account();
        account.setCode("1500");
        account.setName("Equipment");
        Fund fund = new Fund();
        fund.setCode("GEN");
        fund.setName("General Fund");

        assertEquals("1500 — Equipment", AssetsRegisterPanel.accountLabel(account));
        assertEquals("GEN — General Fund", AssetsRegisterPanel.fundLabel(fund));
    }
}
