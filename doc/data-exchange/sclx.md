# SCLX company-data interchange contract

## 1. Purpose and authority

This document governs SCLX import and export for the SCA Bookkeeping Program. SCLX is a portable, company-level business-data format. It is not a database backup, a Chart of Accounts-only document, or a bank-statement format.

The authoritative application data remains the current H2 database and the canonical services described in `doc/persistence-authority-inventory.md`. The donor repository is compatibility evidence only. P15 uses donor commit `c697630ec1f784ebe8338d7300da6c9ac801b180`.

Normative terms **MUST**, **MUST NOT**, **SHOULD**, and **MAY** have their ordinary requirements meaning.

## 2. Format identification and versions

An SCLX document is UTF-8 JSON whose root object contains both:

- `"format": "SCLX"`, matched exactly after JSON string decoding; and
- a supported `version` string.

Filename and extension are hints only. A `.sclx` or `.json` suffix never overrides root content.

Readers MUST accept versions `1.0`, `1.2`, and `1.3`. Unknown, missing, malformed, pre-1.0, and future versions are blocking errors. Readers MAY tolerate unknown bounded fields for forward compatibility, but MUST warn and MUST NOT interpret them as canonical data.

Writers MUST emit SCLX `1.3`. Writers MUST NOT emit 1.0 or 1.2.

## 3. Active-company scope

SCLX export represents exactly one selected active company. Export MUST fail before writing when any included record has no unambiguous ownership path to that company or references a record owned by another company.

The intended supported business scope is:

- company/organization identity and fiscal settings;
- the selected company's chart and accounts;
- funds and fund hierarchy;
- budget categories, plans, and lines;
- activities, counterparties, and merchants used by included company records;
- canonical `Txn` and `TxnSplit` history, correction links, and supported supplemental lines;
- company bank configuration, reviewed bank-statement facts, import issues, matching/reconciliation facts, and cleared state;
- fixed assets and completed depreciation runs;
- inventory items and movements;
- company period-close ranges and factual close/reopen history; and
- factual audit events that can be proven to belong to the selected company.

P15-S0 found that several current tables lack an unambiguous company owner. Those sections MUST remain blocked from active-company export until the nondestructive P15-S1 ownership migration and cross-company checks are complete. The detailed audit is in `doc/persistence-authority-inventory.md`.

## 4. Explicit exclusions

SCLX MUST NOT contain:

- application users, password hashes, authentication credentials, roles, or login state;
- JavaFX layout, table, divider, recent-company, or other UI preferences;
- Flyway history, H2 internals, JDBC URLs, database passwords, or database file paths;
- source-machine absolute paths;
- raw document attachments or arbitrary executable content;
- the compatibility `JournalTransaction`/`PostingLine` ledger as a second authority;
- compatibility open-item or former Schedules authority;
- generic Import/Export Jobs or a generic durable job log; or
- records belonging to another company.

Unsupported donor sections MUST be reported by section and count. They MUST NOT be silently converted into unrelated application concepts.

## 5. Portable identity and references

Portable identities MUST be stable external strings, not local numeric primary keys. Each exported object type MUST use a documented identity such as company code plus format-owned external ID, account code, fund code, or another stable business key.

Canonical transactions, counterparties, merchants, banks, configured bank accounts, reviewed import batches, statement lines, import issues, fixed assets, and completed depreciation runs use durable UUID `portableId` values assigned independently of local numeric primary keys. Reconciliation sessions and matches use UUID portable identities enforced directly on their native tables. Existing rows receive identities during the nondestructive migration sequence, and new rows receive them at creation or from database defaults. SCLX identities namespace those UUIDs by company. Mutable names, account labels, source paths, and local row numbers are presentation or provenance data and never durable identity. Budget plans use company code, fiscal year, and version code; budget lines use their plan identity plus category code, optional fund identity, and optional period month.

The current deterministic snapshot writes every company-owned budget plan and line. Budget lines preserve `categoryCode`, optional fund reference, optional `periodMonth`, and exact `BigDecimal` amount; the current normalized budget model has no direct account relation, so `accountId` remains absent rather than being inferred. Canonical transactions preserve status, deterministic debit/credit lines, and explicit `REVERSAL` or `REPLACEMENT` correction relationships. Transaction-line ordinals are assigned only after sorting by stable business content and never by a serialized database identifier. Supplemental-detail identities similarly use the exported transaction identity plus a deterministic transaction-local ordinal after sorting by persisted line order and exported business content. Company activities use company code plus activity code as their portable identity, and transaction-line `activityId` values resolve to the exported activity extension. Company counterparties and merchants use company-scoped UUID identities. A transaction header payee is repeated as the standard `counterpartyId` on each exported transaction line, while line-level merchant relationships are preserved separately under the governed party extension.

