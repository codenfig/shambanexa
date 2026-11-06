-- Batch, expiry, and inventory reference data. Existing stock lines and the opening ledger stay in place.

CREATE TABLE inventory_references (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    kind varchar(40) NOT NULL,
    code varchar(40) NOT NULL,
    name varchar(160) NOT NULL,
    parent_id uuid REFERENCES inventory_references (id),
    detail varchar(400),
    numeric_value integer,
    sort_order integer NOT NULL DEFAULT 0,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    origin varchar(16) NOT NULL DEFAULT 'SYSTEM',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_by uuid,
    UNIQUE (tenant_id, kind, code)
);

CREATE INDEX inventory_references_tenant_kind_idx ON inventory_references (tenant_id, kind, sort_order);

CREATE TABLE stock_suppliers (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    name varchar(160) NOT NULL,
    supplier_type varchar(20) NOT NULL,
    phone varchar(40),
    location varchar(240),
    registration_no varchar(80),
    license_no varchar(80),
    lead_days integer,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);

ALTER TABLE inventory_items
    ADD COLUMN product_type_id uuid REFERENCES inventory_references (id),
    ADD COLUMN description varchar(400),
    ADD COLUMN brand varchar(160),
    ADD COLUMN reorder_quantity numeric(14, 2),
    ADD COLUMN selling_price numeric(14, 2),
    ADD COLUMN supplier_id uuid REFERENCES stock_suppliers (id),
    ADD COLUMN generic_name varchar(160),
    ADD COLUMN medicine_class_id uuid REFERENCES inventory_references (id),
    ADD COLUMN dosage_form varchar(80),
    ADD COLUMN strength varchar(80),
    ADD COLUMN species_label varchar(160),
    ADD COLUMN prescription_required boolean,
    ADD COLUMN storage_note varchar(240),
    ADD COLUMN registration_no varchar(80),
    ADD COLUMN withdrawal_days integer,
    ADD COLUMN safety_note varchar(400),
    ADD COLUMN feed_type_id uuid REFERENCES inventory_references (id),
    ADD COLUMN growth_stage varchar(80),
    ADD COLUMN pack_size varchar(80),
    ADD COLUMN formulation varchar(400),
    ADD COLUMN manufactured_on date,
    ADD COLUMN best_before_on date,
    ADD COLUMN open_days integer,
    ADD COLUMN perishable boolean NOT NULL DEFAULT false;

CREATE TABLE inventory_batches (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    item_id uuid NOT NULL REFERENCES inventory_items (id),
    farm_id uuid NOT NULL REFERENCES farms (id),
    lot_code varchar(40) NOT NULL,
    quantity_received numeric(14, 2) NOT NULL CHECK (quantity_received >= 0),
    quantity_remaining numeric(14, 2) NOT NULL CHECK (quantity_remaining >= 0),
    manufactured_on date,
    received_on date NOT NULL,
    expires_on date,
    best_before_on date,
    opened_on date,
    open_days integer,
    supplier_id uuid REFERENCES stock_suppliers (id),
    supplier_name varchar(160),
    store_name varchar(160),
    unit_cost numeric(14, 2),
    status varchar(20) NOT NULL DEFAULT 'OPEN',
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (status IN ('OPEN', 'QUARANTINED', 'DISPOSED'))
);

CREATE INDEX inventory_batches_item_idx ON inventory_batches (tenant_id, item_id);
CREATE INDEX inventory_batches_expiry_idx ON inventory_batches (tenant_id, expires_on);

ALTER TABLE inventory_ledger
    ADD COLUMN batch_id uuid REFERENCES inventory_batches (id);

ALTER TABLE inventory_adjustment_requests
    ADD COLUMN batch_id uuid REFERENCES inventory_batches (id),
    ADD COLUMN reason_code varchar(40),
    ADD COLUMN notes varchar(400),
    ADD COLUMN quantity_before numeric(14, 2);

