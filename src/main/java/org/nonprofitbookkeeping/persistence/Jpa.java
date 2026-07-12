package org.nonprofitbookkeeping.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import org.hibernate.jpa.HibernatePersistenceProvider;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple JPA bootstrap helper for a desktop (RESOURCE_LOCAL) application.
 *
 * <p>The application deliberately selects Hibernate in {@code persistence.xml} and
 * bootstraps that provider directly. Desktop JavaFX launchers may place the
 * Jakarta Persistence API and Hibernate on different class-path/module-path
 * segments, where provider service discovery is not reliable.</p>
 */
@ApplicationScoped
public class Jpa implements AutoCloseable
{
    private static final String PERSISTENCE_UNIT = "scaLedgerPU";

    private final EntityManagerFactory emf;

    public Jpa()
    {
        System.err.println("[NPBK] Creating default JPA EntityManagerFactory for persistence unit "
                + PERSISTENCE_UNIT + ".");
        try
        {
            this.emf = createEntityManagerFactory(Map.of());
            System.err.println("[NPBK] Default JPA EntityManagerFactory created.");
        }
        catch (RuntimeException ex)
        {
            System.err.println("[NPBK] Default JPA EntityManagerFactory creation failed: "
                    + ex.getClass().getName() + ": " + ex.getMessage());
            ex.printStackTrace(System.err);
            throw ex;
        }
        catch (LinkageError error)
        {
            throw missingProvider(error);
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
            this.emf = createEntityManagerFactory(overrides);
            System.err.println("[NPBK] JPA EntityManagerFactory created for selected database.");
        }
        catch (RuntimeException ex)
        {
            System.err.println("[NPBK] JPA EntityManagerFactory creation failed: "
                    + ex.getClass().getName() + ": " + ex.getMessage());
            ex.printStackTrace(System.err);
            throw ex;
        }
        catch (LinkageError error)
        {
            throw missingProvider(error);
        }
    }

    private static EntityManagerFactory createEntityManagerFactory(Map<String, Object> properties)
    {
        EntityManagerFactory factory = new HibernatePersistenceProvider()
                .createEntityManagerFactory(PERSISTENCE_UNIT, properties);
        if (factory == null)
        {
            throw new PersistenceException(
                    "Hibernate could not locate persistence unit " + PERSISTENCE_UNIT
                            + ". Verify META-INF/persistence.xml is present on the runtime classpath.");
        }
        return factory;
    }

    private static IllegalStateException missingProvider(LinkageError error)
    {
        System.err.println("[NPBK] Hibernate ORM is missing or incompatible on the runtime classpath: "
                + error.getMessage());
        error.printStackTrace(System.err);
        return new IllegalStateException(
                "Hibernate ORM is missing or incompatible on the runtime classpath. "
                        + "Launch through the Maven/Eclipse Maven classpath so hibernate-core and its dependencies are included.",
                error);
    }

    public EntityManager em()
    {
        return emf.createEntityManager();
    }

    @Override
    public void close()
    {
        emf.close();
    }
}
