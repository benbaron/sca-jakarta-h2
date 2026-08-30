package org.nonprofitbookkeeping.app;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.util.Arrays;

/** CLI bootstrap for supported maintenance utilities. */
public class Main
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args)
    {
        if (args.length > 0 && "recover-admin".equals(args[0]))
        {
            int exitCode = new CommandLine(new RecoverAdminCommand())
                    .execute(Arrays.copyOfRange(args, 1, args.length));
            if (exitCode != 0)
            {
                System.exit(exitCode);
            }
            return;
        }

        Flyway flyway = Flyway.configure()
            .dataSource("jdbc:h2:file:./data/sca-ledger;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH", "sa", "")
            .locations("classpath:db/migration")
            .load();
        flyway.migrate();

        try (SeContainer container = SeContainerInitializer.newInstance().initialize())
        {
            CommandLine cmd = new CommandLine(AppCli.class, new CdiFactory(container));
            int exitCode = cmd.execute(args);
            if (exitCode != 0)
            {
                LOGGER.error("Command failed with exit code {}", exitCode);
                System.exit(exitCode);
            }
        }
        catch (RuntimeException ex)
        {
            LOGGER.error("Fatal error", ex);
            System.exit(2);
        }
    }
}
