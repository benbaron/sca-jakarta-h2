# P16-S7 Owner Desktop Acceptance — Factual Audit History Authority

Use a disposable database with at least one company and several material operations. Two companies are
preferred for the company-isolation check.

## A. Current factual events

1. Perform or identify several operations that write factual audit events, such as a transaction entry or correction, period close/reopen, governed bank-review import, COA CSV import, reconciliation change, or SCLX import.
2. Open **Audit History** and choose **Refresh**.
3. Confirm the table shows factual **Occurred**, **Actor**, **Action**, **Entity Type**, **Entity ID**, and **Summary** values for those operations.
4. Confirm no approval/rejection/decision/run-ID workflow columns or controls are present.

## B. Read-only factual details

1. Select an event with before/after/reason data.
2. Confirm **Before**, **After**, and **Reason** appear in the lower detail region.
3. Confirm those controls cannot be edited.
4. Move the horizontal divider and confirm both table and detail regions remain usable at laptop width.

## C. Filters

1. Filter by part of an Action value and confirm only matching factual actions remain.
2. Filter by Entity using part of either an entity type or entity identifier.
3. Filter by Actor.
4. Apply From/To dates and confirm the endpoints are inclusive.
5. Enter a From date after To and confirm the workspace reports validation rather than querying misleading results.
6. Choose **Reset** and confirm all active-company factual rows return.

## D. Company isolation and restart

1. With Company A active, note one event unique to Company A.
2. Switch to Company B and reopen/refresh Audit History; confirm Company A's event is absent.
3. Create or identify a Company B event and confirm it appears only for Company B.
4. Restart the application, reopen the same company, and confirm the factual events remain visible.
5. Confirm no unresolved/global or legacy approval row is presented as if it were an active-company factual event.

## E. Table state and immutability

1. Resize/reorder/sort Audit History columns and move the table/detail divider.
2. Reopen Audit History for the same company and confirm the company-owned layout state restores.
3. Confirm Audit History offers no Save, Approve, Reject, Escalate, Delete, or other mutation action.

## Automated validation record

Exact implementation head `e140aae3c1d07ceeabbc93d179489d03faa15896` passed Maven PR Tests run `31282119224`, including clean headless `mvn clean verify`, the deliberately repeated test suite, and production JavaFX route compliance. The final documentation-inclusive PR head must pass the same complete gate before owner acceptance is recorded.

## Acceptance record

- [ ] A. Current factual events passed
- [ ] B. Read-only details and divider passed
- [ ] C. Service-backed filtering passed
- [ ] D. Company isolation and restart passed
- [ ] E. Table state and no-write behavior passed
- [ ] Exact tested PR head recorded
- [ ] Owner acceptance recorded

Do not mark P16-S7 DONE or begin P16-S8 until this checklist is accepted and the P16-S7 PR has merged.
