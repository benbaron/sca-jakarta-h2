package org.nonprofitbookkeeping.service;

/** Immutable company-scoped Merchant maintenance projection. */
public record MerchantView(Long id, String name, String notes, boolean active)
{
}
