package org.nonprofitbookkeeping.persistence;

import org.flywaydb.core.Flyway;

import java.nio.file.Path;

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
        String raw = databaseFile.toString();
        String normalized = raw;
        if (raw.endsWith(".mv.db"))
        {
            normalized = raw.substring(0, raw.length() - ".mv.db".length());
        }
        else if (raw.endsWith(".db"))
        {
            normalized = raw.substring(0, raw.length() - ".db".length());
        }

        return "jdbc:h2:file:" + normalized + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;INIT=CREATE SCHEMA IF NOT EXISTS PUBLIC\\;SET SCHEMA PUBLIC";
    }

    private static String redactJdbcUrl(String jdbcUrl)
    {
        return jdbcUrl.replaceAll("(?i)(password=)[^;]*", "$1<redacted>");
    }
}