`FixedAsset` and `FixedAssetDepreciationRun` each have an intrinsic UUID portable identity. A fixed asset identity never uses `fixed_asset.id`, asset name, account name, fund name, or a mutable combination of asset fields. A completed depreciation-run identity never uses `fixed_asset_depreciation_run.id` and is independent of the linked canonical transaction identity; the transaction reference describes accounting provenance rather than serving as the run identity.

`extensions.scaJakartaH2.fixedAssets` version 1 is included in selected-company SCLX 1.3 export. It contains `assets` and `depreciationRuns`. Asset entries preserve the intrinsic asset identity, name, acquisition date and cost, salvage value, useful life, depreciation method, opening accumulated depreciation, status, notes, creation/update timestamps, and references to the selected active chart's asset, accumulated-depreciation, and depreciation-expense accounts plus the company-owned fund. Completed-run entries preserve their intrinsic run identity, asset reference, run date, positive depreciation amount, canonical transaction reference, notes, and creation timestamp. Local numeric IDs and mutable labels are never emitted as references. Cross-company assets, accounts, funds, runs, or transaction provenance are blocking export errors.

Within a document:

- every required identity MUST be present and nonblank;
- identities MUST be unique within their entity type and namespace;
- every reference MUST resolve to exactly one included object or an explicitly selected local mapping;
- references MUST NOT resolve across companies; and
- local H2 IDs MUST NOT be serialized as portable identities.

An import preview classifies incoming records as:

- `NEW`: no local external identity exists;
- `IDENTICAL`: the same external identity and normalized content already exist;
- `CONFLICT`: the identity exists but normalized content differs;
- `DUPLICATE`: the input repeats an identity or canonical transaction fingerprint; or
- `UNRESOLVED`: a required reference or mapping is missing.

`IDENTICAL` records are idempotent skips. `CONFLICT`, unresolved required references, and exact duplicates are blocking until an explicit supported resolution is selected. Probable duplicates are warnings requiring review.

## 6. Account-reference modes

Import supports exactly two account-reference modes:

### 6.1 `AS_IS`

The SCLX account reference is resolved without translation. For a new or empty target company, the imported chart may create the referenced accounts under the governed Chart of Accounts rules. For a populated target, every reference MUST resolve uniquely and compatibly.

### 6.2 `MAPPED`

The user supplies an explicit source-to-target account mapping. Every used source account reference MUST map to one active target account. The preview MUST display every mapping and every unmapped or multiply mapped source reference. Mapping MUST NOT be inferred solely from account names.

The selected mode and complete effective mapping MUST be included in the operation result and factual audit record.

## 7. Transactions, zero values, and generated balancing lines

Canonical imported transactions MUST be balanced, have at least two nonzero posting lines, and satisfy current transaction, closed-period, correction, and reconciliation protections.

Compatibility behavior for donor documents is a separate future SCLX mapping unit.

## 8. Application extensions

Readers MAY preserve unknown extension namespaces for a same-operation round trip, provided they were bounded, preserved exactly, and reported.

Standard SCLX fields MUST be used when they can faithfully express the fact. Extensions MUST NOT duplicate or override standard fields.

### 8.1 Activities extension

`extensions.scaJakartaH2.activities` is an array containing every activity owned by the selected company, including inactive activities needed for historical interpretation. Entries are ordered by activity code and contain exactly:

- `activityId`: `activity:<company-code>:<activity-code>` using the governed portable-identity encoding;
- `code`: the company-scoped activity code;
- `name`: the activity display name; and
- `active`: the persisted active/inactive state.

A canonical transaction line with an activity writes `activityId` in the standard transaction-line DTO. Every nonblank `activityId` MUST resolve to exactly one entry in this extension. Duplicate activity identities, cross-company activities, malformed entries, and unresolved references are blocking export errors. Activity records contribute to the operation entity counts, and the `ACTIVITIES` section is no longer reported as deferred.

### 8.2 Counterparties and merchants extension

`extensions.scaJakartaH2.counterparties` is an object with exactly three arrays:

- `counterparties`: every counterparty owned by the selected company, including inactive records. Each entry contains `counterpartyId`, `displayName`, `kind`, nullable `email`, nullable `phone`, nullable `notes`, and `active`.
- `merchants`: every merchant owned by the selected company, including inactive records. Each entry contains `merchantId`, `name`, nullable `notes`, and `active`.
- `transactionLineMerchants`: line-level merchant relationships. Each entry contains `lineId` and `merchantId`.

