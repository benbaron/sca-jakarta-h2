# Period-close SCLX extension

Selected-company SCLX 1.3 exports authoritative close ranges and their factual close/reopen events under `extensions.scaJakartaH2.periodClose`.

The extension is versioned independently with `version: 1` and contains `ranges` and `events` arrays. Range and event identities derive from the durable UUID primary keys already stored in `period_close_range` and `period_close_event`; local numeric database identifiers are never exported.

Every range preserves start/end dates, calculated/custom kind, current closed/reopened status, close actor/time/reason, and nullable reopen actor/time/reason. Every event preserves the referenced range, event type, actor, nullable reason, and event timestamp. Events must reference a range in the same selected-company snapshot.

Rows for other companies are rejected. Ordering is deterministic by range dates/UUID and event timestamp/UUID. The extension contains factual close history only; it does not export users, authentication state, or generic approval workflow artifacts.
