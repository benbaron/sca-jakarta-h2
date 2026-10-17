package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.report.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.NmrItemTestFixture.*;

class NmrItemDetailTest
{
    @TempDir Path directory;

    @Test
    void explicitFactsPartialPaymentReversalCutoffRestartAndReportTieOutWithoutPolicy()
    {
        Path database = directory.resolve("history");
        UUID item = UUID.randomUUID();
        try (Jpa jpa = new Jpa(database))
        {
            var r = seed(jpa);
            var entry = r.entry(jpa);
            var receipt = entry.enter(r.receipt(item, r.recipient(), r.event()));
            assertEquals(r.recipient(), entry.load(receipt.id()).supplementalLines().get(0).nmrRecipientId());
            var payment = entry.enter(r.payment(item, "5", r.recipient(), r.event()));
            assertAmount("15", query(jpa).items().get(0).openBalance());
            var dimensions = query(jpa).items().get(0).nmr();
            assertEquals(r.event(), dimensions.eventId()); assertEquals(r.fund(), dimensions.fundId());
            assertEquals(r.liability(), dimensions.accountId()); assertEquals(r.recipient(), dimensions.recipientId());
            assertTrue(query(jpa).ready(), () -> query(jpa).toString());
            new TransactionCorrectionService(jpa, () -> COMPANY).reverse(payment.id(), DATE.plusDays(2), "tester", "Explicit reversal", false);
            var queries = new SupplementalOpenItemQueryService(jpa, () -> COMPANY);
            assertAmount("15", queries.queryNmr(DATE.plusDays(1)).items().get(0).openBalance());
            assertAmount("20", query(jpa).items().get(0).openBalance());
            assertTrue(query(jpa).ready());
            var execution = new ReportExecutionService(new FinancialReportService(jpa, () -> COMPANY),
                    FinancialReportDisplayFormat.plain(), null, null, queries, ReportPresentationMetadata.EMPTY);
            var report = execution.execute(new ReportRequest(ReportDefinition.NMR_LIABILITIES, DATE, DATE.plusDays(2), ReportFundOption.ALL_FUNDS, 1));
            assertTrue(report.tabular()); assertTrue(report.text().contains("NMR coverage: COMPLETE"));
            assertTrue(report.text().contains("Synthetic recipient")); assertTrue(report.csv().contains("Synthetic event"));
            assertTrue(report.tableModel().rows().stream().anyMatch(row -> item.toString().equals(row.value("item"))));
            assertEquals(0, count(jpa, "select count(p) from NmrPolicyVersion p"));
            assertEquals(3, count(jpa, "select count(t) from Txn t where t.company.code = 'NMR_DETAIL'"));
        }
        try (Jpa jpa = new Jpa(database))
        {
            assertTrue(query(jpa).ready()); assertAmount("20", query(jpa).items().get(0).openBalance());
        }
    }

