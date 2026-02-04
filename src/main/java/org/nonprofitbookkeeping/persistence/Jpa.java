package org.nonprofitbookkeeping.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

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

    public EntityManager em()
    {
        return emf.createEntityManager();
    }

    public void close()
    {
        emf.close();
    }
}
