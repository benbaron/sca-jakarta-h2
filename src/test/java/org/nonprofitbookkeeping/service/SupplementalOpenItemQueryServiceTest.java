package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplementalOpenItemQueryServiceTest
{
    @Test
    void projectionHandlesAllKindsPartialFullAsOfAndLegacyDiagnostics(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("supplemental-open-items")))
        {
            seed(jpa);
            TransactionEntryService entry = new TransactionEntryService(jpa, () -> "TEST");
            SupplementalOpenItemQueryService query =
                    new SupplementalOpenItemQueryService(jpa, () -> "TEST");

            Map<SupplementalOpenItemQueryService.Kind, UUID> items = new EnumMap<>(SupplementalOpenItemQueryService.Kind.class);
            for (SupplementalOpenItemQueryService.Kind kind : SupplementalOpenItemQueryService.Kind.values())
            {
                UUID itemId = UUID.randomUUID();
                items.put(kind, itemId);
                enterIncrease(entry, kind, itemId, LocalDate.of(2026, 1, 10), new BigDecimal("100.00"));
                enterDecrease(entry, kind, itemId, LocalDate.of(2026, 2, 10), new BigDecimal("30.00"));
            }

            SupplementalOpenItemQueryService.Result before =
                    query.query(SupplementalOpenItemQueryService.Kind.RECEIVABLE, LocalDate.of(2026, 1, 31));
            assertEquals(new BigDecimal("100.0000"), before.authoritativeRows().get(0).openBalance());

            SupplementalOpenItemQueryService.Result after =
                    query.query(SupplementalOpenItemQueryService.Kind.RECEIVABLE, LocalDate.of(2026, 2, 28));
            assertEquals(new BigDecimal("70.0000"), after.authoritativeRows().get(0).openBalance());
            assertEquals("OPEN", after.authoritativeRows().get(0).status());

            for (SupplementalOpenItemQueryService.Kind kind : SupplementalOpenItemQueryService.Kind.values())
            {
                SupplementalOpenItemQueryService.Result result = query.query(kind, LocalDate.of(2026, 2, 28));
                assertTrue(result.authorityAvailable());
                assertEquals(1L, result.openCount());
                assertEquals(new BigDecimal("70.0000"), result.openAmount());
            }

            enterDecrease(entry, SupplementalOpenItemQueryService.Kind.RECEIVABLE,
                    items.get(SupplementalOpenItemQueryService.Kind.RECEIVABLE),
                    LocalDate.of(2026, 3, 10), new BigDecimal("70.00"));
            SupplementalOpenItemQueryService.Result closed =
                    query.query(SupplementalOpenItemQueryService.Kind.RECEIVABLE, LocalDate.of(2026, 3, 31));
            assertEquals(BigDecimal.ZERO.setScale(4), closed.authoritativeRows().get(0).openBalance());
            assertEquals("CLOSED", closed.authoritativeRows().get(0).status());
            assertEquals(0L, closed.openCount());

            TransactionView excess = enterDecrease(entry, SupplementalOpenItemQueryService.Kind.RECEIVABLE,
                    items.get(SupplementalOpenItemQueryService.Kind.RECEIVABLE),
                    LocalDate.of(2026, 3, 20), new BigDecimal("5.00"));
            SupplementalOpenItemQueryService.Result overApplied =
                    query.query(SupplementalOpenItemQueryService.Kind.RECEIVABLE, LocalDate.of(2026, 3, 31));
            assertEquals(new BigDecimal("-5.0000"), overApplied.authoritativeRows().get(0).openBalance());
            assertEquals("OVER_APPLIED", overApplied.authoritativeRows().get(0).status());
            assertEquals(0L, overApplied.openCount());

            new TransactionCorrectionService(jpa, () -> "TEST")
                    .delete(excess.id(), "tester", "remove excess settlement");
            SupplementalOpenItemQueryService.Result restoredClosed =
                    query.query(SupplementalOpenItemQueryService.Kind.RECEIVABLE, LocalDate.of(2026, 3, 31));
            assertEquals("CLOSED", restoredClosed.authoritativeRows().get(0).status());

            entry.enter(new TransactionCommand(
                    LocalDate.of(2026, 4, 1), null, "legacy supplemental", null,
                    List.of(
                            line(1007L, new BigDecimal("5.00"), BigDecimal.ZERO),
                            line(1003L, BigDecimal.ZERO, new BigDecimal("5.00"))),
                    List.of(new TransactionSupplementalLineCommand(
                            "RECEIVABLE", "LEGACY-1", "Legacy", "Legacy detail", null,
                            new BigDecimal("5.00"), null, null, null, null))));
            insertCorruptDiagnosticRows(jpa);
            SupplementalOpenItemQueryService.Result withDiagnostics =
                    query.query(SupplementalOpenItemQueryService.Kind.RECEIVABLE, LocalDate.of(2026, 4, 30));
            assertTrue(withDiagnostics.rows().stream().anyMatch(row -> "UNMATCHED_LEGACY".equals(row.status())));
            assertTrue(withDiagnostics.rows().stream().anyMatch(row -> "UNMATCHED_REDUCTION".equals(row.status())));
            assertTrue(withDiagnostics.rows().stream().anyMatch(row -> "INCONSISTENT".equals(row.status())));
        }
    }

    @Test
    void projectionIsCompanyScopedAndReversalRemovesSettlementEffect(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("supplemental-reversal")))
        {
            seed(jpa);
            TransactionEntryService entry = new TransactionEntryService(jpa, () -> "TEST");
            UUID item = UUID.randomUUID();
            enterIncrease(entry, SupplementalOpenItemQueryService.Kind.RECEIVABLE, item,
                    LocalDate.of(2026, 1, 10), new BigDecimal("100.00"));
            TransactionView settlement = enterDecrease(entry, SupplementalOpenItemQueryService.Kind.RECEIVABLE, item,
                    LocalDate.of(2026, 2, 10), new BigDecimal("25.00"));

            new TransactionCorrectionService(jpa, () -> "TEST")
                    .reverse(settlement.id(), LocalDate.of(2026, 2, 15), "tester", "reverse settlement", false);

            SupplementalOpenItemQueryService.Result result =
                    new SupplementalOpenItemQueryService(jpa, () -> "TEST")
                            .query(SupplementalOpenItemQueryService.Kind.RECEIVABLE, LocalDate.of(2026, 2, 28));
            assertEquals(new BigDecimal("100.0000"), result.openAmount());

            long openingTxnId = result.authoritativeRows().get(0).openingTransactionId();
            TransactionCorrectionService.CorrectionResult replacement =
                    new TransactionCorrectionService(jpa, () -> "TEST")
                            .reverse(openingTxnId, LocalDate.of(2026, 2, 20), "tester", "replace opening", true);
            assertTrue(replacement.replacementTransactionId() != null);
            SupplementalOpenItemQueryService.Result replaced =
                    new SupplementalOpenItemQueryService(jpa, () -> "TEST")
                            .query(SupplementalOpenItemQueryService.Kind.RECEIVABLE, LocalDate.of(2026, 2, 28));
            assertEquals(new BigDecimal("100.0000"), replaced.openAmount());
            assertEquals(item, replaced.authoritativeRows().get(0).itemId());

            SupplementalOpenItemQueryService.Result other =
                    new SupplementalOpenItemQueryService(jpa, () -> "OTHER")
                            .query(SupplementalOpenItemQueryService.Kind.RECEIVABLE, LocalDate.of(2026, 2, 28));
            assertFalse(other.authorityAvailable());
            assertTrue(other.rows().isEmpty());
        }
    }

    private static TransactionView enterIncrease(
            TransactionEntryService entry,
            SupplementalOpenItemQueryService.Kind kind,
            UUID itemId,
            LocalDate date,
            BigDecimal amount)
    {
        long account = accountId(kind);
        boolean liability = kind.accountSubtype().name().contains("PAYABLE")
                || kind.accountSubtype().name().contains("LIABILITY")
                || kind == SupplementalOpenItemQueryService.Kind.DEFERRED_REVENUE;
        List<TransactionLineCommand> lines = liability
                ? List.of(line(1006L, amount, BigDecimal.ZERO), line(account, BigDecimal.ZERO, amount))
                : List.of(line(account, amount, BigDecimal.ZERO), line(1003L, BigDecimal.ZERO, amount));
        int supplementalLine = liability ? 1 : 0;
        return entry.enter(new TransactionCommand(
                date, null, "open " + kind.name(), null, lines,
                List.of(new TransactionSupplementalLineCommand(
                        kind.name(), kind.name() + "-REF", "Counterparty", kind.displayName(), null,
                        amount, date.plusDays(30), null, null, null,
                        null, itemId, "INCREASE", supplementalLine))));
    }

    private static TransactionView enterDecrease(
            TransactionEntryService entry,
            SupplementalOpenItemQueryService.Kind kind,
            UUID itemId,
            LocalDate date,
            BigDecimal amount)
    {
        long account = accountId(kind);
        boolean liability = kind.accountSubtype().name().contains("PAYABLE")
                || kind.accountSubtype().name().contains("LIABILITY")
                || kind == SupplementalOpenItemQueryService.Kind.DEFERRED_REVENUE;
        List<TransactionLineCommand> lines = liability
                ? List.of(line(account, amount, BigDecimal.ZERO), line(1007L, BigDecimal.ZERO, amount))
                : List.of(line(1007L, amount, BigDecimal.ZERO), line(account, BigDecimal.ZERO, amount));
        int supplementalLine = liability ? 0 : 1;
        return entry.enter(new TransactionCommand(
                date, null, "settle " + kind.name(), null, lines,
                List.of(new TransactionSupplementalLineCommand(
                        kind.name(), kind.name() + "-REF", "Counterparty", kind.displayName(), null,
                        amount, null, null, null, null,
                        null, itemId, "DECREASE", supplementalLine))));
    }

    private static TransactionLineCommand line(long accountId, BigDecimal debit, BigDecimal credit)
    {
        return new TransactionLineCommand(accountId, 1001L, null, null, null, debit, credit, false, null);
    }

    private static long accountId(SupplementalOpenItemQueryService.Kind kind)
    {
        return switch (kind)
        {
            case RECEIVABLE -> 1001L;
            case PAYABLE -> 1002L;
            case PREPAID_EXPENSE -> 1004L;
            case DEFERRED_REVENUE -> 1005L;
            case OTHER_ASSET -> 1008L;
            case OTHER_LIABILITY -> 1009L;
        };
    }

    private static void insertCorruptDiagnosticRows(Jpa jpa)
    {
        UUID unmatchedItem = UUID.randomUUID();
        UUID inconsistentItem = UUID.randomUUID();
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("""
                    insert into txn (id, company_id, txn_date, memo, status)
                    values
                    (9001,100,DATE '2026-04-05','unmatched reduction','ENTERED'),
                    (9002,100,DATE '2026-04-06','inconsistent item','ENTERED')
                    """).executeUpdate();
            em.createNativeQuery("""
                    insert into txn_split (id, txn_id, account_id, fund_id, amount_signed)
                    values
                    (9001,9001,1007,1001,10.0000),
                    (9002,9001,1001,1001,-10.0000),
                    (9003,9002,1007,1001,10.0000),
                    (9004,9002,1003,1001,-10.0000)
                    """).executeUpdate();
            em.createNativeQuery("""
                    insert into txn_supplemental_line
                        (id, txn_id, line_order, kind, description, amount, item_id, txn_split_id, item_effect)
                    values
                        (9001,9001,0,'RECEIVABLE','Reduction with no opening',10.0000,?,9002,'DECREASE'),
                        (9002,9002,0,'RECEIVABLE','Wrong account subtype',10.0000,?,9003,'INCREASE')
                    """)
                    .setParameter(1, unmatchedItem)
                    .setParameter(2, inconsistentItem)
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static void seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("insert into company (id, code, display_name) values (100, 'TEST', 'Test'), (200, 'OTHER', 'Other')").executeUpdate();
            em.createNativeQuery("insert into chart_of_accounts (id, company_id, name, version, status) values (100,100,'Test','1','ACTIVE'),(200,200,'Other','1','ACTIVE')").executeUpdate();
            em.createNativeQuery("update company set active_chart_of_accounts_id = id where id in (100,200)").executeUpdate();
            em.createNativeQuery("""
                    insert into account (id, chart_id, code, name, account_type, subtype, normal_balance)
                    values
                    (1001,100,'1100','Receivable','ASSET','RECEIVABLE','DEBIT'),
                    (1002,100,'2100','Payable','LIABILITY','PAYABLE','CREDIT'),
                    (1003,100,'4000','Income','INCOME',null,'CREDIT'),
                    (1004,100,'1200','Prepaid','ASSET','PREPAID','DEBIT'),
                    (1005,100,'2200','Deferred','LIABILITY','DEFERRED_REVENUE','CREDIT'),
                    (1006,100,'5000','Expense','EXPENSE',null,'DEBIT'),
                    (1007,100,'1000','Cash','ASSET','CASH','DEBIT'),
                    (1008,100,'1300','Other Asset','ASSET','OTHER_ASSET','DEBIT'),
                    (1009,100,'2300','Other Liability','LIABILITY','OTHER_LIABILITY','CREDIT')
                    """).executeUpdate();
            em.createNativeQuery("insert into fund (id, company_id, code, name, fund_type) values (1001,100,'OPERATING','Operating','UNRESTRICTED')").executeUpdate();
            em.getTransaction().commit();
        }
    }
}
