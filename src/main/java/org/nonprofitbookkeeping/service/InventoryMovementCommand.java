package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.InventoryMovement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryMovementCommand(InventoryMovement.MovementType movementType,
                                       BigDecimal quantity,
                                       LocalDate movementDate,
                                       String notes)
{
}
