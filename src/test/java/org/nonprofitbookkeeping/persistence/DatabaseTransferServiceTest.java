package org.nonprofitbookkeeping.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTransferServiceTest
{
    @Test
    void backupRestoreAndGuardedSwitchPreserveDatabase(@TempDir Path tempDir) throws Exception
    {
        Path source = tempDir.resolve("source-ledger");
        seedCompany(source);
        AtomicReference<Path> switched = new AtomicReference<>();
        DatabaseTransferService service = new DatabaseTransferService(() -> source, switched::set);

        Path backup = tempDir.resolve("backup.zip");
        DatabaseTransferService.BackupResult backupResult = service.backUpDatabase(backup);
        assertTrue(Files.isRegularFile(backup));
        assertTrue(backupResult.byteCount() > 0);
        assertEquals(64, backupResult.sha256().length());
        assertEquals(2L, backupResult.counts().companies());

        Path restored = tempDir.resolve("restored-ledger");
        DatabaseTransferService.RestoreResult restoreResult = service.restoreDatabaseCopy(backup, restored);
        assertTrue(restoreResult.validated());
        assertTrue(Files.isRegularFile(Path.of(restored + ".mv.db")));
        assertEquals(backupResult.counts(), restoreResult.counts());
        assertFalse(switched.get() != null);

        service.switchToValidatedCopy(restoreResult);
        assertEquals(restored.toAbsolutePath().normalize(), switched.get());

        try (Jpa jpa = new Jpa(restored); EntityManager em = jpa.em())
        {
            assertEquals(1L, em.createQuery(
                    "select count(c) from Company c where c.code = 'TRANSFER'", Long.class).getSingleResult());
        }
    }

    @Test
    void restoreRejectsActiveDatabaseAndExistingTarget(@TempDir Path tempDir) throws Exception
    {
        Path source = tempDir.resolve("active-ledger");
        seedCompany(source);
        DatabaseTransferService service = new DatabaseTransferService(() -> source, ignored -> { });
        Path backup = tempDir.resolve("backup.zip");
        service.backUpDatabase(backup);

        assertThrows(IllegalArgumentException.class, () -> service.restoreDatabaseCopy(backup, source));

        Path existing = tempDir.resolve("existing-ledger");
        Files.writeString(Path.of(existing + ".mv.db"), "occupied");
        assertThrows(IllegalArgumentException.class, () -> service.restoreDatabaseCopy(backup, existing));
    }

    @Test
    void corruptBackupLeavesTargetAbsent(@TempDir Path tempDir) throws Exception
    {
        Path source = tempDir.resolve("active-ledger");
        seedCompany(source);
        DatabaseTransferService service = new DatabaseTransferService(() -> source, ignored -> { });
        Path corrupt = tempDir.resolve("corrupt.zip");
        Files.writeString(corrupt, "not an H2 backup");
        Path target = tempDir.resolve("corrupt-target");

        assertThrows(RuntimeException.class, () -> service.restoreDatabaseCopy(corrupt, target));
        assertFalse(Files.exists(Path.of(target + ".mv.db")));
    }

    private static void seedCompany(Path database)
    {
        try (Jpa jpa = new Jpa(database); EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO company (code, display_name) VALUES ('TRANSFER', 'Transfer Test')")
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }
}