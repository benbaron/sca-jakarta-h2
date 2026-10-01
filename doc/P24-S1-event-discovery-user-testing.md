# P24-S1 event creation and discovery testing

Use a disposable company/database and record the installed build/commit, normal launch route, role, company, display size/scaling and whether saved layout was restored (D01). Capture the navigation or controls involved in the original problem. Source inspection does not substitute for this installed-build evidence.

1. Launch normally. Open **Administration → Events / Activities**. Choose **New Event**, enter a code such as FAIR-2026 and name Autumn Fair, then **Save**. Create FAIR-2027 with the same name; both must remain distinguishable by code.
2. Search for part of the name with mixed case, then by code. Inactive events remain searchable. Select a row and choose **Edit**; rename and save. Close/reopen and restart: the same record and any existing Journal links must remain.
3. Clear **Active** and Save to deactivate. Select Active and Save to reactivate. A used or interchange-linked record must explain why permanent deletion is unavailable. An unused record may be deleted only after the real confirmation.
4. Start a new unsaved event, select another row and cancel the discard prompt. The draft must remain. Repeat with an unsaved rename and Refresh. Change the search while editing: it must filter the list without changing the draft. Save: the saved record becomes visible.
5. Open **Event Accounting → Events / Activities**. Confirm it opens the same maintenance tab. Create an event and return to Event Accounting; the selector must include it with its name/code. Event Accounting must remain read-only.
6. As VIEWER, search and navigate freely, but New Event, Edit, Save, code/name and Active mutation controls must be unavailable. Verify a writer role restores the appropriate controls.
7. Test both a clean layout and a restored customized layout: reordered/wide/sorted columns, moved dividers, normal laptop dimensions, collapsed sidebars and your display scaling. Search results must still sort. Confirm table horizontal/vertical scrolling, independent header/editor scrolling and reachable actions when the window narrows.

Fund/Event Journal tagging changes belong to P24-S2. This slice introduces no database migration or second event model.
