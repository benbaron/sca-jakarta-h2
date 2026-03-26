# Integration Plan: NonprofitBookkeeping Suite Migration

## Purpose
This document defines the end-to-end phased migration plan for importing functionality from `nonprofitbookkeeping.zip` into this repository (`sca-jakarta-h2`) while preserving the current architecture bones:
- Keep this repo as canonical
- Keep package root `org.nonprofitbookkeeping.*`
- Keep `PanelHost` panel catalog architecture
- Prefer clean architecture over backward compatibility
- Use PR-sized slices with tests + code review each iteration

## Global Guardrails
1. **Canonical architecture first**: retain this repo's layering and naming; adapt imported code to fit.
2. **Conflict handling**: when overlapping implementations exist, preserve current architectural pattern and ask user to choose when tradeoffs are non-obvious.
3. **No backward-compat guarantees required**: schema, flow, and API can evolve for better architecture.
4. **Every PR slice must include**:
   - Implementation
   - `mvn test` run and reported result
   - code review findings (critical/high/medium/low)
   - offer to fix review issues and test failures
5. **Definition of done (per slice)**:
   - Acceptance criteria met
   - tests green (or failures documented with fix plan)
   - review completed
   - docs updated

## Target Capability Scope
- Accounting core
- OFX/QFX import/export
- SCLX import/export
- Reporting
- Settings
- Workflows (reconciliation, approvals, period close, open-item state)
- Multi-company
- Database selection
- Database backup/restore
- Chart of accounts import/export (XLSX)

---

## Cross-Slice Checklist Template
Use this checklist for every PR-sized slice:

### Implementation Checklist
- [ ] Identify ZIP files relevant to the slice
- [ ] Identify current repo equivalents and extension points
- [ ] Decide merge approach (adopt/adapt/prune)
- [ ] Implement with current package conventions
- [ ] Add/adjust migrations if persistence changes
- [ ] Add/adjust panel wiring if UI changes via `PanelHost`
- [ ] Add/adjust service interfaces and adapters
- [ ] Add/adjust docs

### Testing Checklist
- [ ] Unit tests for new business logic
- [ ] Integration tests for repository/service workflows
- [ ] UI wiring tests where applicable
- [ ] Run `mvn test`
- [ ] Capture test summary (pass/fail counts, notable failures)

### Code Review Checklist
- [ ] Architecture boundaries respected (domain vs transport vs persistence)
- [ ] DTO/entity separation maintained
- [ ] Duplicate code removed or justified
- [ ] Error handling and validation paths covered
- [ ] Naming/package consistency
- [ ] Transactional/data integrity considerations
- [ ] Observability/logging appropriate
- [ ] Technical debt notes captured

### Reporting Checklist
- [ ] Summary of changes
- [ ] Files touched + rationale
- [ ] Test command(s) + result
- [ ] Review findings by severity
- [ ] Offer to fix issues and failures

---

## PR-01: Import Foundation & Architectural Scaffolding
### Goal
Create the import framework that unifies SCLX and OFX/QFX ingestion paths using DTO boundaries and clean service contracts.

### Checklist
- [ ] Define import envelope abstractions (`ImportRequest`, `ImportResult`, `ValidationIssue`)
- [ ] Introduce DTO packages for external format payloads
- [ ] Define mapper interfaces DTO -> domain input models
- [ ] Implement shared validation/error taxonomy
- [ ] Integrate with existing `ImportPreviewService`/`ImportExportOrchestrationService` where appropriate
- [ ] Add unit tests for envelope and validation behavior

### Acceptance Criteria
- [ ] New import framework supports pluggable format handlers
- [ ] No direct parser-to-domain coupling
- [ ] Existing import flows still compile and tests pass

### Detailed Prompt for Iteration Agent
"Implement PR-01 from `integration_plan.md`. Keep this repo canonical, maintain package root `org.nonprofitbookkeeping.*`, and preserve clean architecture boundaries. Add import framework abstractions and DTO/mappers to support pluggable SCLX and OFX/QFX handlers without direct parser-to-domain coupling. Integrate with existing import orchestration/preview services. Add/adjust tests, run `mvn test`, report results, perform code review findings by severity, and offer to fix any review findings or test failures."

