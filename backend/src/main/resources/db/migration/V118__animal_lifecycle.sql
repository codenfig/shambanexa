-- Animal-level longitudinal records. No synthetic measurements or clinical data.
CREATE TABLE animal_weights (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id),
 animal_id uuid NOT NULL REFERENCES animals(id),
 measured_on date NOT NULL,
 weight_kg numeric(12,3) NOT NULL CHECK(weight_kg > 0 AND weight_kg < 100000),
 notes varchar(500), recorded_by uuid NOT NULL REFERENCES users(id),
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX animal_weights_history_idx ON animal_weights(tenant_id,animal_id,measured_on DESC,created_at DESC);

CREATE TABLE animal_breeding_events (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL REFERENCES tenants(id),
 animal_id uuid NOT NULL REFERENCES animals(id),
 event_type varchar(30) NOT NULL CHECK(event_type IN ('MATING','INSEMINATION','PREGNANCY_CHECK','PREGNANCY_CONFIRMED','BIRTH','LOSS','WEANING')),
 event_on date NOT NULL, expected_birth_on date,
 partner_animal_id uuid REFERENCES animals(id),
 offspring_count integer CHECK(offspring_count IS NULL OR offspring_count >= 0),
 notes varchar(500), recorded_by uuid NOT NULL REFERENCES users(id),
 created_at timestamptz NOT NULL DEFAULT now(),
 CONSTRAINT breeding_dates CHECK(expected_birth_on IS NULL OR expected_birth_on >= event_on)
);
CREATE INDEX animal_breeding_history_idx ON animal_breeding_events(tenant_id,animal_id,event_on DESC,created_at DESC);

-- Existing treatment_doses and animal_vaccinations already store medicine batches
-- and withdrawal dates; index for animal-level traceability through treatment.
CREATE INDEX animal_treatments_animal_trace_idx ON animal_treatments(tenant_id,animal_id,started_on DESC);
CREATE INDEX animal_vaccinations_animal_trace_idx ON animal_vaccinations(tenant_id,animal_id,administered_on DESC);
