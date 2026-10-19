package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.AssetAcquisitionSource;
import java.math.BigDecimal;

/** Read-only acquisition provenance and full shared-invoice controls. */
public record AssetAcquisitionView(AssetAcquisitionSource source, Long splitId, Long transactionId,
                                  BigDecimal sourceDebit, BigDecimal claimed, BigDecimal remaining)
{
    public String label()
    {
        return source == null ? "Legacy unlinked — review acquisition" : source.label();
    }
}
