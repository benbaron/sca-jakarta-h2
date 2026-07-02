# New Interface Development Plan and GPT Prompt Pack

## 1. Purpose

This document defines the implementation plan for bringing the production JavaFX interface into alignment with the approved bookkeeping workspace mockup while preserving the existing application architecture, database authority, accounting rules, and recovery behavior.

The plan is deliberately divided into focused, mergeable pull requests. Each slice begins from current `main`, changes one coherent area, includes tests, and is validated through GitHub Actions before merge.

The approved visual direction is:

- compact white application surfaces;
- Segoe UI on Windows, with normal JavaFX fallback fonts elsewhere;
- subtle gray borders and separators;
- blue selected-navigation and selected-tab states;
- green, amber, red, blue, and neutral status cues;
- resizable navigation, center workspace, and inspector panes;
- no numbered explanatory annotations from the mockup;
- no fictional financial values;
- no accounting SQL in JavaFX controls;
- no loss of database recovery or startup-selection behavior.

## 2. Source of Truth

Before implementing any slice, inspect:

- current `main` in `benbaron/sca-jakarta-h2`;
- `doc/` architecture and workflow documents;
- `MainApp`;
- `ProductionWorkspaceWindow` and `ReferenceWorkspaceWindow`;
- `PanelHost`;
- `NavigationPane`;
- `InspectorPane`;
- the active dashboard panel and dashboard query service;
- `src/main/resources/ui/styles.css`;
- relevant JavaFX tests and GitHub Actions workflows;
- the standalone interface experiment under `experiments/dashboard-ui` as a visual reference only.

Do not assume that an older PR branch, experiment module, screenshot, or prior conversation still matches current `main`.

## 3. Non-Negotiable Architecture Rules

1. The H2 database is the authoritative source for accounting values.
2. SQL remains in repositories or query services, never JavaFX panels.
3. Accounting derivations remain in services, never repositories or cell factories.
4. JavaFX controls do not enter the domain model.
5. Existing user databases are never deleted or recreated to resolve an interface problem.
6. Startup must still allow a database to be selected, repaired, or created when the remembered default cannot be opened.
7. The center workspace must never render beneath either sidebar.
8. Navigation and inspector dividers must remain visible and draggable.
9. Window sizing must respect the usable screen area and Windows display scaling.
10. Every material change includes tests and a final `mvn clean verify` run in GitHub Actions.

## 4. Current Problems to Eliminate

The present interface work has exposed several recurring failure modes. The new implementation must prevent their return.

### 4.1 Competing production shells

There must be one authoritative production shell. A subclass that mutates another shell after construction is fragile because it depends on child order and button text. The long-term interface should construct its menu bar, toolbar, navigation, center workspace, inspector, and status bar directly.

### 4.2 Competing dashboard panels

There must be one authoritative production dashboard route. Compatibility wrappers may remain temporarily, but `PanelHost` must identify one production dashboard factory and documentation must name it accurately.

### 4.3 Clipping caused by child minimum sizes

A layout can appear correct at a large size while pushing the inspector outside the window at laptop widths. Tests must include:

- outer workspace width;
- navigation preferred and minimum widths;
- inspector preferred and minimum widths;
- divider width;
- center viewport width;
- child minimum and preferred widths;
- table horizontal scrolling;
- outer vertical scrolling;
- collapsed-sidebar behavior.

### 4.4 Default JavaFX appearance leaking into production

Unstyled Modena gradients, large controls, default row heights, and inconsistent fonts make the interface diverge from the approved mockup. Styling must be external, complete, and tested for merge-marker corruption.

### 4.5 Fictional or misleading dashboard values

The interface must show real derived data when the schema supports it and a blank or explicit unavailable state otherwise. It must not invent organization names, budget targets, balances, dates, notes, or statuses.

### 4.6 Tab accumulation

Users need one command to close all user-opened tabs while retaining the permanent Dashboard tab. The command must be available through the Workspace menu and by `Ctrl+Shift+W`.

## 5. Target Production Structure

The target class responsibilities are:

### `MainApp`

- obtains primary-screen visual bounds;
- calculates laptop-friendly startup dimensions;
- creates the scene;
- installs the production stylesheet;
- installs global shortcuts;
- shows the stage.

### `ProductionWorkspaceWindow`