Counterparty identities use `counterparty:<company-code>:<portable-uuid>` and merchant identities use `merchant:<company-code>:<portable-uuid>`. Arrays are ordered by portable identity, and line-merchant links are ordered by transaction-line identity. Mutable names are presentation data only.

When a canonical transaction has a header payee, each exported transaction line writes that payee's `counterpartyId`. This repetition is intentional because the governed export DTO carries the standard counterparty reference on transaction lines rather than the transaction header. A line-level `Merchant` is not collapsed into that counterparty reference; it is preserved by `transactionLineMerchants`, allowing both relationships to coexist.

Every nonblank transaction-line `counterpartyId` MUST resolve to exactly one exported counterparty. Every merchant link MUST resolve to exactly one exported transaction line and one exported merchant, and a line may have at most one merchant link. Duplicate identities, malformed extension objects, omitted referenced masters, and cross-company relationships are blocking export errors. Counterparties and merchants contribute separately to export counts, and the `COUNTERPARTIES` section is no longer reported as deferred.

### 8.3 Supplemental transaction details extension

`extensions.scaJakartaH2.supplementalDetails` is an array containing every persisted `TxnSupplementalLine` whose canonical transaction belongs to the selected company. Each entry contains exactly:

- `supplementalDetailId`: `supplemental-detail:<transaction-id>:<ordinal>` using the governed portable-identity encoding;
- `transactionId`: the canonical exported transaction identity;
- `lineOrder`: the persisted non-negative transaction-local display order;
- `kind`: one of `RECEIVABLE`, `PAYABLE`, `PREPAID_EXPENSE`, `DEFERRED_REVENUE`, `OTHER_ASSET`, or `OTHER_LIABILITY`;
- nullable `entryRef`, `counterparty`, `reference`, and `notes` text;
- required `description`;
- non-negative exact decimal `amount`;
- nullable `dueDate`; and
- nullable paired `startDate` and `endDate`.

The supplemental-detail identity never uses `txn_supplemental_line.id`. Details are grouped by canonical transaction, sorted by persisted `lineOrder` and then by all exported business fields, and assigned a positive deterministic ordinal. Exact duplicate rows remain byte deterministic because their exported content is identical and only the sequential ordinal distinguishes them.

Every `transactionId` MUST resolve to exactly one exported canonical transaction. Both start and end dates are present or absent together, and start MUST NOT follow end. Negative amounts, unsupported kinds, duplicate identities, malformed fields, cross-company transactions, and details whose transaction is outside the selected snapshot are blocking export errors. Supplemental details contribute to export counts, and `SUPPLEMENTAL_DETAILS` is no longer reported as deferred.

### 8.4 Bank configuration extension

`extensions.scaJakartaH2.bankConfiguration` is an object with exactly `banks` and `accounts` arrays. It contains every selected-company bank and configured bank account, including inactive records needed to interpret history. Bank entries preserve durable `bankId`, name, routing/contact/address fields, notes, and active state. Configured-account entries preserve durable `bankAccountId`, optional bank and ledger-account references, labels, masking and last-four display data, opening date and balance, supported statement-import format, OFX bank/account identifiers, notes, and active state.

Bank and configured-account arrays are ordered by durable UUID identity. Every optional `bankId` and `ledgerAccountId` MUST resolve within the selected-company snapshot. Mutable institution names, configured-account labels, and masked numbers are presentation data only. Duplicate identities, cross-company ownership, inactive-chart ledger references, malformed fields, and unresolved references are blocking export errors. Banks and configured bank accounts contribute separately to export counts, and `BANK_CONFIGURATION` is no longer reported as deferred.

### 8.5 Reviewed bank-statement facts extension

`extensions.scaJakartaH2.bankStatementFacts` is an object with exactly four arrays: `importBatches`, `statementLines`, `issues`, and `transactionLineClearance`. Import batches preserve durable identity, optional configured-account reference, source filename and SHA-256 or equivalent persisted hash, source format, review status and timestamps, line/accept/reject/issue counts, and notes. The source filesystem path and importing-user identity are explicitly excluded.

Statement lines preserve durable identity and batch/account references plus the persisted source row, FITID or source transaction ID, deterministic fingerprint, statement account identifier, transaction and posted dates, exact amount, transaction type, name, memo, check/reference values, review status, disposition note, and optional accepted or matched canonical transaction references. Import issues preserve severity, code, message, source row, timestamps, and their batch/statement references. Transaction-line clearance entries preserve only non-default clearance facts: exported line identity, cleared flag/date, and optional reviewed statement-line reference.

