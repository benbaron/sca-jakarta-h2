package org.nonprofitbookkeeping.interchange.sclx;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Bounded read-only target-company facts used by SCLX preview. */
record SclxImportTargetSnapshot(
        String companyCode,
        String companyName,
        boolean populated,
        boolean operationalDataPopulated,
        Map<String, TargetAccount> accountsByCode,
        Map<String, TargetFund> fundsByCode,
        Map<String, TargetActivity> activitiesByCode,
        Map<ExternalIdentityKey, IdentityFact> identities,
        Map<NativePortableKey, NativePortableFact> nativePortableIdentities,
        List<ClosedRange> closedRanges,
        Set<String> finalizedTransactionLocalIds)
{
    SclxImportTargetSnapshot
    {
        companyCode = requireText(companyCode, "companyCode");
        companyName = requireText(companyName, "companyName");
        accountsByCode = Map.copyOf(Objects.requireNonNull(accountsByCode, "accountsByCode"));
        fundsByCode = Map.copyOf(Objects.requireNonNull(fundsByCode, "fundsByCode"));
        activitiesByCode = Map.copyOf(Objects.requireNonNull(activitiesByCode, "activitiesByCode"));
        identities = Map.copyOf(Objects.requireNonNull(identities, "identities"));
        nativePortableIdentities = Map.copyOf(Objects.requireNonNull(
                nativePortableIdentities, "nativePortableIdentities"));
        closedRanges = List.copyOf(Objects.requireNonNull(closedRanges, "closedRanges"));
        finalizedTransactionLocalIds = Set.copyOf(Objects.requireNonNull(
                finalizedTransactionLocalIds, "finalizedTransactionLocalIds"));
    }

    /** Compatibility constructor for snapshots that do not model pre-existing activities. */
    SclxImportTargetSnapshot(
            String companyCode,
            String companyName,
            boolean populated,
            boolean operationalDataPopulated,
            Map<String, TargetAccount> accountsByCode,
            Map<String, TargetFund> fundsByCode,
            Map<String, TargetActivity> activitiesByCode,
            Map<ExternalIdentityKey, IdentityFact> identities,
            List<ClosedRange> closedRanges,
            Set<String> finalizedTransactionLocalIds)
    {
        this(companyCode, companyName, populated, operationalDataPopulated,
                accountsByCode, fundsByCode, activitiesByCode, identities, Map.of(), closedRanges,
                finalizedTransactionLocalIds);
    }

    /** Compatibility constructor for snapshots that do not model native portable identities. */
    SclxImportTargetSnapshot(
            String companyCode,
            String companyName,
            boolean populated,
            boolean operationalDataPopulated,
            Map<String, TargetAccount> accountsByCode,
            Map<String, TargetFund> fundsByCode,
            Map<ExternalIdentityKey, IdentityFact> identities,
            List<ClosedRange> closedRanges,
            Set<String> finalizedTransactionLocalIds)
    {
        this(companyCode, companyName, populated, operationalDataPopulated,
                accountsByCode, fundsByCode, Map.of(), identities, Map.of(), closedRanges,
                finalizedTransactionLocalIds);
    }

    boolean isClosed(LocalDate date)
    {
        return date != null && closedRanges.stream().anyMatch(range -> range.contains(date));
    }

    record TargetAccount(
            String portableId,
            String code,
            String type,
            String subtype,
            String increaseSide,
            boolean posting,
            boolean active,
            String localEntityId)
    {
        TargetAccount
        {
            portableId = requireText(portableId, "portableId");
            code = requireText(code, "code");
            type = requireText(type, "type");
            increaseSide = requireText(increaseSide, "increaseSide");
            localEntityId = requireText(localEntityId, "localEntityId");
        }
    }

    record TargetFund(
            String portableId,
            String code,
            String type,
            boolean active,
            String localEntityId)
    {
        TargetFund
        {
            portableId = requireText(portableId, "portableId");
            code = requireText(code, "code");
            type = requireText(type, "type");
            localEntityId = requireText(localEntityId, "localEntityId");
        }
    }

    record TargetActivity(
            String code,
            String name,
            boolean active,
            String localEntityId)
    {
        TargetActivity
        {
            code = requireText(code, "code");
            name = requireText(name, "name");
            localEntityId = requireText(localEntityId, "localEntityId");
        }
    }

    record ExternalIdentityKey(String entityType, String externalId)
    {
        ExternalIdentityKey
        {
            entityType = requireText(entityType, "entityType").toUpperCase(java.util.Locale.ROOT);
            externalId = requireText(externalId, "externalId");
        }
    }

    record IdentityFact(String normalizedContentHash, String localEntityId)
    {
        IdentityFact
        {
            normalizedContentHash = requireText(normalizedContentHash, "normalizedContentHash");
            localEntityId = localEntityId == null || localEntityId.isBlank() ? null : localEntityId.trim();
        }
    }

    record NativePortableKey(String entityType, UUID portableId)
    {
        NativePortableKey
        {
            entityType = requireText(entityType, "entityType").toUpperCase(java.util.Locale.ROOT);
            Objects.requireNonNull(portableId, "portableId");
        }
    }

    record NativePortableFact(
            String localEntityId,
            String companyCode,
            String contentFingerprint)
    {
        NativePortableFact
        {
            localEntityId = requireText(localEntityId, "localEntityId");
            companyCode = companyCode == null || companyCode.isBlank() ? null : companyCode.trim();
            contentFingerprint = contentFingerprint == null || contentFingerprint.isBlank()
                    ? null : contentFingerprint.trim();
        }

        boolean ownedBy(String targetCompanyCode)
        {
            return companyCode != null && companyCode.equalsIgnoreCase(targetCompanyCode);
        }
    }

    record ClosedRange(LocalDate startDate, LocalDate endDate)
    {
        ClosedRange
        {
            Objects.requireNonNull(startDate, "startDate");
            Objects.requireNonNull(endDate, "endDate");
            if (endDate.isBefore(startDate))
            {
                throw new IllegalArgumentException("closed range end must not precede start");
            }
        }

        boolean contains(LocalDate date)
        {
            return !date.isBefore(startDate) && !date.isAfter(endDate);
        }
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