---

## PR-02: SCLX Domain Mapping and Parser Integration
### Goal
Port SCLX models/parsing logic needed for target scope and map them into current domain/service flows.

### Checklist
- [ ] Port required SCLX schema/record models (prune unused generated noise)
- [ ] Add SCLX parser + deserializer module integration
- [ ] Implement SCLX DTO -> domain mappers
- [ ] Handle account/fund resolution strategy and mapping modes
- [ ] Implement balancing policy for incomplete lines with explicit validation/rules
- [ ] Add tests for parsing, mapping, and failure scenarios

### Acceptance Criteria
- [ ] SCLX payload can be parsed and previewed in canonical import framework
- [ ] Mapping results deterministic and validated
- [ ] Known bad payloads produce actionable errors

### Detailed Prompt for Iteration Agent
"Implement PR-02 from `integration_plan.md`. Import only required SCLX parsing/schema/model logic from ZIP and map into existing domain via DTO mappers. Prune duplicate/unneeded generated classes where possible. Preserve existing package conventions and clean architecture. Add comprehensive tests for valid/invalid SCLX payloads, run `mvn test`, report results, perform code review, and offer fixes for findings/failures."

---

## PR-03: Persistence Integration + Database Backup/Restore Foundations
### Goal
Integrate imported data with repositories/migrations and establish backup/restore service foundations.

### Checklist
- [ ] Add/modify Flyway migrations for SCLX/extended import persistence
- [ ] Update repositories for staged import + commit semantics
- [ ] Create backup service contract (`DatabaseBackupService`)
- [ ] Create restore service contract (`DatabaseRestoreService`)
- [ ] Implement initial backup/restore adapters for selected DB
- [ ] Add integrity verification post-restore checks
- [ ] Add integration tests for migration and restore safety

### Acceptance Criteria
- [ ] Imported records persist correctly through canonical repositories
- [ ] Backup artifact can be generated from selected DB
- [ ] Restore workflow works in test scenario with verification

### Detailed Prompt for Iteration Agent
"Implement PR-03 from `integration_plan.md`. Add repository and migration changes needed for imported data workflows and implement DB backup/restore foundational services and adapters aligned to database selection architecture. Include integration tests for migration safety and backup/restore integrity. Run `mvn test`, report results, do a code review, and offer fixes for issues/failures."

---

## PR-04: Workflow Integration (Reconciliation/Approval/Period Close/Open-Item)
### Goal
Wire imported transactions and states into existing workflow services and policies.

### Checklist
- [ ] Align imported transaction lifecycle with reconciliation runs
- [ ] Ensure approval audit captures imported actions
- [ ] Integrate period close constraints with imported data timing
- [ ] Map open-item state transitions to canonical policies
- [ ] Add service-level integration tests spanning workflows

### Acceptance Criteria
- [ ] End-to-end import -> workflow actions operate correctly
- [ ] Workflow auditability preserved
- [ ] State transition rules enforced consistently

### Detailed Prompt for Iteration Agent
"Implement PR-04 from `integration_plan.md`. Integrate imported data into reconciliation, approval, period close, and open-item state workflows without breaking existing policy semantics. Add end-to-end integration tests for workflow paths. Run `mvn test`, report outcomes, perform code review, and offer to fix any findings or failing tests."

---

## PR-05: PanelHost UI Port for Import Features (SCLX + OFX/QFX)
### Goal
Expose import capabilities through existing `PanelHost` and panel catalog patterns.

### Checklist
- [ ] Add/extend import panels under current UI architecture
- [ ] Register panels in `AppPanelId` + `PanelHost`
- [ ] Add panel actions/commands consistent with app conventions
- [ ] Surface validation and preview details in UI
- [ ] Add UI wiring tests / panel catalog tests

### Acceptance Criteria
- [ ] User can access and run import flows from PanelHost-based UI
- [ ] Errors are visible and actionable
- [ ] No ZIP shell-level UI replacement introduced

