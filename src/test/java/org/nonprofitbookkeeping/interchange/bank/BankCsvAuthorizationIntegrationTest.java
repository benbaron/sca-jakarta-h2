package org.nonprofitbookkeeping.interchange.bank;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;
import org.nonprofitbookkeeping.service.AuthorizationException;
import org.nonprofitbookkeeping.service.AuthorizationGuard;
import org.nonprofitbookkeeping.service.BankAccountCommand;
import org.nonprofitbookkeeping.service.BankCommand;
import org.nonprofitbookkeeping.service.BankConfigurationService;
import org.nonprofitbookkeeping.service.ReservedSecurityRole;
import org.nonprofitbookkeeping.service.SecurityBootstrapService;
import org.nonprofitbookkeeping.service.UserAdminService;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BankCsvAuthorizationIntegrationTest
{
    private static final Path PROFILE = Path.of(
            "src/test/resources/data-exchange/bank-statement/csv/valid/mapping-profile-signed.json");

    @Test
    public void normalizedCsvCommitRequiresCurrentBookkeepingWriteSession(@TempDir Path tempDir)
            throws Exception
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("normalized-bank-csv-authorization")))
        {
            long bankAccountId = seedBankAccount(jpa);
            SecurityUsers security = securityUsers(jpa, "SCA");
            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(
                    Optional.of(session(security.viewerId(), "SCA", Set.of(ReservedSecurityRole.VIEWER))));
            NormalizedBankCsvReviewService service = new NormalizedBankCsvReviewService(
                    jpa, new AuthorizationGuard(jpa, current::get));
            Path source = normalizedCsv(tempDir);
            NormalizedBankCsvReviewPreview preview = service.preview(source, "SCA", bankAccountId);

            assertFalse(preview.hasBlockingMessages());
            assertThrows(AuthorizationException.class, () -> service.commit(null, false, null));
            assertEquals(0L, count(jpa, "select count(b) from BankImportBatch b"));

            current.set(Optional.of(session(
                    security.accountantId(), "SCA", Set.of(ReservedSecurityRole.ACCOUNTANT))));
            NormalizedBankCsvReviewResult created = service.commit(preview, false, "compatibility-actor");
            assertTrue(created.created());
            assertEquals(1, created.batchCount());

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
            assertEquals(1, service.preview(source, "SCA", bankAccountId).lines().size());

            assertEquals(1L, count(jpa, "select count(b) from BankImportBatch b"));
            assertEquals(1L, count(jpa, "select count(l) from BankStatementLine l"));
            assertEquals(3L, authorizationDenialCount(jpa));
        }
    }

    @Test
    public void mappingProfileMutationsRequireCurrentBookkeepingWriteSession(@TempDir Path tempDir)
            throws Exception
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("bank-csv-mapping-authorization")))
        {
            long bankAccountId = seedBankAccount(jpa);
            SecurityUsers security = securityUsers(jpa, "SCA");
            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(
                    Optional.of(session(security.viewerId(), "SCA", Set.of(ReservedSecurityRole.VIEWER))));
            BankCsvMappingProfileService service = new BankCsvMappingProfileService(
                    jpa, new AuthorizationGuard(jpa, current::get));
            String profileJson = Files.readString(PROFILE);

            assertThrows(AuthorizationException.class,
                    () -> service.create("SCA", bankAccountId, null));
            assertEquals(0L, count(jpa, "select count(p) from BankCsvMappingProfile p"));

            current.set(Optional.of(session(
                    security.accountantId(), "SCA", Set.of(ReservedSecurityRole.ACCOUNTANT))));
            BankCsvMappingProfileService.ProfileSummary created =
                    service.create("SCA", bankAccountId, profileJson);
            assertEquals("Fictional Signed Amount CSV", created.profileName());

            current.set(Optional.of(session(
                    security.viewerId(), "SCA", Set.of(ReservedSecurityRole.VIEWER))));
            assertThrows(AuthorizationException.class,
                    () -> service.replace(created.id(), "SCA", profileJson));
            assertThrows(AuthorizationException.class,
                    () -> service.setActive(created.id(), "SCA", false));

            current.set(Optional.of(session(
                    security.managerId(), "SCA", Set.of(ReservedSecurityRole.MANAGER))));
            BankCsvMappingProfileService.ProfileSummary replaced = service.replace(
                    created.id(), "SCA",
                    profileJson.replace("Fictional Signed Amount CSV", "Manager Mapping"));
            assertEquals("Manager Mapping", replaced.profileName());

            current.set(Optional.of(session(
                    security.adminId(), "SCA", Set.of(ReservedSecurityRole.ADMIN))));
            service.setActive(created.id(), "SCA", false);
            assertFalse(service.list("SCA").get(0).active());

            current.set(Optional.of(session(
                    security.accountantId(), "SCA",
                    Set.of(ReservedSecurityRole.VIEWER, ReservedSecurityRole.ACCOUNTANT))));
            service.setActive(created.id(), "SCA", true);
            assertTrue(service.list("SCA").get(0).active());

            current.set(Optional.of(session(
                    security.accountantId(), "OTHER", Set.of(ReservedSecurityRole.ACCOUNTANT))));
            assertThrows(AuthorizationException.class,
                    () -> service.setActive(created.id(), "SCA", false));

            current.set(Optional.empty());
            assertThrows(AuthorizationException.class,
                    () -> service.create("SCA", bankAccountId, null));
            assertEquals(1, service.list("SCA").size());
            assertEquals("Manager Mapping", service.list("SCA").get(0).profileName());

            assertEquals(1L, count(jpa, "select count(p) from BankCsvMappingProfile p"));
            assertEquals(5L, authorizationDenialCount(jpa));
        }
    }

    private static Path normalizedCsv(Path tempDir) throws Exception
    {
        BankStatementExportRow row = new BankStatementExportRow(
                "OFX",
                UUID.randomUUID().toString(),
                "authorization.ofx",
                UUID.randomUUID().toString(),
                "FI-SCA",
                "111000111",
                "SCA-4321",
                "CHECKING",
                LocalDate.of(2026, 6, 2),
                LocalDate.of(2026, 6, 3),
                new BigDecimal("10.00"),
                "USD",
                "FIT-AUTH-1",
                "CREDIT",
                "PAYEE-AUTH-1",
                "Authorization Payee",
                "Authorization import",
                "",
                "REF-AUTH-1",
                "",
                "",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                "IMPORTED",
                "",
                "");
        Path source = tempDir.resolve("normalized-authorization.csv");
        Files.write(source, new NormalizedBankCsvSerializer().serialize(List.of(row)));
        return source;
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
                "SCA", "SCA Bank", "111000111", null, null, null, null, null, null, true));
        CompanyBankAccount account = configuration.createBankAccount(new BankAccountCommand(
                "SCA", bank.getId(), 101L, "****4321", "SCA Checking",
                LocalDate.of(2026, 1, 1), BigDecimal.ZERO, BankingDataFormat.CSV,
                "111000111", "SCA-4321", null, true));
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
        Instant now = Instant.parse("2026-09-03T16:30:00Z");
        return new AuthenticatedUserSession(
                userId, "operator", "Operator", companyCode, roles, now, now);
    }

    private static long count(Jpa jpa, String jpql)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(jpql, Long.class).getSingleResult();
        }
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

    private record SecurityUsers(long viewerId, long accountantId, long managerId, long adminId) { }
}