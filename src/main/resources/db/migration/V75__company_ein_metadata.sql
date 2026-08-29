ALTER TABLE company ADD COLUMN IF NOT EXISTS ein VARCHAR(40);

UPDATE company c
SET ein = (
    SELECT NULLIF(TRIM(t.ein), '')
    FROM company_tax_profile t
    WHERE t.company_id = c.id
)
WHERE (c.ein IS NULL OR TRIM(c.ein) = '')
  AND EXISTS (
      SELECT 1
      FROM company_tax_profile t
      WHERE t.company_id = c.id
        AND t.ein IS NOT NULL
        AND TRIM(t.ein) <> ''
  );