Arrays are ordered by durable identity or exported transaction-line identity. Every reference MUST resolve within the selected-company export; `ACCEPTED` statement lines require an accepted transaction, `MATCHED` lines require a matched transaction, and each transaction line may have at most one clearance entry. Duplicate identities, cross-company facts, malformed status/format values, negative counts, omitted masters, and unresolved references are blocking export errors. Batches, statement lines, and issues contribute to entity counts; clearance entries are relationships rather than additional entities. `BANK_STATEMENT_FACTS` is no longer reported as deferred.

### 8.6 Reconciliation extension

`extensions.scaJakartaH2.reconciliation` is an object with exactly `sessions` and `matches` arrays. Sessions preserve durable identity, configured-account reference, statement date range and optional ending balance, mismatch policy, lifecycle status, notes, beginning/book/cleared/difference balances, and timestamps. Matches preserve durable identity, session reference, optional statement-line and transaction-line references, match status, resolution note, and timestamps. At least one statement-line or transaction-line reference is required for every match.

Sessions and matches are ordered by durable UUID identity. Date ranges, policies, statuses, ownership, and all references are strictly validated. Duplicate identities, cross-company sessions, matches outside an exported session, and references to omitted statement or transaction lines are blocking export errors. Sessions and matches contribute separately to export counts, and `RECONCILIATION` is no longer reported as deferred.

### 8.7 Fixed assets and completed depreciation extension

`extensions.scaJakartaH2.fixedAssets` is an object with exactly `assets` and `depreciationRuns` arrays. It contains every fixed asset owned by the selected company, including inactive and disposed records needed to interpret history, plus every completed depreciation run for those assets. It does not create future schedules or inferred depreciation entries.

Each asset entry contains exactly `assetId`, `assetAccountId`, `accumulatedDepreciationAccountId`, `depreciationExpenseAccountId`, `fundId`, `name`, `acquisitionDate`, `acquisitionCost`, `salvageValue`, `usefulLifeMonths`, `depreciationMethod`, `openingAccumulatedDepreciation`, `status`, nullable `notes`, `createdAt`, and `updatedAt`. Each depreciation-run entry contains exactly `depreciationRunId`, `assetId`, `runDate`, `depreciationAmount`, `transactionId`, nullable `notes`, and `createdAt`. Current book value and total accumulated depreciation remain derived from the persisted opening amount and completed runs; they are not serialized as a second accounting authority.

Asset and run arrays are ordered by their intrinsic UUID portable identities. Every account reference MUST resolve to the selected company's exported active chart, every fund reference MUST resolve to an exported company fund, every run MUST resolve to an exported fixed asset, and every run transaction MUST resolve to the canonical transaction created for that completed run. The run keeps its own identity; `transactionId` is accounting provenance and never substitutes for `depreciationRunId`. Local numeric IDs, mutable names, and content-derived ordinals are prohibited as portable identity.

Negative costs, salvage outside zero through acquisition cost, unsupported useful lives or methods, nonpositive run amounts, duplicate identities, cross-company records, and unresolved references are blocking export errors. Fixed assets and completed depreciation runs contribute separately to export counts, and `FIXED_ASSETS` is no longer reported as deferred.

### 8.8 Inventory extension

`extensions.scaJakartaH2.inventory` version 1 contains the selected company inventory items and factual movement history. Items and movements use intrinsic UUID portable identities, retain their authoritative account, fund, quantity, value, status, condition, transaction-provenance, timestamp, and notes fields, and are ordered by portable identity. Cross-company records and unresolved account, fund, item, or canonical transaction references are blocking export errors.

### 8.9 Period-close extension

`extensions.scaJakartaH2.periodClose` version 1 contains authoritative calculated or custom close ranges and their factual close/reopen events. Ranges and events use their intrinsic UUID identities namespaced by company. Range status, close and reopen actors/timestamps/reasons, event type, and event-to-range references are preserved. Legacy accounting-period and close-run compatibility records are not substituted for this authority.

### 8.10 Factual audit-history extension

`extensions.scaJakartaH2.auditHistory` version 1 contains every `AuditEvent` whose `company_id` is the selected company. It contains one `events` array. Each entry contains `auditEventId`, `occurredAt`, `actor`, `actionType`, `entityType`, optional `entityId`, `summary`, optional `beforeValue`, optional `afterValue`, and optional `reason`.

`auditEventId` is `audit-event:<company-code>:<portable-uuid>` using the intrinsic UUID added to `audit_event`; it never uses `audit_event.id`, a mutable summary, or the polymorphic `entityId`. Events are ordered by `occurredAt` and then portable UUID. The polymorphic entity type and identifier are preserved as factual subject text and are not rewritten into a reference to an unrelated SCLX object. This prevents a legacy local ID from being presented as a portable foreign key.

