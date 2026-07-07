# Period close design

## Purpose

This document records the clarified period-close requirements.

## Core model

There is no accounting-period table as the authority for open periods.

Accounting periods are calculated from Settings. Period close records close a calculated or custom date range.

## Period selection

The Period Close panel supports both:

- selecting a named/calculated open period; and
- selecting a through-date or custom date range.

Settings must support period rules:

- monthly;
- fiscal quarter;
- fiscal year;
- custom date.

A custom period is a one-time custom open and close date.

## Closing behavior

Closing a period closes only the selected period/date range. It leaves all other dates alone.

The previous phrase "closure opens from that date forward" is replaced by:

> Closing a given period leaves all other times/dates open.

The UI must also offer a way to reopen a closed period, either in the Period Close panel or a clearly related subpanel.

## New company beginning state

When a new company is created, it starts with:

- period state: open;
- beginning date;
- beginning balance setup.

Beginning balances are entered through a wizard that creates balanced opening entries. The wizard may collect per-account beginning balances and must produce authoritative balanced accounting transactions.

## Snapshots

Period close does not create immutable period-balance snapshots for faster reporting or reconciliation.

Balances are recalculated from the ledger.
