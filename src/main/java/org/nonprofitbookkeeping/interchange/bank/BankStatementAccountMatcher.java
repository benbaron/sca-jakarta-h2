package org.nonprofitbookkeeping.interchange.bank;

import org.nonprofitbookkeeping.interchange.InterchangeMessageSeverity;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Applies the governed single configured-account identity policy. */
public final class BankStatementAccountMatcher
{
    public Match match(Company company, CompanyBankAccount configured, BankStatementDocument document)
    {
        if (company == null || configured == null || document == null)
        {
            throw new IllegalArgumentException("Company, configured account, and statement are required.");
        }
        List<InterchangeValidationMessage> messages = new ArrayList<>();
        if (configured.getCompany() == null
                || !company.getId().equals(configured.getCompany().getId()))
        {
            error(messages, "BANK_ACCOUNT_COMPANY_MISMATCH", "Selected bank account belongs to another company.");
        }
        if (!configured.isActive())
        {
            error(messages, "BANK_ACCOUNT_INACTIVE", "Selected configured bank account is inactive.");
        }
        if (configured.getBank() == null || !configured.getBank().isActive())
        {
            error(messages, "BANK_INACTIVE", "Selected configured account must reference an active bank.");
        }
        if (configured.getAccount() == null)
        {
            error(messages, "BANK_LEDGER_ACCOUNT_MISSING", "Selected configured account has no posting ledger account.");
        }

        BankStatementDocument.AccountIdentity source = document.account();
        compareRequiredIdentity(
                messages,
                configured.getOfxBankId(),
                source.bankId(),
                "BANK_ID_MISMATCH",
                "Statement bank ID does not match the selected configured account.");

        String configuredAccountId = text(configured.getOfxAccountId());
        String sourceAccountId = text(source.accountId());
        boolean confirmationRequired = false;
        if (!configuredAccountId.isBlank())
        {
            if (!configuredAccountId.equalsIgnoreCase(sourceAccountId))
            {
                error(messages, "ACCOUNT_ID_MISMATCH", "Statement account ID does not match the selected configured account.");
            }
        }
        else
        {
            String suffix = firstText(configured.getLastFour(), lastFour(configured.getMaskedAccountNumber()));
            if (suffix.isBlank() || !sourceAccountId.endsWith(suffix))
            {
                error(messages, "ACCOUNT_ID_MISMATCH", "Statement account ID does not match the selected configured account suffix.");
            }
            else
            {
                confirmationRequired = true;
                warning(messages, "MASKED_ACCOUNT_CONFIRMATION_REQUIRED",
                        "Only the configured account suffix matches; explicit confirmation is required before import.");
            }
        }

        String configuredType = text(configured.getAccountType());
        if (!configuredType.isBlank() && !"BANK".equals(configuredType)
                && !source.accountType().isBlank()
                && !configuredType.equalsIgnoreCase(source.accountType()))
        {
            error(messages, "ACCOUNT_TYPE_MISMATCH", "Statement account type does not match the selected configured account.");
        }
        if (!text(company.getDefaultCurrency()).equalsIgnoreCase(document.currency()))
        {
            error(messages, "CURRENCY_MISMATCH", "Statement currency does not match the active company currency.");
        }

        boolean blocking = messages.stream().anyMatch(InterchangeValidationMessage::blocking);
        Status status = blocking ? Status.BLOCKING
                : confirmationRequired ? Status.CONFIRMATION_REQUIRED : Status.EXACT;
        return new Match(status, List.copyOf(messages));
    }

    private static void compareRequiredIdentity(
            List<InterchangeValidationMessage> messages,
            String configured,
            String source,
            String code,
            String message)
    {
        String configuredValue = text(configured);
        String sourceValue = text(source);
        if (!configuredValue.isBlank() && !sourceValue.isBlank()
                && !configuredValue.equalsIgnoreCase(sourceValue))
        {
            error(messages, code, message);
        }
    }

    private static void warning(List<InterchangeValidationMessage> messages, String code, String message)
    {
        messages.add(new InterchangeValidationMessage(
                InterchangeMessageSeverity.WARNING, code, "statement.account", message, false));
    }

    private static void error(List<InterchangeValidationMessage> messages, String code, String message)
    {
        messages.add(new InterchangeValidationMessage(
                InterchangeMessageSeverity.ERROR, code, "statement.account", message, true));
    }

    private static String firstText(String first, String second)
    {
        return !text(first).isBlank() ? text(first) : text(second);
    }

    private static String lastFour(String value)
    {
        String digits = text(value).replaceAll("[^A-Za-z0-9]", "");
        return digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
    }

    private static String text(String value)
    {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public enum Status
    {
        EXACT,
        CONFIRMATION_REQUIRED,
        BLOCKING
    }

    public record Match(Status status, List<InterchangeValidationMessage> messages)
    {
        public Match
        {
            if (status == null)
            {
                throw new IllegalArgumentException("Account-match status is required.");
            }
            messages = messages == null ? List.of() : List.copyOf(messages);
        }

        public boolean commitAllowed(boolean identityConfirmed)
        {
            return status == Status.EXACT
                    || (status == Status.CONFIRMATION_REQUIRED && identityConfirmed);
        }
    }
}
