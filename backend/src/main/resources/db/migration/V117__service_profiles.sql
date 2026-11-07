CREATE TABLE service_profiles (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    user_id uuid NOT NULL REFERENCES users (id),
    kind varchar(40) NOT NULL,
    region varchar(80) NOT NULL,
    district varchar(80),
    phone varchar(40),
    summary varchar(400) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id)
);