- constructs the complete production shell directly;
- owns menus, toolbar, navigation, panel host, inspector, status bar, and database recovery transitions;
- exposes user commands such as Close All Tabs;
- does not depend on positional post-construction mutation by another shell.

### `PanelHost`

- owns the one-tab-per-panel map;
- keeps Dashboard permanent;
- opens and selects panels;
- removes closed panels from internal maps;
- closes every closable tab in one operation;
- supports future dirty-state checks through an explicit panel-close contract.

### `NavigationPane`

- displays categorized functional areas;
- uses stable `AppPanelId` references;
- highlights the active panel;
- supports collapse and restoration;
- does not create duplicate panel instances.

### `InspectorPane`

- displays structured cards for the current context;
- shows authoritative organization, period, balance, and note information;
- uses blank or unavailable states when values cannot be established;
- remains independently scrollable.

### Production Dashboard Panel

- builds the approved card arrangement;
- consumes one immutable dashboard projection;
- owns no SQL;
- derives no accounting rules in JavaFX code;
- uses responsive wide, medium, and narrow placement.

### `JpaDashboardQueryService`

- executes one explicit read transaction;
- loads and derives all dashboard values;
- returns immutable records;
- leaves unsupported budget targets and reconciliation values unavailable rather than inventing them.

### `styles.css`

- defines the complete production look;
- contains no merge markers or duplicated competing themes;
- styles menus, toolbar, navigation, tabs, tables, cards, inspector, status bar, scrollbars, charts, and validation cues.

## 6. Delivery Strategy

Each phase below is a separate branch and pull request from current `main`. Do not continue adding commits to a branch after its PR is merged.

---

# Phase 1 — Establish Interface Baseline and Regression Inventory

## Goal

Create a factual map of the currently merged interface before changing behavior.

## Work

1. Identify the actual production launcher and shell.
2. Trace the Dashboard route from `MainApp` through `PanelHost`.
3. Trace database failure and recovery startup behavior.
4. List all production CSS selectors actually used by Java classes.
5. Identify all panels whose minimum or preferred width can force clipping.
6. Identify all interface tests and whether they run headlessly or skip.
7. Record current screenshots at:
   - 1366×768 at 100% scaling;
   - 1920×1080 at 100% scaling;
   - 1920×1080 at 125% scaling;
   - 1920×1080 at 150% scaling.
8. Update this document with the observed production class graph if it differs from the target structure.

## Tests

No production behavior change is required. Add only missing non-invasive integrity tests, such as:

- stylesheet contains no conflict markers;
- every production `AppPanelId` has a route;
- Dashboard is permanent;
- production stylesheet resource exists.

## Acceptance Criteria

- Current production routing is documented accurately.
- No obsolete branch or experiment is described as production.
- No application behavior changes.
- GitHub Actions passes `mvn clean verify`.

## GPT Prompt

```text
You are working in the authoritative repository:
https://github.com/benbaron/sca-jakarta-h2

Begin from current main. Do not modify production behavior yet.

Perform an interface-baseline audit:
1. Trace the actual production startup path from MainApp to the visible dashboard.
2. Identify the authoritative workspace shell, dashboard panel, navigation, inspector, panel host, stylesheet, and dashboard query service.
3. Trace failed remembered-database startup through select, repair, and create actions.
4. Identify JavaFX child minimum/preferred sizes that can push the inspector outside the window.
5. List the production CSS classes referenced by Java source.
6. Inspect all interface and layout tests and state which are skipped in headless CI.
7. Add only non-invasive integrity tests where needed.
8. Update doc/new-interface.md with factual findings if the current code differs from its target architecture.

Do not redesign the interface in this PR. Do not edit database migrations. Do not introduce placeholder code.

Before declaring the PR ready:
- inspect the final diff;
- confirm only intended audit/test/documentation files changed;
- run mvn clean verify through GitHub Actions;
- update the PR description with actual results.
```

---

# Phase 2 — Consolidate the Production Workspace Shell

## Goal

Replace layered or post-construction shell mutation with one coherent production workspace.

## Work

1. Move final menu, toolbar, navigation, workspace, inspector, and status-bar construction into one authoritative shell.
2. Remove reliance on:
   - child index assumptions;
   - button-text matching;
   - subclass replacement of an already-built menu bar or toolbar.
