package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.sclx.*;
import org.nonprofitbookkeeping.model.InventoryItem;
import org.nonprofitbookkeeping.model.PropertyCustodyEvent;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PropertyCustodySclxTest
{
    private static final String COMPANY = "INVCO";

    @Test void expensedTreatmentAndCorrectionHistoryRoundTripWithoutAccounting(@TempDir Path directory) throws Exception
    {
        Path file = directory.resolve("custody.sclx");
        try (Jpa source = new Jpa(directory.resolve("source")))
        {
            InventoryServiceTest.seedCompanyAccountsAndFund(source);
            InventoryService inventory = new InventoryService(source, new TransactionEntryService(source, () -> COMPANY), () -> COMPANY);
            InventoryItemView item = inventory.create(new InventoryItemCommand(COMPANY, 20_001L, 20_001L,
                    "Expensed flags", "Supply", new BigDecimal("4.0000"), "each", BigDecimal.ZERO,
                    InventoryItem.FinancialTreatment.EXPENSED_SUPPLY, LocalDate.of(2026,1,1), "Alex", "Hall",
                    InventoryItem.Condition.GOOD, InventoryItem.Status.ACTIVE, "portable"));
            PropertyCustodyService custody = new PropertyCustodyService(source, () -> COMPANY);
            var original = custody.save(command(item.id(), PropertyCustodyEvent.EventType.ASSIGNMENT, "Alex", "Hall"), "tester");
            custody.correctLatest(original.id(), command(item.id(), PropertyCustodyEvent.EventType.TRANSFER, "Morgan", "Store"), "tester");
            var document = new SclxCoreSnapshotQueryService(source, () -> COMPANY).query(Instant.EPOCH);
            Files.write(file, new SclxJsonSerializer().serialize(document));
            assertTrue(document.extensions().scaJakartaH2().containsKey("propertyCustody"));
        }
        try (Jpa target = new Jpa(directory.resolve("target")))
        {
            InventoryServiceTest.seedCompanyAccountsAndFund(target);
            SclxImportPreview preview = new SclxImportPreviewService(target, () -> COMPANY).preview(file);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertEquals(2, preview.operation().items().stream().filter(i -> i.entityType().equals("PROPERTY_CUSTODY_EVENT")).count());
            assertTrue(new SclxImportCommitService(target, () -> COMPANY).commit(file, preview, "tester", true, true).committed());
            InventoryItemView item = new InventoryService(target, new TransactionEntryService(target, () -> COMPANY), () -> COMPANY).listItems(COMPANY).get(0);
            assertEquals(InventoryItem.FinancialTreatment.EXPENSED_SUPPLY, item.financialTreatment());
            var history = new PropertyCustodyService(target, () -> COMPANY).history(PropertyCustodyService.SubjectType.INVENTORY_ITEM, item.id());
            assertEquals(2, history.size()); assertEquals(1, history.stream().filter(PropertyCustodyService.EventView::corrected).count());
            assertEquals(0L, InventoryServiceTestCount.count(target, "txn"));
            SclxImportPreview retry = new SclxImportPreviewService(target, () -> COMPANY).preview(file);
            assertFalse(retry.hasBlockingErrors(), () -> retry.operation().messages().toString());
            assertTrue(retry.operation().items().stream().filter(i -> i.entityType().equals("PROPERTY_CUSTODY_EVENT"))
                    .allMatch(i -> i.identityMatch() == org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch.IDENTICAL));
        }
    }

    private static PropertyCustodyService.Command command(long id, PropertyCustodyEvent.EventType type, String guardian, String location)
    {
        return new PropertyCustodyService.Command(PropertyCustodyService.SubjectType.INVENTORY_ITEM, id, type,
                LocalDate.of(2026,2,1), new BigDecimal("4.0000"), guardian, null, null, location,
                InventoryItem.Condition.GOOD, PropertyCustodyEvent.ConfirmationStatus.CONFIRMED,
                LocalDate.of(2026,3,1), LocalDate.of(2026,2,1), null, null, "AUTH", "portable", null, null);
    }

    private static final class InventoryServiceTestCount
    {
        static long count(Jpa jpa, String table)
        {
            try (var em = jpa.em()) { return ((Number)em.createNativeQuery("select count(*) from " + table).getSingleResult()).longValue(); }
        }
    }
}
