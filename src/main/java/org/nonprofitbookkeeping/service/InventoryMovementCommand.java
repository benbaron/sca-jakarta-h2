package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.InventoryMovement;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Financial movements require an Event or explicit Non-event; optional Budget tags the offset split. */
public record InventoryMovementCommand(InventoryMovement.MovementType movementType,
                                       BigDecimal quantity,
                                       LocalDate movementDate,
                                       Long offsetAccountId,
                                       boolean nonfinancialConfirmed,
                                       String notes,
                                       Long activityId,
                                       Long budgetCategoryId,
                                       boolean nonEventConfirmed)
{
    public InventoryMovementCommand(InventoryMovement.MovementType movementType,
            BigDecimal quantity, LocalDate movementDate, Long offsetAccountId,
            boolean nonfinancialConfirmed, String notes)
    {
        this(movementType, quantity, movementDate, offsetAccountId, nonfinancialConfirmed, notes,
                null, null, false);
    }

    public InventoryMovementCommand(
            InventoryMovement.MovementType movementType,
            BigDecimal quantity,
            LocalDate movementDate,
            String notes)
    {
        this(movementType, quantity, movementDate, null, false, notes);
    }
}
