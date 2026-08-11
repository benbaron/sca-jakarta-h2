package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.FixedAssetLifecycleEvent;

import java.math.BigDecimal;
import java.time.LocalDate;

/** User-entered fixed-asset lifecycle facts used to build a frozen accounting preview. */
public record FixedAssetLifecycleCommand(
        FixedAssetLifecycleEvent.EventType eventType,
        LocalDate eventDate,
        BigDecimal proceeds,
        BigDecimal impairmentAmount,
        Long proceedsAccountId,
        Long gainAccountId,
        Long lossAccountId,
        String notes)
{
}
