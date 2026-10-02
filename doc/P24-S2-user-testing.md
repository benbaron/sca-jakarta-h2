# P24-S2 — Journal Fund/Event tagging acceptance

## Visible changes

Journal has searchable Fund and Event/Activity choices above Entry Lines, explicit apply-to-selected-lines buttons and Clear Event. Event names appear in saved Journal rows; Fund/Event filters search current names or codes, including inactive references. Event sits next to Fund in a fresh layout. Existing column preferences are retained. A draggable divider sizes the tagging area, with horizontal/vertical scrolling as needed.

## Owner desktop checks

Use a disposable company with accounts, two funds and two events; use repeated event names with distinct codes to check identity.

1. Start a balanced Journal draft. Select its first line and apply Fund A and Event A. Select the second and apply Fund B and Event B. Select both and apply a fund; verify both events remain distinct. Clear Event on only one line. Confirm the other line, amounts and notes remain unchanged.
2. Save, close/reopen the company, and edit the entry. Verify every saved assignment and the per-line Event/Activity names in saved rows. Untagged lines show **No event**.
3. Change an event name and deactivate it. Reopen the saved entry: verify the current name remains readable, while new-assignment choices exclude that inactive event. Editing an existing tag and clicking elsewhere must preserve the selected ID.
4. While a Journal draft has unsaved memo, payee, bank and line edits, open Events / Activities or Funds and create a choice. Return to Journal (or click Refresh Fund/Event Choices): verify the new active choice is available and all draft data remains. New/Edit navigation must still ask before discarding a dirty entry.
5. Filter saved entries by mixed-case event name and then by unique event code. Combine a fund filter: only transactions with both matches on the same line qualify. A qualifying transaction still shows all its lines and full totals. Clear filters to include untagged entries.
6. At normal laptop width, narrow width and increased display/text scaling, use both tagging/filter scrollbars and drag the tagging divider. Widen/reorder the Event column, reopen the company and verify saved state and access to the tagging controls. Confirm line-table scrolling works independently.
7. As VIEWER, verify Apply Fund, Apply Event and Clear Event are disabled; saved-entry filtering and choice refresh remain available.

## Evidence boundaries

Local automated service and JavaFX results are recorded in `PLAN.md`. Headless JavaFX execution does not establish owner acceptance of the installed desktop build. Record the installed build/commit, company/layout state and any failing screenshot for D01. GitHub CI and owner acceptance remain required before this slice is DONE.