3. Preserve:
   - database recovery dashboard;
   - select database;
   - create database;
   - retry/repair database;
   - active database label;
   - active period selection;
   - status-bar connection state.
4. Keep constructor injection available for state store and database connector tests.
5. Use one explicit method for toggling each sidebar.
6. Keep the center workspace at minimum width zero.

## Tests

- startup with valid database shows Dashboard;
- startup with failed remembered database shows recovery Dashboard;
- accounting panels remain blocked until a database is connected;
- navigation and inspector can each be removed and restored;
- center remains a distinct split-pane child;
- toolbar and menu commands invoke the intended methods.

## Acceptance Criteria

- One production shell class constructs all chrome.
- No production shell mutates another shell by child position or visible text.
- Recovery behavior remains unchanged.
- Existing databases remain untouched.

## GPT Prompt

```text
Inspect current main in benbaron/sca-jakarta-h2 and read doc/new-interface.md.

Implement Phase 2 only: consolidate the production workspace shell.

Requirements:
- One authoritative production class must directly build the menu bar, toolbar, navigation pane, center PanelHost, inspector, and status bar.
- Remove any production subclass that modifies an already-built shell by child index or button text, or reduce it to a compatibility alias with no mutation.
- Preserve database select, create, retry/repair, and failed-startup recovery behavior exactly.
- Preserve constructor injection used by tests.
- Keep JavaFX controls out of domain classes.
- Do not change accounting queries or migrations.
- Use Allman brace style.

Add regression tests for successful startup, failed remembered-database startup, sidebar toggling, and center-pane preservation.

Open one focused PR from current main. Run mvn clean verify in GitHub Actions and report the final run, not an earlier run.
```

---

# Phase 3 — Implement Laptop-Safe Window and Split-Pane Geometry

## Goal

Ensure the full interface opens on a typical laptop without clipping and remains usable under display scaling.

## Work

1. Use `Screen.getPrimary().getVisualBounds()`.
2. Cap the normal startup target near 1180×760 logical pixels.
3. On smaller screens, use no more than 90% of usable width and height.
4. Center the stage within visual bounds.
5. Cap minimum dimensions so the minimum is never larger than the usable screen.
6. Allocate initial navigation and inspector widths from pixel-oriented targets, not fixed 20/80 percentages.
7. Keep visible draggable dividers.
8. Ensure the center pane owns remaining width.
9. Use scroll panes where content genuinely exceeds the viewport.
10. Do not enforce panel minimum widths that push the inspector off-screen.

## Tests

Pure geometry tests must cover:

- 1366×768 laptop visual bounds;
- 1024×600 constrained bounds;
- 1920×1080 desktop bounds;
- navigation, divider, center, divider, inspector arithmetic;
- collapsed navigation;
- collapsed inspector;
- both sidebars collapsed;
- card minimum widths;
- table minimum and preferred widths;
- vertical and horizontal scrolling decisions.

## Acceptance Criteria

- The entire shell is visible on a 1366×768 usable display.
- Navigation and inspector are both visible by default.
- The center does not render underneath either sidebar.
- Users can resize both sidebars.

## GPT Prompt

```text
Implement Phase 3 from doc/new-interface.md in a new branch from current main.

Create or refine pure policies for:
- startup stage width, height, minimum width, minimum height, x, and y;
- initial navigation, center, and inspector allocation;
- dashboard responsive breakpoints and scrolling requirements.

Use the primary screen visual bounds and keep the initial window inside the usable screen. Target no more than approximately 1180×760 logical pixels on a normal desktop and no more than 90% of smaller usable screens.

Do not test only outer sibling rectangles. Geometry tests must include child minimum/preferred widths, center viewport width, divider width, table horizontal scrolling, outer vertical scrolling, and collapsed sidebars.

Do not alter accounting behavior or database migrations.

Run mvn clean verify through GitHub Actions and include actual dimensions and test results in the PR description.
```

---

# Phase 4 — Complete the Production Visual System

## Goal

Make the rendered application resemble the approved white-and-blue mockup instead of default JavaFX Modena controls.

## Work

Create one coherent external stylesheet covering:

