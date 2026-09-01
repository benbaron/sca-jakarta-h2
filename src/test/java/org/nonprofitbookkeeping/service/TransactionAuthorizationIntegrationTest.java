package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionAuthorizationIntegrationTest
{
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-31T18:00:00Z"), ZoneOffset.UTC);

    @Test
    void viewerCannotMutateJournalAndDenialsLeaveCanonicalTransactionsUnchanged(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("journal-viewer-authorization")))
        {
            UserAdminService users = initializeSecurity(jpa);
            long viewerUserId = reservedUserId(users, ReservedSecurityRole.VIEWER);
            seedMasterData(jpa);

            TransactionEntryService setup = unguardedEntry(jpa);
            TransactionView original = setup.enter(command("Original", new BigDecimal("100.00")));
            long transactionCountBefore = transactionCount(jpa);
            long auditCountBefore = auditCount(jpa);

            Supplier<Optional<AuthenticatedUserSession>> viewerSession = () -> Optional.of(
                    session(viewerUserId, "DEFAULT", Set.of(ReservedSecurityRole.VIEWER)));
            TransactionEntryService entry = guardedEntry(jpa, viewerSession);
            TransactionCorrectionService correction = guardedCorrection(jpa, viewerSession);

            assertEquals("Original", entry.load(original.id()).memo());
            assertThrows(AuthorizationException.class,
                    () -> entry.enter(command("Denied new", new BigDecimal("25.00"))));
            assertThrows(AuthorizationException.class,
                    () -> entry.update(original.id(), command("Denied update", new BigDecimal("100.00"))));
            assertThrows(AuthorizationException.class,
                    () -> correction.directEdit(
                            original.id(), LocalDate.of(2026, 3, 15), "Denied edit", "viewer", "viewer"));
            assertThrows(AuthorizationException.class,
                    () -> correction.delete(original.id(), "viewer", "viewer"));
            assertThrows(AuthorizationException.class,
                    () -> correction.reverse(
                            original.id(), LocalDate.of(2026, 3, 15), "viewer", "viewer", false));

            assertEquals(transactionCountBefore, transactionCount(jpa));
            assertEquals(auditCountBefore, auditCount(jpa));
            assertEquals("Original", transactionMemo(jpa, original.id()));
            assertEquals("ENTERED", transactionStatus(jpa, original.id()));
            assertEquals(5L, authorizationDenialCount(jpa));
        }
    }

    @Test
    void accountantManagerAdminAndNonAdminRoleUnionCanMutateWithImmediateSessionChanges(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("journal-bookkeeping-role-authorization")))
        {
            UserAdminService users = initializeSecurity(jpa);
            long adminUserId = reservedUserId(users, ReservedSecurityRole.ADMIN);
            long managerUserId = reservedUserId(users, ReservedSecurityRole.MANAGER);
            long accountantUserId = reservedUserId(users, ReservedSecurityRole.ACCOUNTANT);
            long viewerUserId = reservedUserId(users, ReservedSecurityRole.VIEWER);
            seedMasterData(jpa);

            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(
                    Optional.of(session(
                            accountantUserId,
                            "DEFAULT",
                            Set.of(ReservedSecurityRole.ACCOUNTANT))));
            TransactionEntryService entry = guardedEntry(jpa, current::get);
            TransactionCorrectionService correction = guardedCorrection(jpa, current::get);

            TransactionView first = entry.enter(command("Accountant entry", new BigDecimal("100.00")));
            entry.update(first.id(), command("Accountant update", new BigDecimal("90.00")));
            assertEquals("Accountant update", transactionMemo(jpa, first.id()));

            current.set(Optional.of(session(
                    managerUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.MANAGER))));
            correction.directEdit(
                    first.id(), LocalDate.of(2026, 3, 15), "Manager edit", "manager correction", "manager");
            assertEquals("Manager edit", transactionMemo(jpa, first.id()));

            current.set(Optional.of(session(
                    adminUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.ADMIN))));
            TransactionView second = entry.enter(command("Admin entry", new BigDecimal("50.00")));
            TransactionCorrectionService.CorrectionResult reversed = correction.reverse(
                    second.id(), LocalDate.of(2026, 3, 16), "admin", "admin reversal", false);
            assertNotNull(reversed.reversalTransactionId());
            assertEquals("REVERSED", transactionStatus(jpa, second.id()));

            current.set(Optional.of(session(
                    accountantUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.VIEWER, ReservedSecurityRole.ACCOUNTANT))));
            TransactionView unionEntry = entry.enter(command("Union entry", new BigDecimal("30.00")));
            assertEquals("Union entry", transactionMemo(jpa, unionEntry.id()));

            current.set(Optional.of(session(
                    viewerUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.VIEWER))));
            assertThrows(AuthorizationException.class,
                    () -> entry.update(first.id(), command("Viewer denied", new BigDecimal("90.00"))));
            assertEquals("Manager edit", transactionMemo(jpa, first.id()));
            assertEquals(1L, authorizationDenialCount(jpa));
        }
    }

    @Test
    void absentAndWrongCompanySessionsFailClosedWhileCallerOwnedTransactionSeamsRemainOuterGoverned(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("journal-session-boundary-authorization")))
        {
            UserAdminService users = initializeSecurity(jpa);
            long adminUserId = reservedUserId(users, ReservedSecurityRole.ADMIN);
            seedMasterData(jpa);

            TransactionEntryService setup = unguardedEntry(jpa);
            TransactionView original = setup.enter(command("Original", new BigDecimal("100.00")));

            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(Optional.empty());
            TransactionEntryService entry = guardedEntry(jpa, current::get);
            TransactionCorrectionService correction = guardedCorrection(jpa, current::get);

            assertThrows(AuthorizationException.class,
                    () -> entry.update(original.id(), command("No session", new BigDecimal("100.00"))));

            current.set(Optional.of(session(
                    adminUserId,
                    "OTHER",
                    Set.of(ReservedSecurityRole.ADMIN))));
            assertThrows(AuthorizationException.class,
                    () -> correction.delete(original.id(), "admin", "wrong company"));

            assertEquals("Original", transactionMemo(jpa, original.id()));
            assertEquals("ENTERED", transactionStatus(jpa, original.id()));
            assertEquals(2L, authorizationDenialCount(jpa));

            current.set(Optional.empty());
            Long importedId;
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Company company = new CompanyOwnershipService(jpa).requireCompany(em, "DEFAULT");
                Txn imported = entry.enter(
                        em,
                        company,
                        command("Outer-governed import", new BigDecimal("40.00")),
                        UUID.fromString("12345678-1234-1234-1234-123456789012"),
                        "outer-import");
                em.getTransaction().commit();
                importedId = imported.getId();
            }
            assertNotNull(importedId);
            assertEquals("Outer-governed import", transactionMemo(jpa, importedId));

            Long reversalId;
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Company company = new CompanyOwnershipService(jpa).requireCompany(em, "DEFAULT");
                Txn imported = em.find(Txn.class, importedId);
                Txn reversal = correction.reverse(
                        em,
                        company,
                        imported,
                        LocalDate.of(2026, 3, 17),
                        "outer-domain",
                        "outer-governed correction",
                        UUID.fromString("12345678-1234-1234-1234-123456789013"));
                em.getTransaction().commit();
                reversalId = reversal.getId();
            }
            assertNotNull(reversalId);
            assertEquals("REVERSED", transactionStatus(jpa, importedId));
            assertEquals(2L, authorizationDenialCount(jpa));
        }
    }

    private static UserAdminService initializeSecurity(Jpa jpa)
    {
        new AuthenticationService(jpa).initializeSecurityIfUnambiguous();
        return new UserAdminService(jpa, () -> "DEFAULT", CLOCK, () -> { });
    }

    private static TransactionEntryService unguardedEntry(Jpa jpa)
    {
        return new TransactionEntryService(jpa, new TransactionCommandValidator(), () -> "DEFAULT");
    }

    private static TransactionEntryService guardedEntry(
            Jpa jpa,
            Supplier<Optional<AuthenticatedUserSession>> currentSession)
    {
        return new TransactionEntryService(
                jpa,
                new TransactionCommandValidator(),
                () -> "DEFAULT",
                new AuthorizationGuard(jpa, currentSession, CLOCK));
    }

    private static TransactionCorrectionService guardedCorrection(
            Jpa jpa,
            Supplier<Optional<AuthenticatedUserSession>> currentSession)
    {
        return new TransactionCorrectionService(
                jpa,
                () -> "DEFAULT",
                new AuthorizationGuard(jpa, currentSession, CLOCK));
    }

    private static long reservedUserId(UserAdminService service, ReservedSecurityRole role)
    {
        return service.listUsers().stream()
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
        Instant now = Instant.parse("2026-08-31T18:00:00Z");
        return new AuthenticatedUserSession(
                userId,
                "operator",
                "Operator",
                companyCode,
                roles,
                now,
                now);
    }

    private static TransactionCommand command(String memo, BigDecimal amount)
    {
        return new TransactionCommand(
                LocalDate.of(2026, 3, 14),
                1L,
                memo,
                1L,
                List.of(
                        new TransactionLineCommand(
                                1L, 1L, null, null, null,
                                amount, BigDecimal.ZERO, false, "cash"),
                        new TransactionLineCommand(
                                2L, 1L, null, null, null,
                                BigDecimal.ZERO, amount, false, "income")));
    }

    private static void seedMasterData(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery(
                    "INSERT INTO chart_of_accounts (id, name, version, status) VALUES (1, 'Test', '1', 'ACTIVE')")
                    .executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) "
                            + "VALUES (1, 1, '1000', 'Cash', 'ASSET', 'DEBIT')")
                    .executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) "
                            + "VALUES (2, 1, '4000', 'Income', 'INCOME', 'CREDIT')")
                    .executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO fund (id, code, name, fund_type) VALUES (1, 'OPERATING', 'Operating', 'UNRESTRICTED')")
                    .executeUpdate();
            em.createNativeQuery(
                    "INSERT INTO counterparty (id, display_name, kind) VALUES (1, 'Donor', 'OTHER')")
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static long transactionCount(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(t) from Txn t", Long.class).getSingleResult();
        }
    }

    private static long auditCount(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(a) from AuditEvent a", Long.class).getSingleResult();
        }
    }

    private static String transactionMemo(Jpa jpa, long transactionId)
    {
        try (EntityManager em = jpa.em())
        {
            return em.find(Txn.class, transactionId).getMemo();
        }
    }

    private static String transactionStatus(Jpa jpa, long transactionId)
    {
        try (EntityManager em = jpa.em())
        {
            return em.find(Txn.class, transactionId).getStatus();
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
}
