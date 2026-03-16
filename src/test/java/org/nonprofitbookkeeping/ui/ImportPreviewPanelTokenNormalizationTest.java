package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.NormalBalance;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ImportPreviewPanelTokenNormalizationTest component.
 */
public class ImportPreviewPanelTokenNormalizationTest
{
    @Test
    public void parseAccountTypeToken_supportsCommonAliasesAndCase()
    {
        assertEquals(AccountType.INCOME, ImportPreviewPanel.parseAccountTypeToken("revenue"));
        assertEquals(AccountType.ASSET, ImportPreviewPanel.parseAccountTypeToken("asset"));
        assertEquals(AccountType.BANK, ImportPreviewPanel.parseAccountTypeToken("bank"));
    }

    @Test
    public void parseNormalBalanceToken_supportsDrCrAliases()
    {
        assertEquals(NormalBalance.DEBIT, ImportPreviewPanel.parseNormalBalanceToken("dr"));
        assertEquals(NormalBalance.CREDIT, ImportPreviewPanel.parseNormalBalanceToken("cr"));
        assertEquals(NormalBalance.DEBIT, ImportPreviewPanel.parseNormalBalanceToken("debit"));
    }
}
