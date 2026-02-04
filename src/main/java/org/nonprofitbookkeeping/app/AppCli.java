package org.nonprofitbookkeeping.app;

import picocli.CommandLine.Command;

/**
 * Root command for the desktop/CLI hybrid app.
 * This is intended to be replaced later by a JavaFX launcher or richer CLI.
 */
@Command(
    name = "sca-ledger",
    mixinStandardHelpOptions = true,
    version = "0.1.0",
    description = "SCA Ledger (H2 + Jakarta) utilities",
    subcommands = {
        SeedCommand.class
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
