# Period-close SCLX extension

Selected-company SCLX 1.3 exports authoritative close ranges and their factual close/reopen events under `extensions.scaJakartaH2.periodClose`.

The extension is versioned independently with `version: 1` and contains `ranges` and `events` arrays. Range and event identities derive from the durable UUID primary keys already stored in `period_close_range` and `period_close_event`; local numeric database identifiers are never exported.

Every range preserves start/end dates, calculated/custom kind, current closed/reopened status, close actor/time/reason, and nullable reopen actor/time/reason. Every event preserves the referenced range, event type, actor, nullable reason, and event timestamp. Events must reference a range in the same selected-company snapshot.

Rows for other companies are rejected. Ordering is deterministic by range dates/UUID and event timestamp/UUID. The extension contains factual close history only; it does not export users, authentication state, or generic approval workflow artifacts.

P15-S5-C8 imports the same version-1 facts through a caller-owned `PeriodCloseRangeService` boundary inside the complete SCLX transaction. Import preserves the source UUIDs, dates, status, actors, reasons, and timestamps. It restores factual rows directly rather than replaying interactive Close/Reopen commands, so organization policy is not re-evaluated and duplicate `AuditEvent` rows are not manufactured.

Validation occurs before mutation. Every range must have exactly one matching `CLOSED` event; a `REOPENED` range must also have exactly one matching `REOPENED` event. Event actor, reason, and timestamp must equal the corresponding range facts, active closed ranges may not overlap, and all event references must resolve within the imported snapshot. Existing period-close rows make the target populated and block import. Each imported range/event receives a same-transaction `interchange_identity`; identical reimport is a no-op, and any later failure rolls the entire company graph back.

Imported factual `AuditEvent` history and transaction correction relationships remain later SCLX slices. The production JavaFX commit action therefore remains absent after C8.