- root font, sizes, and text colors;
- menu bar and context menus;
- toolbar and icon buttons;
- date picker, combo boxes, and text fields;
- split panes and dividers;
- workspace tabs;
- navigation brand, sections, items, selected state, and collapse control;
- inspector tabs and cards;
- dashboard cards, amounts, status pills, icons, tables, quick links, and charts;
- general tables and placeholders;
- status bar and connection cue;
- scrollbars;
- validation, warning, and error states.

Use:

- Segoe UI on Windows;
- compact 10–12 px control text where appropriate;
- white content surfaces;
- light gray workspace background;
- subtle #dfe5ec-like borders;
- blue active states;
- green positive values;
- amber warnings;
- red errors.

Do not attempt to style the native Windows title bar through JavaFX.

## Tests

- stylesheet resource loads;
- no merge markers;
- required selectors exist;
- no duplicate incompatible root themes;
- JavaFX controls can be instantiated with the stylesheet in a toolkit-enabled test;
- screenshot comparison checklist is completed manually on Windows.

## Acceptance Criteria

- No default gradient-button appearance remains in normal production controls.
- Font scale and spacing resemble the mockup.
- Navigation selection and inspector cards are visually distinct.
- The stylesheet is readable and grouped by component.

## GPT Prompt

```text
Implement Phase 4 from doc/new-interface.md on current main.

Use the approved interface mockup and the experiment stylesheet as visual references, but do not copy fictional values or experiment-only architecture into production.

Replace the production stylesheet with one coherent external CSS system. Remove merge markers, duplicated competing root definitions, obsolete selectors, and default Modena-like gradients.

Style menus, toolbar, form controls, split panes, tabs, navigation, inspector cards, dashboard cards, tables, scrollbars, charts, validation cues, and status bar.

Use Segoe UI with normal JavaFX fallback, compact spacing, white surfaces, subtle gray borders, blue selected states, green/amber/red status cues, and accessible text labels in addition to color.

Add stylesheet integrity tests. Do not change SQL, accounting rules, or migrations.

After GitHub Actions passes mvn clean verify, leave the PR draft until Windows screenshots at 100%, 125%, and 150% scaling have been reviewed.
```

---

# Phase 5 — Establish One Authoritative Dashboard

## Goal

Remove ambiguity between dashboard implementations and route production through one data-backed panel.

## Work

1. Identify the production dashboard panel explicitly in `PanelHost`.
2. Retain only one production presentation implementation.
3. Convert any experiment or older dashboard class into:
   - a deleted obsolete class; or
   - a small compatibility wrapper clearly documented as non-authoritative.
4. Ensure the selected dashboard contains:
   - Cash Balances;
   - YTD Surplus/Deficit;
   - Budget Performance;
   - Open Items;
   - Recent Transactions;
   - Bank Reconciliation Status;
   - Budget vs Actual;
   - Quick Links.
5. Preserve real data connections.
6. Preserve responsive wide, medium, and narrow layouts.
7. Keep the dashboard read-only.

## Tests

- `PanelHost` creates the intended dashboard class;
- Dashboard is the only permanent tab;
- opening Dashboard repeatedly reuses one tab;
- dashboard projection populates all supported cards;
- unsupported values remain blank or explicitly unavailable;
- responsive placements match policy.

## Acceptance Criteria

- Documentation and code agree on the production dashboard class.
- No two production dashboard implementations evolve independently.
- No fictional numbers remain.

## GPT Prompt

```text
Implement Phase 5 from doc/new-interface.md from current main.

Determine the dashboard currently routed by PanelHost. Select one authoritative production dashboard presentation and remove the parallel production path. The standalone experiment remains a visual reference only.

The production dashboard must use DashboardQueryService/JpaDashboardQueryService and must not contain SQL. It must show the eight approved sections and responsive wide, medium, and narrow layouts.

Do not invent budget targets, cleared balances, organization details, notes, reconciliation amounts, or statuses. Show real derived values when supported; otherwise show blank or a concise unavailable state.

Add routing, reuse, projection, and responsive-layout tests. Update doc/dashboard-workspace.md and doc/new-interface.md to name the actual production class.

Use one focused PR and validate with mvn clean verify in GitHub Actions.
```

---

# Phase 6 — Finish Dashboard Accounting Derivations

## Goal

Make every displayed financial indicator accurate, explainable, and database-derived.

## Work

### Cash

- aggregate posted bank-account lines through the selected date;
- identify individual bank accounts by stable account IDs;
- use `BigDecimal`.

