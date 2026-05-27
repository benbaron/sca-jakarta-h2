package org.nonprofitbookkeeping.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple JPA bootstrap helper for a desktop (RESOURCE_LOCAL) application.
 *
 * This is intentionally minimal: you can replace it later with your preferred
 * factory / DI approach.
 */
@ApplicationScoped
public class Jpa
{
    private final EntityManagerFactory emf;

    public Jpa()
    {
        System.err.println("[NPBK] Creating default JPA EntityManagerFactory for persistence unit scaLedgerPU.");
        try
        {
            this.emf = Persistence.createEntityManagerFactory("scaLedgerPU");
            System.err.println("[NPBK] Default JPA EntityManagerFactory created.");
        }
        catch (RuntimeException ex)
        {
            System.err.println("[NPBK] Default JPA EntityManagerFactory creation failed: " + ex.getClass().getName() + ": " + ex.getMessage());
            ex.printStackTrace(System.err);
            throw ex;
        }
    }

    public Jpa(Path databaseFile)
    {
        if (databaseFile == null)
        {
            throw new IllegalArgumentException("databaseFile is required");
        }

        String jdbcUrl = DatabaseMigrationService.jdbcUrlFor(databaseFile);
        System.err.println("[NPBK] Preparing database: " + databaseFile.toAbsolutePath());
        DatabaseMigrationService.migrateJdbcUrl(jdbcUrl);

        Map<String, Object> overrides = new HashMap<>();
        overrides.put("jakarta.persistence.jdbc.url", jdbcUrl);
        System.err.println("[NPBK] Creating JPA EntityManagerFactory for selected database.");
        try
        {
            this.emf = Persistence.createEntityManagerFactory("scaLedgerPU", overrides);
            System.err.println("[NPBK] JPA EntityManagerFactory created for selected database.");
        }
        catch (RuntimeException ex)
        {
            System.err.println("[NPBK] JPA EntityManagerFactory creation failed: " + ex.getClass().getName() + ": " + ex.getMessage());
            ex.printStackTrace(System.err);
            throw ex;
        }
    }

    public EntityManager em()
    {
        return emf.createEntityManager();
    }

    public void close()
    {
        emf.close();
    }
}