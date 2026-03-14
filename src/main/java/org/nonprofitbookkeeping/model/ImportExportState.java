package org.nonprofitbookkeeping.model;

/**
 * Persistable import/export defaults.
 */
public record ImportExportState(BankingDataFormat bankingFormat,
                                ChartOfAccountsTransferFormat chartFormat,
                                String lastImportPath,
                                String lastExportPath)
{
}
