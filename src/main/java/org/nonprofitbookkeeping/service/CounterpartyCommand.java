package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.CounterpartyKind;

/** A null ID creates a Counterparty; otherwise save updates that exact company-owned ID. */
public record CounterpartyCommand(Long id, String name, CounterpartyKind kind, String email,
        String phone, String notes, boolean active)
{
}
