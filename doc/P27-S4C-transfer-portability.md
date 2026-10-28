# P27-S4C operational transfer portability

PR #394 merged at `00aa3cb6`; accepted head `3437de8b` passed workflow `40604890977`. This slice completes transfer metadata under the [S4 audit](P27-S4-portability-audit.md). S4D evidence/category-master/integrated gates remain incomplete.

## Contract

Add nondestructive V91 intrinsic UUIDs to every FundTransfer, retaining existing rows/status/links/requests and the V90 write guard. Optional `extensions.scaJakartaH2.fundTransfers` has exactly version 1 and transfers. Each fact contains transferId, date, fromFundId, toFundId, amount, nullable memo, status, nullable transactionId, nullable requestId/requestHash, createdAt and updatedAt. Retain legacy DRAFT/VOID rows and unlinked history without manufacturing accounting. POSTED requires a canonical link. No mutable memo, request ID or numeric database key is a transfer identity.

Validate complete linked canonical four-line transfer shape, company ownership, date/amount/direction and canonical reversal relationships. Every linked reversal has the opposite-direction transfer fact, including reversal-of-reversal; missing or contradictory graph facts block. Restore through a caller-owned FundTransferService seam after canonical transactions/correction relationships exist. Never call interactive Save/Reverse to restore history; never duplicate ledger or reversal audit facts. Imported transfers retain generic Journal edit/delete protection and future native reversal behavior.

Preserve original request UUID/hash as provenance. A hash binding native source IDs does not promise replay of a remapped target command. Native exact retries may only return an existing fact when their current normalized command matches; mismatches fail rather than duplicate accounting. Globally colliding request IDs/foreign portable ownership are visible in Preview and rechecked inside commit. Stored timestamps fit microsecond precision; amounts use exact DECIMAL(19,4), memo remains nullable/bounded, and unknown versions/fields/status/references reject before mutation.

Transfer facts receive existing source-specific interchange identities and exact export/preview counts. Their metadata also participates in complete linked transaction identities so stripping it cannot silently erase operational protection. Legacy absence leaves old hashes/ledger-only import compatible and infers no transfer. Changed facts require explicit Keep target; Take source is unavailable. Kept operational and canonical history must remain linked to the same target transaction. Exact reimport/restart is a no-op. BOOKKEEPING_WRITE, current file/company revalidation, mappings, close/reconciliation and atomic rollback remain authoritative.

No new Save surface, approval workflow, second ledger or donor persistence. Native DRAFT/POSTED/VOID remain stored legacy vocabulary, not new visible posting controls. Existing File export and Import Preview/Save expose counts; Fund Transfers shows restored saved/reversal history. Whole-database recovery remains complete; external readers are not assumed to retain optional metadata.

## Inspection and verification

Read AGENTS/PLAN, adopted program/S4 audit, internal transfers and SCLX contracts. Inspect FundTransfer/Service/IntegrityService, canonical correction/history import, V1/V78/V90, snapshots/serializer/preview/identity/commit/counts and transfer/migration/authorization/round-trip tests. Donor FundTransferLifecycleService uses a distinct policy/repair queue and posting facade; do not import that authority or workflow.

Cover V90 upgrade/backfill/restart, original/reversal/reversal-of-reversal and legacy unlinked rows, exact source facts/references/requests/times, deterministic export, cross-company remapping, source/target conflicts and request collisions, malformed/unsupported graph rejection, legacy absence and stripped metadata conflicts, retry/restart, authorization, late rollback, native correction protection and post-import reversal. Run focused tests, full clean verification and production JavaFX.

In a disposable company save and reverse a transfer, export/import, inspect Fund Transfers and Journal, restart/reimport without duplicates, and reverse a restored saved entry. Confirm generic direct edit/delete remains blocked. Preview a changed/stripped/colliding file and inspect the explicit conflict or blocking result. No native human result is inferred from automated checks.
