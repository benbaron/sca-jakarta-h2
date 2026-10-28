# P27-S4B budget metadata portability

PR #393 merged at `8500d99b`; its accepted head `a5263d26` passed workflow `40442798593`. This slice closes the budget plan/line metadata gap identified by the [S4 audit](P27-S4-portability-audit.md). Transfer links and structured close-review validation remain S4C/S4D.

## Contract

Keep core SCLX 1.3 budgets unchanged. Add optional `extensions.scaJakartaH2.budgetMetadata` with exactly `version: 1`, `plans` and `lines`. When present it completely describes the core budget/line identities; missing, duplicate, dangling or foreign identities reject before mutation. Unsupported versions/fields, invalid dates/times, status inconsistent with core `active`, and line months outside their exact plan dates reject. A non-calendar fiscal plan may contain months in the next calendar year. Without the extension, preserve the existing calendar-year/active-or-draft compatibility behavior and old identity hashes.

Each plan fact contains budgetId, periodStart, periodEnd, status (DRAFT/ACTIVE/ARCHIVED), nullable activatedAt/archivedAt, nullable notes, createdAt and updatedAt. Each line fact contains lineId, nullable notes, createdAt and updatedAt. Preserve exact text/nulls and source timestamps. Mirror the existing V50 constraint: ACTIVE requires activation and no archive time; ARCHIVED requires archive time (activation may be absent); DRAFT requires no archive time. Do not invent a lifecycle event to fill absent optional history. Metadata does not activate, archive or revise an existing target plan. Numeric database IDs never travel.

Metadata participates in existing BUDGET/BUDGET_LINE hashes, including a line's owning plan metadata, so changed or stripped facts conflict visibly. Explicit Keep target preserves the complete retained plan/lines; no partial addition of enriched source lines beneath a kept plan. Take source is unavailable for normalized budget history. Exact retry/restart is a no-op. Source identities and mappings remain company-scoped. Current BOOKKEEPING_WRITE authority, file/hash/target revalidation and caller-owned atomic commit apply.

Timestamps must be exactly representable at H2's stored microsecond precision; finer values reject before mutation instead of rounding a retained source fact. Text notes are bounded to one MiB of UTF-8 per field without trimming.

Older application readers may reject this populated extension, and external consumers may discard it. Retain the original enriched SCLX file and whole-database backup; a pass through a legacy consumer does not certify complete history retention.

Restore through BudgetPlanService's governed import seam, including managed ownership, scope, one active version per fiscal year and all-or-nothing rollback. No new table/migration, parallel budget model, ledger entry or UI Save authority. Existing File export and Import Preview/Save expose the operation and its explicit metadata count. Budget Category descriptive/effective metadata is not a plan/line field and is not newly mapped by this slice; retain that distinction in the final S4 inventory.

The archived status and source archive timestamp must be applied before the identity-generated insert to satisfy V50; applying history afterward is too late. A reproduced partial-reimport failure also requires the mapped-fund check to preserve an exact retained AS_IS source identity when both source and target are inactive. This does not permit a new remapping onto an inactive fund or waive a changed type/active-source check.

## Inspection and design

Read AGENTS/PLAN, adopted P23–P28 program/S4 audit, P26-S3 contract and SCLX. Inspect BudgetPlan/Line/Category, BudgetPlanService and commands/lifecycle tests; current snapshot/assembler/serializer, parser/preview/identity/conflict/commit, V88 and budget/interchange tests. Donor NonprofitBookkeepingSclxExportService maps a different BudgetRecord representation with line notes/extensions; retain production normalized JPA and its import seam rather than donor persistence.

## Required verification and owner checks

Cover non-calendar cross-year months, DRAFT/ACTIVE/ARCHIVED (including a never-activated archived draft), null/Unicode/whitespace notes and timestamps; inactive Fund/Event relationships; deterministic output; cross-company import; unsupported/malformed/partial metadata, contradictory core active state, month bounds and duplicate/dangling identity rejection; legacy absence; enriched-to-legacy and changed source conflicts/Keep target; restart/exact retry; denied actor and injected late rollback. Run focused tests, full clean verification and production JavaFX.

In a disposable company, export a July–June active plan, its archived predecessor and a draft with Fund/Event/month scopes and notes. Preview/import into an empty company and confirm dates, states, scopes and notes in Budget Editor; restart/reimport without duplicates. Change a source note and verify preview reports a conflict with Keep target. Confirm legacy files still import using their historical reduced contract. No native human result is inferred from automated checks.
