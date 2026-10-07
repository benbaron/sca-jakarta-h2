package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.CounterpartyKind;

/** Immutable company-scoped Counterparty maintenance projection. */
public record CounterpartyView(Long id, String name, CounterpartyKind kind, String email,
        String phone, String notes, boolean active)
{
}
