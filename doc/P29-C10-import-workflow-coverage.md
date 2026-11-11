# P29-C10 Import workflow coverage

The owner requests closing the application workflow gaps measured in C9. Use the
same Import destination and service/H2 authorities; no new financial model,
import policy, migration or production dependency is introduced.

## Behavior coverage

`ImportWorkflowPanelTest` drives production JavaFX buttons, tables, cell choice
controls and real confirmation dialogs against isolated temporary H2 databases.
Its authenticated fixture user exists in H2 and is bound to the fixture company;
manager/viewer tests retain actual UI and service authorization. File selection
is injected at the narrow native-dialog boundary, preserving the configured
JavaFX FileChooser, owner, titles, filters and production default delegate.

| Workflow | Assertions |
|---|---|
| COA CSV | Domain-invalid types block Save; rejected rows expose line/raw/reason; only accepted rows commit; missing/changed source fails safely and fresh preview can retry |
| Chooser selection/cancellation | Every import/profile route uses its chooser; cancellation and missing targets/window never start an import or write facts; correct owner and filters |
| OFX and QFX | Read-only asynchronous preview, confirmation cancellation, exact-source commit, repeat idempotency and no ledger writes |
| Mapped CSV | Profile creation, retained compatible selection, original-row display, preview/commit/repeat and malformed input clearing an earlier preview |
| Normalized CSV | Read-only preview, separate confirmation/commit/result, repeat idempotency and exact captured reconciliation return |
| Source changes | OFX, mapped CSV and normalized CSV commits reject changed file hashes without partial batches/rows and require a fresh preview |
| Profiles and targets | Invalid/missing JSON restores controls without writes; target changes clear incompatible profiles; inactive profiles are not restored |
| Permissions | Viewer can preview but cannot commit/save profiles; returning to manager restores valid draft capability |
| SCLX asynchronous workflow | Actual file-button preview, same-source re-preview without a second chooser, confirmation cancellation, changed-source rejection/retry, atomic balanced import and repeat idempotency |
| SCLX mapping/conflict | Real cell selectors require re-preview; an alternate ledger target is persisted; Keep Target/Take SCLX each preserve the selected counterparty winner |
| SCLX disposition | Suggested correction drops only an undated nonposting annotation, preserves the balanced entry and never rewrites the selected source |
| Preview lifecycle | A running preview is off-thread and cancellable, controls/progress change, late results cannot publish after cancellation, and a fresh preview can succeed |
| Atomic failure | A temporary H2 constraint rejects transaction creation after earlier writes; accounts, funds, transaction and splits roll back and actionable UI diagnostics remain |

The test-only H2 rejection deliberately produces a SQL constraint diagnostic. It
is not an unresolved application exception. Native required source fields and
extension collections are complete in positive fixtures; no service validation
or assertion is weakened to make a test pass.

`InterchangeTaskControllerTest` adds exclusion/failure/retry behavior: a second
writer cannot replace a busy operation, failure clears busy/cancel state and a
fresh commit executes once. Existing preview-cancellation and noncancellable
commit tests remain. CI executes these display cases, the workflow class and
existing rendered SCLX cases in isolated JVMs.

## Reproduced defect

The mapped CSV workflow failed before the repair: Preview Mapped Bank CSV reloads
bank targets, and replacing a detached account object fires its selection
listener, clearing the profile immediately before reading it. The screen then
asks for a profile that the user already selected.

Retain selected account/profile IDs across target reload, and restore a profile
only from the current active profile list for that same target. Profile-list
refresh also retains a still-valid ID. Different bank targets and inactive
profiles remain unselected. Existing preview/commit services and frozen profile
validation remain authoritative. Real Save Profile -> Preview CSV -> confirmation
-> atomic commit/repeat tests prove this path works after the fix.

## Measurement and validation

C9 baseline (72 mixed unit/UI/service/source tests, JaCoCo 0.8.13): Import screen
796/1,440 lines (55.3%), 176/420 branches (41.9%). These are not unit-only or whole
repository percentages. C10 repeats those classes plus new workflow/lifecycle
cases using the same agent/version, append-mode execution data, independent JVMs
and a real Xvfb display. Source-file counters include nested classes/lambdas.
The final combined run passed **98 tests, zero failures/errors/skips** (02:37),
including 23 workflow and 3 lifecycle cases. It adds the COA paths and uses the
visible result tabs for cell interactions. A superseded clean verification was
stopped to include these additions; only the final gate is authoritative.

| Source file | C9 lines / branches | C10 lines covered | C10 branches covered |
|---|---|---:|---:|
| ImportPreviewPanel.java | 55.3% / 41.9% | 1,372/1,459 (94.0%) | 320/432 (74.1%) |
| ImportPreviewService.java | 94.0% / 71.6% | 173/184 (94.0%) | 73/102 (71.6%) |
| SclxImportPreviewService.java | 82.6% / 66.0% | 723/875 (82.6%) | 422/632 (66.8%) |
| SclxImportCommitService.java | 83.4% / 61.7% | 1,299/1,555 (83.5%) | 393/632 (62.2%) |
| InterchangeTaskController.java | Not in C9 report | 59/60 (98.3%) | 13/18 (72.2%) |

The screen denominator changes because of the chooser boundary and profile
repair. This is a focused mixed unit/UI/service measurement, not a unit-only or
whole-repository percentage and not 100% branch coverage. Residual guards and
exception variants remain; no score is inflated by invoking disabled/private
commands or excluding production code.

Reproduce with JaCoCo agent/CLI 0.8.13 (`org.jacoco:org.jacoco.agent:0.8.13:jar:runtime`
and `org.jacoco:org.jacoco.cli:0.8.13:jar:nodeps`). On a display, run the C9 eight
classes plus ImportWorkflowPanelTest and InterchangeTaskControllerTest with
`-DreuseForks=false` and an agent argLine that preserves the configured test
user.home. Use a fresh append-mode data file and `includes=org.nonprofitbookkeeping.*`.
Generate the report from matching compiled classes, including nested classes,
and the source tree. No project dependency or CI coverage threshold is added.

Local evidence: `/tmp/c10-coverage-final.log`, `/tmp/c10-import-final.exec`,
`/tmp/c10-import-final.xml`, `/tmp/c10-import-final.csv` and
`/tmp/c10-import-final-report/index.html`. The counters above survive temporary
artifact cleanup. Final `clean verify` passed **1,190 tests, zero failures/errors, 134 expected
display skips**, BUILD SUCCESS (06:09). The display-dependent cases executed
separately above. Publication/exact-head CI status is in PLAN.

Native Windows/macOS file-dialog appearance, navigation and accessibility remain
owner/platform checks. Automated tests cover the application's chooser contract
and selected/cancelled results; they do not claim to test JavaFX's native OS dialog
implementation exhaustively. Invalid/disabled UI commands are not forced through
private reflection just to inflate branch coverage.

## Owner check

In a test company, save a bank CSV profile, select it, open the bank dropdown,
then preview the mapped file. Its profile must remain selected. Cancel a chooser
or commit confirmation and verify no imported records appear. Repeat a successful
import and verify the batch is not duplicated. Switch banks or deactivate a
profile and verify incompatible choices disappear. Test a changed source file,
then preview it again before retrying. In SCLX, change a mapping/conflict/message
choice, re-preview, inspect the new counts and only then confirm import. Verify
read-only viewer access and the reconciliation return destination.
