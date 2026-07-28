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

Canonical transactions use `Txn.portableId`, and counterparties and merchants use their own durable UUID `portableId` values, all assigned independently of local numeric primary keys. Existing rows receive identities during the nondestructive migration sequence, and new rows receive them at creation. SCLX identities namespace those UUIDs by company. Names remain mutable presentation data and are never used as the durable identity for counterparties or merchants. Budget plans use company code, fiscal year, and version code; budget lines use their plan identity plus category code, optional fund identity, and optional period month.

The current deterministic snapshot writes every company-owned budget plan and line. Budget lines preserve `categoryCode`, optional fund reference, optional `periodMonth`, and exact `BigDecimal` amount; the current normalized budget model has no direct account relation, so `accountId` remains absent rather than being inferred. Canonical transactions preserve status, deterministic debit/credit lines, and explicit `REVERSAL` or `REPLACEMENT` correction relationships. Transaction-line ordinals are assigned only after sorting by stable business content and never by a serialized database identifier. Company activities use company code plus activity code as their portable identity, and transaction-line `activityId` values resolve to the exported activity extension. Counterparty and merchant references remain unset until their portable master section is implemented, avoiding unresolved or locally keyed references.

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

[extensions.scaJakartaFinance.activities] is an array containing every activity owned by the selected company, including inactive activities needed for historical interpretation. Entries are ordered by activity code and contain exactly:

- `activityId`: `activity:<company-code>:<activity-code>` using the governed portable-identity encoding;
- `code`: the company-scoped activity code;
- `name`: the activity display name; and
- `active`: the persisted active/inactive state.

A canonical transaction line with an activity writes `activityId` in the standard transaction-line DTO. Every nonblank `activityId` MUST resolve to exactly one entry in this extension. Duplicate activity identities, cross-company activities, malformed entries, and unresolved references are blocking export errors. Activity records contribute to the operation entity counts, and the `ACTIVITIES` section is no longer reported as deferred.

## 9. Deterministic SCLX 1.3 output

For a fixed export request, fixed operation timestamp, and unchanged database state, output bytes MUST be identical.

The following are fixed:

- root field order;
- array order;
- money format;
- date and time zone;
- UNFORMATION_SPACE normalization; and
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

## 11. Import transaction boundary and results

Preview and validation MUST make no H2 changes. Commit MUST use one caller-owned transaction for the documented import boundary and route financial records through canonical services. A late failure MUST roll back all records in that boundary.

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
organization identity, byte count, SHA-256, core and activity entity counts, deferred-extension warnings, and the
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
