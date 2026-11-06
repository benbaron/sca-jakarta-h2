# P29-S2 — Menus, work panes and overflow access

## Accepted direction

The owner requests Windows 11 style dropdown menus and cascading submenus, retaining existing menu labels and commands; complete removal of the right Inspector; no more than two major visible sections per work pane, counting nested subdivisions; and scrollbars wherever necessary at smaller screen sizes. This overrides the older three-column shell and nested Journal layout requirements.

Use the current JavaFX application and its existing controllers, services, permissions, dirty-state checks and save boundaries. A named work pane is selected through tabs within its existing navigation destination; changing views never creates a second editor or discards a draft. Command bars, labels and local table tools are chrome rather than additional independent work sections.

## Required reading and inspection

- `AGENTS.md`, `PLAN.md`, `interface-operation-matrix.md`, `ui_design_rules.md`, `ui/editor-guidelines.md`.
- `ProductionWorkspaceWindow`, `NavigationPane`, `PanelHost`, `PanelFactory`, `GlobalCommandRegistry`, `WorkspaceShellLayoutPolicy`, the divider preference model and application CSS.
- Every canonical production route and its nested layouts, including Administration, Import Preview and Period Close subpanes.
- Existing Journal, report, asset/inventory, master-data, Save/dirty-state, theme, geometry and scrolling regressions.

This is a composition change to the existing application. Prior donor decisions in the Journal guidelines remain applicable; no donor persistence, alternate editor, framework, model or service is imported.

## Layout inventory and implemented composition

| Destination | Major sections visible at once |
| --- | --- |
| Shell | Resizable/collapsible navigation and the remaining work area; Inspector and its toggle/shortcut/context action removed |
| Dashboard | Two cards in each named Overview, Budget, Open items / Reconciliation, or Recent transactions / Links pane |
| Journal | Journal: filters + register; Entry: header + lines; Additional Details: one form; Supplemental Details: one table with compact local actions for every kind |
| Report Library | Report list + either Parameters or Preview; explicit Run Report opens Preview |
| Event Accounting | Event scope/summary + either linked transactions or bank activity |
| Banking | Banks or Bank accounts, each using the existing two-section table/editor layout; global New/Save follows the selected view and preserves hidden drafts |
| Asset Register | Asset list + either asset editor or lifecycle history |
| Inventory | Movement/lifecycle controls + either item list or movement history; item editing retains its existing separate mode |
| Property Custody | Subjects + either custody event draft or history |
| Recognition Assistant | Open items + either recognition draft or due proposals |
| Merchant Settlement Assistant | Receivables + either settlement draft or NMR items |
| Import Preview | Messages or selected-message detail + selected result view; accepted/rejected COA results are separate work panes |
| Period Close | Existing close history and readiness each retain two sections; review draft has editor + references, retained reviews have history + details |
| Payees, Merchants, Fund Transfers, NMR Policies | Scrolling header chrome above two table/editor sections; redundant outer header split removed |
| Other routes | Reviewed through the complete canonical route inventory; existing one/two-section layouts retained |
| Budget vs Actual | One comparison table; the former explanation-only split is removed |

## Scrolling and menus

`PanelViewport` gives every hosted destination and every logical tab, including inactive Administration subpanes, horizontal/vertical overflow access. It retains bounded table viewports and native table scrolling. Existing form scrollers remain; none deliberately disables an overflow axis. Work panes have a usable minimum surface within the outer scroller, so reduced windows scroll instead of collapsing the content to nothing. Local forms and table columns retain their own overflow behavior.

The menu bar retains File, Workspace, Destinations and View. Database commands retain their labels and handlers in File → Database. Dropdowns and cascading menus share rounded corners, padded rows, focus states and shadows using neutral light/dark palettes selected from the current theme. Native JavaFX keyboard navigation, accelerators, submenu arrows and Escape dismissal remain. The obsolete Inspector command and accelerator are removed. Database/recovery/sample-data messages move to the status area with complete text in a tooltip.

Only the left shell divider remains active. Existing two-field preference files remain readable; the retired right value is ignored and normalized, preserving the existing storage format. Existing company table state remains authoritative. Changed pane geometry uses new divider keys where old positions no longer describe the layout.

## Layout-save lifecycle correction

Rendered UI tests exposed delayed divider/table-layout callbacks firing after logout. The common split binder, Report Library and Company Administration now capture the original company/database scope with `UiStateWriteContext` and skip stale or unauthenticated layout saves. Authoritative preference-service authorization remains unchanged. The regression first proves a valid layout save, then proves that logout, company change and database change preserve the retained value rather than writing through the stale callback. This is a narrow prerequisite for reliable layout/overflow changes, with no accounting or schema change.

## Supplemental-tab availability correction

The owner's Windows screenshot showed Receivable checked while its editor remained disabled. JavaFX writes a tab's effective disabled state directly to its content root. Wrapping an initially disabled tab after construction left that flag on the inner root; enabling the tab subsequently enabled only the new viewport. `PanelViewport.prepare` now clears the obsolete tab-owned flag when transferring content into its viewport. Independently bound controls and disabled forms in enabled tabs retain their own state.

`JournalSupplementalAvailabilityTest` exercises the production `PanelFactory` route with an ACCOUNTANT session and all six supplemental types. Each checkbox enables the real inner table and Add action; Add/Remove operate on the live draft, and unchecking/rechecking retains correct availability. Generic viewport regressions also cover a disabled parent TabPane and independently restricted controls. These checks are included in both Linux display and Windows presentation CI. Earlier direct Journal-controller tests bypassed the factory's viewport preparation and therefore missed this regression.

