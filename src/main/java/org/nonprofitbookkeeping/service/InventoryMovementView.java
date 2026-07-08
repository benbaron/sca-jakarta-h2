package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.InventoryMovement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryMovementView(Long id,
                                    Long inventoryItemId,
                                    String inventoryItemName,
                                    LocalDate movementDate,
                                    InventoryMovement.MovementType movementType,
                                    BigDecimal quantityChange,
                                    BigDecimal resultingQuantity,
                                    BigDecimal unitValue,
                                    Long transactionId,
                                    String notes)
{
}
