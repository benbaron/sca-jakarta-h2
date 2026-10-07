# Payee / Counterparty maintenance

P24-S4B extends the existing Counterparty authority; there is no separate Payee master. Stable database and portable IDs survive edits and deactivation. The transaction header retains its Payee relationship; supplemental counterparty narrative remains free text.

Maintenance lists only the active company, including inactive rows. Create/edit requires BOOKKEEPING_WRITE at the service boundary. Save locks the owning company and existing party, validates company ownership, name (required, at most 200 characters), kind (required), email (at most 200) and phone (at most 40), and persists an authenticated before/after audit in the same transaction. Names are compared case-insensitively after trimming, including inactive rows. A different company's same name is allowed; imported historical duplicates remain readable and must be given distinct names before saving. A failed save changes neither master nor audit.

Records are retained instead of physically deleted to preserve transaction, contact, audit and portable identity history. Clear Active and save to remove a party from new Journal choices. Existing inactive references remain readable and retain their identity during refresh and editing. Renaming does not rewrite supplemental narrative. No migration or SCLX format/import/export policy change is required.

The production Payees / Counterparties screen provides New, Edit Selected, Save, Refresh, name/contact search, contact/kind editing, active/inactive status and Return to Journal. Searches and choice refresh preserve unsaved drafts. New or selecting a different row asks before discarding edits. The screen uses resizable split regions, scrolling and company-owned table/divider state. Journal exposes a maintenance route and refreshes choices on return without resetting its entry.

Acceptance: [S4B user testing](../P24-S4B-user-testing.md). Merchant maintenance remains S4C.
