package org.nonprofitbookkeeping.interchange.coa;

/** Frozen database/destination identity and account-state fingerprint for reviewed import. */
public record CoaImportScope(long companyId, Long chartId, String fingerprint)
{
}
