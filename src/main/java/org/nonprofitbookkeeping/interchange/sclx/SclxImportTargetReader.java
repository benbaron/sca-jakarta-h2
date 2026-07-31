package org.nonprofitbookkeeping.interchange.sclx;

/** Read-only target-company boundary used by the non-mutating SCLX preview service. */
interface SclxImportTargetReader
{
    SclxImportTargetSnapshot read(String companyCode, String sourceSystem);
}
