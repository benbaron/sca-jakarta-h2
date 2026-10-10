package org.nonprofitbookkeeping.service;

import java.time.LocalDate;

/** Optional factual payment identity owned by one canonical bank split. */
public record PaymentReference(Method method, String reference, LocalDate issuedOn, LocalDate deliveredOn,
        String evidenceReference, LocalDate reviewedOn, String reviewNote)
{
    public PaymentReference(Method method, String reference, LocalDate issuedOn, LocalDate deliveredOn)
    {
        this(method, reference, issuedOn, deliveredOn, null, null, null);
    }

    public enum Method
    {
        CHECK, EFT, CARD, CASH, OTHER
    }

    public PaymentReference
    {
        evidenceReference = normalize(evidenceReference, 500, "Evidence reference");
        reviewNote = normalize(reviewNote, 1000, "Review note");
        if (reviewedOn != null && reviewNote == null)
        {
            throw new IllegalArgumentException("A dated review requires a factual review note.");
        }
        if (reviewedOn != null && issuedOn != null && reviewedOn.isBefore(issuedOn))
        {
            throw new IllegalArgumentException("A check review cannot precede the issue date.");
        }
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

    private static String normalize(String value, int maximum, String label)
    {
        String text = value == null || value.isBlank() ? null : value.trim();
        if (text != null && text.length() > maximum)
        {
            throw new IllegalArgumentException(label + " is limited to " + maximum + " characters.");
        }
        return text;
    }
}
