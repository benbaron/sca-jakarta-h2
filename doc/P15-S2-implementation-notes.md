# P15-S2 implementation notes

This branch implements the initial whole-database transfer service slice:

- H2 `BACKUP TO` through a live connection;
- restore only into a new target path;
- migration and Hibernate validation before final placement;
- active-database overwrite protection;
- checksum, byte count, and company/transaction count results;
- explicit guarded switching only after successful validation;
- focused round-trip, overwrite, and corrupt-input tests.

The service reuses `DatabaseMigrationService` and `Jpa`; it does not create another database selector or persistence bootstrap path.
