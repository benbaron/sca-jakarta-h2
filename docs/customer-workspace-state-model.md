# Customer Workspace State Model

This document defines a concrete, testable customer-panel model for the SCA accounting application.

## Goals

- Support supervisory vs user login.
- Support optional login validation mode.
- Enforce role-based panel access.
- Track panel navigation history as state.

## Core classes

- `CustomerPanelDefinition`
  - Panel metadata, minimum role, and action capabilities.
- `CustomerPanelRegistry`
  - Canonical list of panel definitions mapped by `CustomerPanelId`.
- `CustomerWorkspaceState`
  - Stateful session object with login mode, current role/user, active group code,
    active panel, and open-panel history.
- `LoginMode`
  - `OPTIONAL` vs `REQUIRED` login validation behavior.
- `PanelAction`
  - Action capabilities (select group, post, resolve, reconcile, close period, import/export, approve, audit).

## Behavioral rules

- If login mode is `REQUIRED`, both access checks and panel open operations fail until login is completed.
- If login mode is `OPTIONAL`, the default effective role is `USER`.
- `USER` role cannot open supervisor-only panels (`PERIOD_CLOSE`, `IMPORT_EXPORT`, `APPROVAL_AUDIT`).
- `SUPERVISOR` role can access all panels.
- `logout()` clears user, role, active panel, and history.

## Test coverage

Unit tests exercise:

- Constructor validation and role checks for panel definitions.
- Registry coverage of all panel ids and action bindings.
- Workspace state transitions for login-required/login-optional behavior, role gating,
  navigation history, group selection, and logout reset.
