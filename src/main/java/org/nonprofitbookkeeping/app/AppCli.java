package org.nonprofitbookkeeping.app;

import picocli.CommandLine.Command;

/** Root command for supported local bookkeeping utilities. */
@Command(
    name = "sca-ledger",
    mixinStandardHelpOptions = true,
    version = "0.2.0",
    description = "SCA Ledger (H2 + Jakarta) utilities",
    subcommands = {
        SeedCommand.class,
        RecoverAdminCommand.class
    }
)
public class AppCli implements Runnable
{
    @Override
    public void run()
    {
        // Default is help output; no action.
    }
}
