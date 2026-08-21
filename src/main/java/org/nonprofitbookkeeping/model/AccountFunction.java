package org.nonprofitbookkeeping.model;

/**
 * Operational roles that an account may perform independently of its accounting type.
 *
 * <p>A bank ledger account remains an {@link AccountType#ASSET}; the BANK function
 * enables statement, reconciliation, and cleared-state behavior without changing
 * the account's balance-sheet classification.</p>
 */
public enum AccountFunction
{
    BANK
}
