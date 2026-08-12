# Factual Audit History

## Authority

The production **Audit History** destination reads `audit_event` through `AuditHistoryService`.
`AuditEvent` is the factual history authority for material application operations. The stable shell
identifier remains `APPROVAL_AUDIT` only so saved navigation state continues to open the same destination.
The visible workspace does not implement an approval, rejection, escalation, or decision workflow.

`approval_audit_record`, `ApprovalAuditRecord`, `ApprovalAuditRepository`, and `ApprovalAuditService`
remain compatibility structures for historical code/tests. They are not queried by the production
Audit History panel and their rows are not silently mixed with factual `AuditEvent` rows.

## Company scope

Every production query is scoped by the authoritative active `Company.code` through an inner join to
`AuditEvent.company`. Events owned by another company and events whose company is unresolved/null are
excluded. The panel does not infer company ownership from entity text, actor text, or legacy group codes.

## Query and filtering contract

`AuditHistoryService` returns immutable `AuditEventView` projections ordered newest first by occurrence
time and durable row identity. The production panel requests at most 500 rows per refresh. Filters are
service inputs, not SQL embedded in JavaFX:

- **Action** performs a case-insensitive contains match on `action_type`.
- **Entity** performs a case-insensitive contains match on `entity_type` or `entity_id`.
- **Actor** performs a case-insensitive contains match on `actor`.
- **From** and **To** form an inclusive local-date range using the desktop system zone for date-to-instant boundaries.

A From date after the To date is rejected before a query runs.

## Presentation contract

The table displays company-formatted occurrence timestamp, actor, action, entity type, entity identifier,
and summary. Selecting an event exposes read-only **Before**, **After**, and **Reason** text areas in a
separate vertically resizable detail region. The detail controls are bounded viewports with their own
scrolling; stored audit text is never edited from this panel.

The table uses unconstrained, sortable/resizable/reorderable columns and company-owned table state through
the production table binder. The table/detail split position is company-owned through
`CompanySplitPaneStateBinder`.

## Operation coverage

Any current operation that persists an `AuditEvent` for the active company is visible through the same
query after refresh and restart, including transaction entry/update/correction, period-close/reopen,
COA CSV import, governed bank-review import, reconciliation events, and SCLX-restored factual history.
The panel itself performs no writes.

P16-S16 adds stable user/role create, update, and deactivate events plus assignment create, end, and
revoke events. Global user and role maintenance is audited in the active-company context from which the
operation was performed; assignment events are owned by the assignment company. The editable actor is
a factual local-operator label and is not an authenticated identity.
