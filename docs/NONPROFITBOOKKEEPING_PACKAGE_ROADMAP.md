# NonprofitBookkeeping Package Integration Roadmap (API-Preserving)

## Current status
The expected archives were not present in this working tree at the time of this pass:
- `nonprofitbookkeeping.zip`
- `nonprofitbookkeeping-javadoc.zip`

This roadmap defines how to integrate those packages as **whole packages** with minimal API changes once the archives are available.

## Integration principles
1. **Package-first adoption**: import the original package trees intact before writing adapters.
2. **No API breaks**: keep public constructors/method signatures untouched whenever possible.
3. **Adapter boundaries only**: add wrappers/adapters in this repository (not edits inside imported package APIs).
4. **Incremental wiring**: wire one vertical feature slice at a time (UI panel + service + persistence dependencies).
5. **Traceability**: maintain a compatibility matrix from imported package/class -> current app panel/service.

## Proposed phases

### Phase 0 — Intake and inventory
- Unzip both archives under `imports/nonprofitbookkeeping/`.
- Generate an inventory:
  - Java packages
  - public classes/interfaces
  - JavaFX controllers/panels
  - service and repository dependencies
- Build a package dependency graph using imports and constructor dependencies.

**Deliverable**: `docs/nonprofitbookkeeping-inventory.md` (class/package list + dependency map).

### Phase 1 — Compatibility assessment (no code moves yet)
- Compare imported packages with existing project namespaces:
  - `org.nonprofitbookkeeping.ui`
  - `org.nonprofitbookkeeping.service`
  - `org.nonprofitbookkeeping.model`
- Identify direct compatibility and conflict zones:
  - JavaFX version assumptions
  - DI/runtime assumptions (CDI vs manual wiring)
  - persistence layer assumptions
- Create API-preservation decisions:
  - KEEP: import untouched
  - WRAP: adapt at boundary
  - DEFER: postpone modules with high dependency cost

**Deliverable**: `docs/nonprofitbookkeeping-compatibility-matrix.md`.

### Phase 2 — Runtime bridge layer
- Introduce a bridge module in this repo (e.g., `org.nonprofitbookkeeping.bridge`):
  - adapter factories for service construction
  - UI launcher/host adapters for imported panels
  - mapping utilities for model interop if needed
- Keep imported APIs intact; bridge layer is responsible for converting to current app expectations.

**Deliverable**: compile-time integration with zero API edits in imported classes.

### Phase 3 — UI panel migration in vertical slices
Prioritize high-value panels first:
1. Dashboard
2. Chart of Accounts
3. Funds
4. Schedules

For each panel:
- import package intact
- mount panel in `PanelHost`
- wire service dependencies via bridge/registry
- verify navigation, refresh, and error handling

**Deliverable**: each migrated panel selectable and functional from main app shell.

### Phase 4 — Service and data integration
- Wire imported service packages against existing persistence stack.
- Preserve service APIs; adapt only at instantiation points.
- Add mapping for any enum/model mismatches behind adapters.

**Deliverable**: read paths and core write paths functioning with current DB/runtime.

### Phase 5 — Stabilization and documentation
- Add integration tests for adapter boundaries.
- Add manual QA checklist per migrated panel.
- Document package ownership (imported upstream vs local adapters).

**Deliverable**: reproducible integration guide + regression checks.

## Suggested directory strategy
- Imported source (verbatim):
  - `src/main/java/org/nonprofitbookkeeping/imported/...`
- Local adapters/wrappers:
  - `src/main/java/org/nonprofitbookkeeping/bridge/...`
- Integration docs:
  - `docs/nonprofitbookkeeping-*.md`

This keeps upstream package APIs stable while isolating local adaptation code.

## Risk register
- **Dependency drift**: imported code may rely on unavailable libs/runtime.
  - Mitigation: adapter layer + phased enablement.
- **Model collisions**: same class names with divergent semantics.
  - Mitigation: namespace isolation + mapping adapters.
- **UI assumptions**: FXML/controller lifecycle differences.
  - Mitigation: host adapters and scoped integration tests.

## Acceptance criteria for “API-preserving integration”
- Imported package public APIs remain unchanged.
- Any behavioral adaptation is implemented outside imported package code.
- Main app can launch and navigate migrated imported screens from `PanelHost`.
- Service calls from migrated screens execute through bridge wiring without API edits.

## Next action once archives are present
Run the following from repo root:

```bash
mkdir -p imports/nonprofitbookkeeping
unzip -o nonprofitbookkeeping.zip -d imports/nonprofitbookkeeping/src
unzip -o nonprofitbookkeeping-javadoc.zip -d imports/nonprofitbookkeeping/javadoc
```

Then proceed with Phase 0 inventory and publish the two docs listed above.
