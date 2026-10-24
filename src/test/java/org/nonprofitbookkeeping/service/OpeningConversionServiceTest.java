package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountFunction;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.NormalBalance;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeningConversionServiceTest
{
    private static final LocalDate CUTOFF = LocalDate.of(2026, 1, 31);
    @TempDir Path directory;

    @Test
    void balancedReviewAcceptsExactlyOnceAndSurvivesRestartWithoutLedgerWrites()
    {
        Path database = directory.resolve("restart");
        UUID request = UUID.randomUUID();
        String fingerprint;
        long eventId;
        try (Jpa jpa = new Jpa(database))
        {
            Fixture fixture = seed(jpa);
            OpeningConversionService service = service(jpa, ReservedSecurityRole.MANAGER);
            OpeningConversionService.Preview preview = service.preview(CUTOFF);
            assertTrue(preview.ready());
            assertTrue(preview.saveAvailable());
            assertEquals(2, preview.accountOpenings().size());
            assertEquals(1, preview.bankOpenings().size());
            assertEquals(new BigDecimal("100.0000"), preview.bankOpenings().get(0).ledgerOpening());
            assertEquals(6, preview.openItems().size());
            fingerprint = preview.fingerprint();

            OpeningConversionService.Acceptance first = service.saveAcceptance(
                    request, CUTOFF, fingerprint, "Owner reviewed source records");
            OpeningConversionService.Acceptance retry = service.saveAcceptance(
                    request, CUTOFF, fingerprint, "Owner reviewed source records");
            eventId = first.auditEventId();
            assertEquals(eventId, retry.auditEventId());
            assertEquals("conversion-operator", first.actor());
            assertEquals(1L, audits(jpa));
            assertEquals(0L, count(jpa, "Txn"));
            assertEquals(0L, count(jpa, "TxnSplit"));
            assertNotNull(fixture.bankAccountId());
        }
        try (Jpa jpa = new Jpa(database))
        {
            OpeningConversionService.Preview restored = service(jpa, ReservedSecurityRole.ADMIN).preview(CUTOFF);
            assertFalse(restored.saveAvailable());
            assertEquals(eventId, restored.acceptance().auditEventId());
            assertEquals(fingerprint, restored.acceptance().fingerprint());
            assertEquals(1L, audits(jpa));
        }
    }

    @Test
    void mismatchedBankOpeningAndOrphanControlBlockAcceptance()
    {
        try (Jpa jpa = new Jpa(directory.resolve("banks")))
        {
            Fixture fixture = seed(jpa);
            change(jpa, em ->
            {
                Account bank = em.find(Account.class, fixture.bankAccountId());
                em.createQuery("from CompanyBankAccount", CompanyBankAccount.class)
                        .getSingleResult().setOpeningBalance(new BigDecimal("99"));
                Account orphan = account(em, bank.getChart(), "1010", "Orphan bank",
                        AccountType.ASSET, NormalBalance.DEBIT, "25");
                orphan.setAccountFunction(AccountFunction.BANK);
                Account offset = account(em, bank.getChart(), "3010", "Orphan offset",
                        AccountType.EQUITY, NormalBalance.CREDIT, "25");
                assertNotNull(offset);
            });
            OpeningConversionService.Preview preview = service(jpa, ReservedSecurityRole.MANAGER).preview(CUTOFF);
            assertFalse(preview.ready());
            assertEquals(1, preview.bankOpenings().size());
            assertEquals(new BigDecimal("1.0000"), preview.bankOpenings().get(0).difference());
            assertTrue(preview.bankOpenings().get(0).blocking());
            assertEquals(1, preview.orphanControls().size());
            assertThrows(IllegalStateException.class, () -> service(jpa, ReservedSecurityRole.MANAGER)
                    .saveAcceptance(UUID.randomUUID(), CUTOFF, preview.fingerprint(), "cannot accept"));
            assertEquals(0L, audits(jpa));
        }
    }

    @Test
    void rawSupplementalOpeningAndAssetInventoryOrphansAreVisibleBlockingFacts()
    {
        try (Jpa jpa = new Jpa(directory.resolve("controls")))
        {
            Fixture fixture = seed(jpa);
            change(jpa, em ->
            {
                ChartOfAccounts chart = em.find(Account.class, fixture.bankAccountId()).getChart();
                Account receivable = account(em, chart, "1200", "Raw receivable",
                        AccountType.ASSET, NormalBalance.DEBIT, "30");
                receivable.setSubtype(AccountSubtype.RECEIVABLE);
                Account inventory = account(em, chart, "1300", "Orphan inventory",
                        AccountType.ASSET, NormalBalance.DEBIT, "20");
                inventory.setSubtype(AccountSubtype.INVENTORY);
                Account asset = account(em, chart, "1500", "Orphan fixed asset",
                        AccountType.ASSET, NormalBalance.DEBIT, "10");
                asset.setSubtype(AccountSubtype.FIXED_ASSET);
                account(em, chart, "3020", "Control offset",
                        AccountType.EQUITY, NormalBalance.CREDIT, "60");
            });
            OpeningConversionService.Preview preview = service(jpa, ReservedSecurityRole.MANAGER).preview(CUTOFF);
            assertFalse(preview.ready());
            assertEquals(2, preview.orphanControls().size());
            assertTrue(preview.openItems().stream()
                    .filter(row -> row.kind() == SupplementalOpenItemQueryService.Kind.RECEIVABLE)
                    .noneMatch(OpeningConversionService.OpenItemSummary::ready));
            assertTrue(preview.checks().stream().anyMatch(check ->
                    check.area().equals("Accounts Receivable") && check.blocking()));
        }
    }

    @Test
    void stalePreviewAuthorizationAndConflictingSecondAcceptanceAreRejected()
    {
        try (Jpa jpa = new Jpa(directory.resolve("guards")))
        {
            Fixture fixture = seed(jpa);
            OpeningConversionService manager = service(jpa, ReservedSecurityRole.MANAGER);
            OpeningConversionService.Preview preview = manager.preview(CUTOFF);
            assertThrows(AuthorizationException.class, () -> service(jpa, ReservedSecurityRole.VIEWER)
                    .saveAcceptance(UUID.randomUUID(), CUTOFF, preview.fingerprint(), "viewer"));

            change(jpa, em -> em.find(Account.class, fixture.equityAccountId())
                    .setOpeningBalance(new BigDecimal("101")));
            assertThrows(IllegalStateException.class, () -> manager.saveAcceptance(
                    UUID.randomUUID(), CUTOFF, preview.fingerprint(), "stale"));
            assertEquals(0L, audits(jpa));

            change(jpa, em -> em.find(Account.class, fixture.equityAccountId())
                    .setOpeningBalance(new BigDecimal("100")));
            OpeningConversionService.Preview current = manager.preview(CUTOFF);
            manager.saveAcceptance(UUID.randomUUID(), CUTOFF, current.fingerprint(), "accepted");
            assertThrows(IllegalStateException.class, () -> manager.saveAcceptance(
                    UUID.randomUUID(), CUTOFF.minusDays(1),
                    manager.preview(CUTOFF.minusDays(1)).fingerprint(), "different snapshot"));
            assertEquals(1L, audits(jpa));
        }
    }

    @Test
    void failureAfterAuditPersistRollsBackCompletely()
    {
        try (Jpa jpa = new Jpa(directory.resolve("rollback")))
        {
            seed(jpa);
            OpeningConversionService.Preview preview = service(jpa, ReservedSecurityRole.MANAGER).preview(CUTOFF);
            AuthorizationGuard guard = guard(jpa, ReservedSecurityRole.MANAGER);
            OpeningConversionService failing = new OpeningConversionService(
                    jpa, () -> "DEFAULT", guard, ignored ->
                    {
                        throw new IllegalStateException("simulated persistence failure");
                    });
            assertThrows(IllegalStateException.class, () -> failing.saveAcceptance(
                    UUID.randomUUID(), CUTOFF, preview.fingerprint(), "rollback"));
            assertEquals(0L, audits(jpa));
            assertTrue(service(jpa, ReservedSecurityRole.MANAGER).preview(CUTOFF).saveAvailable());
        }
    }

    private static OpeningConversionService service(Jpa jpa, ReservedSecurityRole role)
    {
        return new OpeningConversionService(jpa, () -> "DEFAULT", guard(jpa, role));
    }

    private static AuthorizationGuard guard(Jpa jpa, ReservedSecurityRole role)
    {
        Instant now = Instant.now();
        AuthenticatedUserSession session = new AuthenticatedUserSession(
                9100L, "conversion-operator", "Conversion Operator", "DEFAULT",
                Set.of(role), now, now);
        return new AuthorizationGuard(jpa, () -> Optional.of(session));
    }

    private static Fixture seed(Jpa jpa)
    {
        final long[] ids = new long[2];
        change(jpa, em ->
        {
            Company company = company(em);
            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setCompany(company);
            chart.setName("Opening conversion chart");
            chart.setVersion("1");
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);
            company.setActiveChartOfAccounts(chart);
            Account bank = account(em, chart, "1000", "Operating bank",
                    AccountType.ASSET, NormalBalance.DEBIT, "100");
            bank.setAccountFunction(AccountFunction.BANK);
            Account equity = account(em, chart, "3000", "Opening net assets",
                    AccountType.EQUITY, NormalBalance.CREDIT, "100");
            CompanyBankAccount configured = configuredBank(company, bank, "Operating", "100");
            em.persist(configured);
            em.flush();
            ids[0] = bank.getId();
            ids[1] = equity.getId();
        });
        return new Fixture(ids[0], ids[1]);
    }

    private static Company company(EntityManager em)
    {
        return em.createQuery("from Company c where c.code = 'DEFAULT'", Company.class).getSingleResult();
    }

    private static Account account(EntityManager em, ChartOfAccounts chart, String code, String name,
            AccountType type, NormalBalance normal, String opening)
    {
        Account account = new Account();
        account.setChart(chart);
        account.setCode(code);
        account.setName(name);
        account.setAccountType(type);
        account.setNormalBalance(normal);
        account.setOpeningBalance(new BigDecimal(opening));
        account.setPosting(true);
        account.setActive(true);
        em.persist(account);
        return account;
    }

    private static CompanyBankAccount configuredBank(
            Company company, Account account, String name, String opening)
    {
        CompanyBankAccount configured = new CompanyBankAccount();
        configured.setCompany(company);
        configured.setAccount(account);
        configured.setName(name);
        configured.setOpeningDate(CUTOFF.minusDays(30));
        configured.setOpeningBalance(new BigDecimal(opening));
        return configured;
    }

    private static void change(Jpa jpa, Consumer<EntityManager> action)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            action.accept(em);
            em.getTransaction().commit();
        }
    }

    private static long audits(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(e) from AuditEvent e where e.actionType = 'OPENING_CONVERSION_ACCEPTED'", Long.class)
                    .getSingleResult();
        }
    }

    private static long count(Jpa jpa, String entity)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult();
        }
    }

    private record Fixture(long bankAccountId, long equityAccountId) { }
}
