package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.persistence.DatabaseTransferService;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/** Authoritative post-login database-administration boundary for whole-database transfer. */
public final class DatabaseAdministrationService
{
    private final DatabaseTransferService transferService;
    private final Supplier<AuthorizationGuard> authorizationGuardSupplier;

    public DatabaseAdministrationService(
            DatabaseTransferService transferService,
            Supplier<AuthorizationGuard> authorizationGuardSupplier)
    {
        this.transferService = Objects.requireNonNull(transferService, "transferService");
        this.authorizationGuardSupplier = Objects.requireNonNull(
                authorizationGuardSupplier, "authorizationGuardSupplier");
    }

    public DatabaseTransferService.BackupResult backUpDatabase(Path destination)
    {
        requireDatabaseAdmin("back up database");
        return transferService.backUpDatabase(destination);
    }

    public DatabaseTransferService.RestoreResult restoreDatabaseCopy(Path backupFile, Path targetDatabase)
    {
        requireDatabaseAdmin("restore database copy");
        return transferService.restoreDatabaseCopy(backupFile, targetDatabase);
    }

    public void switchToValidatedCopy(DatabaseTransferService.RestoreResult result)
    {
        requireDatabaseAdmin("switch to validated database copy");
        transferService.switchToValidatedCopy(result);
    }

    private void requireDatabaseAdmin(String operation)
    {
        AuthorizationGuard guard = Objects.requireNonNull(
                authorizationGuardSupplier.get(), "current authorization guard");
        guard.require(ApplicationPermission.DATABASE_ADMIN, operation);
    }
}
