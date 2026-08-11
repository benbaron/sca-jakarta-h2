package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.FixedAssetLifecycleEvent;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Read-only lifecycle history projection for the Asset Register. */
public record FixedAssetLifecycleEventView(
        Long id,
        Long fixedAssetId,
        String assetName,
        FixedAssetLifecycleEvent.EventType eventType,
        LocalDate eventDate,
        BigDecimal carryingAmountBefore,
        BigDecimal proceeds,
        BigDecimal impairmentAmount,
        BigDecimal gainAmount,
        BigDecimal lossAmount,
        Long transactionId,
        Long reversalTransactionId,
        String notes)
{
}
