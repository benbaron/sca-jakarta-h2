# P15-S4-C3 inventory export corrective slice

PR #222 merged only the governed inventory SCLX contract. This corrective slice publishes the production mapping, query, validation, counts, serializer integration, user-visible completion counts, and focused tests required to implement that contract.

## Current state

- Branch: `codex/P15-S4-C3-inventory-sclx-export`
- Pull request: #223
- Base: merged PR #222 at `3e8c7c8220a8cfe20ee697d835e28d4cc69e4092`
- The SHA-256-verified publication workflow succeeded and removed all temporary payload and workflow files.
- Maven PR Tests run `30511334865` compiled all 398 production sources but found two stale fixed-asset fixtures using the pre-inventory assembler signature.
- Those two fixtures now pass explicit empty inventory-item and inventory-movement lists on corrected head `749a70f88ef74a3f295d583bdafdf2374b2db430`.
- Authoritative Maven PR validation is required on the resulting normal repository commit before review.