CREATE TABLE inventory_alert_acks (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    batch_id uuid NOT NULL REFERENCES inventory_batches (id),
    alert_kind varchar(40) NOT NULL,
    assignee_name varchar(160),
    note varchar(300),
    acknowledged_at timestamptz,
    acknowledged_by uuid,
    acknowledged_by_name varchar(160),
    UNIQUE (tenant_id, batch_id, alert_kind)
);

INSERT INTO inventory_references (id, tenant_id, kind, code, name, sort_order, status, origin)
SELECT gen_random_uuid(), t.id, 'INV_CATEGORY', v.code, v.name, v.sort_order, 'ACTIVE', 'SYSTEM'
FROM tenants t
CROSS JOIN (VALUES
    ('FEED', 'Animal feed', 1),
    ('MEDICINE', 'Veterinary medicine', 2),
    ('SUPPLY', 'Agricultural supplies', 3),
    ('EQUIPMENT', 'Equipment', 4)
) AS v(code, name, sort_order);

INSERT INTO inventory_references (id, tenant_id, kind, code, name, parent_id, sort_order, status, origin)
SELECT gen_random_uuid(), c.tenant_id, 'INV_TYPE', v.code, v.name, c.id, v.sort_order, 'ACTIVE', 'SYSTEM'
FROM inventory_references c
JOIN (VALUES
    ('ANTIBIOTIC', 'Antibiotics', 'MEDICINE', 1),
    ('ANTIPARASITIC', 'Antiparasitic medicines', 'MEDICINE', 2),
    ('DEWORMER', 'Dewormers', 'MEDICINE', 3),
    ('VACCINE', 'Vaccines', 'MEDICINE', 4),
    ('VITAMIN', 'Vitamins and minerals', 'MEDICINE', 5),
    ('PAIN', 'Pain management', 'MEDICINE', 6),
    ('DISINFECTANT', 'Disinfectants', 'MEDICINE', 7),
    ('VET_OTHER', 'Other veterinary treatments', 'MEDICINE', 8),
    ('POULTRY_STARTER', 'Poultry starter feed', 'FEED', 1),
    ('POULTRY_GROWER', 'Poultry grower feed', 'FEED', 2),
    ('POULTRY_FINISHER', 'Poultry finisher feed', 'FEED', 3),
    ('LAYER', 'Layer mash', 'FEED', 4),
    ('DAIRY_MEAL', 'Dairy meal', 'FEED', 5),
    ('CALF', 'Calf starter', 'FEED', 6),
    ('PIG', 'Pig feed', 'FEED', 7),
    ('FISH_FEED', 'Fish feed', 'FEED', 8),
    ('FORAGE', 'Hay and silage', 'FEED', 9),
    ('SUPPLEMENT', 'Feed supplements', 'FEED', 10),
    ('BRAN', 'Grain by-products', 'FEED', 11),
    ('SEED', 'Seeds', 'SUPPLY', 1),
    ('FERTILIZER', 'Fertilizers', 'SUPPLY', 2),
    ('CROP', 'Crop protection', 'SUPPLY', 3),
    ('SOIL', 'Soil amendments', 'SUPPLY', 4),
    ('CLEANING', 'Cleaning supplies', 'SUPPLY', 5),
    ('CONSUMABLE', 'Farm consumables', 'SUPPLY', 6),
    ('DAIRY_EQUIP', 'Dairy equipment', 'EQUIPMENT', 1),
    ('SPRAY_EQUIP', 'Spraying equipment', 'EQUIPMENT', 2)
) AS v(code, name, parent_code, sort_order) ON c.kind = 'INV_CATEGORY' AND c.code = v.parent_code;

