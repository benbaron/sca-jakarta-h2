package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.InventoryItem;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryItemView(Long id,
                                String companyCode,
                                Long inventoryAccountId,
                                String inventoryAccountCode,
                                String inventoryAccountName,
                                Long fundId,
                                String fundCode,
                                String fundName,
                                String name,
                                String itemType,
                                BigDecimal quantity,
                                String unit,
                                BigDecimal unitValue,
                                BigDecimal totalValue,
                                InventoryItem.FinancialTreatment financialTreatment,
                                LocalDate acquisitionDate,
                                String custodian,
                                String storageLocation,
                                InventoryItem.Condition condition,
                                InventoryItem.Status status,
                                String notes)
{
    /** Source-compatible constructor for callers created before explicit financial treatment. */
    public InventoryItemView(Long id, String companyCode, Long inventoryAccountId,
            String inventoryAccountCode, String inventoryAccountName, Long fundId, String fundCode,
            String fundName, String name, String itemType, BigDecimal quantity, String unit,
            BigDecimal unitValue, BigDecimal totalValue, LocalDate acquisitionDate, String custodian,
            String storageLocation, InventoryItem.Condition condition, InventoryItem.Status status, String notes)
    {
        this(id, companyCode, inventoryAccountId, inventoryAccountCode, inventoryAccountName, fundId,
                fundCode, fundName, name, itemType, quantity, unit, unitValue, totalValue,
                InventoryItem.FinancialTreatment.CAPITALIZED_INVENTORY, acquisitionDate, custodian,
                storageLocation, condition, status, notes);
    }
}
