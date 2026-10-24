package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.ExpenseFunction;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.nonprofitbookkeeping.service.NmrItemTestFixture.COMPANY;
import static org.nonprofitbookkeeping.service.NmrItemTestFixture.DATE;

class MerchantSettlementAssistantServiceTest
{
    @TempDir Path directory;

    @Test
    void payoutFeeAndObservationSaveOnceAndReplayAcrossRestartWithoutDuplicateIncome()
    {
        Path database = directory.resolve("payout");
        MerchantSettlementAssistantService.Preview retained;
        long savedId;
        try (Jpa jpa = new Jpa(database))
        {
            var fixture = MerchantSettlementTestFixture.seed(jpa);
            var entry = fixture.nmr().base().entry(jpa);
            entry.enter(fixture.receipt());
            var service = service(jpa, entry);
            var receivable = service.openReceivables(DATE.plusDays(1)).get(0);
            retained = service.preview(new MerchantSettlementAssistantService.Request(
                    receivable.itemId(), DATE.plusDays(1), fixture.nmr().base().cash(), decimal("950"),
                    fixture.nmr().fee(), decimal("20"), ExpenseFunction.AR,
                    null, null, decimal("30"), "processor/withheld/001", "Processor payout"));

            amount("970", retained.total());
            amount("30", retained.remainingReceivable());
            assertEquals(3, retained.command().lines().size());
            savedId = service.save(retained).id();
            assertEquals(savedId, service.save(retained).id());

            var result = new SupplementalOpenItemQueryService(jpa, () -> COMPANY)
                    .queryMerchantReceivables(DATE, DATE.plusDays(1));
            assertTrue(result.ready(), result::toString);
            var summary = result.summaries().get(0);
            amount("950", summary.payout());
            amount("20", summary.fee());
            amount("30", summary.closing());
            amount("30", summary.latestObservation().amount());
            try (EntityManager em = jpa.em())
            {
                assertEquals(2L, em.createQuery("select count(t) from Txn t", Long.class).getSingleResult());
                assertEquals(1L, em.createQuery("select count(t) from Txn t where t.memo = 'Processor payout'", Long.class).getSingleResult());
            }
        }
        try (Jpa jpa = new Jpa(database))
        {
            var service = service(jpa, new TransactionEntryService(jpa, () -> COMPANY));
            assertEquals(savedId, service.save(retained).id());
            try (EntityManager em = jpa.em())
            {
                assertEquals(2L, em.createQuery("select count(t) from Txn t", Long.class).getSingleResult());
            }
        }
    }

    @Test
    void laterAndCombinedEvidencedNmrDischargeUseCanonicalItemsAndNeverCreateIncome()
    {
        try (Jpa jpa = new Jpa(directory.resolve("later")))
        {
            var fixture = MerchantSettlementTestFixture.seed(jpa);
            var entry = fixture.nmr().base().entry(jpa);
            entry.enter(fixture.receipt());
            var service = service(jpa, entry);
            var receivable = service.openReceivables(DATE.plusDays(1)).get(0);
            service.save(service.preview(request(fixture, receivable, "950", "20", null, null, "30")));
            var nmr = service.openNmrItems(DATE.plusDays(2)).get(0);
            var discharge = service.preview(request(fixture, receivable, "0", "0", nmr.itemId(), "30", "0"));
            assertEquals(2, discharge.command().lines().size());
            assertEquals(2, discharge.command().supplementalLines().size());
            service.save(discharge);

            var query = new SupplementalOpenItemQueryService(jpa, () -> COMPANY);
            amount("0", query.query(SupplementalOpenItemQueryService.Kind.RECEIVABLE, DATE.plusDays(2))
                    .authoritativeRows().get(0).openBalance());
            amount("70", query.queryNmr(DATE.plusDays(2)).items().get(0).openBalance());
            try (EntityManager em = jpa.em())
            {
                assertEquals(1L, em.createQuery("select count(s) from TxnSplit s where s.account.accountType = 'INCOME'", Long.class)
                        .getSingleResult());
            }
        }

        try (Jpa jpa = new Jpa(directory.resolve("combined")))
        {
            var fixture = MerchantSettlementTestFixture.seed(jpa);
            var entry = fixture.nmr().base().entry(jpa);
            entry.enter(fixture.receipt());
            var service = service(jpa, entry);
            var receivable = service.openReceivables(DATE.plusDays(1)).get(0);
            var nmr = service.openNmrItems(DATE.plusDays(1)).get(0);
            var combined = service.preview(request(fixture, receivable, "950", "20", nmr.itemId(), "30", "0"));
            amount("1000", combined.total());
            amount("0", combined.remainingReceivable());
            assertEquals(4, combined.command().lines().size());
            service.save(combined);
            var result = new SupplementalOpenItemQueryService(jpa, () -> COMPANY)
                    .queryMerchantReceivables(DATE, DATE.plusDays(2));
            amount("30", result.summaries().get(0).nmr());
            amount("0", result.summaries().get(0).closing());
        }
    }