INSERT INTO inventory_references (id, tenant_id, kind, code, name, sort_order, status, origin)
SELECT gen_random_uuid(), t.id, v.kind, v.code, v.name, v.sort_order, 'ACTIVE', 'SYSTEM'
FROM tenants t
CROSS JOIN (VALUES
    ('INV_MED_CLASS', 'ANTIBIOTIC', 'Antibiotic', 1),
    ('INV_MED_CLASS', 'ANTHELMINTIC', 'Anthelmintic', 2),
    ('INV_MED_CLASS', 'VACCINE', 'Vaccine', 3),
    ('INV_MED_CLASS', 'VITAMIN', 'Vitamin', 4),
    ('INV_MED_CLASS', 'OTHER', 'Other', 5),
    ('INV_FEED_TYPE', 'MASH', 'Mash', 1),
    ('INV_FEED_TYPE', 'PELLETS', 'Pellets', 2),
    ('INV_FEED_TYPE', 'BLOCK', 'Block', 3),
    ('INV_FEED_TYPE', 'FORAGE', 'Forage', 4),
    ('INV_FEED_TYPE', 'CONCENTRATE', 'Concentrate', 5),
    ('INV_UNIT', 'KG', 'kg', 1),
    ('INV_UNIT', 'L', 'L', 2),
    ('INV_UNIT', 'DOSE', 'dose', 3),
    ('INV_UNIT', 'BOTTLE', 'bottle', 4),
    ('INV_UNIT', 'PIECE', 'piece', 5),
    ('INV_UNIT', 'BAG', 'bag', 6),
    ('INV_STORAGE', 'FEED_BARN', 'Feed barn', 1),
    ('INV_STORAGE', 'MINERAL', 'Mineral store', 2),
    ('INV_STORAGE', 'VET_FRIDGE', 'Vet fridge', 3),
    ('INV_STORAGE', 'DAIRY_SHED', 'Dairy shed', 4),
    ('INV_STORAGE', 'CHEMICAL', 'Chemical store', 5),
    ('INV_STORAGE', 'NORTHERN', 'Northern store', 6),
    ('INV_STORAGE', 'OUTPOST', 'Outpost shed', 7),
    ('INV_REASON', 'EXPIRED', 'Expired stock', 1),
    ('INV_REASON', 'DAMAGED', 'Damaged stock', 2),
    ('INV_REASON', 'SPOILED', 'Spoiled feed', 3),
    ('INV_REASON', 'CONTAMINATED', 'Contaminated stock', 4),
    ('INV_REASON', 'COUNT', 'Stock count correction', 5),
    ('INV_REASON', 'PACKAGING', 'Broken packaging', 6),
    ('INV_REASON', 'RECALL', 'Product recall', 7),
    ('INV_REASON', 'DISPOSAL', 'Authorized disposal', 8),
    ('INV_STATUS', 'VALID', 'Valid', 1),
    ('INV_STATUS', 'EXPIRING', 'Expiring soon', 2),
    ('INV_STATUS', 'EXPIRED', 'Expired', 3),
    ('INV_STATUS', 'BEST_BEFORE', 'Best before approaching', 4),
    ('INV_STATUS', 'OPEN_USE', 'Use soon after opening', 5),
    ('INV_STATUS', 'QUARANTINED', 'Quarantined', 6),
    ('INV_STATUS', 'DISPOSED', 'Disposed', 7)
) AS v(kind, code, name, sort_order);

INSERT INTO inventory_references (id, tenant_id, kind, code, name, detail, numeric_value, sort_order, status, origin)
SELECT gen_random_uuid(), t.id, 'INV_WINDOW', v.code, v.name, v.detail, v.days, v.days, 'ACTIVE', 'SYSTEM'
FROM tenants t
CROSS JOIN (VALUES
    ('D90', '90 days before expiry', NULL, 90),
    ('D60', '60 days before expiry', NULL, 60),
    ('D30', '30 days before expiry', 'primary', 30),
    ('D14', '14 days before expiry', NULL, 14),
    ('D7', '7 days before expiry', NULL, 7),
    ('D0', 'Expired', NULL, 0)
) AS v(code, name, detail, days);

INSERT INTO stock_suppliers (id, tenant_id, name, supplier_type, status)
SELECT gen_random_uuid(), s.tenant_id, s.supplier_name,
       CASE
           WHEN s.supplier_name IN ('AgroVet Supplies', 'Livestock Medics') THEN 'MEDICAL'
           WHEN s.supplier_name = 'Mkulima Traders' THEN 'FEED'
           ELSE 'GENERAL'
       END,
       'ACTIVE'
