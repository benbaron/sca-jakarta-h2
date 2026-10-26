package org.nonprofitbookkeeping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.NmrItemTestFixture.*;

class AccountingReadinessTest
{
    @TempDir Path directory;

    @Test
    void canonicalSnapshotFindingsMatchPacketAndRevisionsTrackAssessedContentWithoutWrites() throws Exception
    {
        Path db = directory.resolve("readiness");
        try (Jpa jpa = new Jpa(db))
        {
            var f = seed(jpa);
            f.entry(jpa).enter(f.receipt(UUID.randomUUID(), f.recipient(), f.event()));
            var service = new CoreAccountingPacketService(() -> db, () -> COMPANY, null);
            long txns = count(jpa, "Txn"), audits = count(jpa, "AuditEvent");
            var first = service.previewReadiness(DATE, DATE);
            var second = service.previewReadiness(DATE, DATE);
            assertEquals(first.dataRevision(), second.dataRevision());
            assertEquals(first.findings(), second.findings());
            assertEquals(64, first.snapshotSha256().length());
            assertEquals("Blocked", first.automatedStatus());
            assertTrue(first.blockers() > 0);
            assertTrue(first.findings().stream().anyMatch(row -> row.area().equals("Bank")
                    && row.destination() == AccountingReadiness.Destination.BANK_CONFIGURATION));
            assertTrue(first.findings().stream().anyMatch(row -> row.area().equals("Unsettled Items")
                    && row.severity() == AccountingReadiness.Severity.WARNING));
            var packet = service.export(DATE, DATE, directory.resolve("packet.zip"));
            try (var zip = new ZipFile(packet.destination().toFile()))
            {
                var manifest = new ObjectMapper().readTree(zip.getInputStream(zip.getEntry("manifest.json")));
                assertEquals(first.dataRevision(), manifest.get("dataRevision").asText());
                assertEquals(first.automatedStatus(), manifest.get("automatedReadiness").asText());
                assertEquals(first.blockers(), manifest.get("readinessBlockers").asLong());
                assertEquals(first.warnings(), manifest.get("readinessWarnings").asLong());
                assertEquals(first.sections(), packet.sections());
                var csv = new String(zip.getInputStream(zip.getEntry("Readiness Findings.csv")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                first.findings().forEach(row -> assertTrue(csv.contains(row.id())));
                try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(zip.getInputStream(zip.getEntry("Accounting Packet.xlsx"))))
                {
                    assertEquals(first.findings().size() + 1, workbook.getSheet("Readiness Findings").getPhysicalNumberOfRows());
                }
            }
            assertEquals(txns, count(jpa, "Txn"));
            assertEquals(audits, count(jpa, "AuditEvent"));
            try (var em = jpa.em())
            {
                assertEquals(0L, ((Number) em.createNativeQuery("select count(*) from period_close_range").getSingleResult()).longValue());
            }
            f.entry(jpa).enter(new TransactionCommand(DATE.plusDays(1), null, "Outside cutoff", null,
                    List.of(f.line(f.cash(), "5", "0", f.event()), f.line(f.income(), "0", "5", f.event()))));
            assertEquals(first.dataRevision(), service.previewReadiness(DATE, DATE).dataRevision());
            assertNotEquals(first.dataRevision(), service.previewReadiness(DATE, DATE.plusDays(1)).dataRevision());
            f.entry(jpa).enter(new TransactionCommand(DATE, null, "Inside cutoff", null,
                    List.of(f.line(f.cash(), "3.1234", "0", f.event()), f.line(f.income(), "0", "3.1234", f.event()))));
            assertNotEquals(first.dataRevision(), service.previewReadiness(DATE, DATE).dataRevision());
            var other = new CoreAccountingPacketService(() -> db, () -> "DEFAULT", null).previewReadiness(DATE, DATE);
            assertEquals("DEFAULT", other.companyCode());
            assertNotEquals(first.dataRevision(), other.dataRevision());
            assertFalse(other.findings().stream().anyMatch(row -> row.area().equals("Unsettled Items")));
        }
    }

    @Test
    void financialImbalanceAndUnclassifiedExpenseAreBlockersWithCanonicalCorrectionRoutes() throws Exception
    {
        Path db = directory.resolve("imbalanced");
        try (Jpa jpa = new Jpa(db))
        {
            var f = seed(jpa);
            long expense;
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                var cash = em.find(Account.class, f.cash());
                cash.setOpeningBalance(new BigDecimal("10.01"));
                var account = new Account(); account.setChart(cash.getChart()); account.setCode("5000"); account.setName("Expense");
                account.setAccountType(AccountType.EXPENSE); account.setNormalBalance(NormalBalance.DEBIT); account.setPosting(true); account.setActive(true);
                em.persist(account); em.getTransaction().commit(); expense = account.getId();
            }
            f.entry(jpa).enter(new TransactionCommand(DATE, null, "Unclassified expense", null,
                    List.of(f.line(expense, "5", "0", null), f.line(f.cash(), "0", "5", null))));
            var result = new CoreAccountingPacketService(() -> db, () -> COMPANY, null).previewReadiness(DATE, DATE);
            assertTrue(result.findings().stream().anyMatch(row -> row.area().equals("Trial Balance")
                    && row.detail().contains("10.01") && row.severity() == AccountingReadiness.Severity.BLOCKER));
            assertTrue(result.findings().stream().anyMatch(row -> row.area().equals("Balance Sheet")));
            assertTrue(result.findings().stream().anyMatch(row -> row.area().equals("Functional Expenses")
                    && row.destination() == AccountingReadiness.Destination.JOURNAL && row.severity() == AccountingReadiness.Severity.BLOCKER));
            assertTrue(result.findings().stream().anyMatch(row -> row.area().equals("Ledger")
                    && row.severity() == AccountingReadiness.Severity.WARNING));
        }
    }

