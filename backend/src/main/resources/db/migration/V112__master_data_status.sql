ALTER TABLE species
    ADD COLUMN status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN origin varchar(16) NOT NULL DEFAULT 'SYSTEM',
    ADD COLUMN created_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN created_by uuid,
    ADD COLUMN updated_by uuid;

ALTER TABLE breeds
    ADD COLUMN code varchar(40),
    ADD COLUMN status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN origin varchar(16) NOT NULL DEFAULT 'SYSTEM',
    ADD COLUMN created_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN created_by uuid,
    ADD COLUMN updated_by uuid;

UPDATE breeds
SET code = upper(trim(both '-' FROM regexp_replace(name, '[^A-Za-z0-9]+', '-', 'g')))
WHERE code IS NULL OR btrim(code) = '';

ALTER TABLE breeds ALTER COLUMN code SET NOT NULL;

CREATE UNIQUE INDEX species_code_lower ON species (tenant_id, lower(code));
CREATE UNIQUE INDEX species_name_lower ON species (tenant_id, lower(name));
CREATE UNIQUE INDEX breeds_code_per_species ON breeds (tenant_id, species_id, lower(code));
CREATE UNIQUE INDEX breeds_name_per_species ON breeds (tenant_id, species_id, lower(name));
