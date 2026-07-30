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
- Maven PR Tests run `30512885063` passed `mvn clean verify`, the repeated Maven test suite, and JavaFX production-route compliance on exact head `8a7eec251d4bba14c3d7cc0e167e2e2bebfdfe47`.
- `doc/PLAN.md` now records the successful run and next governed action on clean bot-authored head `810b1e652fdb097f12ce59c2205940badb0d2600`.
- This normal repository commit triggers the final plan-inclusive Maven PR validation before review.
