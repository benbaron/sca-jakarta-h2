# P29-C11 Journal Save completion feedback

Current status: blocked by the [independent H2 online-backup CI failure](P29-C11-ci-recovery-blocker.md). The local verification below predates that failure; it does not establish a passing final-head GitHub gate.

The reported Entry view shows balanced totals and a footer stuck at Saving new
journal entry. The footer previously copied only the immediate synchronous result
from AppPanel.executeCommand. Journal saves run in UiAsync; their later success
or failure changed a local status label in the Journal work tab, out of view when
Entry is selected. Thus an enabled editor could coexist with an obsolete Saving
footer even after the database operation had finished.

## Reproduction

Two real-shell tests with the initialized ACCOUNTANT login reproduced the exact
pending footer before the repair. In one, H2 had committed the new transaction.
In the other, H2 rejected a receivable line lacking supplemental allocations and
retained no new transaction. Both had re-enabled Save, but the footer still read
Saving new journal entry. The initial assertions failed on that stale text after
independently checking the persisted outcome. No user database was inspected;
this establishes the application feedback defect rather than claiming every
possible database stall has this cause.

The receivable error in the regression is: Ledger line 1 (1100) requires
supplemental allocations of 100.00; allocated 0. Add or repair matching Item ID,
Effect and Ledger Line details before saving. Balancing debits and credits alone
does not waive supplemental-detail validation. The repair surfaces the existing
service rejection; it does not suppress that accounting requirement.

## Contract

AppPanel has an optional asynchronous result listener, published on the JavaFX
thread. Synchronous panels retain their existing immediate-result behavior.
Journal emits pending, saved/updated, blocked and failed Save feedback through
its compliance wrapper and PanelHost to the production footer and tooltip.
Service errors also appear in the Entry validation area, with draft and request
identity retained for correction/retry. Both global and local Save buttons use
the same path. Background journal-list refresh messages do not overwrite the
completed command's footer message.

PanelHost accepts callbacks only from the active current panel instance. Hidden,
replaced, closed/reset owners cannot overwrite another workspace. Outer workspace
navigation clears the former message/tooltip; it does not cancel an in-flight
write. Inner Journal work-tab changes retain the current command feedback.

Save busy state is released in finally blocks on completion paths. A service
success followed by an editor-rendering exception reports the saved transaction
ID and a reload instruction, rather than claiming persistence failed or leaving
Saving displayed. A failed service response is not a blanket assertion that
nothing committed: existing request UUID/idempotent retry protections remain.
No timeout enables a second concurrent write. Canonical services, accounting
validation, closed-range links, role gates, audit and rollback rules are unchanged.
There is no migration or new persistence model.

## Verification

Actual H2/production-shell tests cover ACCOUNTANT create and update, terminal text
and tooltip surviving list refresh, rejected receivable draft correction/retry,
local Save, immediate imbalance rejection and known committed state when view
presentation fails. A bound DatePicker property deterministically injects that
post-commit view failure without replacing persistence. Component/real-shell
routing cases exercise hidden, replaced/reset owners and navigation clearing.
Existing request-receipt tests retain lost-response and concurrent-create checks.
CI explicitly selects the new display classes so headless skips cannot hide them.
Final display regression: 23 tests, zero failures/errors/skips (01:54), after
the broader 41-case display/service regression (03:06). Final clean verify:
1,197 tests, zero failures/errors, 141 expected display skips, BUILD SUCCESS
(06:07). No uncaught FX/SQL errors in the final display run. Publication and
exact-head CI evidence are recorded in PLAN.

## Owner check

As ACCOUNTANT, enter a valid balanced transaction and Save from Entry using the
main toolbar. The footer should progress to Saved new transaction #ID; confirm it
in Journal and verify subsequent Save updates the same transaction. Test the
local Journal Save button too. With a control-account line lacking required
supplemental details, Save should show the service reason in Entry and the footer,
keep the draft and re-enable correction/retry. Supply the appropriate details
for the intended transaction; do not bypass the rejection. Navigate between
workspaces and verify stale pending/completion messages do not overwrite the
current context. A saved-but-editor-refresh warning means reload the existing
transaction by its reported ID. Native Windows visual acceptance remains pending.
