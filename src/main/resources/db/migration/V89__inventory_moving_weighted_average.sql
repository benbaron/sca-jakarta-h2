ALTER TABLE inventory_item ADD COLUMN IF NOT EXISTS carrying_value DECIMAL(19,4);
ALTER TABLE inventory_item ADD COLUMN IF NOT EXISTS valuation_method VARCHAR(40);

UPDATE inventory_item
SET carrying_value = ROUND(quantity * unit_value, 4)
WHERE carrying_value IS NULL;
UPDATE inventory_item
SET valuation_method = 'MOVING_WEIGHTED_AVERAGE'
WHERE valuation_method IS NULL;
UPDATE inventory_item SET unit_value = 0 WHERE quantity = 0;

ALTER TABLE inventory_item ALTER COLUMN carrying_value SET NOT NULL;
ALTER TABLE inventory_item ALTER COLUMN valuation_method SET NOT NULL;
ALTER TABLE inventory_item ADD CONSTRAINT IF NOT EXISTS ck_inventory_item_carrying_value
    CHECK (carrying_value >= 0 AND (quantity <> 0 OR carrying_value = 0));
ALTER TABLE inventory_item ADD CONSTRAINT IF NOT EXISTS ck_inventory_item_valuation_method
    CHECK (valuation_method = 'MOVING_WEIGHTED_AVERAGE');

ALTER TABLE inventory_movement ADD COLUMN IF NOT EXISTS value_change DECIMAL(19,4);
ALTER TABLE inventory_movement ADD COLUMN IF NOT EXISTS resulting_carrying_value DECIMAL(19,4);

UPDATE inventory_movement
SET value_change = ROUND(quantity_change * unit_value, 4)
WHERE value_change IS NULL;
UPDATE inventory_movement
SET resulting_carrying_value = ROUND(resulting_quantity * unit_value, 4)
WHERE resulting_carrying_value IS NULL;

ALTER TABLE inventory_movement ALTER COLUMN value_change SET NOT NULL;
ALTER TABLE inventory_movement ALTER COLUMN resulting_carrying_value SET NOT NULL;
ALTER TABLE inventory_movement ADD CONSTRAINT IF NOT EXISTS ck_inventory_movement_resulting_carrying_value
    CHECK (resulting_carrying_value >= 0
        AND (resulting_quantity <> 0 OR resulting_carrying_value = 0));