### Detailed Prompt for Iteration Agent
"Implement PR-05 from `integration_plan.md`. Port import UI capabilities into the existing PanelHost architecture (do not replace application shell). Register panels/actions consistently and expose preview/validation feedback. Add tests for panel catalog wiring and command handling. Run `mvn test`, report results, perform code review, and offer fixes for findings/failures."

---

## PR-06: Reporting + Settings + CoA XLSX Import/Export
### Goal
Converge reporting/settings behavior and implement Chart of Accounts XLSX round-trip.

### Checklist
- [ ] Merge/extend reporting services/adapters where ZIP adds capability
- [ ] Integrate settings options needed by imported modules
- [ ] Implement CoA XLSX export pipeline
- [ ] Implement CoA XLSX import pipeline with validation
- [ ] Add round-trip tests (export -> import) for supported fields
- [ ] Add failure tests for malformed XLSX rows

### Acceptance Criteria
- [ ] Reporting and settings capabilities aligned with target scope
- [ ] CoA XLSX round-trip succeeds for supported fields
- [ ] Validation errors are precise and user-facing

### Detailed Prompt for Iteration Agent
"Implement PR-06 from `integration_plan.md`. Merge reporting/settings features from ZIP as needed and implement Chart of Accounts XLSX import/export with strong validation and round-trip tests. Keep canonical architecture and package conventions. Run `mvn test`, report results, do a code review, and offer to fix issues/failures."

---

## PR-07: Multi-Company + Database Selection + Backup/Restore UI Integration
### Goal
Complete multi-company/database selection flows and connect backup/restore operations to UI and app workflows.

### Checklist
- [ ] Merge missing multi-company behaviors
- [ ] Merge/extend database selection state and workflows
- [ ] Connect backup/restore services to app commands/panels
- [ ] Ensure correct scoping of operations to selected database/company
- [ ] Add integration tests for cross-company/DB isolation

### Acceptance Criteria
- [ ] Users can select company/database and safely run operations
- [ ] Backup/restore respects selected context
- [ ] Isolation guarantees tested

### Detailed Prompt for Iteration Agent
"Implement PR-07 from `integration_plan.md`. Complete multi-company and database selection features and integrate backup/restore operations into UI/workflows with strict context isolation. Add integration tests for cross-context correctness. Run `mvn test`, report results, perform code review, and offer to fix findings and test failures."

---

## PR-08: Consolidation, De-duplication, and Hardening
### Goal
Finalize architecture quality, remove duplicates, and harden test coverage across all migrated capabilities.

### Checklist
- [ ] Remove obsolete/duplicate code introduced during migration
- [ ] Normalize naming and package placement
- [ ] Expand regression suite across all target capabilities
- [ ] Add architecture notes documenting key boundaries
- [ ] Resolve outstanding review debt from prior slices

### Acceptance Criteria
- [ ] No known critical/high review findings remain
- [ ] End-to-end test suite stable
- [ ] Migration documentation complete for future contributors

### Detailed Prompt for Iteration Agent
"Implement PR-08 from `integration_plan.md`. Focus on consolidation: remove duplication, normalize structure, improve regression coverage, and close outstanding technical debt while keeping canonical architecture clean. Run `mvn test`, report results, conduct a code review with severity ratings, and offer to fix any remaining issues or failures."

---

## Suggested Per-PR Report Format
Use this structure in every iteration response:
1. **Summary**
   - Scope completed
   - Main files changed
2. **Testing**
   - Command(s) run
   - Pass/fail summary
3. **Code Review Findings**
   - Critical/High/Medium/Low issues
4. **Proposed Fixes**
   - immediate fixes for findings/failures
5. **Next Slice Recommendation**
   - go/no-go and handoff notes

## Conflict Escalation Questions (ask user when needed)
Use these when implementation conflicts arise:
1. Prefer existing implementation or imported implementation for this feature?
2. If merging, which API shape should be canonical?
3. Is temporary adapter acceptable, or require immediate consolidation?
4. Should we prioritize behavior parity or architecture purity for this conflict?

