package org.nonprofitbookkeeping.model;

/** Explicit accounting attribution, never an assessment or automatic settlement rule. */
public enum NmrMovementKind
{
    COLLECTION,
    BRANCH_REMITTANCE,
    MERCHANT_DISCHARGE,
    REFUND,
    ADJUSTMENT;

    public void validate(String effect, boolean merchantPresent, String evidence)
    {
        if (this != ADJUSTMENT && !(this == COLLECTION ? "INCREASE" : "DECREASE").equals(effect))
        {
            throw new IllegalArgumentException("NMR " + this + " has an incompatible item effect.");
        }
        if (this == MERCHANT_DISCHARGE && (!merchantPresent || evidence == null || evidence.isBlank()))
        {
            throw new IllegalArgumentException("Merchant discharge requires a Merchant on its linked liability line and an evidence reference.");
        }
    }
}
