package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.repository.LedgerQueryRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LedgerQueryServiceTest component.
 */
public class LedgerQueryServiceTest
{
    @Test
    public void listRecent_returnsRepositoryOrderWithExpectedRowShape()
    {
        LedgerQueryRepository repository = new StubLedgerQueryRepository(
                List.of(
                        new LedgerQueryRepository.LedgerRecentRow(44L, LocalDate.of(2026, 3, 2), "", "", "", 1),
                        new LedgerQueryRepository.LedgerRecentRow(43L, LocalDate.of(2026, 3, 1), "Acme Org", "Office supplies", "1000-BANK", 2)),
                List.of(),
                List.of());
        LedgerQueryService service = new LedgerQueryService(repository);

        List<LedgerQueryService.LedgerRow> rows = service.listRecent(10);

        assertEquals(2, rows.size());
        assertEquals(44L, rows.get(0).id());
        assertEquals(LocalDate.of(2026, 3, 2), rows.get(0).date());
        assertEquals("", rows.get(0).payee());
        assertEquals("", rows.get(0).memo());
        assertEquals("", rows.get(0).bank());
        assertEquals(1, rows.get(0).splitCount());

        assertEquals(43L, rows.get(1).id());
        assertEquals(LocalDate.of(2026, 3, 1), rows.get(1).date());
        assertEquals("Acme Org", rows.get(1).payee());
        assertEquals("Office supplies", rows.get(1).memo());
        assertEquals("1000-BANK", rows.get(1).bank());
        assertEquals(2, rows.get(1).splitCount());
    }

    @Test
    public void journalForTxn_mapsDebitCreditByNormalBalanceAndSignedAmount()
    {
        LedgerQueryRepository repository = new StubLedgerQueryRepository(
                List.of(),
                List.of(
                        new LedgerQueryRepository.LedgerJournalRow(LocalDate.of(2026, 4, 1), 77L, "Memo", "Payee", "1100", "AR", "GEN", "General", NormalBalance.DEBIT, new BigDecimal("50.00")),
                        new LedgerQueryRepository.LedgerJournalRow(LocalDate.of(2026, 4, 1), 77L, "Memo", "Payee", "2000", "AP", "GEN", "General", NormalBalance.DEBIT, new BigDecimal("-10.00")),
                        new LedgerQueryRepository.LedgerJournalRow(LocalDate.of(2026, 4, 1), 77L, "Memo", "Payee", "4100", "Income", "GEN", "General", NormalBalance.CREDIT, new BigDecimal("25.00")),
                        new LedgerQueryRepository.LedgerJournalRow(LocalDate.of(2026, 4, 1), 77L, "Memo", "Payee", "4200", "Contra Income", "GEN", "General", NormalBalance.CREDIT, new BigDecimal("-5.00"))),
                List.of());
        LedgerQueryService service = new LedgerQueryService(repository);

        List<JournalLine> lines = service.journalForTxn(77L);

        assertEquals(4, lines.size());

        assertAmountShape(lines.get(0), "50.00", "0");
        assertAmountShape(lines.get(1), "0", "10.00");
        assertAmountShape(lines.get(2), "0", "25.00");
        assertAmountShape(lines.get(3), "5.00", "0");

        assertEquals("1100", lines.get(0).getAccountCode());
        assertEquals("AR", lines.get(0).getAccountName());
        assertEquals("GEN", lines.get(0).getFundCode());
        assertEquals("General", lines.get(0).getFundName());
    }

    @Test
    public void listBankLedgerActivity_mapsCanonicalBankSplitMoneyAndClearedFacts()
    {
        LedgerQueryRepository repository = new StubLedgerQueryRepository(
                List.of(),
                List.of(),
                List.of(
                        new LedgerQueryRepository.BankLedgerActivityRow(
                                501L, 88L, LocalDate.of(2026, 5, 9), 17L, "Checking ••••1234",
                                "1010", "Operating Checking", "GEN", "General", "Vendor", "Supplies",
                                NormalBalance.DEBIT, new BigDecimal("-42.75"), true, LocalDate.of(2026, 5, 12)),
                        new LedgerQueryRepository.BankLedgerActivityRow(
                                502L, 89L, LocalDate.of(2026, 5, 10), 17L, "Checking ••••1234",
                                "1010", "Operating Checking", "GEN", "General", "Donor", "Deposit",
                                NormalBalance.DEBIT, new BigDecimal("100.00"), false, null)));
        LedgerQueryService service = new LedgerQueryService(repository);

        List<LedgerQueryService.BankLedgerRow> rows =
                service.listBankLedgerActivity("ACME", 17L, 1000);

        assertEquals(2, rows.size());
        assertEquals(new BigDecimal("0"), rows.get(0).debit());
        assertEquals(new BigDecimal("42.75"), rows.get(0).credit());
        assertTrue(rows.get(0).cleared());
        assertEquals(LocalDate.of(2026, 5, 12), rows.get(0).clearedOn());
        assertEquals(new BigDecimal("100.00"), rows.get(1).debit());
        assertEquals(new BigDecimal("0"), rows.get(1).credit());
        assertFalse(rows.get(1).cleared());
        assertEquals(88L, rows.get(0).transactionId());
        assertEquals(501L, rows.get(0).splitId());
        assertEquals("Operating Checking", rows.get(0).accountName());
    }

    private void assertAmountShape(JournalLine line, String debit, String credit)
    {
        assertEquals(new BigDecimal(debit), line.getDebit());
        assertEquals(new BigDecimal(credit), line.getCredit());
    }

    private record StubLedgerQueryRepository(List<LedgerQueryRepository.LedgerRecentRow> recentRows,
                                             List<LedgerQueryRepository.LedgerJournalRow> journalRows,
                                             List<LedgerQueryRepository.BankLedgerActivityRow> bankRows)
            implements LedgerQueryRepository
    {
        @Override
        public List<LedgerRecentRow> listRecent(int maxRows)
        {
            return recentRows;
        }

        @Override
        public List<LedgerJournalRow> journalForTxn(Long txnId)
        {
            return journalRows;
        }

        @Override
        public List<BankLedgerActivityRow> listBankLedgerActivity(
                String companyCode,
                Long configuredBankAccountId,
                int maxRows)
        {
            return bankRows;
        }
    }
}
