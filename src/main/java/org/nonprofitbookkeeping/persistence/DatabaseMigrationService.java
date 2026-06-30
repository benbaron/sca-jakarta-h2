package org.nonprofitbookkeeping.persistence;

import org.flywaydb.core.Flyway;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Runs database migrations before Hibernate validates the schema. */
public final class DatabaseMigrationService
{
    private DatabaseMigrationService()
    {
    }

    public static void migrate(Path databaseFile)
    {
        if (databaseFile == null)
        {
            throw new IllegalArgumentException("databaseFile is required");
        }
        migrateJdbcUrl(jdbcUrlFor(databaseFile));
    }

    public static void migrateJdbcUrl(String jdbcUrl)
    {
        if (jdbcUrl == null || jdbcUrl.isBlank())
        {
            throw new IllegalArgumentException("jdbcUrl is required");
        }

        System.err.println("[NPBK] Flyway migration starting.");
        System.err.println("[NPBK] JDBC URL: " + redactJdbcUrl(jdbcUrl));
        try
        {
            Optional<String> untrackedVersion =
                    FlywaySchemaRecoveryService.prepareBaselineForUntrackedSchema(jdbcUrl);
            if (untrackedVersion.isPresent())
            {
                String version = untrackedVersion.get();
                System.err.println("[NPBK] Existing application schema has no usable Flyway history; "
                        + "recording a non-destructive baseline at version " + version + ".");
                Flyway.configure()
                        .dataSource(jdbcUrl, "sa", "")
                        .locations("classpath:db/migration")
                        .baselineVersion(version)
                        .baselineDescription("Recovered existing application schema")
                        .load()
                        .baseline();
            }

            Flyway.configure()
                    .dataSource(jdbcUrl, "sa", "")
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load()
                    .migrate();
            System.err.println("[NPBK] Flyway migration complete.");
        }
        catch (RuntimeException ex)
        {
            System.err.println("[NPBK] Flyway migration failed: " + ex.getClass().getName() + ": " + ex.getMessage());
            ex.printStackTrace(System.err);
            throw ex;
        }
    }

    public static String jdbcUrlFor(Path databaseFile)
    {
        if (databaseFile == null)
        {
            throw new IllegalArgumentException("databaseFile is required");
        }

        Path absolute = databaseFile.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null)
        {
            try
            {
                Files.createDirectories(parent);
            }
            catch (Exception ex)
            {
                throw new IllegalStateException("Could not create database directory: " + parent, ex);
            }
        }

        String normalized = stripH2FileSuffix(absolute.toString()).replace('\\', '/');
        return "jdbc:h2:file:" + normalized
                + ";MODE=PostgreSQL"
                + ";DATABASE_TO_LOWER=TRUE"
                + ";DEFAULT_NULL_ORDERING=HIGH"
                + ";INIT=CREATE SCHEMA IF NOT EXISTS PUBLIC\\;SET SCHEMA PUBLIC";
    }

    private static String stripH2FileSuffix(String raw)
    {
        if (raw.endsWith(".mv.db"))
        {
            return raw.substring(0, raw.length() - ".mv.db".length());
        }
        if (raw.endsWith(".db"))
        {
            return raw.substring(0, raw.length() - ".db".length());
        }
        return raw;
    }

    private static String redactJdbcUrl(String jdbcUrl)
    {
        return jdbcUrl.replaceAll("(?i)(password=)[^;]*", "$1<redacted>");
    }
}
