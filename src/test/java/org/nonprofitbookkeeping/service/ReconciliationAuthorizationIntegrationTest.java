package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AppUser;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.ClearedStatePolicy;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.SessionImport;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.SessionStatus;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.StartCommand;
import org.nonprofitbookkeeping.service.BankReconciliationWorkspaceService.SuccessorCommand;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconciliationAuthorizationIntegrationTest
{
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-02T01:30:00Z"), ZoneOffset.UTC);
    private static final long CHART_ID = 22_001L;
    private static final long FUND_ID = 22_001L;
    private static final long BANK_ACCOUNT_ID = 22_001L;
    private static final long EXPENSE_ACCOUNT_ID = 22_002L;
    private static final long BANK_ID = 22_001L;
    private static final long CONFIGURED_BANK_ACCOUNT_ID = 22_001L;
    private static final long TXN_ID = 22_001L;
    private static final long BANK_SPLIT_ID = 22_001L;
    private static final long EXPENSE_SPLIT_ID = 22_002L;
    private static final long BATCH_ID = 22_001L;
    private static final long STATEMENT_LINE_ID = 22_001L;

    @Test
    void viewerCannotMutateReconciliationAndAuthorizationRunsBeforeValidation(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("reconciliation-viewer-authorization")))
        {
            UserAdminService users = initializeSecurity(jpa);
            long viewerUserId = reservedUserId(users, ReservedSecurityRole.VIEWER);
            seedMasterData(jpa);

            BankReconciliationWorkspaceService setup = new BankReconciliationWorkspaceService(jpa);
            long sessionId = startMarch(setup);
            long sessionCountBefore = reconciliationSessionCount(jpa);
            long statementCountBefore = statementLineCount(jpa);
            long matchCountBefore = reconciliationMatchCount(jpa);

            Supplier<Optional<AuthenticatedUserSession>> viewerSession = () -> Optional.of(
                    session(viewerUserId, "DEFAULT", Set.of(ReservedSecurityRole.VIEWER)));
            AuthorizationGuard guard = new AuthorizationGuard(jpa, viewerSession, CLOCK);
            BankReconciliationWorkspaceService service =
                    new BankReconciliationWorkspaceService(jpa, guard);
            BankClearedStateService clearedStateService = new BankClearedStateService(jpa, guard);

            assertEquals(1, service.listConfiguredBankAccounts("DEFAULT").size());
            assertEquals(1, service.listSessions("DEFAULT").size());
            assertEquals(sessionId, service.load(sessionId).sessionId());

            assertThrows(AuthorizationException.class, () -> service.start(null));
            assertThrows(AuthorizationException.class, () -> service.startSuccessor(null));
            assertThrows(AuthorizationException.class, () -> service.addManualLine(null));
            assertThrows(AuthorizationException.class, () -> service.autoMatch(sessionId));
            assertThrows(AuthorizationException.class,
                    () -> service.matchSelected(sessionId, null, null, false));
            assertThrows(AuthorizationException.class,
                    () -> service.unmatchSelected(sessionId, null, null));
            assertThrows(AuthorizationException.class,
                    () -> service.markCleared(sessionId, null));
            assertThrows(AuthorizationException.class,
                    () -> service.recordDifferenceExplanation(sessionId, null, null, null));
            assertThrows(AuthorizationException.class, () -> service.save(sessionId, false));
            assertThrows(AuthorizationException.class,
                    () -> clearedStateService.markMatchedAndCleared(STATEMENT_LINE_ID, BANK_SPLIT_ID));

            assertEquals(sessionCountBefore, reconciliationSessionCount(jpa));
            assertEquals(statementCountBefore, statementLineCount(jpa));
            assertEquals(matchCountBefore, reconciliationMatchCount(jpa));
            assertFalse(bankSplitCleared(jpa));
            assertEquals(10L, authorizationDenialCount(jpa));
        }
    }

    @Test
    void bookkeepingRolesAndUnionCanMutateAndSessionChangesTakeEffectImmediately(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("reconciliation-role-authorization")))
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
            BankReconciliationWorkspaceService service = new BankReconciliationWorkspaceService(
                    jpa, new AuthorizationGuard(jpa, current::get, CLOCK));

            long sessionId = startMarch(service);
            assertNotNull(sessionId);

            current.set(Optional.of(session(
                    managerUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.MANAGER))));
            assertTrue(service.markCleared(sessionId, BANK_SPLIT_ID)
                    .ledgerLines().stream()
                    .filter(line -> line.splitId().equals(BANK_SPLIT_ID))
                    .findFirst()
                    .orElseThrow()
                    .cleared());

            current.set(Optional.of(session(
                    adminUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.ADMIN))));
            assertEquals(1, service.matchSelected(
                            sessionId, STATEMENT_LINE_ID, BANK_SPLIT_ID, true)
                    .statementEntries().stream()
                    .filter(line -> line.matchedLedgerSplitId() != null)
                    .count());

            current.set(Optional.of(session(
                    accountantUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.VIEWER, ReservedSecurityRole.ACCOUNTANT))));
            assertEquals(SessionStatus.FINALIZED, service.save(sessionId, true).status());

            current.set(Optional.of(session(
                    adminUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.ADMIN))));
            long successorId = service.startSuccessor(new SuccessorCommand(
                    sessionId,
                    LocalDate.of(2026, 4, 30),
                    new BigDecimal("-25.75"),
                    null,
                    "April successor",
                    "compatibility actor",
                    "Continue with the April statement.")).sessionId();
            assertEquals(SessionStatus.IN_PROGRESS, service.load(successorId).status());

            current.set(Optional.of(session(
                    viewerUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.VIEWER))));
            assertThrows(AuthorizationException.class, () -> service.save(successorId, false));
            assertEquals(SessionStatus.IN_PROGRESS, service.load(successorId).status());
            assertEquals(1L, authorizationDenialCount(jpa));
        }
    }

    @Test
    void absentAndWrongCompanySessionsFailClosedWhileCallerOwnedImportSeamsRemainOuterGoverned(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("reconciliation-session-boundary-authorization")))
        {
            UserAdminService users = initializeSecurity(jpa);
            long adminUserId = reservedUserId(users, ReservedSecurityRole.ADMIN);
            long viewerUserId = reservedUserId(users, ReservedSecurityRole.VIEWER);
            seedMasterData(jpa);

            long sessionId = startMarch(new BankReconciliationWorkspaceService(jpa));
            AtomicReference<Optional<AuthenticatedUserSession>> current =
                    new AtomicReference<>(Optional.empty());
            AuthorizationGuard guard = new AuthorizationGuard(jpa, current::get, CLOCK);
            BankReconciliationWorkspaceService service =
                    new BankReconciliationWorkspaceService(jpa, guard);
            BankClearedStateService clearedStateService = new BankClearedStateService(jpa, guard);

            assertThrows(AuthorizationException.class,
                    () -> service.markCleared(sessionId, BANK_SPLIT_ID));
            assertFalse(bankSplitCleared(jpa));

            current.set(Optional.of(session(
                    adminUserId,
                    "OTHER",
                    Set.of(ReservedSecurityRole.ADMIN))));
            assertThrows(AuthorizationException.class, () -> service.autoMatch(sessionId));
            assertEquals(0L, reconciliationMatchCount(jpa));

            current.set(Optional.empty());
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Company company = em.createQuery(
                                "select c from Company c where c.code = 'DEFAULT'", Company.class)
                        .getSingleResult();
                CompanyBankAccount bankAccount = em.find(
                        CompanyBankAccount.class, CONFIGURED_BANK_ACCOUNT_ID);
                var imported = service.importForInterchange(
                        em,
                        company,
                        List.of(new SessionImport(
                                "imported-session",
                                UUID.fromString("22000000-0000-0000-0000-000000000001"),
                                "configured-bank",
                                LocalDate.of(2026, 4, 1),
                                LocalDate.of(2026, 4, 30),
                                new BigDecimal("-25.7500"),
                                ClearedStatePolicy.WARN_ONLY.name(),
                                SessionStatus.IN_PROGRESS.name(),
                                "Imported reconciliation history",
                                new BigDecimal("-25.7500"),
                                new BigDecimal("-25.7500"),
                                new BigDecimal("0.0000"),
                                new BigDecimal("-25.7500"),
                                Instant.parse("2026-04-30T00:00:00Z"),
                                Instant.parse("2026-04-30T00:00:00Z"))),
                        List.of(),
                        Map.of("configured-bank", bankAccount),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of());
                assertTrue(imported.sessions().containsKey("imported-session"));

                TxnSplit split = em.find(TxnSplit.class, BANK_SPLIT_ID);
                BankStatementLine statementLine = em.find(
                        BankStatementLine.class, STATEMENT_LINE_ID);
                clearedStateService.applyForImport(
                        em,
                        company,
                        split,
                        statementLine,
                        LocalDate.of(2026, 3, 16));
                em.getTransaction().commit();
            }

            assertEquals(2L, reconciliationSessionCount(jpa));
            assertTrue(bankSplitCleared(jpa));

            current.set(Optional.of(session(
                    viewerUserId,
                    "DEFAULT",
                    Set.of(ReservedSecurityRole.VIEWER))));
            assertThrows(AuthorizationException.class,
                    () -> clearedStateService.markMatchedAndCleared(
                            STATEMENT_LINE_ID, BANK_SPLIT_ID));
            assertEquals(3L, authorizationDenialCount(jpa));
        }
    }

    private static UserAdminService initializeSecurity(Jpa jpa)
    {
        new AuthenticationService(jpa).initializeSecurityIfUnambiguous();
        return new UserAdminService(jpa, () -> "DEFAULT", CLOCK, () -> { });
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
        Instant now = Instant.parse("2026-09-02T01:30:00Z");
        return new AuthenticatedUserSession(
                userId,
                "operator",
                "Operator",
                companyCode,
                roles,
                now,
                now);
    }

    private static long startMarch(BankReconciliationWorkspaceService service)
    {
        return service.start(new StartCommand(
                "DEFAULT",
                CONFIGURED_BANK_ACCOUNT_ID,
                LocalDate.of(2026, 3, 31),
                new BigDecimal("-25.75"),
                ClearedStatePolicy.OVERWRITE_LEDGER_CLEARED_STATE,
                "March reconciliation")).sessionId();
    }

    private static void seedMasterData(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Long companyId = em.createQuery(
                            "select c.id from Company c where c.code = 'DEFAULT'", Long.class)
                    .getSingleResult();
            em.createNativeQuery(
                            "INSERT INTO chart_of_accounts (id, company_id, name, version, status) "
                                    + "VALUES (?, ?, 'Reconciliation Authorization Test', '1', 'ACTIVE')")
                    .setParameter(1, CHART_ID)
                    .setParameter(2, companyId)
                    .executeUpdate();
            em.createNativeQuery(
                            "UPDATE company SET active_chart_of_accounts_id = ? WHERE id = ?")
                    .setParameter(1, CHART_ID)
                    .setParameter(2, companyId)
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) "
                                    + "VALUES (?, ?, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')")
                    .setParameter(1, BANK_ACCOUNT_ID)
                    .setParameter(2, CHART_ID)
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) "
                                    + "VALUES (?, ?, '5000', 'Expense', 'EXPENSE', 'DEBIT')")
                    .setParameter(1, EXPENSE_ACCOUNT_ID)
                    .setParameter(2, CHART_ID)
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO fund (id, company_id, code, name, fund_type) "
                                    + "VALUES (?, ?, 'UNR-AUTH', 'Authorization Fund', 'UNRESTRICTED')")
                    .setParameter(1, FUND_ID)
                    .setParameter(2, companyId)
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO bank (id, company_id, name) VALUES (?, ?, 'Authorization Bank')")
                    .setParameter(1, BANK_ID)
                    .setParameter(2, companyId)
                    .executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO company_bank_account
                        (id, company_id, name, bank_id, account_id, opening_date,
                         opening_balance, statement_import_format)
                    VALUES (?, ?, 'Operating Checking', ?, ?, DATE '2026-03-01', 0.0000, 'CSV')
                    """)
                    .setParameter(1, CONFIGURED_BANK_ACCOUNT_ID)
                    .setParameter(2, companyId)
                    .setParameter(3, BANK_ID)
                    .setParameter(4, BANK_ACCOUNT_ID)
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO txn (id, company_id, txn_date, memo, status) "
                                    + "VALUES (?, ?, DATE '2026-03-15', 'Office supplies', 'ENTERED')")
                    .setParameter(1, TXN_ID)
                    .setParameter(2, companyId)
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) "
                                    + "VALUES (?, ?, ?, ?, -25.7500)")
                    .setParameter(1, BANK_SPLIT_ID)
                    .setParameter(2, TXN_ID)
                    .setParameter(3, BANK_ACCOUNT_ID)
                    .setParameter(4, FUND_ID)
                    .executeUpdate();
            em.createNativeQuery(
                            "INSERT INTO txn_split (id, txn_id, account_id, fund_id, amount_signed) "
                                    + "VALUES (?, ?, ?, ?, 25.7500)")
                    .setParameter(1, EXPENSE_SPLIT_ID)
                    .setParameter(2, TXN_ID)
                    .setParameter(3, EXPENSE_ACCOUNT_ID)
                    .setParameter(4, FUND_ID)
                    .executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO bank_import_batch
                        (id, company_id, bank_account_id, source_name, source_format, status, total_line_count)
                    VALUES (?, ?, ?, 'march.csv', 'CSV', 'IMPORTED', 1)
                    """)
                    .setParameter(1, BATCH_ID)
                    .setParameter(2, companyId)
                    .setParameter(3, CONFIGURED_BANK_ACCOUNT_ID)
                    .executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO bank_statement_line
                        (id, batch_id, company_id, bank_account_id, source_row_number,
                         deterministic_fingerprint, transaction_date, posted_date, amount, status)
                    VALUES (?, ?, ?, ?, 1, 'reconciliation-auth-fp',
                            DATE '2026-03-15', DATE '2026-03-16', -25.7500, 'IMPORTED')
                    """)
                    .setParameter(1, STATEMENT_LINE_ID)
                    .setParameter(2, BATCH_ID)
                    .setParameter(3, companyId)
                    .setParameter(4, CONFIGURED_BANK_ACCOUNT_ID)
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static long reconciliationSessionCount(Jpa jpa)
    {
        return nativeCount(jpa, "bank_reconciliation_session");
    }

    private static long reconciliationMatchCount(Jpa jpa)
    {
        return nativeCount(jpa, "bank_reconciliation_match");
    }

    private static long statementLineCount(Jpa jpa)
    {
        return nativeCount(jpa, "bank_statement_line");
    }

    private static long nativeCount(Jpa jpa, String table)
    {
        try (EntityManager em = jpa.em())
        {
            Number count = (Number) em.createNativeQuery("select count(*) from " + table)
                    .getSingleResult();
            return count.longValue();
        }
    }

    private static boolean bankSplitCleared(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            return em.find(TxnSplit.class, BANK_SPLIT_ID).isBankCleared();
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
