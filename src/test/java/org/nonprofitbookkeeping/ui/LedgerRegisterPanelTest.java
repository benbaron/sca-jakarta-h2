package org.nonprofitbookkeeping.ui;

import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.service.LedgerQueryService;
import org.nonprofitbookkeeping.service.AccountingJournalProjection;
import org.nonprofitbookkeeping.service.TransactionView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LedgerRegisterPanelTest component.
 */
public class LedgerRegisterPanelTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @Test
    public void toRow_mapsBlanksToNoneAndPreservesSplitCount()
    {
        LedgerQueryService.LedgerRow source = new LedgerQueryService.LedgerRow(
                101L,
                LocalDate.of(2026, 3, 13),
                "",
                "",
                "",
                3);

        LedgerRegisterPanel.Row row = LedgerRegisterPanel.toRow(source);

        assertEquals(101L, row.id());
        assertEquals("2026-03-13", row.date());
        assertEquals("(none)", row.payee());
        assertEquals("(none)", row.memo());
        assertEquals("(none)", row.bank());
        assertEquals("3", row.splitCount());
        assertEquals("Posted", row.status());
    }

    @Test
    public void toRow_mapsTransactionViewForServiceBackedRegister()
    {
        TransactionView view = new TransactionView(
                202L,
                LocalDate.of(2026, 4, 5),
                null,
                "",
                "Donation",
                null,
                null,
                "ENTERED",
                List.of(
                        new TransactionView.Line(1L, 10L, "1000", "Cash", 20L, "GEN", "General", null, null, null,
                                new BigDecimal("25.00"), BigDecimal.ZERO, false, ""),
                        new TransactionView.Line(2L, 11L, "4000", "Income", 20L, "GEN", "General", null, null, null,
                                BigDecimal.ZERO, new BigDecimal("25.00"), false, "")));

        LedgerRegisterPanel.Row row = LedgerRegisterPanel.toRow(view);

        assertEquals(202L, row.id());
        assertEquals("2026-04-05", row.date());
        assertEquals("(none)", row.payee());
        assertEquals("Donation", row.memo());
        assertEquals("(none)", row.bank());
        assertEquals("2", row.splitCount());
        assertEquals("ENTERED", row.status());
    }

    @Test
    public void editorContextUsesStableTransactionId()
    {
        assertEquals("Edit transaction Txn #202", LedgerRegisterPanel.editorContext(202L));
        assertEquals(202L, TransactionEditorPanel.transactionIdFromContext("Load transaction Txn #202"));
    }

    @Test
    public void registerPrimaryButtonsExposeNewAndDisabledOpenSelected()
    {
        LedgerRegisterPanel panel = FxTestSupport.onFx(LedgerRegisterPanel::new);

        FxTestSupport.onFx(() -> {
            Button newButton = (Button) panel.root().lookup("#ledgerRegisterNewButton");
            Button openSelected = (Button) panel.root().lookup("#ledgerRegisterOpenSelectedButton");

            assertEquals("New", newButton.getText());
            assertEquals("Open Selected", openSelected.getText());
            assertTrue(openSelected.isDisabled());
            assertEquals("New transaction", LedgerRegisterPanel.newEditorContext());
            return null;
        });
    }

    @Test
    public void renderJournal_formatsDrCrUsingJournalLineGetters()
    {
        LedgerRegisterPanel.Row row = new LedgerRegisterPanel.Row(
                77L,
                "2026-03-13",
                "Acme",
                "Office supplies",
                "1000-BANK",
                "2",
                "Posted");

        AccountingJournalProjection projection = new AccountingJournalProjection(
                77L,
                LocalDate.of(2026, 3, 13),
                "Acme",
                "Office supplies",
                List.of(
                        new AccountingJournalProjection.Line("6100-EXP", "Supplies Expense", "GEN", "General",
                                new BigDecimal("25.00"), BigDecimal.ZERO, ""),
                        new AccountingJournalProjection.Line("1000-BANK", "Operating Bank", "GEN", "General",
                                BigDecimal.ZERO, new BigDecimal("25.00"), "")));

        String rendered = LedgerRegisterPanel.renderJournal(row, projection);

        assertTrue(rendered.contains("Txn #77 | Date 2026-03-13 | Payee Acme"));
        assertTrue(rendered.contains("Memo: Office supplies"));
        assertTrue(rendered.contains("6100-EXP Supplies Expense | Fund GEN | DR 25.00 | CR 0"));
        assertTrue(rendered.contains("1000-BANK Operating Bank | Fund GEN | DR 0 | CR 25.00"));
        assertTrue(rendered.contains("Debits=25.00 Credits=25.00"));
    }
}