Application-global audit rows, unresolved historical rows with no company owner, legacy `ApprovalAuditRecord` workflow records, users, roles, authentication facts, and UI state are not included. Duplicate identities, blank required fields, unsupported extension fields, and any event owned by another company are blocking export errors. Audit events contribute to exact entity counts, and `AUDIT_HISTORY` is no longer reported as deferred.

## 9. Deterministic SCLX 1.3 output

For a fixed export request, fixed operation timestamp, and unchanged database state, output bytes MUST be identical.

The following are fixed:

- root field order;
- array order;
- money format;
- date and time zone;
- Unicode NFC normalization; and
- the `exportedAt` fixed once at operation start and supplied to the serializer.

A repeat initiated later normally has a different `exportedAt`; callers that require byte comparison MUST use the same explicit export context. The operation result MUST separately report the SHA-256 hash of the exact final bytes.

## 10. Atomic writing and overwrite protection

Export MUST:

1. validate the complete document before opening the destination;
2. reject a destination that is a directory, symlink escape, active database file, or otherwise prohibited path;
3. refuse to replace an existing file unless the user explicitly confirms overwrite;
4. write to a uniquely named temporary file in the destination directory;
5. flush and close the temporary file and request durable sync where supported;
6. compute SHA-256 from the bytes that will be committed;
7. atomically move the temporary file into place when supported, with a safe same-directory fallback; and
8. remove the temporary file after any failure.

A failed export MUST leave the pre-existing destination unchanged.

### 10.1 Production selected-company export route

The production File menu exposes **Export Active Company to SCLX…** only when an active database and selected company are available. The action uses the current `SclxFileExportService`; it does not serialize panel state, reuse the database-transfer service, or open a generic Import/Export Jobs surface.

The file chooser is labeled **SCLX Active Company Files** and normalizes the destination to `.sclx`. Replacing an existing file requires explicit confirmation that names the exact destination. The export runs away from the JavaFX application thread and the menu action remains disabled while it is running.

The completion result displays the selected company code, SCLX version, fixed operation timestamp, exact destination, byte count, SHA-256, included record counts, warning count, governed deferred sections, and explicit exclusions. Deferred sections remain visible warnings until their P15-S4 mapping is implemented; the UI MUST NOT imply that those records were exported.

### 10.2 Production non-mutating import-preview route

The production **Import Preview** workspace exposes **Preview SCLX…**. Its open chooser is labeled
**SCLX Active Company Files** and accepts `.sclx` and `.json` candidates, but recognition remains
content-based: the root format and supported version are authoritative.

The JavaFX action captures the selected target company before background work begins and calls
`SclxImportPreviewService` through the shell-owned composition root. Preview parses and validates the
bounded source, performs one read-only target-company query, and makes no H2 changes. The workspace
shows:

- exact entity counts by governed section plus total entities, references, relationships, and unsupported sections;
- every external identity disposition as `NEW`, `IDENTICAL`, or `CONFLICT`;
- every account and fund mapping with `AS_IS`, `MAPPED`, `CONFLICT`, or `UNRESOLVED` resolution;
- transaction posting-line, zero-value-line, balance, closed-period, and finalized-reconciliation diagnostics; and
- every warning and blocking error with its stable code and source path.

The status text names the source, SCLX version, explicit target company, recommended account mode,
new/identical/error totals, and whether the preview is blocked. It always states that no data was
changed. The existing COA commit action is disabled after an SCLX preview, and this slice exposes no
SCLX commit control. P15-S5-C2 adds a service-level core commit boundary but deliberately keeps the
button absent until every exported section has a governed canonical writer.

### 10.3 P15-S5-C2 core transactional-import boundary

`SclxImportCommitService` re-reads and re-previews the exact source immediately before commit. A
changed SHA-256, changed target company, blocking validation message, non-empty unsupported section,
or newly populated target prevents the operation from entering its transaction. The first core
boundary supports only:

- the source organization profile applied to the explicit existing target while retaining the
  target company code;
- the active Chart of Accounts metadata, accounts, and account hierarchy under `AS_IS` rules;
- funds and fund hierarchy under `AS_IS` rules; and
- balanced `ENTERED` canonical transactions whose nonzero lines resolve to those accounts and funds.

Accounts and funds are written parent-before-child. Canonical transactions are created through the
caller-owned transaction overload of `TransactionEntryService`, so command validation, ownership,
closed-period protection, signed split conversion, and factual transaction audit behavior remain in
the established accounting service. Zero-value source lines are not posted, but their identical
external identity may be recorded without a local row so a repeated import remains deterministic.