### YTD Surplus/Deficit

- derive from posted income and expense activity within the fiscal-year range;
- do not assume January 1 if the company fiscal-year configuration provides another start.

### Fund-Class Balances

- derive unrestricted, temporarily restricted, permanently restricted, and designated net assets from appropriate posted equity, income, and expense effects;
- do not sum all lines of balanced transactions and thereby collapse values to zero.

### Recent Transaction Balance

- calculate a posted aggregate bank balance immediately before the oldest displayed transaction;
- apply posted bank deltas in stable date-and-ID order;
- show blank where a reliable running balance cannot be established;
- show blank for reversed or non-posted transactions.

### Affects Bank

- true only for posted transactions with at least one bank-account line;
- blank otherwise.

### Affects Budget

- true only for posted income or expense lines with a budget-category reference;
- blank otherwise.

### Open Items and Reconciliation

- use repository/service projections scoped to the active company and period;
- do not fabricate outstanding checks, deposits in transit, receivables, or payables.

## Tests

Use in-memory H2 repository/service tests for:

- normal posted receipts and expenses;
- reversed transactions;
- same-date stable ID ordering;
- multiple bank accounts;
- transactions with no bank lines;
- transactions with and without budget categories;
- non-calendar fiscal-year start;
- unrestricted, restricted, and designated funds;
- empty database;
- unavailable reconciliation state.

## Acceptance Criteria

- Every numeric dashboard value is traceable to a query and test.
- Balance/Affects Bank/Affects Budget follow derive-or-blank behavior.
- Reversed history does not affect current indicators.

## GPT Prompt

```text
Implement Phase 6 from doc/new-interface.md from current main.

Focus only on dashboard accounting projections and tests. Do not redesign the interface in this PR.

Audit JpaDashboardQueryService and related repositories. Correct cash, fiscal-year surplus/deficit, fund-class net assets, recent running bank balance, Affects Bank, Affects Budget, open-item, and reconciliation projections.

Requirements:
- BigDecimal only for money;
- stable database IDs;
- posted transactions only for current balances;
- reversed/non-posted history may remain visible but must show blank for current Balance, Affects Bank, and Affects Budget;
- active company and configured fiscal year must be respected;
- unsupported values remain unavailable rather than fictional.

Add in-memory H2 tests covering multiple bank accounts, same-date ordering, reversals, budget-category presence, fund classifications, empty databases, and non-calendar fiscal years.

No JavaFX SQL. No migrations unless a genuine missing normalized field is proven and separately documented.

Run mvn clean verify through GitHub Actions and include the exact accounting scenarios in the PR description.
```

---

# Phase 7 — Complete Navigation and Inspector Behavior

## Goal

Match the mockup's navigation and inspector behavior while preserving all production panels.

## Work

### Navigation

- organize panels into stable functional groups;
- use icons and text;
- highlight the active panel;
- keep a visible collapse action;
- preserve vertical scrolling;
- restore width predictably after collapse;
- ensure every routed panel appears exactly once.

### Inspector

- maintain Inspector and Alerts tabs;
- show dashboard Organization, Period Information, Balances, and Notes cards;
- display context-specific details for non-dashboard panels;
- use authoritative company and accounting-period data;
- leave missing notes blank or unavailable;
- wrap long values and avoid truncation where possible;
- keep independent vertical scrolling.

## Tests

- every supported `AppPanelId` is indexed once;
- active panel highlighting follows tab selection;
- navigation collapse/restore maintains center content;
- inspector dashboard cards use snapshot values;
- unconfigured organization/period does not appear active or open;
- long inspector values wrap within available width.

## Acceptance Criteria

- Navigation resembles the mockup and remains usable on a laptop.
- Inspector does not invent data.
- No duplicate methods or merged implementations remain.

## GPT Prompt

```text
Implement Phase 7 from doc/new-interface.md from current main.

Refine NavigationPane and InspectorPane only.

Navigation requirements:
- one item per supported AppPanelId;
- grouped sections matching the approved mockup;
- vector icon plus text;
- blue selected state synchronized with the active workspace tab;
- independent vertical scrolling;
- visible collapse and predictable restore.

Inspector requirements:
- Inspector and Alerts tabs;
- dashboard cards for Organization, Period Information, Balances, and Notes;
- authoritative snapshot values;
- no hard-coded fiscal year, currency, status, or notes;
- wrapping values and independent scrolling;
- context details for other panels.

Add tests for panel indexing, active highlight, collapse/restore geometry, unconfigured states, and inspector content.

Do not change dashboard accounting queries or database migrations in this slice.
```

