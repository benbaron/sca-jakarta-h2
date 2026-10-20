package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.InventoryItem;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryItemCommand(String companyCode,
                                   Long inventoryAccountId,
                                   Long fundId,
                                   String name,
                                   String itemType,
                                   BigDecimal quantity,
                                   String unit,
                                   BigDecimal unitValue,
                                   InventoryItem.FinancialTreatment financialTreatment,
                                   LocalDate acquisitionDate,
                                   String custodian,
                                   String storageLocation,
                                   InventoryItem.Condition condition,
                                   InventoryItem.Status status,
                                   String notes)
{
    /** Source-compatible constructor for callers created before explicit financial treatment. */
    public InventoryItemCommand(String companyCode, Long inventoryAccountId, Long fundId, String name,
            String itemType, BigDecimal quantity, String unit, BigDecimal unitValue, LocalDate acquisitionDate,
            String custodian, String storageLocation, InventoryItem.Condition condition,
            InventoryItem.Status status, String notes)
    {
        this(companyCode, inventoryAccountId, fundId, name, itemType, quantity, unit, unitValue,
                InventoryItem.FinancialTreatment.CAPITALIZED_INVENTORY, acquisitionDate, custodian,
                storageLocation, condition, status, notes);
    }
}