The organization, every created account/fund/transaction/posted line, and every deliberately skipped
zero line receive a source-specific `interchange_identity` in the same transaction. A successful
operation writes one company-owned `SCLX_CORE_IMPORTED` factual audit event containing the source,
version, SHA-256, and counts. An identical second import is a committed no-op and creates no duplicate
business rows, identities, or operation audit event.

This boundary rejects budgets, activities, counterparties, merchants, supplemental details, banking,
reconciliation, fixed assets, inventory, period-close facts, imported audit history, correction
relationships, and non-empty unknown sections. Those facts are not dropped or partially imported;
later P15-S5 slices must add their canonical writers and round-trip tests before the production SCLX
commit action is enabled.

### 10.4 P15-S5-C3 transaction-linked detail import boundary

P15-S5-C3 extends the same caller-owned transaction to the governed transaction-linked application
extensions. Before any H2 write, the commit service strictly validates the exact `activities`,
`counterparties`, and `supplementalDetails` shapes, including all activity/counterparty/merchant
references, one merchant per transaction line, one canonical header counterparty per transaction,
and supplemental-detail transaction references and semantics.

The import creates company-owned activities, counterparties, and merchants before canonical
transactions. Counterparty and merchant intrinsic UUIDs are recovered from their governed portable
identities when possible, with deterministic UUID fallback for older compatible identities. The
standard repeated transaction-line `counterpartyId` values resolve to the canonical transaction
header payee; line activity and merchant references flow through `TransactionLineCommand`.
Supplemental details flow through `TransactionSupplementalLineCommand`, including their persisted
non-negative `lineOrder`, and remain owned by `TransactionEntryService` inside the import transaction.

Every activity, counterparty, merchant, and supplemental detail receives a source-specific
`interchange_identity` in the same transaction. A successful operation writes one company-owned
`SCLX_TRANSACTION_DETAILS_IMPORTED` event. Identical reimport remains a no-op. A skipped zero-value
line is rejected if it carries activity, counterparty, or merchant facts because those relationships
cannot be preserved without a canonical posting line.

C3 still rejects budgets, banking and reconciliation, fixed assets and depreciation, inventory,
period-close facts, imported audit history, correction relationships, populated unknown sections,
and populated target-company merge. The JavaFX SCLX commit action remains absent.

### 10.5 P15-S5-C4 budget import boundary

P15-S5-C4 extends the same caller-owned transaction to the governed standard `budgets` section.
Before mutation, `SclxBudgetImportData` validates plan and line identities, required fields, four-digit
fiscal years, one active version per fiscal year, category/fund/month scope uniqueness, fund
references, calendar-year period months, and exact `DECIMAL(19,4)` amounts. A non-null budget-line
`accountId` is blocking because the normalized `BudgetLine` authority has no account relationship and
the deterministic application exporter deliberately leaves that field absent.

SCLX carries `categoryCode` on a budget line but no separate portable budget-category master or
category display name. For a required empty target, the importer therefore creates one company-owned
`BudgetCategory` for each referenced code and initially uses that same code as its display name. It
does not infer a category name from an account, activity, or another mutable label.

Budget categories and plans are created through caller-owned overloads on
`BudgetCategoryAdminService` and `BudgetPlanService`. Plans preserve name, fiscal year, version, and
active state. Lines preserve category code, optional fund, optional period month, and exact amount.
Inactive source plans become editable `DRAFT` plans because the standard SCLX boolean does not
distinguish local draft from archived state. The local required plan date range is the source fiscal
calendar year; those support fields are not presented as additional portable SCLX facts.

Every budget plan and line receives a source-specific `interchange_identity` in the same transaction.
Supporting category rows are canonical local master data rather than fabricated SCLX entities and do
not receive a source identity. A successful operation writes one company-owned
`SCLX_BUDGETS_IMPORTED` event, and an identical reimport remains a no-op.

C4 still rejects banking and reconciliation, fixed assets and depreciation, inventory, period-close
facts, imported audit history, correction relationships, populated unknown sections, and populated
target-company merge. The JavaFX SCLX commit action remains absent.

### 10.6 P15-S5-C5 fixed-asset import boundary

P15-S5-C5 extends the same caller-owned transaction to
`extensions.scaJakartaH2.fixedAssets` version 1. Before mutation, the importer strictly validates the
asset and completed-run shapes, identities, account/fund/transaction references, supported status and
depreciation values, nonnegative `DECIMAL(19,4)` asset amounts, positive run amounts, timestamps, and
one completed run per asset and run date.

