package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.DatabaseTransferService;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.UserManualProcedureFixture.*;

@DisplayName("Manual: Back up → restore separate validated copy → inspect → switch")
class UserManualHandoverProcedureTest
{
    @TempDir Path directory;

    @Test
    void handoverRestoresBackupTimeBooksWithoutReplacingLiveCompanyOrLaterEntries() throws Exception
    {
        Path live = directory.resolve("live-books");
        Path copy = directory.resolve("handover-copy");
        Path backup = directory.resolve("before-import.zip");
        AtomicReference<Path> active = new AtomicReference<>(live.toAbsolutePath().normalize());
        var transfer = new DatabaseTransferService(active::get, active::set);
        UserManualProcedureFixture f;
        long originalId;
        DatabaseTransferService.RestoreResult restored;
        try (Jpa jpa = new Jpa(live))
        {
            f = setUp(jpa, directory);
            originalId = f.enter(jpa, START, "Handover reference entry", f.checking(), f.income(), "100").id();
            var result = transfer.backUpDatabase(backup);
            assertEquals(2, result.counts().companies());
            assertEquals(1, result.counts().transactions());
            assertEquals(2, result.counts().transactionSplits());
            assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(backup))), result.sha256());
            byte[] archived = Files.readAllBytes(backup);
            assertThrows(IllegalArgumentException.class, () -> transfer.backUpDatabase(backup));
            assertArrayEquals(archived, Files.readAllBytes(backup));
            f.enter(jpa, START.plusDays(1), "After backup", f.checking(), f.income(), "25");
            restored = transfer.restoreDatabaseCopy(backup, copy);
            assertTrue(restored.validated());
            assertEquals(result.counts(), restored.counts());
            assertEquals(live.toAbsolutePath().normalize(), active.get(), "Restoring does not switch databases");
            assertEquals(2, count(jpa, "Txn"));
            assertThrows(IllegalArgumentException.class, () -> transfer.restoreDatabaseCopy(backup, live));
            assertThrows(IllegalArgumentException.class, () -> transfer.restoreDatabaseCopy(backup, copy));
        }
        try (Jpa restoredJpa = new Jpa(copy); Jpa originalJpa = new Jpa(live))
        {
            long auditsBeforeInspection = count(restoredJpa, "AuditEvent");
            var company = new CompanyAdminService(restoredJpa).requireActiveCompany(COMPANY);
            assertEquals(f.company(), company.id());
            assertEquals("Handover reference entry", f.journal(restoredJpa).load(originalId).memo());
            assertEquals(1, count(restoredJpa, "Txn"));
            assertEquals(2, count(originalJpa, "Txn"));
            var accounts = new BankingWorkspaceQuery(restoredJpa).accounts(COMPANY, END);
            assertEquals(2, accounts.size());
            assertTrue(accounts.stream().anyMatch(a -> a.id() == f.checkingBank() && a.ledgerId() == f.checking()));
            UserManualBookkeepingProcedureTest.money("100", new FinancialReportService(restoredJpa, () -> COMPANY)
                    .trialBalance(END, null).totalDebits());
            assertEquals(1, count(restoredJpa, "Txn"), "Inspection is read-only");
            assertEquals(auditsBeforeInspection, count(restoredJpa, "AuditEvent"));
        }
        transfer.switchToValidatedCopy(restored);
        assertEquals(copy.toAbsolutePath().normalize(), active.get());
        assertTrue(Files.isRegularFile(Path.of(live + ".mv.db")));
        assertTrue(Files.isRegularFile(backup));
    }
}
