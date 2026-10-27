-- A transaction mutex, never an accounting balance or readiness cache.
CREATE TABLE IF NOT EXISTS accounting_write_guard
(
    id INTEGER PRIMARY KEY CHECK (id = 1)
);
INSERT INTO accounting_write_guard (id)
SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM accounting_write_guard);

-- Install BEFORE statement triggers on the existing application tables.
-- The installer alias is removed; application commands cannot invoke a recovery shortcut.
CREATE ALIAS IF NOT EXISTS install_accounting_write_guard
FOR 'org.nonprofitbookkeeping.persistence.AccountingWriteGuardTrigger.install';
CALL install_accounting_write_guard();
DROP ALIAS install_accounting_write_guard;
