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
                                LocalDate acquisitionDate,
                                String custodian,
                                String storageLocation,
                                InventoryItem.Condition condition,
                                InventoryItem.Status status,
                                String notes)
{
}
