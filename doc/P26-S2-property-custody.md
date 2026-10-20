# P26-S2 — Property custody, supplies, and transfer records

Status IN_PROGRESS. [PLAN](PLAN.md) selects this slice after owner-accepted/merged P26-S1. It completes adopted G11/A11 custody scope over the existing FixedAsset and InventoryItem authorities. Inventory costing remains P26-S5; official workbook/template mapping remains P27.

## Canonical subjects and financial boundary

FixedAsset and InventoryItem remain the only property subjects. Add no parallel asset register. Inventory items explicitly distinguish CAPITALIZED_INVENTORY from EXPENSED_SUPPLY. Existing rows migrate to CAPITALIZED_INVENTORY without changing amounts. An expensed supply must retain zero unit value; its receipt/issue quantity movements are nonfinancial and require the existing explicit nonfinancial confirmation. The software never reconstructs an expense, acquisition cost, or inventory value from custody data.

Custody history is an immutable company-owned event stream linked to exactly one fixed asset or inventory item. Events cover assignment, confirmation, loan, return, transfer, and removal. Each accepted event retains event date, affected quantity, guardian name/contact, location, condition, confirmation status/due/confirmation dates, expected return date, removal reason, external authorization reference, notes, actor, portable identity, and the complete resulting custody state. Existing Inventory custodian/location/condition fields remain a compatibility current-state projection; legacy values are visible as unconfirmed starting context and are not fabricated into historical events.

Corrections append a replacement linked to the latest effective event; the original remains visible and excluded from current-state projection. A corrected replacement can itself be corrected. Stale/concurrent correction, cross-company references, foreign subjects, inactive company and VIEWER writes reject atomically. Confirmation is factual: PENDING becomes overdue only as a derived as-of status; CONFIRMED and EXCEPTION require a confirmation date and optional explanation. No internal approval workflow is claimed.

## Transfers, removals, and accounting treatment

Assignment, confirmation, loan, return and transfer are custody-only and never write GL or change canonical inventory quantity. A transfer records prior and destination guardian/location, its quantity, condition, and optional external authorization reference. Inventory custody events cover the item's complete current quantity; split custody uses separate canonical inventory items rather than an untracked sub-ledger. Fixed assets use quantity one until removal.

Removal does not perform accounting. A fixed-asset removal must reference an effective Sale or Retirement lifecycle event that already governs status and accounting. An inventory removal must reference an ISSUE or reducing ADJUSTMENT movement for the same item and enough quantity. Capitalized inventory retains that movement's canonical transaction; an expensed supply retains its nonfinancial zero-value movement. Reversed source accounting/movements cannot support a new removal. The branch Property Custody form/report states the retained treatment/reference so a physical transfer is never presented as an accounting transfer.

## UI, reporting, and portability

Add one Property Custody production destination under Assets & Inventory. It lists both canonical subject types, current custody/confirmation state, full corrected history, and a shared Save Custody Event command. New/Correct Selected, live BOOKKEEPING_WRITE gating, dirty protection, table state, and independently reachable editor scrolling follow existing UI contracts. Fixed Asset and Inventory maintenance remain the places to create/financially maintain subjects.

Report Library adds Property Custody & Transfers with date-range and row-limit controls, physical event history, derived overdue state, correction history, external authorization references, and the retained accounting-treatment reference. Text/CSV/table outputs share the same projection. This is an internal branch form and does not claim the unresolved official template/revision mapping owned by P27.

SCLX uses an optional strict version-1 propertyCustody extension over existing intrinsic fixed-asset/inventory identities and event portable IDs. Absence remains compatible and does not clear target history. Import restores immutable events without posting accounting, validates subjects/source references/correction chains/current state, participates in conflicts/Keep target, and rolls back atomically. Computed overdue state is never imported.

## Required verification and owner checks

Test migrated legacy rows, expensed zero-value supplies, valued-inventory rejection, assignment/confirmation/overdue, loan/return, fixed and inventory transfers, lifecycle/movement-backed removals, corrections/restart/concurrency, foreign/VIEWER/stale/reversed-source rejection, report totals/forms/row limits, strict SCLX round trip/conflict/legacy/rollback, and actual Save/draft/role/geometry routes. Run focused tests, full clean verification, actual JavaFX, and exact-head JDK 17 CI.

In a disposable company, create one bank-linked fixed asset and one zero-value expensed supply. Assign guardians and locations, loan/return the asset, transfer both subjects, leave one confirmation overdue, then confirm it. Dispose the asset through its existing lifecycle before recording removal; issue the supply quantity before its removal. Correct the latest transfer and verify both versions remain visible. Generate/export Property Custody & Transfers and confirm each physical event shows its factual accounting treatment/reference without creating another GL entry or inventory value. No per-check human result is inferred from automated tests.