Assets are recreated through the caller-owned `FixedAssetService` boundary after accounts and funds
exist. Their intrinsic portable UUID, source creation/update timestamps, name, acquisition facts,
method, opening accumulated depreciation, status, notes, account references, and fund reference are
preserved. Completed runs are written only after canonical source transactions exist and preserve
their own portable UUID, asset, run date, amount, transaction provenance, notes, and creation time.
The importer never calculates a new depreciation amount or creates another ledger transaction for an
already-completed source run.

Every imported asset and completed run receives a same-transaction `interchange_identity`. A
successful operation writes one company-owned `SCLX_FIXED_ASSETS_IMPORTED` event, and an identical
reimport remains a no-op. C5 still rejects banking and reconciliation, inventory, period-close facts,
imported audit history, correction relationships, populated unknown sections, and populated-target
merge. The JavaFX SCLX commit action remains absent.

### 10.7 P15-S5-C6 inventory import boundary

P15-S5-C6 extends the caller-owned transaction to `extensions.scaJakartaH2.inventory` version 1. Before mutation, the importer strictly validates item and movement shapes, identities, enum values, exact decimals, timestamps, account/fund/item references, and optional canonical transaction provenance.

Items are recreated through `InventoryService` after their account and fund dependencies exist. Movement history is then written through the same caller-owned service boundary after items and canonical source transactions exist. The importer preserves intrinsic UUIDs, source timestamps, factual quantities and values, item lifecycle fields, movement types, notes, and transaction provenance. It does not synthesize an initial receipt, recompute the source movement history, or create another canonical transaction.

Every item and movement receives a same-transaction `interchange_identity`. A successful operation writes one company-owned `SCLX_INVENTORY_IMPORTED` event, and an identical reimport remains a no-op. C6 still rejects banking and reconciliation, period-close facts, imported audit history, correction relationships, populated unknown sections, and populated-target merge. The JavaFX SCLX commit action remains absent.

### 10.8 P15-S5-C7 banking and reconciliation import boundary

P15-S5-C7 extends the same caller-owned transaction to
`extensions.scaJakartaH2.bankConfiguration`, `bankStatementFacts`, and `reconciliation`. Before any
mutation, `SclxBankingImportData` validates the exact extension shapes, durable identities, enums,
dates, timestamps, `DECIMAL(19,4)` values, source counts, status-required transaction links, and every
reference between configured accounts, import batches, statement lines, issues, canonical
transactions/splits, reconciliation sessions, and matches.

Banks and configured accounts are recreated through caller-owned `BankConfigurationService` seams
after their chart accounts exist. Reviewed batches, statement lines, and issues are then recreated
through `BankImportReviewService` without normalizing the already-reviewed source or creating another
ledger transaction. Source-machine path and importing-user values remain excluded, while the governed
source name/hash/format, review dispositions, counts, intrinsic UUIDs, and timestamps are preserved.

Transaction-line cleared state is restored through `BankClearedStateService` only after canonical
transactions and statement facts exist. Native reconciliation sessions and matches are written last
through `BankReconciliationWorkspaceService`, preserving their portable UUIDs, statement range,
policy, status, balance snapshot, resolution facts, and timestamps without recalculating or reopening
a finalized source session.

Every bank, configured account, import batch, statement line, issue, reconciliation session, and
match receives a same-transaction `interchange_identity`. A successful operation writes one
company-owned `SCLX_BANKING_RECONCILIATION_IMPORTED` event, and an identical reimport remains a no-op.
C7 still rejects period-close facts, imported audit history, correction relationships, populated
unknown sections, and populated-target merge. The JavaFX SCLX commit action remains absent.

## 11. Import transaction boundary and results

Preview and validation MUST make no H2 changes. Commit MUST use one caller-owned transaction for the documented import boundary and route financial records through canonical services. A late failure MUST roll back all records in that boundary.

For P15-S5-C2, rollback includes target profile changes, Chart of Accounts metadata, accounts, funds,
canonical transactions and splits, transaction audit events, interchange identities, and the single
operation audit event. The service returns an explicit rolled-back result with zero created/updated
counts and a blocking `SCLX_COMMIT_ROLLED_BACK` message.

For P15-S5-C3, the same rollback boundary additionally includes activities, counterparties,
merchants, supplemental transaction rows, their relationships, and their interchange identities.

For P15-S5-C4, the same rollback boundary additionally includes created budget categories, normalized
budget plans and lines, their interchange identities, and the C4 operation audit event.

For P15-S5-C5, the same rollback boundary additionally includes fixed assets, completed depreciation
runs, their intrinsic portable metadata and interchange identities, and the C5 operation audit event.

