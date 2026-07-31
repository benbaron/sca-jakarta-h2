package org.nonprofitbookkeeping.interchange.sclx;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Exact entity, reference, relationship, and unsupported-section counts for one SCLX preview. */
public record SclxImportPreviewCounts(
        Map<String, Long> entitiesByType,
        long totalEntities,
        long referenceCount,
        long relationshipCount,
        long unsupportedSectionCount)
{
    public SclxImportPreviewCounts
    {
        Objects.requireNonNull(entitiesByType, "entitiesByType");
        TreeMap<String, Long> sorted = new TreeMap<>();
        entitiesByType.forEach((key, value) ->
        {
            if (key == null || key.isBlank())
            {
                throw new IllegalArgumentException("entity-count keys must not be blank");
            }
            if (value == null || value < 0L)
            {
                throw new IllegalArgumentException("entity counts must not be negative");
            }
            sorted.put(key, value);
        });
        entitiesByType = Map.copyOf(sorted);
        if (totalEntities < 0L || referenceCount < 0L || relationshipCount < 0L
                || unsupportedSectionCount < 0L)
        {
            throw new IllegalArgumentException("SCLX preview counts must not be negative");
        }
        long calculatedTotal = entitiesByType.values().stream().mapToLong(Long::longValue).sum();
        if (calculatedTotal != totalEntities)
        {
            throw new IllegalArgumentException("totalEntities must equal the entity-type count sum");
        }
    }

    public long count(String entityType)
    {
        return entitiesByType.getOrDefault(entityType, 0L);
    }
}
