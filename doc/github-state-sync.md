# GitHub state and local archive resync

Observed 2026-10-29 (America/Denver). Refetched all remote branches and all 397 `refs/pull/*/head` objects into local remote-tracking archive refs. Local task branches and unpublished evidence were preserved. Scanned the 30 most recently updated PRs (#368–#397) and the complete open-PR listing; #397 is the latest numbered PR. This is a repository/ref and recent-history rescan, not a claim that every historical CI log was reread.

Current main is `f8206e45a1f1f245956c6447db7c0c2625d8227c`, merging [#397](https://github.com/benbaron/sca-jakarta-h2/pull/397), accepted head `c852a10a5a913b6b548d8ea426d6ea0e19a2c681`. Independently checked [workflow 40809782195](https://github.com/benbaron/sca-jakarta-h2/actions/runs/40809782195), job `123237359004`: clean verification, repeated tests and production JavaFX succeeded. Its implementation supersedes this conversation's older #396 S4D1 handoff. No native desktop acceptance is inferred from CI or merge.

Only [#396](https://github.com/benbaron/sca-jakarta-h2/pull/396) remains open, as a draft, at `fd3b6a994551e90e09896cd0cdde1adec80728d5`. It overlaps merged S4D1; its description's `5224626c` result is historical and does not certify its newer head. Workflow `40992759119` was in progress when scanned. The rescan does not close or merge it. Do not use it as the next implementation base.

PRs #368–#395 and #397 are merged. Current main, not the old local task branch or open overlapping draft, owns production. [PLAN](PLAN.md) now closes S4D1 through #397 and selects S4D2 category-master portability on a fresh branch. S4D3 integrated compatibility/recovery remains required; P28 stays blocked.
