package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;

/** Service boundary for P05-S1 bank and bank-account configuration. */
public class BankConfigurationService
{
    private final Jpa jpa;

    public BankConfigurationService(Jpa jpa)
    {
        this.jpa = jpa;
    }

    public Bank createBank(BankCommand command)
    {
        if (isBlank(command.name()))
        {
            throw new IllegalArgumentException("Bank name is required.");
        }
        try (EntityManager em = jpa.em())
        {
            var tx = em.getTransaction();
            tx.begin();
            try
            {
                Company company = companyByCode(em, command.companyCode());
                Bank bank = new Bank();
                bank.setCompany(company);
                bank.setName(command.name().trim());
                bank.setRoutingNumber(blankToNull(command.routingNumber()));
                bank.setAddress(blankToNull(command.address()));
                bank.setWebsite(blankToNull(command.website()));
                bank.setContactName(blankToNull(command.contactName()));
                bank.setContactPhone(blankToNull(command.contactPhone()));
                bank.setContactEmail(blankToNull(command.contactEmail()));
                bank.setNotes(blankToNull(command.notes()));
                bank.setActive(command.active());
                em.persist(bank);
                tx.commit();
                return bank;
            }
            catch (RuntimeException ex)
            {
                if (tx.isActive())
                {
                    tx.rollback();
                }
                throw ex;
            }
        }
    }

    public CompanyBankAccount createBankAccount(BankAccountCommand command)
    {
        try (EntityManager em = jpa.em())
        {
            var tx = em.getTransaction();
            tx.begin();
            try
            {
                Company company = companyByCode(em, command.companyCode());
                Bank bank = em.find(Bank.class, command.bankId());
                if (bank == null || !bank.getCompany().getId().equals(company.getId()))
                {
                    throw new IllegalArgumentException("Bank does not exist for company: " + command.bankId() + ".");
                }
                Account account = em.find(Account.class, command.accountId());
                validateBankLedgerAccount(account);

                CompanyBankAccount bankAccount = new CompanyBankAccount();
                bankAccount.setCompany(company);
                bankAccount.setBank(bank);
                bankAccount.setAccount(account);
                String displayName = isBlank(command.nickname()) ? account.getName() : command.nickname().trim();
                bankAccount.setName(displayName);
                bankAccount.setNickname(blankToNull(command.nickname()));
                bankAccount.setInstitutionName(bank.getName());
                bankAccount.setAccountType(account.getAccountType().name());
                bankAccount.setMaskedAccountNumber(blankToNull(command.maskedAccountNumber()));
                bankAccount.setLastFour(lastFour(command.maskedAccountNumber()));
                bankAccount.setOpeningDate(command.openingDate());
                bankAccount.setOpeningBalance(command.openingBalance() == null ? BigDecimal.ZERO : command.openingBalance());
                bankAccount.setStatementImportFormat(command.statementImportFormat());
                bankAccount.setOfxBankId(blankToNull(command.ofxBankId()));
                bankAccount.setOfxAccountId(blankToNull(command.ofxAccountId()));
                bankAccount.setNotes(blankToNull(command.notes()));
                bankAccount.setActive(command.active());
                em.persist(bankAccount);
                tx.commit();
                return bankAccount;
            }
            catch (RuntimeException ex)
            {
                if (tx.isActive())
                {
                    tx.rollback();
                }
                throw ex;
            }
        }
    }

    static void validateBankLedgerAccount(Account account)
    {
        if (account == null)
        {
            throw new IllegalArgumentException("Chart-of-accounts bank account is required.");
        }
        if (account.getAccountType() != AccountType.BANK
                || account.getNormalBalance() != NormalBalance.DEBIT
                || account.getSubtype() != AccountSubtype.CASH)
        {
            throw new IllegalArgumentException("Linked chart account must be BANK / DEBIT / CASH.");
        }
    }

    private static Company companyByCode(EntityManager em, String code)
    {
        if (isBlank(code))
        {
            throw new IllegalArgumentException("Company code is required.");
        }
        return em.createQuery("""
                        select c
                        from Company c
                        where c.code = :code
                        """, Company.class)
                .setParameter("code", code.trim())
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Company does not exist: " + code + "."));
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value)
    {
        return isBlank(value) ? null : value.trim();
    }

    private static String lastFour(String value)
    {
        if (isBlank(value))
        {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.isBlank())
        {
            return null;
        }
        return digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
    }
}