FROM (
    SELECT DISTINCT tenant_id, supplier_name
    FROM inventory_items
    WHERE supplier_name IS NOT NULL AND btrim(supplier_name) <> ''
) s
WHERE NOT EXISTS (
    SELECT 1 FROM stock_suppliers existing
    WHERE existing.tenant_id = s.tenant_id AND existing.name = s.supplier_name
);

UPDATE inventory_items i
SET supplier_id = s.id
FROM stock_suppliers s
WHERE s.tenant_id = i.tenant_id AND s.name = i.supplier_name AND i.supplier_id IS NULL;

UPDATE inventory_items i
SET product_type_id = r.id
FROM inventory_references r
WHERE r.tenant_id = i.tenant_id
  AND r.kind = 'INV_TYPE'
  AND i.product_type_id IS NULL
  AND r.code = CASE i.sku
      WHEN 'FEED-DM-W' THEN 'DAIRY_MEAL'
      WHEN 'MIN-N' THEN 'SUPPLEMENT'
      WHEN 'VAC-LSD' THEN 'VACCINE'
      WHEN 'FUEL-S' THEN 'CONSUMABLE'
      WHEN 'FEED-MZ-D' THEN 'BRAN'
      WHEN 'SALT-01' THEN 'SUPPLEMENT'
      WHEN 'EQ-BUCKET' THEN 'DAIRY_EQUIP'
      WHEN 'EQ-SPRAY-W' THEN 'SPRAY_EQUIP'
      WHEN 'MED-OXY-C' THEN 'ANTIBIOTIC'
      WHEN 'SUP-FOOT-N' THEN 'CONSUMABLE'
      ELSE NULL
  END;

UPDATE inventory_items i
SET medicine_class_id = r.id,
    generic_name = CASE i.sku WHEN 'MED-OXY-C' THEN 'Oxytetracycline' WHEN 'VAC-LSD' THEN 'Lumpy skin disease vaccine' ELSE i.generic_name END,
    species_label = CASE WHEN i.sku IN ('MED-OXY-C', 'VAC-LSD') THEN 'Cattle' ELSE i.species_label END,
    perishable = true
FROM inventory_references r
WHERE r.tenant_id = i.tenant_id
  AND r.kind = 'INV_MED_CLASS'
  AND i.category = 'MEDICINE'
  AND r.code = CASE i.sku WHEN 'MED-OXY-C' THEN 'ANTIBIOTIC' WHEN 'VAC-LSD' THEN 'VACCINE' ELSE NULL END;

UPDATE inventory_items i
SET feed_type_id = r.id,
    perishable = true
FROM inventory_references r
WHERE r.tenant_id = i.tenant_id
  AND r.kind = 'INV_FEED_TYPE'
  AND r.code = 'CONCENTRATE'
  AND i.category = 'FEED'
  AND i.feed_type_id IS NULL;

UPDATE inventory_items SET perishable = true WHERE category IN ('FEED', 'MEDICINE') AND perishable = false;

INSERT INTO inventory_batches (
    id, tenant_id, item_id, farm_id, lot_code, quantity_received, quantity_remaining,
    received_on, expires_on, best_before_on, supplier_id, supplier_name, store_name, unit_cost, status, created_at
)
SELECT gen_random_uuid(), i.tenant_id, i.id, i.farm_id,
       COALESCE(NULLIF(btrim(i.lot_code), ''), 'OPENING'),
       i.quantity_on_hand, i.quantity_on_hand,
       i.created_at::date, i.expires_on, i.best_before_on,
       i.supplier_id, i.supplier_name, i.store_name, i.unit_cost, 'OPEN', i.created_at
FROM inventory_items i
WHERE NOT EXISTS (SELECT 1 FROM inventory_batches b WHERE b.item_id = i.id);