    @Test
    void stalePreviewAmountsDimensionsAuthorizationAndAtomicFailuresAreRejected()
    {
        try (Jpa jpa = new Jpa(directory.resolve("guards")))
        {
            var fixture = MerchantSettlementTestFixture.seed(jpa);
            var entry = fixture.nmr().base().entry(jpa);
            entry.enter(fixture.receipt());
            var service = service(jpa, entry);
            var receivable = service.openReceivables(DATE.plusDays(1)).get(0);
            var stale = service.preview(request(fixture, receivable, "400", "0", null, null, null));
            service.save(service.preview(request(fixture, receivable, "100", "0", null, null, null)));
            PostingException changed = assertThrows(PostingException.class, () -> service.save(stale));
            assertTrue(changed.getMessage().contains("changed after Preview"));

            assertThrows(PostingException.class, () -> service.preview(request(fixture, receivable, "901", "0", null, null, null)));
            assertThrows(PostingException.class, () -> service.preview(request(fixture, receivable, "-1", "0", null, null, null)));
            assertThrows(PostingException.class, () -> service.preview(request(fixture, receivable, "1.00001", "0", null, null, null)));
            assertThrows(PostingException.class, () -> service.preview(new MerchantSettlementAssistantService.Request(
                    receivable.itemId(), DATE.plusDays(1), fixture.nmr().fee(), decimal("10"),
                    null, null, null, null, null, null, null, null)));
            assertThrows(PostingException.class, () -> service.preview(new MerchantSettlementAssistantService.Request(
                    receivable.itemId(), DATE.plusDays(1), fixture.nmr().base().cash(), decimal("0"),
                    fixture.nmr().fee(), decimal("20"), null, null, null, null, null, null)));
            var nmr = service.openNmrItems(DATE.plusDays(1)).get(0);
            assertThrows(PostingException.class, () -> service.preview(new MerchantSettlementAssistantService.Request(
                    receivable.itemId(), DATE.plusDays(1), null, null, null, null, null,
                    nmr.itemId(), decimal("30"), null, null, null)));

            var now = Instant.now();
            var guard = new AuthorizationGuard(jpa, () -> Optional.of(new AuthenticatedUserSession(
                    99L, "viewer", "Viewer", COMPANY, Set.of(ReservedSecurityRole.VIEWER), now, now)));
            var viewerEntry = new TransactionEntryService(jpa, () -> COMPANY, guard);
            var viewerService = service(jpa, viewerEntry);
            var viewerPreview = viewerService.preview(request(fixture, receivable, "10", "0", null, null, null));
            assertThrows(AuthorizationException.class, () -> viewerService.save(viewerPreview));

            try (EntityManager em = jpa.em())
            {
                assertEquals(2L, em.createQuery("select count(t) from Txn t", Long.class).getSingleResult());
                assertEquals(2L, em.createQuery("select count(a) from AuditEvent a where a.actionType = 'TRANSACTION_ENTERED'", Long.class)
                        .getSingleResult());
            }
        }
    }

    private static MerchantSettlementAssistantService service(Jpa jpa, TransactionEntryService entry)
    {
        return new MerchantSettlementAssistantService(jpa, () -> COMPANY, entry);
    }

    private static MerchantSettlementAssistantService.Request request(
            MerchantSettlementTestFixture fixture,
            MerchantSettlementAssistantService.Receivable receivable,
            String payout,
            String fee,
            java.util.UUID nmrItem,
            String nmr,
            String observed)
    {
        return new MerchantSettlementAssistantService.Request(
                receivable.itemId(), DATE.plusDays(nmr == null ? 1 : 2),
                decimal(payout).signum() > 0 ? fixture.nmr().base().cash() : null, decimal(payout),
                decimal(fee).signum() > 0 ? fixture.nmr().fee() : null, decimal(fee),
                decimal(fee).signum() > 0 ? ExpenseFunction.AR : null,
                nmrItem, nmr == null ? null : decimal(nmr), observed == null ? null : decimal(observed),
                nmr != null || observed != null ? "processor/evidence/001" : null, null);
    }

    private static BigDecimal decimal(String value)
    {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value);
    }

    private static void amount(String expected, BigDecimal actual)
    {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> expected + " != " + actual);
    }
}
