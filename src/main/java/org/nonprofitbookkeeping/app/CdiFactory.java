package org.nonprofitbookkeeping.app;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.se.SeContainer;
import picocli.CommandLine.IFactory;

/**
 * Picocli factory that resolves command instances via CDI (Weld SE).
 */
public class CdiFactory implements IFactory
{
    private final SeContainer container;

    public CdiFactory(SeContainer container)
    {
        this.container = container;
    }

    @Override
    public <K> K create(Class<K> cls) throws Exception
    {
        Instance<K> inst = container.select(cls);
        if (inst.isResolvable())
        {
            return inst.get();
        }
        // Fallback to reflection for plain POJOs.
        return cls.getDeclaredConstructor().newInstance();
    }
}
