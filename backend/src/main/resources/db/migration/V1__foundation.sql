CREATE TABLE tenants (
    id uuid PRIMARY KEY,
    name varchar(160) NOT NULL,
    code varchar(40) NOT NULL UNIQUE,
    currency_code varchar(3) NOT NULL,
    timezone varchar(64) NOT NULL,
    season_label varchar(80) NOT NULL,
    locale varchar(16) NOT NULL,
    subscription_plan varchar(80) NOT NULL,
    subscription_valid_until date NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    email varchar(180) NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    full_name varchar(160) NOT NULL,
    job_title varchar(80) NOT NULL,
    status varchar(40) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE farms (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    code varchar(40) NOT NULL,
    name varchar(160) NOT NULL,
    region varchar(80) NOT NULL,
    status varchar(40) NOT NULL,
    latitude numeric(9, 6) NOT NULL,
    longitude numeric(9, 6) NOT NULL,
    timezone varchar(64) NOT NULL,
    address varchar(300) NOT NULL,
    manager_name varchar(160),
    contact_phone varchar(40),
    established_on date,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, code)
);

CREATE TABLE user_role_grants (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    user_id uuid NOT NULL REFERENCES users (id),
    role_code varchar(40) NOT NULL,
    farm_id uuid REFERENCES farms (id),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE facilities (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    farm_id uuid NOT NULL REFERENCES farms (id),
    parent_id uuid REFERENCES facilities (id),
    code varchar(40) NOT NULL,
    name varchar(160) NOT NULL,
    facility_type varchar(40) NOT NULL,
    status varchar(40) NOT NULL,
    capacity numeric(14, 2),
    capacity_unit varchar(40),
    supervisor_name varchar(160),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, farm_id, code)
);

CREATE TABLE species (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    code varchar(40) NOT NULL,
    name varchar(80) NOT NULL,
    category varchar(40) NOT NULL,
    chart_color varchar(16) NOT NULL,
    sort_order integer NOT NULL,
    individual_tracking boolean NOT NULL DEFAULT true,
    UNIQUE (tenant_id, code)
);

CREATE TABLE breeds (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    species_id uuid NOT NULL REFERENCES species (id),
    name varchar(120) NOT NULL,
    UNIQUE (tenant_id, species_id, name)
);

CREATE TABLE animals (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    farm_id uuid NOT NULL REFERENCES farms (id),
    facility_id uuid REFERENCES facilities (id),
    species_id uuid NOT NULL REFERENCES species (id),
    breed_id uuid REFERENCES breeds (id),
    tag varchar(40) NOT NULL,
    name varchar(120),
    sex varchar(20) NOT NULL,
    birth_date date,
    color varchar(80),
    status varchar(40) NOT NULL,
    health_status varchar(40) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, tag)
);

CREATE TABLE batches (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    farm_id uuid NOT NULL REFERENCES farms (id),
    facility_id uuid REFERENCES facilities (id),
    species_id uuid NOT NULL REFERENCES species (id),
    breed_id uuid REFERENCES breeds (id),
    code varchar(40) NOT NULL,
    name varchar(160) NOT NULL,
    quantity integer NOT NULL CHECK (quantity >= 0),
    purpose varchar(80) NOT NULL,
    status varchar(40) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, code)
);

CREATE TABLE movement_events (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    event_type varchar(40) NOT NULL,
    subject_type varchar(40) NOT NULL,
    subject_id uuid NOT NULL,
    farm_id uuid REFERENCES farms (id),
    source_farm_id uuid REFERENCES farms (id),
    destination_farm_id uuid REFERENCES farms (id),
    quantity integer NOT NULL,
    effective_at timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT now(),
    actor_id uuid REFERENCES users (id),
    reason varchar(300),
    idempotency_key varchar(80) NOT NULL,
    UNIQUE (tenant_id, idempotency_key)
);

CREATE TABLE population_snapshots (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    as_of_date date NOT NULL,
    animal_count integer NOT NULL,
    UNIQUE (tenant_id, as_of_date)
);