---

# Phase 8 — Add Close All Tabs and Tab Lifecycle Controls

## Goal

Allow users to dismiss all user-opened tabs in one action while keeping Dashboard open.

## Required User Experience

- Menu path: `Workspace` → `Close All Tabs`.
- Keyboard shortcut: `Ctrl+Shift+W`.
- Dashboard remains open and selected.
- Every closable tab is removed from both the visible `TabPane` and `PanelHost` maps.
- Reopening a closed panel constructs or restores a valid panel instance.
- Repeating Close All Tabs when only Dashboard is open is harmless.

## Dirty-State Follow-Up

The present `AppPanel` contract has save, new, copy, and paste hooks but no standard dirty-state or close-veto contract. Implement close-all in two steps if necessary:

1. First PR: close all currently closable tabs and keep Dashboard.
2. Follow-up PR: introduce an explicit close protocol, such as:
   - `boolean isDirty()`;
   - `CloseDecision requestClose()`; or
   - a workspace-owned confirmation service.

Do not pretend unsaved-edit protection exists until panels implement a real dirty-state contract.

## Tests

- opens Dashboard plus several replacement test panels;
- closes all closable tabs;
- retains Dashboard;
- selects Dashboard;
- removes closed IDs from maps;
- returns the number of closed tabs;
- does nothing harmful when only Dashboard is open;
- shortcut and menu action invoke the same command;
- ordinary individual close behavior remains intact.

## Acceptance Criteria

- One command dismisses all opened work tabs.
- Dashboard cannot be closed.
- Internal tab and panel maps remain consistent.
- Documentation does not overstate unsaved-edit protection.

## GPT Prompt

```text
Implement Phase 8 from doc/new-interface.md from current main.

Add a user-facing Workspace → Close All Tabs command with Ctrl+Shift+W.

PanelHost must provide one tested operation that:
- identifies all currently open closable tabs;
- removes each from the TabPane;
- removes corresponding entries from internal tab and panel maps;
- keeps or recreates the permanent Dashboard tab;
- selects Dashboard;
- returns the number of tabs closed;
- is idempotent when only Dashboard is open.

Use the same command method for the menu item and shortcut. Preserve individual tab close behavior.

Inspect AppPanel before claiming unsaved-edit protection. If there is no dirty-state/close-veto contract, document that limitation and do not add a fake prompt.

Add JavaFX lifecycle tests using the repository's FxTestSupport. Run mvn clean verify through GitHub Actions and inspect the final diff.
```

---

# Phase 9 — Normalize Panel Toolbars and Responsive Editors

## Goal

Make non-dashboard workspaces resemble the compact mockup and avoid horizontal clipping.

## Work

1. Replace long fixed `HBox` action strips with wrapping `FlowPane` or adaptive toolbar groups where appropriate.
2. Set panel roots and table containers to minimum width zero.
3. Use responsive `GridPane` constraints for editors.
4. Wrap explanatory labels and validation messages.
5. Keep tables horizontally scrollable when columns cannot reasonably compress.
6. Avoid hard-coded panel minimum widths.
7. Apply consistent:
   - panel title;
   - action-row spacing;
   - status-message placement;
   - table header and row sizing;
   - button hierarchy.
8. Start with high-traffic panels:
   - Transaction Editor;
   - Ledger Register;
   - Period Close Runs;
   - Reconciliation;
   - Budget Editor;
   - Import Preview;
   - Approval Audit.

## Tests

For each converted panel:

- child minimum/preferred size test;
- action wrapping at constrained width;
- table remains visible;
- center content does not enlarge the whole split pane;
- validation messages wrap;
- keyboard navigation remains functional where relevant.

## Acceptance Criteria

- No action strip pushes the inspector outside the window.
- Tables and editors remain reachable at laptop width.
- Toolbar styling is consistent across panels.

## GPT Prompt

