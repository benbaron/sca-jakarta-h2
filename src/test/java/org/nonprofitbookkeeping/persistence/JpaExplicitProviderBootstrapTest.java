package org.nonprofitbookkeeping.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.spi.PersistenceProvider;
import jakarta.persistence.spi.PersistenceProviderResolver;
import jakarta.persistence.spi.PersistenceProviderResolverHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JpaExplicitProviderBootstrapTest
{
    private static final Object PROVIDER_RESOLVER_LOCK = new Object();

    @Test
    void bootstrapDoesNotDependOnJpaProviderServiceDiscovery(@TempDir Path tempDir)
    {
        synchronized (PROVIDER_RESOLVER_LOCK)
        {
            PersistenceProviderResolver previous =
                    PersistenceProviderResolverHolder.getPersistenceProviderResolver();
            PersistenceProviderResolverHolder.setPersistenceProviderResolver(new EmptyProviderResolver());
            try (Jpa jpa = new Jpa(tempDir.resolve("explicit-hibernate-provider"));
                 EntityManager em = jpa.em())
            {
                assertTrue(em.isOpen());
            }
            finally
            {
                PersistenceProviderResolverHolder.setPersistenceProviderResolver(previous);
            }
        }
    }

    private static final class EmptyProviderResolver implements PersistenceProviderResolver
    {
        @Override
        public List<PersistenceProvider> getPersistenceProviders()
        {
            return List.of();
        }

        @Override
        public void clearCachedProviders()
        {
            // No cached providers.
        }
    }
}
