ALTER TABLE inventory_items
    ADD COLUMN unit_cost numeric(14, 2),
    ADD COLUMN supplier_name varchar(160),
    ADD COLUMN store_name varchar(160);

ALTER TABLE inventory_items
    DROP CONSTRAINT IF EXISTS inventory_items_tenant_id_sku_key;

ALTER TABLE inventory_items
    ADD CONSTRAINT inventory_items_tenant_farm_sku_key UNIQUE (tenant_id, farm_id, sku);

ALTER TABLE inventory_items
    DROP CONSTRAINT IF EXISTS inventory_items_unit_cost_nonnegative;

ALTER TABLE inventory_items
    ADD CONSTRAINT inventory_items_unit_cost_nonnegative CHECK (unit_cost IS NULL OR unit_cost >= 0);

CREATE TABLE inventory_ledger (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    item_id uuid NOT NULL REFERENCES inventory_items (id),
    farm_id uuid NOT NULL REFERENCES farms (id),
    movement_type varchar(20) NOT NULL,
    quantity_delta numeric(14, 2) NOT NULL,
    quantity_after numeric(14, 2) NOT NULL CHECK (quantity_after >= 0),
    unit varchar(20) NOT NULL,
    reason varchar(300) NOT NULL,
    lot_code varchar(40),
    expires_on date,
    supplier_name varchar(160),
    store_name varchar(160),
    related_id uuid,
    actor_id uuid,
    actor_name varchar(160) NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX inventory_ledger_tenant_occurred_idx
    ON inventory_ledger (tenant_id, occurred_at DESC);

CREATE TABLE inventory_adjustment_requests (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    item_id uuid NOT NULL REFERENCES inventory_items (id),
    farm_id uuid NOT NULL REFERENCES farms (id),
    delta numeric(14, 2) NOT NULL,
    reason varchar(300) NOT NULL,
    status varchar(20) NOT NULL,
    requested_by uuid NOT NULL,
    requested_by_name varchar(160) NOT NULL,
    requested_at timestamptz NOT NULL DEFAULT now(),
    decided_by uuid,
    decided_by_name varchar(160),
    decided_at timestamptz,
    ledger_id uuid
);

CREATE INDEX inventory_adjustment_tenant_status_idx
    ON inventory_adjustment_requests (tenant_id, status, requested_at DESC);

INSERT INTO inventory_ledger (
    id, tenant_id, item_id, farm_id, movement_type, quantity_delta, quantity_after,
    unit, reason, lot_code, expires_on, actor_name, occurred_at
)
SELECT
    gen_random_uuid(),
    tenant_id,
    id,
    farm_id,
    'OPENING',
    quantity_on_hand,
    quantity_on_hand,
    unit,
    'Opening balance from stock already on hand',
    lot_code,
    expires_on,
    'Stock on hand',
    created_at
FROM inventory_items;

CREATE OR REPLACE FUNCTION reject_inventory_ledger_change() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'inventory ledger is append-only';
END;
$$;

DO $$
BEGIN
    IF current_setting('server_version_num')::int >= 140000 THEN
        EXECUTE 'CREATE TRIGGER inventory_ledger_no_change BEFORE UPDATE OR DELETE ON inventory_ledger FOR EACH ROW EXECUTE FUNCTION reject_inventory_ledger_change()';
    ELSE
        EXECUTE 'CREATE TRIGGER inventory_ledger_no_change BEFORE UPDATE OR DELETE ON inventory_ledger FOR EACH ROW EXECUTE PROCEDURE reject_inventory_ledger_change()';
    END IF;
END $$;