```text
Implement Phase 9 from doc/new-interface.md as one focused panel-family PR from current main.

Choose one coherent family of panels, starting with Period Close and Approval Audit or Transaction Editor and Ledger Register.

Replace non-wrapping action bars with adaptive layout, set panel and table containers to minWidth 0, wrap validation/status text, and preserve horizontal table scrolling where needed. Do not force the outer workspace wider.

Keep SQL and accounting rules out of JavaFX panels. Preserve keyboard behavior and service boundaries.

Add geometry tests that evaluate child min/pref sizes, action wrapping, table viewport behavior, and outer split-pane constraints—not only sibling rectangles.

Run mvn clean verify through GitHub Actions. Do not mix unrelated panels into the same PR.
```

---

# Phase 10 — Accessibility, Keyboard Navigation, and Visual Status Semantics

## Goal

Ensure the compact interface remains understandable without relying solely on color or mouse interaction.

## Work

1. Provide text labels alongside status colors.
2. Add accessible text for icon-only controls.
3. Establish predictable keyboard traversal.
4. Preserve table keyboard selection and activation.
5. Define shortcuts centrally and document them.
6. Ensure focus cues remain visible.
7. Confirm text contrast for blue, green, amber, red, gray, and disabled states.
8. Add tooltips only where labels cannot be shown directly.
9. Avoid controls whose meaning is available only through a glyph.

## Tests

- shortcut registry tests;
- accessible text exists for icon-only buttons;
- focus traversal does not strand the user in sidebars;
- status rows contain text as well as CSS class/color;
- selected and disabled states remain distinguishable.

## Acceptance Criteria

- Common commands are keyboard reachable.
- Status meanings remain clear in grayscale.
- Focus styling is visible.

## GPT Prompt

```text
Implement Phase 10 from doc/new-interface.md from current main.

Audit the production interface for keyboard access and color-only meaning. Add accessible text to icon-only controls, centralize documented shortcuts, preserve visible focus cues, and ensure status indicators include readable text in addition to CSS colors.

Do not redesign accounting behavior. Do not add decorative tooltips where visible labels already exist.

Add tests for shortcut registration, accessible text, and status semantics. Run mvn clean verify through GitHub Actions.
```

---

# Phase 11 — Windows Visual Closure

## Goal

Close the gap between implementation and the approved mockup using repeatable screenshots and measured observations.

## Required Screenshot Matrix

Capture the production application on Windows at:

| Display | Scaling | Window state |
|---|---:|---|
| 1366×768 | 100% | default startup |
| 1920×1080 | 100% | default startup |
| 1920×1080 | 125% | default startup |
| 1920×1080 | 150% | default startup |
| 1920×1080 | 100% | maximized |
| 1024×768 or equivalent constrained VM | 100% | manually resized |

For each screenshot inspect:

- full window inside usable screen;
- navigation visible;
- inspector visible;
- center not beneath either sidebar;
- divider visibility;
- dashboard card order;
- tab visibility;
- table headers and rows;
- toolbar wrapping or clipping;
- status-bar visibility;
- font size and density;
- scrollbar placement;
- error and unavailable states.

## Acceptance Criteria

- No clipping at default laptop startup.
- No numbered mockup annotations.
- Colors and font density are substantially aligned with the reference.
- Any remaining deviation is recorded as a specific follow-up issue rather than silently accepted.

## GPT Prompt

```text
Perform Phase 11 visual closure for the production interface.

Use current main and the approved mockup. Do not change code before collecting the full Windows screenshot matrix listed in doc/new-interface.md.

For each screenshot, record exact window size, display resolution, scaling, active panel, visible sidebars, scrollbar state, and any clipping. Convert every observed problem into a reproducible statement tied to a class, CSS selector, or layout policy.

Then implement only the minimum corrective changes in one focused PR. Add a regression test for each geometry defect that can be expressed without screenshot comparison.

Run mvn clean verify in GitHub Actions and update the PR with before/after screenshots and actual validation results.
```

---

# Phase 12 — Remove Compatibility Debris and Finalize Documentation

## Goal

Remove obsolete interface paths after the new production system is stable.

## Work

1. Remove unused compatibility wrappers.
2. Remove obsolete CSS selectors.
3. Remove generated files accidentally tracked under `target/`.
4. Verify `.gitignore` coverage.
5. Correct experiment README language so it does not claim to be production.
6. Update:
   - `doc/dashboard-workspace.md`;
   - `doc/new-interface.md`;
   - relevant architecture documents;
   - keyboard shortcut documentation;
   - testing guidance.
