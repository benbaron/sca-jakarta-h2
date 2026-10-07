# P24-S4C — Merchant maintenance acceptance

Use this slice's installed build on a disposable company copy. Record commit, OS/display scaling, role and company.

1. Open **Merchants** from Accounting navigation. Create two distinct merchants with name and notes. Save, search name/notes, refresh and restart; verify persistence.
2. Enter a balanced Journal transaction with different merchants on its two lines. Rename and deactivate one in maintenance, then reopen the transaction. Both line IDs remain assigned; the inactive line displays its current name. Inactive merchants remain in maintenance and history, and disappear from new choices. Reactivate and verify the same identity returns.
3. Attempt blank and over-200-character names, plus duplicate names differing only in case or outer spaces, including an inactive name. Verify visible rejection without changes to master data or audit history.
4. Begin a maintenance draft, search, refresh, select another merchant, and choose New Merchant. Cancel discard prompts. Verify the draft remains; confirm an intentional discard and verify the requested form loads.
5. Begin an unsaved Journal entry with amounts, Fund/Event/Budget references, memo and mixed merchants. Open Merchants from Journal, create a new merchant, rename/deactivate selected merchants, and return. Verify refreshed active choices and current labels, with all draft values, individual Merchant IDs and dirty state preserved.
6. Resize to laptop width and narrower, enlarge text/display scaling, and expand/collapse sidebars. Move both maintenance dividers. Overflow the table vertically and horizontally with enough rows and a wide column. Confirm header/editor scrolling exposes all controls and hover tooltips expose clipped text.
7. Sort, reorder and resize columns; move dividers. Reopen and verify remembered layout. Switch companies and verify separate records and layout.
8. As VIEWER, search/read existing merchants and verify New/Save/name/notes/lifecycle changes are unavailable. Check authenticated creation/edit audit history for before/after values.
9. Reverse the mixed transaction and export/import through the existing SCLX workflow. Verify original and reversal line relationships, portable identities, notes and inactive state remain intact. Settlement assistance belongs to a later slice.

Automated service coverage: validation/duplicate concurrency, authorization/company isolation, audit rollback, restart, mixed-line history, reversal and SCLX round trip. MerchantsPanelTest covers draft retention, mixed-line label refresh, permissions, scrolling, scaling and table-state ownership. The CI Xvfb gate includes both UI cases. Native desktop acceptance remains an owner check; headless skips do not establish visual validation.
