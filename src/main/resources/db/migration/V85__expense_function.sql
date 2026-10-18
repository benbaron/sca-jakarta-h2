-- Deliberately nullable: historical and unspecified purposes remain Unclassified.
ALTER TABLE txn_split ADD COLUMN IF NOT EXISTS expense_function VARCHAR(2);
ALTER TABLE txn_split ADD CONSTRAINT IF NOT EXISTS ck_txn_split_expense_function
    CHECK (expense_function IS NULL OR expense_function IN ('AR', 'OA', 'FR'));
