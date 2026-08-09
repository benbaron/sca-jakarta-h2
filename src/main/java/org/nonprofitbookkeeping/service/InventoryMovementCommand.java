package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.InventoryMovement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryMovementCommand(InventoryMovement.MovementType movementType,
                                       BigDecimal quantity,
                                       LocalDate movementDate,
                                       Long offsetAccountId,
                                       boolean nonfinancialConfirmed,
                                       String notes)
{
    public InventoryMovementCommand(
            InventoryMovement.MovementType movementType,
            BigDecimal quantity,
            LocalDate movementDate,
            String notes)
    {
        this(movementType, quantity, movementDate, null, false, notes);
    }
}
