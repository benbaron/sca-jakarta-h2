package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.service.TransactionCommand;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TransactionEditorPanelCommandMappingTest
{
    @Test
    public void toTransactionCommand_mapsSignedRowsToDebitCreditStableIds() throws Exception
    {
        Account cash = account(10L, "1000");
        Account income = account(20L, "4000");
        Fund operating = fund(30L, "OPERATING");

        TransactionCommand command = TransactionEditorPanel.toTransactionCommand(
                "2026-04-15",
                "Donation",
                List.of(
                        new TransactionEditorPanel.SplitRow("1000", "OPERATING", "125.00", "", "", "false", "cash"),
                        new TransactionEditorPanel.SplitRow("4000", "OPERATING", "-125.00", "", "", "false", "income")),
                List.of(cash, income),
                List.of(operating));

        assertEquals(LocalDate.of(2026, 4, 15), command.date());
        assertEquals("Donation", command.memo());
        assertEquals(2, command.lines().size());
        assertEquals(10L, command.lines().get(0).accountId());
        assertEquals(30L, command.lines().get(0).fundId());
        assertEquals(new BigDecimal("125.00"), command.lines().get(0).debit());
        assertEquals(BigDecimal.ZERO, command.lines().get(0).credit());
        assertEquals(20L, command.lines().get(1).accountId());
        assertEquals(BigDecimal.ZERO, command.lines().get(1).debit());
        assertEquals(new BigDecimal("125.00"), command.lines().get(1).credit());
    }

    private static Account account(Long id, String code) throws Exception
    {
        Account account = new Account();
        setId(account, id);
        account.setCode(code);
        return account;
    }

    private static Fund fund(Long id, String code) throws Exception
    {
        Fund fund = new Fund();
        setId(fund, id);
        fund.setCode(code);
        return fund;
    }

    private static void setId(Object target, Long id) throws Exception
    {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
