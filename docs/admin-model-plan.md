# Company and User Administration Model

This document describes the first application-level administration model for companies, users, roles, and company-owned configuration.

## Goal

The application should not rely on a loose session password, an ad hoc company-code text box, or globally floating configuration records. It needs a real administration area that can answer these questions:

- Which company/branch is currently active?
- What are this company's legal/display properties?
- Which chart of accounts does this company use?
- Which bank accounts belong to this company?
- Which tax or filing identifiers apply?
- Which users can access this company?
- What roles/permissions do those users have?
- What reporting/fiscal defaults apply to this company?

## First implementation slice

The first code slice establishes the database model, services, and basic panels. It does not yet implement password hashing, login flows, or full authorization enforcement.

## Company properties

A company should eventually own or reference:

- company code
- display name
- legal name
- branch type
- parent organization / kingdom
- active flag
- fiscal year start month/day
- default currency
- active chart of accounts
- bank accounts
- tax / filing profile
- reporting defaults
- users and company-specific roles

## User and permission properties

A user should eventually include:

- username
- display name
- email
- active flag
- optional local-auth metadata in a later slice

Roles should initially be simple application roles:

- ADMIN
- MANAGER
- ACCOUNTANT
- VIEWER

A user may have different roles in different companies.

## Panels

Add two admin panels:

- Company Admin
- User Admin

Company Admin should show company profile, current company, chart of accounts, bank accounts, tax/filing settings, and reporting defaults.

User Admin should show users, roles, and company assignments.

## Relationship to existing panels

The existing Chart of Accounts, Funds, Bank Accounts, and Settings areas should eventually become company-aware. The first pass presents company administration as a foundation panel and leaves full scoping enforcement for later.
