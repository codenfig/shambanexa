-- Animal treatments, vaccination protocols, and optional shop licence details.
-- No sample clinical rows. Existing health cases, stock, and shops stay as they are.

CREATE TABLE animal_treatments (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    farm_id uuid NOT NULL REFERENCES farms (id),
    animal_id uuid REFERENCES animals (id),
    herd_id uuid REFERENCES batches (id),
    symptoms varchar(500) NOT NULL,
    diagnosis varchar(240) NOT NULL,
    description varchar(800) NOT NULL,
    veterinarian_name varchar(120) NOT NULL,
    treatment_status varchar(20) NOT NULL,
    started_on date NOT NULL,
    outcome varchar(240),
    follow_up_on date,
    created_by uuid NOT NULL REFERENCES users (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT animal_treatments_subject CHECK (animal_id IS NOT NULL OR herd_id IS NOT NULL),
    CONSTRAINT animal_treatments_status CHECK (treatment_status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX animal_treatments_farm_idx ON animal_treatments (tenant_id, farm_id, started_on DESC);

CREATE TABLE treatment_doses (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    treatment_id uuid NOT NULL REFERENCES animal_treatments (id),
    item_id uuid NOT NULL REFERENCES inventory_items (id),
    batch_id uuid REFERENCES inventory_batches (id),
    quantity numeric(14, 3) NOT NULL CHECK (quantity > 0),
    withdrawal_days integer,
    withdrawal_ends_on date,
    issued boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX treatment_doses_treatment_idx ON treatment_doses (treatment_id);

CREATE TABLE vaccination_protocols (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    species_name varchar(80) NOT NULL,
    name varchar(160) NOT NULL,
    age_group varchar(80) NOT NULL,
    interval_days integer NOT NULL CHECK (interval_days > 0 AND interval_days <= 3650),
    item_id uuid REFERENCES inventory_items (id),
    notes varchar(400),
    approved_by uuid NOT NULL REFERENCES users (id),
    approved_by_name varchar(120) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX vaccination_protocols_tenant_idx ON vaccination_protocols (tenant_id, active);

CREATE TABLE animal_vaccinations (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    farm_id uuid NOT NULL REFERENCES farms (id),
    animal_id uuid REFERENCES animals (id),
    herd_id uuid REFERENCES batches (id),
    protocol_id uuid REFERENCES vaccination_protocols (id),
    species_name varchar(80) NOT NULL,
    age_group varchar(80),
    vaccine_name varchar(160) NOT NULL,
    item_id uuid REFERENCES inventory_items (id),
    batch_id uuid REFERENCES inventory_batches (id),
    quantity numeric(14, 3),
    withdrawal_days integer,
    withdrawal_ends_on date,
    administered_on date,
    next_due_on date,
    veterinarian_name varchar(120) NOT NULL,
    notes varchar(400),
    vaccination_status varchar(20) NOT NULL,
    dose_issued boolean NOT NULL DEFAULT false,
    created_by uuid NOT NULL REFERENCES users (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT animal_vaccinations_subject CHECK (animal_id IS NOT NULL OR herd_id IS NOT NULL),
    CONSTRAINT animal_vaccinations_status CHECK (vaccination_status IN ('PLANNED', 'COMPLETED', 'MISSED', 'CANCELLED')),
    CONSTRAINT animal_vaccinations_quantity CHECK (quantity IS NULL OR quantity > 0)
);

CREATE INDEX animal_vaccinations_due_idx ON animal_vaccinations (tenant_id, next_due_on);

ALTER TABLE medical_supplier
    ADD COLUMN license_authority varchar(160),
    ADD COLUMN license_expires_on date,
    ADD COLUMN products_offered varchar(400);
