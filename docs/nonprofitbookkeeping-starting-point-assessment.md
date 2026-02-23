# NonprofitBookkeeping vs sca-jakarta-h2 as the Base Codebase

## Question
Would it be simpler to move this work into the upstream `nonprofitbookkeeping` codebase, rather than continuing package-by-package integration into `sca-jakarta-h2`?

## Short answer
For your stated goal (keep `sca-jakarta-h2` data model and front screen), **`sca-jakarta-h2` should remain the base**, and imported NonprofitBookkeeping features should continue to be mounted as subpanels with adapters.

## Why `sca-jakarta-h2` should stay the base
1. **Model authority is already here**
   - You explicitly want to keep this project's model semantics and persistence flow.
   - Re-basing on upstream would require remapping nearly every domain object (`Account`, `Fund`, `ChartOfAccounts`, etc.) back to this project's model conventions.
2. **Shell/navigation consistency is already here**
   - `PanelHost`, `MainWindow`, and AppPanel contracts define your desired UI experience.
   - Upstream is a different app shell and lifecycle model, so adopting it as base would force re-implementation of your current shell.
3. **Lower migration risk with vertical slices**
   - Current strategy allows KEEP/WRAP/DEFER decisions per package and keeps blast radius small.
   - A full pivot to upstream creates a large one-time integration risk and longer stabilization period.

## When upstream could be the better base
Choose upstream as base only if all are true:
- You are willing to adopt upstream domain/persistence semantics as source of truth.
- You are willing to replace current shell/navigation patterns.
- You want fastest exposure of broad upstream feature set over shell/model continuity.

## Practical recommendation
Continue current path:
- Keep `sca-jakarta-h2` as host app.
- Keep imported packages namespaced under `nonprofitbookkeeping.*`.
- Use bridge adapters under `org.nonprofitbookkeeping.bridge.*`.
- Preserve host panel structure; mount imported features as subpanels/workspaces.

## Next implementation consequence
For upcoming Chart of Accounts and Funds slices, follow the same pattern used for Dashboard:
- host panel remains in `org.nonprofitbookkeeping.ui.*`
- imported panel appears inside a dedicated workspace section
- data flow comes from host services through bridge adapters
