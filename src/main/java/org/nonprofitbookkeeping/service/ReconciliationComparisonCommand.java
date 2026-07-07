package org.nonprofitbookkeeping.service;

import java.time.LocalDate;

/** Command for producing a configured-bank-account reconciliation comparison. */
public record ReconciliationComparisonCommand(String companyCode,
                                              Long bankAccountId,
                                              LocalDate fromDate,
                                              LocalDate statementEndingOn,
                                              boolean saveUnresolvedReport)
{
}
