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
        this.emf = Persistence.createEntityManagerFactory("scaLedgerPU");
    }

    public Jpa(Path databaseFile)
    {
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("jakarta.persistence.jdbc.url", jdbcUrlFor(databaseFile));
        this.emf = Persistence.createEntityManagerFactory("scaLedgerPU", overrides);
    }

    public EntityManager em()
    {
        return emf.createEntityManager();
    }


    private static String jdbcUrlFor(Path databaseFile)
    {
        if (databaseFile == null)
        {
            throw new IllegalArgumentException("databaseFile is required");
        }

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

    public void close()
    {
        emf.close();
    }
}
