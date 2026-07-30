# P15-S4-C3 inventory export corrective slice

PR #222 merged only the governed inventory SCLX contract. This corrective slice publishes the production mapping, query, validation, counts, serializer integration, user-visible completion counts, and focused tests required to implement that contract.

## Current state

- Branch: `codex/P15-S4-C3-inventory-sclx-export`
- Pull request: #223
- Base: merged PR #222 at `3e8c7c8220a8cfe20ee697d835e28d4cc69e4092`
- The SHA-256-verified publication workflow succeeded and removed all temporary payload and workflow files.
- Maven PR Tests run `30511334865` compiled all 398 production sources but found two stale fixed-asset fixtures using the pre-inventory assembler signature.
- Those fixtures were corrected to pass explicit empty inventory-item and inventory-movement lists.
- Maven PR Tests run `30511454947` compiled production and tests and ran 462 tests; 461 passed, with one inventory-extension error caused by exact-key validation rejecting omitted governed optional fields.
- The shared extension reader now rejects unknown keys while allowing optional governed keys to be omitted; required fields remain enforced by typed readers.
- `doc/PLAN.md` is reconciled through PR #223 on clean bot-authored head `bae0bbe74b2b6d856d7547aebaeb38ee642ab23c`.
- This normal repository commit triggers authoritative plan-inclusive Maven PR validation before review.
