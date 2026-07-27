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

Canonical transactions use `Txn.portableId`, a durable UUID assigned independently of the local numeric primary key. Existing rows receive one during the nondestructive ownership/interchange migration sequence, and new rows receive one at creation. SCLX transaction identities namespace that UUID by company. Budget plans use company code, fiscal year, and version code; budget lines use their plan identity plus category code, optional fund identity, and optional period month.

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

Compatibility behavior for donor documents is:

- a zero-value source line is skipped with a warning and count;
- a transaction with no remaining posting lines is skipped with a warning and count;
- a balanced transaction is imported without generated lines; and
- a single-sided or otherwise unbalanced transaction is never silently accepted.

To import an incomplete transaction, the user MUST explicitly select one active posting cash/bank account owned by the target company. Preview MUST display each generated balancing line, including source transaction identity, target account, fund, signed amount, and explanation. Commit requires explicit confirmation of those lines. If no valid cash account is selected, or the generated line would violate a closed period or reconciliation protection, the transaction is blocking.

Generated lines are ordinary visible canonical `TxnSplit` lines after commit. They MUST NOT be hidden metadata.

## 8. Extensions

Application-specific output uses only the `extensions.scaJakartaH2` namespace. Extension members MUST be versioned, bounded, deterministic, and documented before they are emitted.

Readers MAY preserve unknown extension namespaces for a same-operation round trip when doing so is safe and bounded. Unknown extensions MUST NOT affect accounting behavior. Executable code, external URLs to fetch, path traversal material, serialized Java objects, and embedded database content are prohibited.

Standard SCLX fields MUST be used when they can faithfully express the fact. Extensions MUST NOT duplicate or override standard fields.

## 9. Deterministic SCLX 1.3 output

For a fixed export request, fixed operation timestamp, and unchanged database state, output bytes MUST be identical.

Determinism requires:

- UTF-8 without BOM;
- LF line endings;
- two-space JSON indentation;
- the root property order frozen by the governed 1.3 DTO;
- entity arrays ordered by entity type, stable portable identity, date where applicable, and stable child identity;
- object members in documented DTO order;
- decimal values emitted as plain decimal strings with no exponent and no unnecessary negative zero;
- dates as ISO `YYYY-MM-DD` and instants as UTC RFC 3339 with `Z`;
- no local database ID, hash-map iteration order, locale formatting, or platform path in output; and
- `exportedAt` fixed once at operation start and supplied to the serializer.

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

Money MUST be finite decimal text, have at most four fractional digits for transactional facts, and fit H2 `DECIMAL(19,4)` (`-999999999999999.9999` through `999999999999999.9999`). Account opening balances additionally MUST fit the current `DECIMAL(19,2)` column unless a later migration changes that authority. NaN, infinity, exponent overflow, and silent rounding are prohibited.

Business dates MUST be valid ISO local dates. Instants MUST include an offset and are normalized to UTC. Dates outside `1900-01-01` through `9999-12-31` are rejected.

Malformed JSON, duplicate required object keys, unsupported versions, duplicate identifiers, missing references, hierarchy cycles, and limit violations are blocking errors.

## 13. Difference from other exchange types

- **Whole-database transfer** preserves every company and database record, including application administration and compatibility schema. SCLX carries selected company business data only.
- **Chart of Accounts JSON** carries chart structure only and never transaction history.
- **Bank-statement interchange** carries external statement activity for durable review and never represents a double-entry ledger.

The four entry points, DTO families, previews, confirmations, and result labels MUST remain visibly distinct.