## Validation and owner checks

### Panel text review

All 29 canonical destinations, their inner panes, Administration's nested views,
sign-in/recovery and chart-import dialogs were reviewed. Detailed instructions now
live under function headings in the [panel and function guide](ui/panel-guide.md),
which Help links for later manual assembly. The UI design rules adopt this policy.

| Area | Treatment |
| --- | --- |
| Journal, Event Accounting, banking/reconciliation and check review | Remove introductions and instruction blocks; attach concise hints to search, tagging, payment context and actions. Remove Journal's supplemental text-only splits. |
| Budgets, masters, assets/inventory, custody, recognition and settlement | Remove tutorial paragraphs; keep brief retention/correction notices where Delete is unavailable. Budget vs Actual becomes a single table. |
| Period Close, readiness, review evidence and Opening Conversion | Move workflow detail to the guide; keep live findings, status, attestation and required confirmations visible. |
| Administration, authentication and recovery | Move detailed chart/report/ownership/security instructions to the guide and relevant controls; remove obsolete phase and legacy-preference notes. Keep current errors, account/session facts and recovery actions. |
| Import/COA dialogs and Help | Put mapping syntax in the mapping field tooltip. Replace Help's long navigation/tutorial blocks with the function-guide link and installed shortcut reference. Keep import validation/details and explicit acceptance confirmations. |
| Dashboard, Report Library, Depreciation Runs, Audit History and Diagnostics | Existing compact results/controls retained; dynamic operational records are not tutorial text. Accounting-packet hover guidance shortened. |

The supplemental Add/Apply/Remove bar wraps when narrow, and the table receives
the remaining height with native two-axis overflow. No empty help section or
obsolete divider binding remains. Tooltips on gated actions are installed before
the gate captures them, preserving permission-denial explanations and later role
refresh behavior. Review changed views at reduced width and enlarged text, hover
their controls, and confirm errors/status remain readable without hovering.

Run baseline compile, focused shell/menu/pane/overflow tests, the complete production JavaFX route and business-editor regressions, and `mvn clean verify`. Test every canonical destination for a maximum of two visible split sections across all inner tabs. Exercise actual horizontal and vertical scrollbars in small windows and confirm the final form command remains reachable. Retain save, correction, permissions, table-state and dirty-draft assertions when adapting retired geometry assertions.

Owner Windows checks:

1. Open File → Database with mouse and keyboard. Open a cascading submenu, use arrow keys and Escape, and verify menus remain readable in light and dark themes.
2. Confirm no right Inspector, toggle button, View menu entry, right-click action or Inspector shortcut exists.
3. Visit every destination and named work pane at laptop size, then reduce width/height and increase text/display scaling. Reach the bottom/right of every form and table using the provided scrollbars.
4. In Journal, create/edit an entry, switch through Entry, Additional Details and Supplemental Details, and confirm the draft survives. New/Edit opens Entry; Save retains the existing full transaction scope.
   Check each supplemental type and confirm its table and Add Open Item Detail action become usable. Uncheck/recheck it and confirm it enables again. Remove Detail should remain disabled until a row is selected.
5. Check report Parameters/Preview, asset/history, inventory/movement, bank/account and review-evidence/history view switching without losing selection.

Local automated display checks do not establish native Windows appearance or owner acceptance. The owner explicitly authorized remote publication on 2026-11-05. Native Windows visual acceptance remains separate from automated validation.

## Local verification result

Initial baseline compile and headless clean verification passed (1,125 tests, zero failures/errors, 83 expected display skips). The initial complete UI package passed 333 tests on a local-only virtual display. Screens at 1280×800 and 680×520 with enlarged text were reviewed.

After the owner's supplemental-tab report, the new production-route and generic regressions reproduced the disabled inner editor before the fix. Focused acceptance passed 10 tests. The corrected source then passed `DISPLAY=:95 /workspace/.cloud-setup/maven.sh clean verify`: **1,162 tests, zero failures/errors/skips**, including all **336 UI tests**, in 12:12 (`/workspace/work/supplemental-display-clean-verify.log`). No uncaught JavaFX-thread exception was logged. Source/workflow/document checks pass. Native Windows appearance/scaling and owner acceptance remain pending; local virtual-display execution does not establish them. The branch is published in draft [PR #410](https://github.com/benbaron/sca-jakarta-h2/pull/410); inspect final-head CI separately.

Panel-text follow-up validation: focused source/document checks passed **21 tests**.
The final display-enabled full run passed all **336 UI tests**, including all
production routes, compact supplemental actions, real two-axis table overflow,
editing/Save/correction and role changes. Its only failure among 1,162 tests was
the unchanged native-recovery fixture's second close-review setup, before backup.
All three recovery tests passed unchanged in isolation, and again in the fresh
normal headless `clean verify`: **1,129 tests, zero failures/errors, 87 display
skips**, in 08:09 (`/workspace/work/panel-text-headless-clean-verify.log`). All skips
are in the UI package and have separate display-enabled coverage. No recovery or
accounting implementation was changed or assertion bypassed. The initial
intermittent setup failure remains recorded in PLAN rather than claimed fixed.
Representative Journal, Administration, Help and Budget vs Actual layouts were
reviewed at 1100×700 and 680×520 with 13px/20px text. Markdown links and whitespace
pass. Final-head CI and native Windows owner acceptance remain separate gates.
