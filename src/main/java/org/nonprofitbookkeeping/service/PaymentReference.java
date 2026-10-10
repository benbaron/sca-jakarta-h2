package org.nonprofitbookkeeping.service;

import java.time.LocalDate;

/** Optional factual payment identity owned by one canonical bank split. */
public record PaymentReference(Method method, String reference, LocalDate issuedOn, LocalDate deliveredOn)
{
    public enum Method
    {
        CHECK, EFT, CARD, CASH, OTHER
    }

    public PaymentReference
    {
        if (method == null)
        {
            throw new IllegalArgumentException("Payment method is required.");
        }
        reference = reference == null || reference.isBlank() ? null : reference.trim();
        if (reference != null && reference.length() > 80)
        {
            throw new IllegalArgumentException("Payment reference is limited to 80 characters.");
        }
        if (method == Method.CHECK && reference == null)
        {
            throw new IllegalArgumentException("A check requires a check number.");
        }
        if (deliveredOn != null && (issuedOn == null || deliveredOn.isBefore(issuedOn)))
        {
            throw new IllegalArgumentException("Delivery requires an issue date and cannot precede it.");
        }
    }
}
