package org.nonprofitbookkeeping.model;

/**
 * Subtype drives which subsidiary schedules apply (Receivables, Payables, etc.).
 *
 * This list is intentionally aligned with your spreadsheet-era concepts:
 * Receivable, Payable, Prepaid, DeferredRevenue, Inventory, FixedAsset,
 * OtherAsset, OtherLiability, Cash.
 *
 * Naming: stored as enum constant names (UPPER_SNAKE_CASE) in DB/CSV.
 * Parsing helpers accept common human spellings too (e.g. DeferredRevenue).
 */
public enum AccountSubtype
{
    RECEIVABLE,
    PAYABLE,
    PREPAID,
    DEFERRED_REVENUE,
    INVENTORY,
    FIXED_ASSET,
    OTHER_ASSET,
    OTHER_LIABILITY,
    CASH
}
