# Fund lifecycle and hierarchy contract

## Authority and identity

`Fund.id` is the durable H2 identity. Code, name, type, parent, effective dates, restriction/purpose text, and Active state are editable business fields; changing them does not create a replacement Fund row.

`FundAdminService` is the interactive write authority. `FundLookupService` is the company-scoped read authority. Transactions, budgets, fixed assets, inventory, aliases, and transfers continue to reference the same retained Fund identity.

## Retirement versus deletion

A referenced Fund is retired by setting **Active** off. Deactivation preserves the Fund row and all historical references. The existing **Delete Unused** operation is a real physical delete, but only when `FundUsage` proves there are no transaction, budget, asset, inventory, alias, transfer, or child-Fund references.

There is no placeholder Delete operation. If a Fund is referenced, the UI explains that deactivation is the supported lifecycle action.

## Hierarchy invariant

An active Fund must have an active parent hierarchy.

- Creating, reactivating, or reparenting an active Fund beneath an inactive parent is rejected.
- A parent Fund cannot be deactivated while any direct child Fund remains active.
- Retirement therefore proceeds child-first (or by reparenting active children).
- Reactivation proceeds parent-first.
- Inactive children beneath inactive parents are valid retained history.
- Existing self-parent and circular-parent protections remain mandatory.

Interactive hierarchy writes and protected deletion serialize through a pessimistic lock on the owning Company so the check and mutation cannot race another Fund hierarchy change.

For a legacy database that already contains an invalid active child beneath an inactive or circular parent hierarchy, `FundLookupService.listActiveFunds()` fails closed and omits that child from production active-Fund selectors. `listAllFunds()` still returns the retained row so the **Funds** maintenance panel can repair or retire it without losing history.

## SCLX boundary

SCLX does not bypass this lifecycle contract.

- Structure validation resolves `parentFundId`, rejects circular Fund hierarchies, and rejects an active source Fund beneath an inactive source ancestor before commit.
- Export validation refuses to serialize a Fund graph that violates the same hierarchy rule.
- Fund creation during SCLX commit takes the same Company write lock used by interactive Fund administration and rechecks active parent ancestry before persisting.

This does not reinterpret source inactive Funds, synthesize lifecycle audit facts, or create a second Fund store. Existing-company mappings that reuse compatible target Funds remain governed by the normal SCLX mapping/identity contract.

## Non-goals

P17-C6 does not change Fund accounting semantics, transaction posting, budget calculations, report grouping, transfer accounting, Fund type definitions, or effective-date policy. It adds no schema migration and no parallel persistence path.
