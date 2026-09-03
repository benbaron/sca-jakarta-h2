package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.bank.BankStatementReviewPreview;
import org.nonprofitbookkeeping.interchange.bank.BankStatementReviewResult;
import org.nonprofitbookkeeping.interchange.bank.BankStatementReviewService;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BankImportAuthorizationIntegrationTest
{
    private static final Path FIXTURE = Path.of(
            "src/test/resources/data-exchange/bank-statement/ofx/valid/ofx2-checking.xml");

    @Test
    public void bankStatementReviewCommitRequiresCurrentBookkeepingWriteSession(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-statement-review-authorization")))
        {
            long bankAccountId = seedBankAccount(jpa);
            SecurityUsers security = securityUsers(jpa, "SCA");
            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(
                    Optional.of(session(security.viewerId(), "SCA", Set.of(ReservedSecurityRole.VIEWER))));
            AuthorizationGuard guard = new AuthorizationGuard(jpa, current::get);
            BankStatementReviewService service = new BankStatementReviewService(jpa, guard);
            BankStatementReviewPreview preview = service.preview(FIXTURE, "SCA", bankAccountId);

            assertEquals(3, preview.lines().size());
            assertThrows(AuthorizationException.class, () -> service.commit(null, false, null));
            assertEquals(0L, count(jpa, "select count(b) from BankImportBatch b"));

            current.set(Optional.of(session(
                    security.accountantId(), "SCA", Set.of(ReservedSecurityRole.ACCOUNTANT))));
            BankStatementReviewResult created = service.commit(preview, false, "compatibility-actor");
            assertTrue(created.created());

            current.set(Optional.of(session(
                    security.managerId(), "SCA", Set.of(ReservedSecurityRole.MANAGER))));
            assertFalse(service.commit(preview, false, "manager").created());

            current.set(Optional.of(session(
                    security.adminId(), "SCA", Set.of(ReservedSecurityRole.ADMIN))));
            assertFalse(service.commit(preview, false, "admin").created());

            current.set(Optional.of(session(
                    security.accountantId(), "SCA",
                    Set.of(ReservedSecurityRole.VIEWER, ReservedSecurityRole.ACCOUNTANT))));
            assertFalse(service.commit(preview, false, "union").created());

            current.set(Optional.of(session(
                    security.accountantId(), "OTHER", Set.of(ReservedSecurityRole.ACCOUNTANT))));
            assertThrows(AuthorizationException.class,
                    () -> service.commit(preview, false, "wrong-company"));

            current.set(Optional.empty());
            assertThrows(AuthorizationException.class, () -> service.commit(null, false, null));
            assertEquals(3L, service.preview(FIXTURE, "SCA", bankAccountId).lines().size());

            assertEquals(1L, count(jpa, "select count(b) from BankImportBatch b"));
            assertEquals(3L, authorizationDenialCount(jpa));
        }
    }

    @Test
    public void genericReviewBatchCreationRequiresCurrentBookkeepingWriteSession(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-import-review-authorization")))
        {
            seedCompanies(jpa);
            SecurityUsers security = securityUsers(jpa, "SCA");
            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(
                    Optional.of(session(security.viewerId(), "SCA", Set.of(ReservedSecurityRole.VIEWER))));
            BankImportReviewService service = new BankImportReviewService(
                    jpa, new AuthorizationGuard(jpa, current::get));

            assertThrows(AuthorizationException.class, () -> service.createReviewBatch(null));
            assertEquals(0L, count(jpa, "select count(b) from BankImportBatch b"));

            current.set(Optional.of(session(
                    security.accountantId(), "SCA", Set.of(ReservedSecurityRole.ACCOUNTANT))));
            service.createReviewBatch(reviewCommand("accountant", "hash-a"));

            current.set(Optional.of(session(
                    security.managerId(), "SCA", Set.of(ReservedSecurityRole.MANAGER))));
            service.createReviewBatch(reviewCommand("manager", "hash-m"));

            current.set(Optional.of(session(
                    security.adminId(), "SCA", Set.of(ReservedSecurityRole.ADMIN))));
            service.createReviewBatch(reviewCommand("admin", "hash-admin"));

            current.set(Optional.of(session(
                    security.accountantId(), "SCA",
                    Set.of(ReservedSecurityRole.VIEWER, ReservedSecurityRole.ACCOUNTANT))));
            service.createReviewBatch(reviewCommand("union", "hash-u"));

            current.set(Optional.of(session(
                    security.accountantId(), "OTHER", Set.of(ReservedSecurityRole.ACCOUNTANT))));
            assertThrows(AuthorizationException.class,
                    () -> service.createReviewBatch(reviewCommand("wrong-company", "hash-w")));

            current.set(Optional.empty());
            assertThrows(AuthorizationException.class, () -> service.createReviewBatch(null));

            assertEquals(4L, count(jpa, "select count(b) from BankImportBatch b"));
            assertEquals(4L, count(jpa, "select count(l) from BankStatementLine l"));
            assertEquals(3L, authorizationDenialCount(jpa));
        }
    }

    @Test
    public void reviewedRowAcceptanceRequiresCurrentBookkeepingWriteSession(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("reviewed-row-acceptance-authorization")))
        {
            Seed seed = seedReviewedRow(jpa);
            SecurityUsers security = securityUsers(jpa, "SCA");
            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(
                    Optional.of(session(security.viewerId(), "SCA", Set.of(ReservedSecurityRole.VIEWER))));
            AuthorizationGuard guard = new AuthorizationGuard(jpa, current::get);
            ReviewedStatementAcceptanceService service = new ReviewedStatementAcceptanceService(
                    jpa, new TransactionEntryService(jpa, () -> "SCA"), () -> "SCA", guard);
            ReviewedStatementAcceptanceService.AcceptancePreview preview = service.preview(seed.statementLineId());
            TransactionCommand command = balancedCommand(preview);

            assertThrows(AuthorizationException.class,
                    () -> service.accept(null, null, false, null));
            assertEquals(0L, count(jpa, "select count(t) from Txn t"));
            assertEquals(BankStatementLine.Status.IMPORTED, statementStatus(jpa, seed.statementLineId()));

            current.set(Optional.of(session(
                    security.accountantId(), "SCA", Set.of(ReservedSecurityRole.ACCOUNTANT))));
            ReviewedStatementAcceptanceService.AcceptanceResult created =
                    service.accept(preview, command, false, "compatibility-actor");
            assertFalse(created.reusedExisting());

            current.set(Optional.of(session(
                    security.managerId(), "SCA", Set.of(ReservedSecurityRole.MANAGER))));
            assertTrue(service.accept(preview, command, false, "manager").reusedExisting());

            current.set(Optional.of(session(
                    security.adminId(), "SCA", Set.of(ReservedSecurityRole.ADMIN))));
            assertTrue(service.accept(preview, command, false, "admin").reusedExisting());

            current.set(Optional.of(session(
                    security.accountantId(), "SCA",
                    Set.of(ReservedSecurityRole.VIEWER, ReservedSecurityRole.ACCOUNTANT))));
            assertTrue(service.accept(preview, command, false, "union").reusedExisting());

            current.set(Optional.of(session(
                    security.accountantId(), "OTHER", Set.of(ReservedSecurityRole.ACCOUNTANT))));
            assertThrows(AuthorizationException.class,
                    () -> service.accept(preview, command, false, "wrong-company"));

            current.set(Optional.empty());
            assertThrows(AuthorizationException.class,
                    () -> service.accept(null, null, false, null));
            assertFalse(service.preview(seed.statementLineId()).eligible());

            assertEquals(1L, count(jpa, "select count(t) from Txn t"));
            assertEquals(BankStatementLine.Status.ACCEPTED, statementStatus(jpa, seed.statementLineId()));
            assertEquals(3L, authorizationDenialCount(jpa));
        }
    }

    private static BankImportReviewCommand reviewCommand(String source, String hash)
    {
        return new BankImportReviewCommand(
                "SCA",
                null,
                source + ".csv",
                null,
                hash,
                BankImportBatch.SourceFormat.CSV,
                List.of(new BankTransactionRecord(
                        source + "-fit",
                        "20260315000000",
                        new BigDecimal("10.00"),
                        "CREDIT",
                        "Donor",
                        "Gift")),
                "Authorization integration test");
    }

    private static TransactionCommand balancedCommand(
            ReviewedStatementAcceptanceService.AcceptancePreview preview)
    {
        BigDecimal amount = preview.amount().abs();
        boolean positive = preview.amount().signum() > 0;
        long counterAccount = positive ? 102L : 103L;
        TransactionLineCommand bank = new TransactionLineCommand(
                preview.ledgerAccountId(), 101L, null, null, null,
                positive ? amount : BigDecimal.ZERO,
                positive ? BigDecimal.ZERO : amount,
                false, "bank source");
        TransactionLineCommand counter = new TransactionLineCommand(
                counterAccount, 101L, null, null, null,
                positive ? BigDecimal.ZERO : amount,
                positive ? amount : BigDecimal.ZERO,
                false, "counter split");
        return new TransactionCommand(
                preview.effectiveSourceDate(), null, "Accepted reviewed row",
                preview.ledgerAccountId(), List.of(bank, counter));
    }

    private static Seed seedReviewedRow(Jpa jpa)
    {
        long bankAccountId = seedBankAccount(jpa);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery(
                    "INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) "
                            + "VALUES (102, 101, '4000', 'Event Income', 'INCOME', 'CREDIT')")
                    .executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) "
                            + "VALUES (103, 101, '5000', 'Event Expense', 'EXPENSE', 'DEBIT')")
                    .executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO fund (id, company_id, code, name, fund_type) "
                            + "VALUES (101, (SELECT id FROM company WHERE code = 'SCA'), "
                            + "'OPERATING', 'Operating', 'UNRESTRICTED')")
                    .executeUpdate();
            em.getTransaction().commit();
        }
        BankStatementReviewService review = new BankStatementReviewService(jpa);
        review.commit(review.preview(FIXTURE, "SCA", bankAccountId), false, "Owner Tester");
        try (EntityManager em = jpa.em())
        {
            long lineId = em.createQuery(
                            "select l.id from BankStatementLine l "
                                    + "where l.company.code = 'SCA' and l.status = :status order by l.id",
                            Long.class)
                    .setParameter("status", BankStatementLine.Status.IMPORTED)
                    .setMaxResults(1)
                    .getSingleResult();
            return new Seed(bankAccountId, lineId);
        }
    }

    private static long seedBankAccount(Jpa jpa)
    {
        seedCompanies(jpa);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery(
                    "INSERT INTO account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) "
                            + "VALUES (101, 101, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')")
                    .executeUpdate();
            em.getTransaction().commit();
        }
        BankConfigurationService configuration = new BankConfigurationService(jpa);
        Bank bank = configuration.createBank(new BankCommand(
                "SCA", "Example Bank", "999000111", null, null, null, null, null, null, true));
        CompanyBankAccount account = configuration.createBankAccount(new BankAccountCommand(
                "SCA", bank.getId(), 101L, "****4321", "Operating Checking",
                LocalDate.of(2026, 1, 1), BigDecimal.ZERO, BankingDataFormat.OFX,
                "999000111", "FICTIONAL-4321", null, true));
        return account.getId();
    }

    private static void seedCompanies(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery(
                    "INSERT INTO chart_of_accounts (id, name, version, status) "
                            + "VALUES (101, 'SCA Chart', '1', 'ACTIVE')")
                    .executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO company (code, display_name, default_currency, active_chart_of_accounts_id) "
                            + "VALUES ('SCA', 'SCA Branch', 'USD', 101)")
                    .executeUpdate();
            em.createNativeQuery(
                    "UPDATE chart_of_accounts SET company_id = "
                            + "(SELECT id FROM company WHERE code = 'SCA') WHERE id = 101")
                    .executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO chart_of_accounts (id, name, version, status) "
                            + "VALUES (201, 'Other Chart', '1', 'ACTIVE')")
                    .executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO company (code, display_name, default_currency, active_chart_of_accounts_id) "
                            + "VALUES ('OTHER', 'Other Branch', 'USD', 201)")
                    .executeUpdate();
            em.createNativeQuery(
                    "UPDATE chart_of_accounts SET company_id = "
                            + "(SELECT id FROM company WHERE code = 'OTHER') WHERE id = 201")
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static SecurityUsers securityUsers(Jpa jpa, String companyCode)
    {
        new SecurityBootstrapService(jpa).initializeIfUnambiguous();
        UserAdminService users = new UserAdminService(jpa, () -> companyCode);
        return new SecurityUsers(
                reservedUserId(users, ReservedSecurityRole.VIEWER),
                reservedUserId(users, ReservedSecurityRole.ACCOUNTANT),
                reservedUserId(users, ReservedSecurityRole.MANAGER),
                reservedUserId(users, ReservedSecurityRole.ADMIN));
    }

    private static long reservedUserId(UserAdminService users, ReservedSecurityRole role)
    {
        return users.listUsers().stream()
                .filter(user -> role.name().equalsIgnoreCase(user.getUsername()))
                .map(AppUser::getId)
                .findFirst()
                .orElseThrow();
    }

    private static AuthenticatedUserSession session(
            long userId,
            String companyCode,
            Set<ReservedSecurityRole> roles)
    {
        Instant now = Instant.parse("2026-09-02T22:00:00Z");
        return new AuthenticatedUserSession(
                userId, "operator", "Operator", companyCode, roles, now, now);
    }

    private static long authorizationDenialCount(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            Number count = (Number) em.createNativeQuery(
                            "select count(*) from security_event where action_type = 'AUTHORIZATION_DENIED'")
                    .getSingleResult();
            return count.longValue();
        }
    }

    private static long count(Jpa jpa, String jpql)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(jpql, Long.class).getSingleResult();
        }
    }

    private static BankStatementLine.Status statementStatus(Jpa jpa, long statementLineId)
    {
        try (EntityManager em = jpa.em())
        {
            return em.find(BankStatementLine.class, statementLineId).getStatus();
        }
    }

    private record SecurityUsers(long viewerId, long accountantId, long managerId, long adminId) { }

    private record Seed(long bankAccountId, long statementLineId) { }
}
