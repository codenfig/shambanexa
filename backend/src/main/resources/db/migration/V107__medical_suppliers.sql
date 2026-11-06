CREATE TABLE medical_supplier (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    owner_id uuid NOT NULL REFERENCES users (id),
    name varchar(160) NOT NULL,
    mark varchar(16) NOT NULL,
    region varchar(80) NOT NULL,
    district varchar(80) NOT NULL,
    address varchar(240) NOT NULL,
    phone varchar(40) NOT NULL,
    open_hours varchar(120) NOT NULL,
    latitude numeric(9, 6) NOT NULL,
    longitude numeric(9, 6) NOT NULL,
    license_number varchar(80) NOT NULL,
    summary varchar(400) NOT NULL,
    approval varchar(20) NOT NULL,
    shop_state varchar(20) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_medical_supplier_owner ON medical_supplier (tenant_id, owner_id);
CREATE INDEX idx_medical_supplier_tenant ON medical_supplier (tenant_id, approval, region);

CREATE TABLE supplier_medicine (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    supplier_id uuid NOT NULL REFERENCES medical_supplier (id),
    name varchar(160) NOT NULL,
    animal_types varchar(240) NOT NULL,
    category varchar(80) NOT NULL,
    price_amount numeric(12, 2) NOT NULL,
    currency_code varchar(3) NOT NULL,
    quantity numeric(12, 2) NOT NULL,
    unit_label varchar(40) NOT NULL,
    stock_state varchar(20) NOT NULL,
    prescription_required boolean NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_supplier_medicine_shop ON supplier_medicine (tenant_id, supplier_id, name);

CREATE TABLE medicine_request (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    supplier_id uuid NOT NULL REFERENCES medical_supplier (id),
    medicine_id uuid REFERENCES supplier_medicine (id),
    requester_id uuid NOT NULL REFERENCES users (id),
    farm_id uuid REFERENCES farms (id),
    medicine_name varchar(160) NOT NULL,
    quantity numeric(12, 2) NOT NULL,
    unit_label varchar(40) NOT NULL,
    fulfillment varchar(20) NOT NULL,
    status varchar(32) NOT NULL,
    prescription_required boolean NOT NULL,
    stock_drawn boolean NOT NULL DEFAULT false,
    requester_note varchar(400),
    adviser_id uuid REFERENCES users (id),
    adviser_note varchar(400),
    supplier_note varchar(400),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_medicine_request_tenant ON medicine_request (tenant_id, created_at DESC);
