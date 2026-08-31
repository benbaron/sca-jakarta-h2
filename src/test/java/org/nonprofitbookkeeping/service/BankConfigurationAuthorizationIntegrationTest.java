package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankConfigurationAuthorizationIntegrationTest
{
    @Test
    void viewerCannotUseAnyServiceOwnedBankConfigurationMutation(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-configuration-viewer-authorization")))
        {
            seedCompanyAndAccounts(jpa);
            BankConfigurationService setup = new BankConfigurationService(jpa);
            Bank bank = setup.createBank(bankCommand("Existing Bank", true));
            CompanyBankAccount configured = setup.createBankAccount(
                    bankAccountCommand(bank.getId(), 101L, "Existing Checking", true));

            BankConfigurationService banks = guardedBanks(
                    jpa,
                    () -> Optional.of(session("SCA", ReservedSecurityRole.VIEWER)));

            assertThrows(AuthorizationException.class,
                    () -> banks.createBank(bankCommand("Viewer Bank", true)));
            assertThrows(AuthorizationException.class,
                    () -> banks.updateBank(bank.getId(), bankCommand("Viewer Rewrite", true)));
            assertThrows(AuthorizationException.class,
                    () -> banks.createBankAccount(
                            bankAccountCommand(bank.getId(), 103L, "Viewer Account", true)));
            assertThrows(AuthorizationException.class,
                    () -> banks.updateBankAccount(
                            configured.getId(),
                            bankAccountCommand(bank.getId(), 101L, "Viewer Rewrite", true)));

            assertEquals(1L, bankCount(jpa));
            assertEquals(1L, configuredAccountCount(jpa));
            assertEquals("Existing Bank", bankName(jpa, bank.getId()));
            assertEquals("Existing Checking", configuredAccountNickname(jpa, configured.getId()));
        }
    }

    @Test
    void companyAdminTracksRoleCompanySwitchesAndMultiRoleUnion(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-configuration-role-switching")))
        {
            seedCompanyAndAccounts(jpa);
            AtomicReference<Optional<AuthenticatedUserSession>> current =
                    new AtomicReference<>(Optional.of(session("SCA", ReservedSecurityRole.ACCOUNTANT)));
            BankConfigurationService banks = guardedBanks(jpa, current::get);

            assertThrows(AuthorizationException.class,
                    () -> banks.createBank(bankCommand("Accountant Bank", true)));
            assertEquals(0L, bankCount(jpa));

            current.set(Optional.of(session("SCA", ReservedSecurityRole.MANAGER)));
            Bank bank = banks.createBank(bankCommand("Manager Bank", true));
            assertEquals(1L, bankCount(jpa));

            current.set(Optional.of(session("SCA", ReservedSecurityRole.ADMIN)));
            banks.updateBank(bank.getId(), bankCommand("Admin Updated Bank", true));
            assertEquals("Admin Updated Bank", bankName(jpa, bank.getId()));

            current.set(Optional.of(session(
                    "SCA",
                    Set.of(ReservedSecurityRole.VIEWER, ReservedSecurityRole.MANAGER))));
            CompanyBankAccount configured = banks.createBankAccount(
                    bankAccountCommand(bank.getId(), 101L, "Union Checking", true));
            assertEquals(1L, configuredAccountCount(jpa));
            assertEquals("Union Checking", configured.getNickname());

            current.set(Optional.of(session("OTHER", ReservedSecurityRole.MANAGER)));
            assertThrows(AuthorizationException.class,
                    () -> banks.updateBank(bank.getId(), bankCommand("Wrong Company", true)));
            assertEquals("Admin Updated Bank", bankName(jpa, bank.getId()));
        }
    }

    @Test
    void authorizationDoesNotBypassBankConfigurationDomainProtections(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-configuration-domain-protections")))
        {
            seedCompanyAndAccounts(jpa);
            BankConfigurationService banks = guardedBanks(
                    jpa,
                    () -> Optional.of(session("SCA", ReservedSecurityRole.MANAGER)));
            Bank bank = banks.createBank(bankCommand("Lifecycle Bank", true));

            IllegalArgumentException invalidLedger = assertThrows(
                    IllegalArgumentException.class,
                    () -> banks.createBankAccount(
                            bankAccountCommand(bank.getId(), 102L, "Not A Bank Account", true)));
            assertEquals(
                    "Linked chart account must be an ASSET with BANK function and DEBIT normal balance.",
                    invalidLedger.getMessage());
            assertEquals(0L, configuredAccountCount(jpa));

            banks.createBankAccount(bankAccountCommand(bank.getId(), 101L, "Checking", true));
            IllegalStateException activeReference = assertThrows(
                    IllegalStateException.class,
                    () -> banks.updateBank(bank.getId(), bankCommand("Lifecycle Bank", false)));
            assertEquals(
                    "Deactivate this Bank's configured bank accounts before deactivating the Bank.",
                    activeReference.getMessage());
            assertTrue(bankActive(jpa, bank.getId()));
            assertEquals(1L, configuredAccountCount(jpa));
        }
    }

    @Test
    void callerOwnedImportHelpersRemainUsableInsideAuthorizedOuterTransaction(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-configuration-caller-owned-import")))
        {
            seedCompanyAndAccounts(jpa);
            AuthorizationGuard outerGuard = new AuthorizationGuard(
                    jpa,
                    () -> Optional.of(session("SCA", ReservedSecurityRole.MANAGER)));
            outerGuard.require(
                    ApplicationPermission.COMPANY_ADMIN,
                    "SCA",
                    "commit governed bank configuration import");

            BankConfigurationService banks = new BankConfigurationService(
                    jpa,
                    new AuthorizationGuard(jpa, () -> Optional.empty()));

            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Company company = company(em);
                Account account = em.find(Account.class, 101L);
                Bank importedBank = banks.createBankForImport(
                        em,
                        company,
                        bankCommand("Imported Bank", true),
                        UUID.randomUUID());
                em.flush();
                CompanyBankAccount importedAccount = banks.createBankAccountForImport(
                        em,
                        company,
                        importedBank,
                        account,
                        new BankAccountImportCommand(
                                "Imported Checking",
                                "Imported Checking",
                                "Imported Bank",
                                "BANK",
                                "4321",
                                "****4321",
                                LocalDate.of(2026, 1, 1),
                                BankingDataFormat.OFX,
                                "BANK-ID",
                                "ACCOUNT-ID",
                                new BigDecimal("15.0000"),
                                true,
                                "Imported through governed outer transaction"),
                        UUID.randomUUID());
                em.flush();
                assertEquals("Imported Bank", importedBank.getName());
                assertEquals("Imported Checking", importedAccount.getNickname());
                em.getTransaction().commit();
            }

            assertEquals(1, banks.listBanks("SCA").size());
            assertEquals(1, banks.listBankAccounts("SCA").size());
        }
    }

    private static BankConfigurationService guardedBanks(
            Jpa jpa,
            java.util.function.Supplier<Optional<AuthenticatedUserSession>> currentSession)
    {
        return new BankConfigurationService(jpa, new AuthorizationGuard(jpa, currentSession));
    }

    private static BankCommand bankCommand(String name, boolean active)
    {
        return new BankCommand(
                "SCA",
                name,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                active);
    }

    private static BankAccountCommand bankAccountCommand(
            long bankId,
            long accountId,
            String nickname,
            boolean active)
    {
        return new BankAccountCommand(
                "SCA",
                bankId,
                accountId,
                "****1234",
                nickname,
                LocalDate.of(2026, 1, 1),
                BigDecimal.ZERO,
                BankingDataFormat.OFX,
                null,
                null,
                null,
                active);
    }

    private static void seedCompanyAndAccounts(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery(
                    "INSERT INTO chart_of_accounts (id, name, version, status) VALUES (101, 'SCA Chart', '1', 'ACTIVE')")
                    .executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO company (code, display_name, active_chart_of_accounts_id) VALUES ('SCA', 'SCA Branch', 101)")
                    .executeUpdate();
            em.createNativeQuery(
                    "UPDATE chart_of_accounts SET company_id = (SELECT id FROM company WHERE code = 'SCA') WHERE id = 101")
                    .executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) "
                            + "VALUES (101, 101, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')")
                    .executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) "
                            + "VALUES (102, 101, '5000', 'Program Expense', 'EXPENSE', NULL, 'DEBIT')")
                    .executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) "
                            + "VALUES (103, 101, '1100', 'Reserve Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')")
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static Company company(EntityManager em)
    {
        return em.createQuery("from Company c where c.code = 'SCA'", Company.class)
                .getSingleResult();
    }

    private static long bankCount(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(b) from Bank b", Long.class).getSingleResult();
        }
    }

    private static long configuredAccountCount(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(b) from CompanyBankAccount b", Long.class).getSingleResult();
        }
    }

    private static String bankName(Jpa jpa, long bankId)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select b.name from Bank b where b.id = :id", String.class)
                    .setParameter("id", bankId)
                    .getSingleResult();
        }
    }

    private static boolean bankActive(Jpa jpa, long bankId)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select b.active from Bank b where b.id = :id", Boolean.class)
                    .setParameter("id", bankId)
                    .getSingleResult();
        }
    }

    private static String configuredAccountNickname(Jpa jpa, long bankAccountId)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(
                            "select b.nickname from CompanyBankAccount b where b.id = :id",
                            String.class)
                    .setParameter("id", bankAccountId)
                    .getSingleResult();
        }
    }

    private static AuthenticatedUserSession session(String companyCode, ReservedSecurityRole role)
    {
        return session(companyCode, Set.of(role));
    }

    private static AuthenticatedUserSession session(
            String companyCode,
            Set<ReservedSecurityRole> roles)
    {
        Instant now = Instant.parse("2026-08-31T02:30:00Z");
        return new AuthenticatedUserSession(
                8L,
                "operator",
                "Operator",
                companyCode,
                roles,
                now,
                now);
    }
}
