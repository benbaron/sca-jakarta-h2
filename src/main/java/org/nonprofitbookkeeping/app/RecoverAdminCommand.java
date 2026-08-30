package org.nonprofitbookkeeping.app;

import org.nonprofitbookkeeping.service.SecurityRecoveryService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/** Offline local recovery for the singleton ADMIN credential. */
@Command(
        name = "recover-admin",
        mixinStandardHelpOptions = true,
        description = "Clear the singleton ADMIN credential in an offline local H2 database.")
public class RecoverAdminCommand implements Callable<Integer>
{
    @Option(names = "--database", required = true, description = "H2 database file (.mv.db or base path)")
    private Path database;

    @Option(names = "--confirm", required = true,
            description = "Required acknowledgement that ADMIN will return to passwordless login")
    private boolean confirm;

    @Override
    public Integer call()
    {
        if (!confirm)
        {
            throw new IllegalArgumentException("--confirm is required to clear the ADMIN credential.");
        }
        SecurityRecoveryService.recoverAdminCredential(database);
        System.out.println("ADMIN credential cleared. The next ADMIN login is passwordless and will acknowledge recovery.");
        return 0;
    }
}