    @Test
    void designationRejectsMissingEventForeignRecipientMixedIdentityAndExcessAtomically()
    {
        try (Jpa jpa = new Jpa(directory.resolve("invalid")))
        {
            var r = seed(jpa); var entry = r.entry(jpa); UUID item = UUID.randomUUID();
            long recipient2;
            long foreign;
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                var company = em.find(Counterparty.class, r.recipient()).getCompany();
                Counterparty other = new Counterparty(); other.setCompany(company); other.setDisplayName("Second recipient"); em.persist(other);
                Company foreignCompany = new Company(); foreignCompany.setCode("FOREIGN"); foreignCompany.setDisplayName("Foreign"); em.persist(foreignCompany);
                Counterparty party = new Counterparty(); party.setCompany(foreignCompany); party.setDisplayName("Foreign recipient"); em.persist(party);
                em.getTransaction().commit(); recipient2 = other.getId(); foreign = party.getId();
            }
            assertThrows(PostingException.class, () -> entry.enter(r.receipt(item, r.recipient(), null)));
            assertThrows(RuntimeException.class, () -> entry.enter(r.receipt(item, foreign, r.event())));
            assertEquals(0, count(jpa, "select count(t) from Txn t"));
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin(); em.find(Account.class, r.liability()).setAccountType(AccountType.INCOME); em.getTransaction().commit();
            }
            assertThrows(PostingException.class, () -> entry.enter(r.receipt(item, r.recipient(), r.event())),
                    "An NMR subtype must not promote an income account to custodial authority");
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin(); em.find(Account.class, r.liability()).setAccountType(AccountType.LIABILITY); em.getTransaction().commit();
            }
            entry.enter(r.receipt(item, r.recipient(), r.event()));
            long audits = count(jpa, "select count(a) from AuditEvent a");
            for (var command : List.of(r.payment(item, "5", recipient2, r.event()), r.payment(item, "5", null, r.event()),
                    r.payment(item, "5", r.recipient(), null), r.payment(item, "21", r.recipient(), r.event())))
            {
                assertThrows(PostingException.class, () -> entry.enter(command));
                assertEquals(1, count(jpa, "select count(t) from Txn t"));
                assertEquals(1, count(jpa, "select count(l) from TxnSupplementalLine l"));
                assertEquals(audits, count(jpa, "select count(a) from AuditEvent a"));
            }
            assertAmount("20", query(jpa).items().get(0).openBalance());
        }
    }

    @Test
    void legacyFlagsAndUnclassifiedLiabilitiesNeverBecomeExplicitItemsOrCompleteCoverage()
    {
        try (Jpa jpa = new Jpa(directory.resolve("legacy")))
        {
            var r = seed(jpa); var entry = r.entry(jpa);
            entry.enter(r.receipt(UUID.randomUUID(), null, r.event()));
            assertTrue(query(jpa).items().isEmpty()); assertFalse(query(jpa).ready());
            assertEquals(1, query(jpa).legacyFlaggedLines()); assertAmount("20", query(jpa).controls().get(0).unclassified());
            entry.enter(r.receipt(UUID.randomUUID(), r.recipient(), r.event()));
            assertEquals(1, query(jpa).items().size()); assertFalse(query(jpa).ready());
            assertAmount("40", query(jpa).controls().get(0).ledger());
            assertAmount("20", query(jpa).controls().get(0).nmr());
            assertAmount("20", query(jpa).controls().get(0).unclassified());
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin(); em.find(Account.class, r.liability()).setOpeningBalance(new BigDecimal("7")); em.getTransaction().commit();
            }
            assertTrue(query(jpa).controls().stream().anyMatch(c -> c.fundId() == null && c.unclassified().compareTo(new BigDecimal("7")) == 0));
        }
    }

    @Test
    void viewerDeniedAndAccountantUsesExistingCanonicalGuardAndAudit()
    {
        try (Jpa jpa = new Jpa(directory.resolve("permission")))
        {
            var r = seed(jpa); var command = r.receipt(UUID.randomUUID(), r.recipient(), r.event());
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                AppUser user = new AppUser(); user.setUsername("nmr-operator"); user.setDisplayName("NMR operator"); em.persist(user);
                em.getTransaction().commit();
            }
            assertThrows(AuthorizationException.class, () -> new TransactionEntryService(jpa, () -> COMPANY,
                    guard(jpa, ReservedSecurityRole.VIEWER)).enter(command));
            assertEquals(0, count(jpa, "select count(t) from Txn t"));
            new TransactionEntryService(jpa, () -> COMPANY, guard(jpa, ReservedSecurityRole.ACCOUNTANT)).enter(command);
            assertTrue(query(jpa).ready());
            try (EntityManager em = jpa.em())
            {
                var audit = em.createQuery("from AuditEvent a where a.actionType = 'TRANSACTION_ENTERED'", AuditEvent.class).getSingleResult();
                assertTrue(audit.getAfterValue().contains("nmrRecipientId=" + r.recipient()));
            }
        }
    }

    @Test
    void onePaymentAllocatesTwoExplicitEventItemsAndLimitedReportKeepsFullControls()
    {
        try (Jpa jpa = new Jpa(directory.resolve("multi-event")))
        {
            var r = seed(jpa); long secondEvent;
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Activity event = new Activity(); event.setCompany(em.find(Activity.class, r.event()).getCompany());
                event.setCode("SECOND"); event.setName("Second event"); event.setActive(true); em.persist(event);
                em.getTransaction().commit(); secondEvent = event.getId();
            }
            var second = new NmrItemTestFixture(r.cash(), r.income(), r.liability(), r.fund(), secondEvent, r.recipient());
            var entry = r.entry(jpa); UUID firstItem = UUID.randomUUID(), secondItem = UUID.randomUUID();
            var receipt = entry.enter(r.receipt(firstItem, r.recipient(), r.event()));
            entry.enter(second.receipt(secondItem, second.recipient(), second.event()));
            long audits = count(jpa, "select count(a) from AuditEvent a");
            assertThrows(PostingException.class, () -> entry.enter(second.payment(firstItem, "1", r.recipient(), second.event())));
            assertEquals(audits, count(jpa, "select count(a) from AuditEvent a"));
            entry.enter(new TransactionCommand(DATE.plusDays(1), null, "Two-event payment", null, List.of(
                    r.line(r.liability(), "5", "0", r.event()), second.line(r.liability(), "7", "0", second.event()),
                    r.line(r.cash(), "0", "12", null)), List.of(
                    r.allocation(firstItem, "DECREASE", 0, "5", r.recipient()), second.allocation(secondItem, "DECREASE", 1, "7", r.recipient()))));
            assertTrue(query(jpa).ready()); assertEquals(2, query(jpa).items().size());
            assertAmount("28", query(jpa).controls().get(0).ledger()); assertAmount("28", query(jpa).controls().get(0).nmr());
            // A correction cannot remove designation from only the opening allocation.
            assertThrows(PostingException.class, () -> entry.update(receipt.id(), r.receipt(firstItem, null, r.event())));
            assertTrue(query(jpa).ready());
            var request = new ReportRequest(ReportDefinition.NMR_LIABILITIES, DATE, DATE.plusDays(2), ReportFundOption.ALL_FUNDS, 1);
            var report = NmrLiabilityReportBuilder.result(request, query(jpa), FinancialReportDisplayFormat.plain());
            assertTrue(report.text().contains("Showing 1 of 2 NMR items; controls use all items"));
            assertEquals(1, report.tableModel().rows().stream().filter(row -> "ITEM".equals(row.value("record"))).count());
            assertTrue(report.tableModel().rows().stream().anyMatch(row -> "CONTROL".equals(row.value("record"))
                    && new BigDecimal("28").compareTo((BigDecimal) row.value("nmr")) == 0));
        }
    }

    @Test
    void journalCannotReplaceInheritedReversalFactsWithDuplicateItemAllocations()
    {
        try (Jpa jpa = new Jpa(directory.resolve("reversal-edit")))
        {
            var r = seed(jpa); var entry = r.entry(jpa); UUID item = UUID.randomUUID();
            entry.enter(r.receipt(item, r.recipient(), r.event()));
            var payment = entry.enter(r.payment(item, "5", r.recipient(), r.event()));
            var reversal = new TransactionCorrectionService(jpa, () -> COMPANY)
                    .reverse(payment.id(), DATE.plusDays(2), "tester", "Restore item", false);
            var duplicate = new TransactionCommand(DATE.plusDays(2), null, "Duplicate inherited item effect", null,
                    List.of(r.line(r.cash(), "5", "0", r.event()), r.line(r.liability(), "0", "5", r.event())),
                    List.of(r.allocation(item, "INCREASE", 1, "5", r.recipient())));
            long audits = count(jpa, "select count(a) from AuditEvent a");
            var detached = new TransactionCommand(DATE.plusDays(2), null, "Detach inherited liability", null,
                    List.of(r.line(r.cash(), "5", "0", r.event()), r.line(r.income(), "0", "5", r.event())), List.of());
            for (var command : List.of(duplicate, detached))
            {
                assertThrows(PostingException.class, () -> entry.update(reversal.reversalTransactionId(), command));
            }
            assertEquals(audits, count(jpa, "select count(a) from AuditEvent a"));
            assertTrue(entry.load(reversal.reversalTransactionId()).supplementalLines().isEmpty());
            assertTrue(query(jpa).ready()); assertAmount("20", query(jpa).items().get(0).openBalance());
            // Supported correction is another dated inverse, preserving the original cutoff.
            new TransactionCorrectionService(jpa, () -> COMPANY).reverse(reversal.reversalTransactionId(), DATE.plusDays(3), "tester", "Undo reversal", false);
            assertAmount("20", query(jpa).items().get(0).openBalance());
            var after = new SupplementalOpenItemQueryService(jpa, () -> COMPANY).queryNmr(DATE.plusDays(3));
            assertTrue(after.ready()); assertAmount("15", after.items().get(0).openBalance());
        }
    }

    private static AuthorizationGuard guard(Jpa jpa, ReservedSecurityRole role)
    {
        Instant now = Instant.now();
        long userId;
        try (EntityManager em = jpa.em())
        {
            userId = em.createQuery("select u.id from AppUser u order by u.id", Long.class).setMaxResults(1).getSingleResult();
        }
        return new AuthorizationGuard(jpa, () -> Optional.of(new AuthenticatedUserSession(userId,
                "nmr-operator", "NMR operator", COMPANY, Set.of(role), now, now)));
    }

    private static NmrLiabilityQueryService.Result query(Jpa jpa)
    {
        return new SupplementalOpenItemQueryService(jpa, () -> COMPANY).queryNmr(DATE.plusDays(2));
    }

    private static long count(Jpa jpa, String query)
    {
        try (EntityManager em = jpa.em()) { return em.createQuery(query, Long.class).getSingleResult(); }
    }

    private static void assertAmount(String expected, BigDecimal actual)
    {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
