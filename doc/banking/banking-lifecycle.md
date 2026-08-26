# Banking durable-record lifecycle

Status: P17-C4 governing lifecycle contract for `Bank` and `CompanyBankAccount` records.

## Durable identity

- `Bank.id` is the durable identity of one financial institution record for one company.
- `CompanyBankAccount.id` is the durable identity of one configured bank-account record for one company.
- Editing either record must preserve its database identity, portable identity where present, creation timestamp, and historical references.
- Name, nickname, routing/contact metadata, linked qualifying Chart account, import configuration, notes, and active state are business fields, not record identity.

## Lifecycle instead of hard deletion

Banking does not expose physical Delete for either record family. Historical statement import, reconciliation, ledger, SCLX, audit, and configuration references must remain resolvable. The supported retirement operation is deactivation through the existing Active fields.

The UI must explain this lifecycle through visible status/help text and must not add a disabled placeholder Delete control.

## Parent/child active-state invariant

An active configured bank account requires an active Bank. Therefore:

1. A Bank cannot be deactivated while any active `CompanyBankAccount` references it.
2. An active configured bank account cannot be created under an inactive Bank.
3. An inactive configured bank account cannot be reactivated while its Bank is inactive.
4. After all configured bank accounts for a Bank are inactive, the Bank may be deactivated.
5. Deactivation never physically removes either record or its historical relationships.

The authoritative write service enforces these rules. Interactive JavaFX validation is not a substitute for service validation.

## Serialization

Bank lifecycle changes and configured-account lifecycle changes serialize through a pessimistic write lock on the owning Company row before the parent/child invariant is evaluated. This prevents a concurrent Bank-deactivation/account-reactivation race from committing an invalid active account beneath an inactive Bank.

## Historical visibility

Inactive Banks and configured bank accounts remain returned by Banking maintenance queries. Existing historical ledger activity remains visible for inactive configured accounts. New statement/reconciliation operations may continue to apply their own existing active-account eligibility rules; P17-C4 does not weaken those rules or redefine ledger authority.

## Non-goals

P17-C4 does not:

- physically delete Bank or configured bank-account records;
- delete or declassify linked Chart accounts;
- change bank-statement import, reviewed-row acceptance, cleared-state, reconciliation, or ledger authority;
- introduce a second bank-account model or persistence path;
- reinterpret `ASSET / BANK-function / DEBIT` eligibility or `CASH` presentation semantics.