For P15-S5-C6, the same rollback boundary additionally includes inventory items, movement history,
their intrinsic portable metadata and interchange identities, and the C6 operation audit event.

For P15-S5-C7, the same rollback boundary additionally includes banks, configured bank accounts,
reviewed statement batches/lines/issues, transaction-line cleared state, reconciliation sessions and
matches, their intrinsic portable metadata and interchange identities, and the C7 operation audit
event.

The result MUST report at least:

- source name, detected format/version, byte count, and SHA-256;
- target database and company identity without exposing credentials;
- account mode and mappings;
- counts read, accepted, created, updated, skipped-identical, skipped-zero, skipped-nonposting, generated, duplicate, warning, error, unsupported, and excluded;
- generated balancing lines;
- unresolved references and conflicts; and
- final commit or rollback state.

## 12. Security and resource limits

The default hard limits are:

| Limit | Value |
|---|---:|
| File size | 256 MiB |
| JSON nesting depth | 32 |
| JSON number length | 128 characters |
| JSON string length | 4 MiB per value |
| Total entities | 1,000,000 |
| Transactions | 250,000 |
| Transaction lines | 1,000,000 |
| Accounts or funds | 100,000 each |
| Portable identity length | 160 Unicode code points |
| General text field | 1 MiB unless the DTO defines less |

Input MUST be strict UTF-8. A UTF-8 BOM MAY be stripped with a warning; UTF-16, UTF-32, invalid byte sequences, and replacement-character decoding are rejected.

Money MUST be finite decimal text, have at most four fractional digits for transactional facts, and fit H2 `DECIMAL(19,4)` (`d-999999999999999.9999` through `999999999999999.9999`). Account opening balances additionally MUST fit the current `DECIMAL(19,2)` column unless a later migration changes that authority. NaN, infinity, exponent overflow, and silent rounding are prohibited.

Business dates MUST be valid ISO local dates. Instants MUST include an offset and are normalized to UTC. Dates outside `1900-01-01` through `9999-12-31` are rejected.

Malformed JSON, duplicate required object keys, unsupported versions, duplicate identifiers, missing references, hierarchy cycles, and limit violations are blocking errors.

## 13. Difference from other exchange types

- **Whole-database transfer** preserves every company and database record, including application administration and compatibility schema. SCLX carries selected company business data only.
- **Chart of Accounts JSON** carries chart structure only and never transaction history.
- **Bank-statement interchange** carries external statement activity for durable review and never represents a double-entry ledger.

The four entry points, DTO families, previews, confirmations, and result labels MUST remain visibly distinct.

## 14. Current deterministic file-export API

P15-S4 exposes selected-company file export through `SclxFileExportService` and an immutable
`SclxExportRequest`. The request fixes the destination, operation timestamp, and explicit overwrite
confirmation before snapshot reconstruction begins. `SclxJsonSerializer` validates the complete DTO
and emits the governed root/member order, stable entity order, UTF-8/LF/two-space formatting, ISO
dates and instants, and plain-string decimal values. Application extension map keys are sorted and
unsupported extension value types are rejected rather than serialized through reflection.

The file service rejects directories, symbolic-link traversal, active H2 database files, non-regular
targets, and an existing destination without explicit overwrite confirmation. It writes and forces a
uniquely named same-directory temporary file, computes SHA-256 from the exact final bytes, uses an
atomic replacing move when available, restores the previous destination if the safe fallback fails,
and removes temporary artifacts after failure.

`SclxExportResult` reports the final destination, format/version, fixed export timestamp, portable
organization identity, byte count, SHA-256, exact governed entity counts, deferred-extension warnings, and the
governed explicit-exclusion section list. Deferred extension sections are reported as warnings until
their selected-company snapshot mappings are implemented; policy exclusions are reported separately
and are not silently treated as exported empty sections.

## 15. Production selected-company export route

The production File menu exposes **Export Active Company to SCLX…**. The save chooser is labeled
**SCLX Active Company Files** and proposes a company-scoped `.sclx` filename. This route is distinct
from whole-database backup, Chart of Accounts JSON, and bank-statement exchange.

The UI fixes the selected company code, active database path, destination, operation timestamp, and
overwrite decision before the background task begins. A later company or database selection cannot
silently retarget that operation; the fixed-scope operation either completes for the captured scope or
fails without committing the destination. Existing files require explicit replacement confirmation.

The action is unavailable without an active database and nonblank selected company and remains disabled
while its export is running. Completion presents the exact company, SCLX version, timestamp, destination,
byte count, SHA-256, included record counts, validation messages, deferred governed sections, and explicit
exclusions. Deferred sections remain visible warnings and are not represented as implemented empty data.
