package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.model.TxnSupplementalLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SclxSupplementalDetailSnapshotTest
{
    private static final UUID TRANSACTION_UUID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");

    @Test
    void exportsEverySupplementalFieldInDeterministicBusinessOrder()
    {
        Fixture fixture = fixture("TEST", TRANSACTION_UUID);
        TxnSupplementalLine deferred = detail(
                fixture.transaction(),
                1,
                "DEFERRED_REVENUE",
                "line 2",
                "Registrant",
                "Registration deferred revenue",
                "REG-1",
                "50.0000",
                null,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 9, 30),
                "Recognize over event period");
        TxnSupplementalLine receivable = detail(
                fixture.transaction(),
                0,
                "RECEIVABLE",
                "line 1",
                "Donor",
                "Pledge receivable",
                "INV-100",
                "125.5000",
                LocalDate.of(2026, 8, 15),
                null,
                null,
                "Expected payment");

        SclxExportDocument document = new SclxCoreSnapshotAssembler().assemble(
                fixture.company(),
                fixture.accounts(),
                List.of(fixture.fund()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(fixture.transaction()),
                fixture.splits(),
                List.of(deferred, receivable),
                Instant.parse("2026-07-28T04:30:00Z"));

        List<SclxSupplementalDetailExtension.Entry> entries =
                SclxSupplementalDetailExtension.entries(document.extensions());
        String transactionId = SclxPortableIdentity.transaction("TEST", TRANSACTION_UUID.toString());

        assertEquals(List.of("RECEIVABLE", "DEFERRED_REVENUE"),
                entries.stream().map(SclxSupplementalDetailExtension.Entry::kind).toList());
        assertEquals(SclxPortableIdentity.supplementalDetail(transactionId, 1),
                entries.get(0).supplementalDetailId());
        assertEquals(transactionId, entries.get(0).transactionId());
        assertEquals(0, entries.get(0).lineOrder());
        assertEquals("line 1", entries.get(0).entryRef());
        assertEquals("Donor", entries.get(0).counterparty());
        assertEquals("Pledge receivable", entries.get(0).description());
        assertEquals("INV-100", entries.get(0).reference());
        assertEquals(new BigDecimal("125.5000"), entries.get(0).amount());
        assertEquals(LocalDate.of(2026, 8, 15), entries.get(0).dueDate());
        assertEquals(LocalDate.of(2026, 7, 1), entries.get(1).startDate());
        assertEquals(LocalDate.of(2026, 9, 30), entries.get(1).endDate());
    }

    @Test
    void rejectsSupplementalDetailOutsideSelectedTransactionSnapshot()
    {
        Fixture selected = fixture("TEST", TRANSACTION_UUID);
        Fixture foreign = fixture(
                "OTHER",
                UUID.fromString("22222222-2222-2222-2222-222222222222"));
        TxnSupplementalLine foreignDetail = detail(
                foreign.transaction(),
                0,
                "PAYABLE",
                null,
                "Vendor",
                "Foreign payable",
                "BILL-1",
                "10.0000",
                LocalDate.of(2026, 8, 31),
                null,
                null,
                null);

        assertThrows(IllegalArgumentException.class, () -> new SclxCoreSnapshotAssembler().assemble(
                selected.company(),
                selected.accounts(),
                List.of(selected.fund()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(selected.transaction()),
                selected.splits(),
                List.of(foreignDetail),
                Instant.EPOCH));
    }

    @Test
    void validatorRejectsUnresolvedAndDuplicateSupplementalIdentities()
    {
        Fixture fixture = fixture("TEST", TRANSACTION_UUID);
        SclxExportDocument base = new SclxCoreSnapshotAssembler().assemble(
                fixture.company(),
                fixture.accounts(),
                List.of(fixture.fund()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(fixture.transaction()),
                fixture.splits(),
                List.of(),
                Instant.parse("2026-07-28T04:30:00Z"));
        String missingTransaction = "transaction:TEST:missing";
        MapBuilder extensions = new MapBuilder(base.extensions().scaJakartaH2());
        extensions.put(SclxSupplementalDetailExtension.KEY, List.of(
                SclxSupplementalDetailExtension.entry(
                        "supplemental-detail:missing:1",
                        missingTransaction,
                        0,
                        "PAYABLE",
                        null,
                        "Vendor",
                        "Missing transaction payable",
                        null,
                        BigDecimal.ONE,
                        LocalDate.of(2026, 8, 31),
                        null,
                        null,
                        null)));
        SclxExportDocument unresolved = copy(base, extensions.values());
        assertThrows(IllegalArgumentException.class,
                () -> new SclxExportDocumentValidator().validate(unresolved));

        String transactionId = base.transactions().get(0).transactionId();
        java.util.Map<String, Object> duplicate = SclxSupplementalDetailExtension.entry(
                "supplemental-detail:duplicate",
                transactionId,
                0,
                "OTHER_ASSET",
                null,
                null,
                "Duplicate detail",
                null,
                BigDecimal.ONE,
                null,
                null,
                null,
                null);
        extensions = new MapBuilder(base.extensions().scaJakartaH2());
        extensions.put(SclxSupplementalDetailExtension.KEY, List.of(duplicate, duplicate));
        SclxExportDocument duplicated = copy(base, extensions.values());
        assertThrows(IllegalArgumentException.class,
                () -> new SclxExportDocumentValidator().validate(duplicated));
    }

    private static SclxExportDocument copy(
            SclxExportDocument base,
            java.util.Map<String, Object> extensionValues)
    {
        return new SclxExportDocument(
                base.format(),
                base.version(),
                base.exportedAt(),
                base.organization(),
                base.chartOfAccounts(),
                base.funds(),
                base.budgets(),
                base.transactions(),
                new SclxExportDocument.Extensions(1, extensionValues));
    }

    private static Fixture fixture(String companyCode, UUID transactionUuid)
    {
        Company company = new Company();
        company.setCode(companyCode);
        company.setDisplayName(companyCode + " Company");
        company.setDefaultCurrency("USD");
        company.setFiscalYearStartMonth(1);
        company.setFiscalYearStartDay(1);

        ChartOfAccounts chart = new ChartOfAccounts();
        chart.setCompany(company);
        chart.setName("Standard");
        chart.setVersion("1");
        company.setActiveChartOfAccounts(chart);

        Account cash = account(chart, "1010", "Cash", AccountType.ASSET);
        Account expense = account(chart, "6100", "Expense", AccountType.EXPENSE);
        Fund fund = new Fund();
        fund.setCompany(company);
        fund.setCode("GENERAL");
        fund.setName("General Fund");
        fund.setFundType(FundType.UNRESTRICTED);

        Txn transaction = new Txn();
        transaction.setCompany(company);
        transaction.setPortableId(transactionUuid);
        transaction.setTxnDate(LocalDate.of(2026, 7, 28));
        transaction.setMemo("Transaction with supplemental details");

        TxnSplit expenseLine = split(transaction, expense, fund, new BigDecimal("25.0000"));
        TxnSplit cashLine = split(transaction, cash, fund, new BigDecimal("-25.0000"));
        return new Fixture(company, List.of(cash, expense), fund, transaction, List.of(cashLine, expenseLine));
    }

    private static Account account(
            ChartOfAccounts chart,
            String code,
            String name,
            AccountType type)
    {
        Account account = new Account();
        account.setChart(chart);
        account.setCode(code);
        account.setName(name);
        account.setAccountType(type);
        account.setNormalBalance(NormalBalance.DEBIT);
        account.setOpeningBalance(BigDecimal.ZERO);
        return account;
    }

    private static TxnSplit split(Txn transaction, Account account, Fund fund, BigDecimal amount)
    {
        TxnSplit split = new TxnSplit();
        split.setTxn(transaction);
        split.setAccount(account);
        split.setFund(fund);
        split.setAmountSigned(amount);
        return split;
    }

    private static TxnSupplementalLine detail(
            Txn transaction,
            int lineOrder,
            String kind,
            String entryRef,
            String counterparty,
            String description,
            String reference,
            String amount,
            LocalDate dueDate,
            LocalDate startDate,
            LocalDate endDate,
            String notes)
    {
        TxnSupplementalLine detail = new TxnSupplementalLine();
        detail.setTxn(transaction);
        detail.setLineOrder(lineOrder);
        detail.setKind(kind);
        detail.setEntryRef(entryRef);
        detail.setCounterparty(counterparty);
        detail.setDescription(description);
        detail.setReference(reference);
        detail.setAmount(new BigDecimal(amount));
        detail.setDueDate(dueDate);
        detail.setStartDate(startDate);
        detail.setEndDate(endDate);
        detail.setNotes(notes);
        return detail;
    }

    private record Fixture(
            Company company,
            List<Account> accounts,
            Fund fund,
            Txn transaction,
            List<TxnSplit> splits)
    {
    }

    private static final class MapBuilder
    {
        private final java.util.Map<String, Object> values;

        private MapBuilder(java.util.Map<String, Object> source)
        {
            values = new java.util.LinkedHashMap<>(source);
        }

        private void put(String key, Object value)
        {
            values.put(key, value);
        }

        private java.util.Map<String, Object> values()
        {
            return values;
        }
    }
}