CREATE TABLE production_records (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    farm_id uuid NOT NULL REFERENCES farms (id),
    product_type varchar(40) NOT NULL,
    quantity numeric(14, 2) NOT NULL,
    unit varchar(20) NOT NULL,
    recorded_on date NOT NULL,
    notes varchar(300),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE production_monthly (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    year_month date NOT NULL,
    milk_litres numeric(14, 2) NOT NULL,
    revenue_amount numeric(14, 2) NOT NULL,
    currency_code varchar(3) NOT NULL,
    UNIQUE (tenant_id, year_month)
);

CREATE TABLE financial_entries (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    farm_id uuid REFERENCES farms (id),
    entry_type varchar(40) NOT NULL,
    category varchar(80) NOT NULL,
    amount numeric(14, 2) NOT NULL,
    currency_code varchar(3) NOT NULL,
    recorded_on date NOT NULL,
    description varchar(300) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE inventory_items (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    farm_id uuid NOT NULL REFERENCES farms (id),
    sku varchar(40) NOT NULL,
    name varchar(160) NOT NULL,
    category varchar(40) NOT NULL,
    quantity_on_hand numeric(14, 2) NOT NULL CHECK (quantity_on_hand >= 0),
    reserved_quantity numeric(14, 2) NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    unit varchar(20) NOT NULL,
    reorder_point numeric(14, 2) NOT NULL,
    lot_code varchar(40),
    expires_on date,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, sku)
);

CREATE TABLE staff_members (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    farm_id uuid NOT NULL REFERENCES farms (id),
    full_name varchar(160) NOT NULL,
    role_title varchar(80) NOT NULL,
    phone varchar(40),
    status varchar(40) NOT NULL,
    hired_on date,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE alerts (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    farm_id uuid REFERENCES farms (id),
    severity varchar(20) NOT NULL,
    title varchar(160) NOT NULL,
    body varchar(400) NOT NULL,
    status varchar(40) NOT NULL,
    source_type varchar(40),
    source_id uuid,
    occurred_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE activity_events (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    farm_id uuid REFERENCES farms (id),
    event_type varchar(40) NOT NULL,
    title varchar(160) NOT NULL,
    detail varchar(300) NOT NULL,
    occurred_at timestamptz NOT NULL
);

CREATE TABLE tasks (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    farm_id uuid NOT NULL REFERENCES farms (id),
    title varchar(160) NOT NULL,
    task_type varchar(40) NOT NULL,
    status varchar(40) NOT NULL,
    due_on date NOT NULL,
    assignee_name varchar(160),
    notes varchar(400),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE fish_cycles (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    farm_id uuid NOT NULL REFERENCES farms (id),
    facility_id uuid REFERENCES facilities (id),
    species_id uuid NOT NULL REFERENCES species (id),
    code varchar(40) NOT NULL,
    status varchar(40) NOT NULL,
    stocked_quantity integer NOT NULL CHECK (stocked_quantity >= 0),
    mortality integer NOT NULL DEFAULT 0 CHECK (mortality >= 0),
    harvested integer NOT NULL DEFAULT 0 CHECK (harvested >= 0),
    started_on date NOT NULL,
    closed_on date,
    UNIQUE (tenant_id, code)
);

CREATE TABLE health_cases (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    farm_id uuid NOT NULL REFERENCES farms (id),
    animal_id uuid REFERENCES animals (id),
    title varchar(160) NOT NULL,
    case_status varchar(40) NOT NULL,
    severity varchar(20) NOT NULL,
    opened_on date NOT NULL,
    notes varchar(400)
);

CREATE TABLE audit_events (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    actor_id uuid,
    action varchar(80) NOT NULL,
    entity_type varchar(80) NOT NULL,
    entity_id uuid,
    detail varchar(500),
    recorded_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_farms_tenant ON farms (tenant_id);
CREATE INDEX idx_animals_tenant_farm ON animals (tenant_id, farm_id);
CREATE INDEX idx_batches_tenant_farm ON batches (tenant_id, farm_id);
CREATE INDEX idx_movements_tenant ON movement_events (tenant_id, subject_id);
CREATE INDEX idx_alerts_tenant ON alerts (tenant_id, status);
CREATE INDEX idx_production_tenant_day ON production_records (tenant_id, recorded_on);
CREATE INDEX idx_finance_tenant_day ON financial_entries (tenant_id, recorded_on);
