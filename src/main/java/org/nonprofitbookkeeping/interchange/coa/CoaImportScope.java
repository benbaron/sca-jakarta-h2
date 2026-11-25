package org.nonprofitbookkeeping.interchange.coa;

/** Frozen destination identity and account-state fingerprint for reviewed import. */
public record CoaImportScope(long companyId, Long chartId, String fingerprint)
{
}
