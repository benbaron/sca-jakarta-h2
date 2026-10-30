package org.nonprofitbookkeeping.interchange.sclx;

import java.util.Set;
import java.util.Map;
import java.util.UUID;

/** Read-only target-company boundary used by the non-mutating SCLX preview service. */
interface SclxImportTargetReader
{
    record CategoryTarget(String localId, org.nonprofitbookkeeping.service.BudgetCategoryAdminService.ImportFact value) { }

    default Map<String, CategoryTarget> categories(String companyCode)
    {
        return Map.of();
    }

    record TransferTarget(UUID id, UUID request, String company, UUID transaction)
    {
    }

    default Map<UUID, TransferTarget> transfers(Set<UUID> portableIds, Set<UUID> requests)
    {
        return Map.of();
    }
    SclxImportTargetSnapshot read(String companyCode, String sourceSystem);

    default SclxImportTargetSnapshot read(
            String companyCode,
            String sourceSystem,
            Set<SclxImportTargetSnapshot.NativePortableKey> nativePortableKeys)
    {
        return read(companyCode, sourceSystem);
    }
}
