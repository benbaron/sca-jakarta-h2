package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.BankingDataFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Input for configuring a company bank account linked to a chart account. */
public record BankAccountCommand(String companyCode,
                                 Long bankId,
                                 Long accountId,
                                 String maskedAccountNumber,
                                 String nickname,
                                 LocalDate openingDate,
                                 BigDecimal openingBalance,
                                 BankingDataFormat statementImportFormat,
                                 String ofxBankId,
                                 String ofxAccountId,
                                 String notes,
                                 boolean active)
{
}
