package org.nonprofitbookkeeping.service;

/** A null ID creates a Merchant; otherwise save updates that exact company-owned ID. */
public record MerchantCommand(Long id, String name, String notes, boolean active)
{
}
