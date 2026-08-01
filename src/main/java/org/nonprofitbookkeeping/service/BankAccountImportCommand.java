package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.BankingDataFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Complete configured-bank-account facts supplied by a governed interchange import. */
public record BankAccountImportCommand(
        String name,
        String nickname,
        String institutionName,
        String accountType,
        String lastFour,
        String maskedAccountNumber,
        LocalDate openingDate,
        BankingDataFormat statementImportFormat,
        String ofxBankId,
        String ofxAccountId,
        BigDecimal openingBalance,
        boolean active,
        String notes)
{
}
