package org.nonprofitbookkeeping.interchange.sclx;

import java.util.Set;

/** Read-only target-company boundary used by the non-mutating SCLX preview service. */
interface SclxImportTargetReader
{
    SclxImportTargetSnapshot read(String companyCode, String sourceSystem);

    default SclxImportTargetSnapshot read(
            String companyCode,
            String sourceSystem,
            Set<SclxImportTargetSnapshot.NativePortableKey> nativePortableKeys)
    {
        return read(companyCode, sourceSystem);
    }
}
