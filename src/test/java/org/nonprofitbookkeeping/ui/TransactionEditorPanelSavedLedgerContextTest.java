package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TransactionEditorPanelSavedLedgerContextTest
{
    @Test
    public void savedLedgerContext_identifiesSavedTransactionForRegisterDrillThrough()
    {
        assertEquals("Saved transaction Txn #42", TransactionEditorPanel.savedLedgerContext(42L));
    }
}
