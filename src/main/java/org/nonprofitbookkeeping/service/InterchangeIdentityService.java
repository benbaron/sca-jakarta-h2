package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.interchange.InterchangeFormat;
import org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.InterchangeIdentity;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Durable identity boundary for idempotent company-data interchange. */
@ApplicationScoped
public class InterchangeIdentityService
{
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private final Jpa jpa;
    private final CompanyOwnershipService ownership;

    @Inject
    public InterchangeIdentityService(Jpa jpa, CompanyOwnershipService ownership)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.ownership = Objects.requireNonNull(ownership, "ownership");
    }

    public InterchangeIdentityMatch classify(
            String companyCode,
            InterchangeFormat format,
            String sourceSystem,
            String entityType,
            String externalId,
            String normalizedContentHash)
    {
        try (EntityManager em = jpa.em())
        {
            Company company = ownership.requireCompany(em, companyCode);
            return classify(em, company, format, sourceSystem, entityType, externalId, normalizedContentHash);
        }
    }

    public InterchangeIdentityView record(
            String companyCode,
            InterchangeFormat format,
            String sourceSystem,
            String entityType,
            String externalId,
            String normalizedContentHash,
            String localEntityId)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = ownership.requireCompany(em, companyCode);
                InterchangeIdentity identity = record(
                        em, company, format, sourceSystem, entityType, externalId,
                        normalizedContentHash, localEntityId);
                em.getTransaction().commit();
                return toView(identity);
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive())
                {
                    em.getTransaction().rollback();
                }
                throw ex;
            }
        }
    }

    /** Caller-owned transaction variant used by format-specific commit services. */
    public InterchangeIdentity record(
            EntityManager em,
            Company company,
            InterchangeFormat format,
            String sourceSystem,
            String entityType,
            String externalId,
            String normalizedContentHash,
            String localEntityId)
    {
        Objects.requireNonNull(em, "em");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(format, "format");
        String cleanSource = requireText(sourceSystem, "Source system", 160);
        String cleanType = requireText(entityType, "Entity type", 80).toUpperCase(Locale.ROOT);
        String cleanExternalId = requireText(externalId, "External ID", 160);
        String cleanHash = normalizeHash(normalizedContentHash);
        String cleanLocalId = optionalText(localEntityId, 120, "Local entity ID");

        List<InterchangeIdentity> matches = find(
                em, company, format, cleanSource, cleanType, cleanExternalId);
        if (matches.isEmpty())
        {
            InterchangeIdentity identity = new InterchangeIdentity();
            identity.setCompany(company);
            identity.setFormatCode(format.name());
            identity.setSourceSystem(cleanSource);
            identity.setEntityType(cleanType);
            identity.setExternalId(cleanExternalId);
            identity.setNormalizedContentHash(cleanHash);
            identity.setLocalEntityId(cleanLocalId);
            em.persist(identity);
            return identity;
        }

        InterchangeIdentity identity = matches.get(0);
        if (!identity.getNormalizedContentHash().equals(cleanHash))
        {
            throw new IllegalStateException("External identity conflicts with different normalized content: "
                    + cleanType + " " + cleanExternalId + ".");
        }
        if (cleanLocalId != null && identity.getLocalEntityId() != null
                && !identity.getLocalEntityId().equals(cleanLocalId))
        {
            throw new IllegalStateException("External identity is already linked to a different local record: "
                    + cleanType + " " + cleanExternalId + ".");
        }
        if (identity.getLocalEntityId() == null && cleanLocalId != null)
        {
            identity.setLocalEntityId(cleanLocalId);
            identity.touchUpdatedAt();
        }
        return identity;
    }

    private InterchangeIdentityMatch classify(
            EntityManager em,
            Company company,
            InterchangeFormat format,
            String sourceSystem,
            String entityType,
            String externalId,
            String normalizedContentHash)
    {
        String cleanSource = requireText(sourceSystem, "Source system", 160);
        String cleanType = requireText(entityType, "Entity type", 80).toUpperCase(Locale.ROOT);
        String cleanExternalId = requireText(externalId, "External ID", 160);
        String cleanHash = normalizeHash(normalizedContentHash);
        List<InterchangeIdentity> matches = find(
                em, company, format, cleanSource, cleanType, cleanExternalId);
        if (matches.isEmpty())
        {
            return InterchangeIdentityMatch.NEW;
        }
        return matches.get(0).getNormalizedContentHash().equals(cleanHash)
                ? InterchangeIdentityMatch.IDENTICAL
                : InterchangeIdentityMatch.CONFLICT;
    }

    private static List<InterchangeIdentity> find(
            EntityManager em,
            Company company,
            InterchangeFormat format,
            String sourceSystem,
            String entityType,
            String externalId)
    {
        return em.createQuery("""
                from InterchangeIdentity i
                where i.company = :company
                  and i.formatCode = :format
                  and i.sourceSystem = :source
                  and i.entityType = :type
                  and i.externalId = :externalId
                """, InterchangeIdentity.class)
                .setParameter("company", company)
                .setParameter("format", format.name())
                .setParameter("source", sourceSystem)
                .setParameter("type", entityType)
                .setParameter("externalId", externalId)
                .setMaxResults(2)
                .getResultList();
    }

    private static InterchangeIdentityView toView(InterchangeIdentity identity)
    {
        return new InterchangeIdentityView(
                identity.getId(),
                identity.getCompany().getCode(),
                InterchangeFormat.valueOf(identity.getFormatCode()),
                identity.getSourceSystem(),
                identity.getEntityType(),
                identity.getExternalId(),
                identity.getNormalizedContentHash(),
                identity.getLocalEntityId(),
                identity.getCreatedAt(),
                identity.getUpdatedAt());
    }

    private static String normalizeHash(String value)
    {
        String hash = requireText(value, "Normalized content hash", 64).toLowerCase(Locale.ROOT);
        if (!SHA_256.matcher(hash).matches())
        {
            throw new IllegalArgumentException("Normalized content hash must be a lowercase SHA-256 value.");
        }
        return hash;
    }

    private static String requireText(String value, String label, int maxLength)
    {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty())
        {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (text.length() > maxLength)
        {
            throw new IllegalArgumentException(label + " exceeds " + maxLength + " characters.");
        }
        return text;
    }

    private static String optionalText(String value, int maxLength, String label)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        return requireText(value, label, maxLength);
    }
}
