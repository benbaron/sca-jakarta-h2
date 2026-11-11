# P29-C8 local overflow and responsive text

Keep one scroll owner for a form: reuse existing ScrollPanes in tabs rather than
adding a second viewport. Inner work sections must not inherit whole-window
minimum dimensions. Grid forms keep their computed preferred minimum width so
horizontal overflow appears instead of compressing or truncating fields.

Tables retain native row scrolling and column sizing/reordering/sort/state. Empty
and populated tables must expose horizontal column access independently inside
their pane when columns exceed its width. Use public JavaFX skin APIs if needed;
do not replace canonical data, add fake rows or move the vertical scrollbar offscreen.

Wrap descriptive/status labels and button text outside table cells, retaining
input behavior, bindings and commands. Banking action groups wrap onto new rows.
Buttons retain enough width for their longest word so short actions do not
collapse into individual letters. This width follows font/text changes.
Wrapped labels retain their preferred height so a crowded container cannot squeeze
multiple lines into one. Keep full text accessible at narrow widths and 13px/18px; both axes appear only
when content overflows. Audit all canonical routes and inactive logical tabs.

Event Accounting's block comes from four read-only ReviewFact projections, not a
close operation or an approval queue. Keep every current status visible in a
compact wrapping summary, and put full factual details in an expandable section.
An uncleared-bank Attention status must remain visible without expanding details.

Owner checks: use narrow panes and enlarged text; scroll to the last column in
linked transactions, Merchant Settlement receivables/NMR, saved reconciliations
and each Match table, including empty tables. Settlement Draft has one vertical
scrollbar and horizontal access to its last fields. Both Banking editors expose
horizontal scrolling and the top status/actions wrap. Expand Event Accounting's
review details and verify statuses/details, scope, linked entries and Save/draft
behavior remain factual and unchanged. Automated geometry and real production
Save/permission tests accompany the shared policy change.

The canonical route audit finds 70 tables across 29 destinations, including
inactive Journal, reconciliation, import, opening-conversion and Settings tabs.
All receive the empty-table fallback. Populated tables continue using their
native horizontal/vertical bars. Other forms and descriptions use the same
intrinsic grid-width, local viewport and wrapping preparation rather than
independent panel-specific overflow implementations.

Validation: final complete UI package passed 360 tests across 138 classes with no
failures/errors/skips (09:30), including the five new overflow cases. Final
`clean verify` passed 1,164 tests, zero failures/errors and 109 expected display
skips (05:56). Rendered Linux/Xvfb snapshots were inspected; Windows/macOS owner
visual acceptance remains pending. The earlier obsolete viewport source guard
and intermittent private-snapshot/GTK observations are recorded in PLAN; the
final complete runs are green, without an accounting or recovery service change.
