package org.nonprofitbookkeeping.service;

/** Input for creating or updating a configured financial institution. */
public record BankCommand(String companyCode,
                          String name,
                          String routingNumber,
                          String address,
                          String website,
                          String contactName,
                          String contactPhone,
                          String contactEmail,
                          String notes,
                          boolean active)
{
}