7. Confirm one source of truth for:
   - production shell;
   - dashboard panel;
   - layout policies;
   - stylesheet;
   - tab lifecycle.

## Tests

- no references to deleted classes;
- no orphaned CSS selectors for removed components where practical;
- no files under `target/` tracked;
- clean Eclipse import;
- clean Maven build from an empty target directory.

## Acceptance Criteria

- Production architecture is coherent and documented.
- No experiment or obsolete wrapper is described as authoritative.
- `mvn clean verify` passes from a clean checkout.

## GPT Prompt

```text
Implement Phase 12 from doc/new-interface.md after all earlier interface PRs are merged.

Begin from current main. Remove obsolete interface wrappers, unused CSS, duplicate layout policies, and accidentally tracked generated files. Correct all documentation to name the actual production shell, dashboard, stylesheet, panel host, and tab lifecycle.

Do not remove a compatibility class until repository search proves it is unused or all callers are migrated in the same PR.

Verify Eclipse-compatible project structure and a clean build from an empty target directory. Run mvn clean verify through GitHub Actions. Inspect the final diff and confirm no accounting or migration files changed.
```

## 7. Standard Prompt Header for Every Interface Task

Prepend this header to future interface implementation prompts:

```text
You are the principal Java architect and implementor for the SCA Bookkeeping Program.

Authoritative repository:
https://github.com/benbaron/sca-jakarta-h2

Before designing or changing code:
- inspect current main;
- inspect relevant files under doc/;
- inspect the current production route, not an obsolete PR branch;
- treat current main as the source of truth.

Technical requirements:
- Java 17 or later;
- JavaFX;
- H2;
- Maven;
- JUnit 5;
- Eclipse-compatible structure;
- Allman brace style;
- constructor injection and clear package boundaries;
- no SQL in JavaFX panels;
- no accounting rules in repositories;
- no JavaFX controls in domain classes;
- no destructive database workaround;
- complete, coherent, mergeable slice;
- no placeholder implementation or unexplained TODO.

Workflow requirements:
- branch from current main;
- one focused PR;
- add material tests;
- inspect final diff;
- verify no unintended files changed;
- run mvn clean verify through GitHub Actions;
- read and fix failing logs;
- update the PR description with actual validation;
- mark ready only after checks pass.
```

## 8. Standard Review Prompt

Use this after an implementation PR is complete:

```text
Review the current pull request against current main and doc/new-interface.md.

Check:
1. Does the PR implement only its stated phase?
2. Does it preserve database selection, repair, creation, and recovery?
3. Does it keep SQL out of JavaFX panels?
4. Does it keep accounting rules out of repositories?
5. Does the center workspace remain separate from navigation and inspector?
6. Do child min/pref sizes, dividers, viewport width, and scrolling behave correctly?
7. Are Dashboard values real or explicitly unavailable?
8. Are Balance, Affects Bank, and Affects Budget derived-or-blank?
9. Are there duplicate classes, methods, selectors, merge markers, generated files, or stale documentation?
10. Do tests cover the reproduced defect?
11. Did the final branch head pass mvn clean verify in GitHub Actions?

Return findings ordered by severity with file and line references. Do not declare the PR ready if the final head is unverified.
```

## 9. Definition of Interface Completion

The new interface is complete only when all of the following are true:

- one production shell constructs the application chrome;
- one production dashboard is routed by `PanelHost`;
- the default window fits a laptop screen;
- navigation, center, and inspector remain visible and independently resizable;
- the center never renders beneath a sidebar;
- the visual system resembles the approved mockup without its annotations;
- menus, toolbar, tabs, cards, tables, and status bar use one coherent stylesheet;
- Dashboard values are database-backed or explicitly unavailable;
- Balance, Affects Bank, and Affects Budget are derived when possible and blank otherwise;
- all user-opened tabs can be closed with Workspace → Close All Tabs or `Ctrl+Shift+W`, leaving Dashboard selected;
- dirty-state protection is implemented through a real panel contract before being claimed;
- high-traffic editors remain usable at laptop widths;
- Windows screenshots pass the defined visual matrix;
- documentation matches the actual merged architecture;
- the final `main` commit passes `mvn clean verify`.