    @Test
    void previewRejectsDeniedMissingInvalidAndOversizeSourcesRatherThanReturningEmptySuccess() throws Exception
    {
        Path db = directory.resolve("guards");
        try (Jpa jpa = new Jpa(db))
        {
            var f = seed(jpa);
            f.entry(jpa).enter(new TransactionCommand(DATE, null, "Capacity", null,
                    List.of(f.line(f.cash(), "1", "0", f.event()), f.line(f.income(), "0", "1", f.event()))));
            var denied = new CoreAccountingPacketService(() -> { fail("Denied capture must not access source"); return db; },
                    () -> COMPANY, new AuthorizationGuard(jpa, Optional::empty));
            assertThrows(AuthorizationException.class, () -> denied.previewReadiness(DATE, DATE));
            assertThrows(IllegalArgumentException.class, () -> new CoreAccountingPacketService(() -> db, () -> COMPANY, null, 1).previewReadiness(DATE, DATE));
            var service = new CoreAccountingPacketService(() -> db, () -> COMPANY, null);
            assertThrows(IllegalArgumentException.class, () -> service.previewReadiness(DATE, DATE.minusDays(1)));
            assertThrows(IllegalArgumentException.class, () -> new CoreAccountingPacketService(() -> db, () -> "MISSING", null).previewReadiness(DATE, DATE));
            var checks = new AtomicInteger();
            var revoked = new AuthorizationGuard(jpa, Optional::empty)
            {
                @Override public AuthenticatedUserSession require(ApplicationPermission permission, String company, String operation)
                {
                    assertEquals(ApplicationPermission.EXPORT, permission);
                    if (checks.incrementAndGet() == 2) throw new AuthorizationException(permission, "Access revoked during capture");
                    return null;
                }
            };
            assertThrows(AuthorizationException.class, () -> new CoreAccountingPacketService(() -> db, () -> COMPANY, revoked).previewReadiness(DATE, DATE));
            assertEquals(2, checks.get());
        }
    }

    @Test
    void stableFindingIdentitiesAreIndependentOfCaptureTimeAndStatusesNeverCertifyClose()
    {
        var warning = new PacketOperationalSections.ExceptionRow("WARNING", "Custody", "item-7", "Count needs review");
        var first = AccountingReadiness.assess(COMPANY, DATE, DATE, Instant.EPOCH, "backup-1", Map.of("Facts", "same"), List.of(warning));
        var second = AccountingReadiness.assess(COMPANY, DATE, DATE, Instant.now(), "backup-2", Map.of("Facts", "same"), List.of(warning));
        assertEquals(first.findings(), second.findings());
        assertEquals(first.dataRevision(), second.dataRevision());
        assertEquals("Review required", first.automatedStatus());
        assertEquals(AccountingReadiness.Destination.CUSTODY, first.findings().get(0).destination());
        var empty = AccountingReadiness.assess(COMPANY, DATE, DATE, Instant.EPOCH, "backup", Map.of(), List.of());
        assertEquals("No automated exceptions", empty.automatedStatus());
        assertThrows(UnsupportedOperationException.class, () -> first.findings().clear());
    }

    private static long count(Jpa jpa, String entity)
    {
        try (var em = jpa.em())
        {
            return em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult();
        }
    }
}
