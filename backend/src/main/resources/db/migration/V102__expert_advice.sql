CREATE TABLE expert_advice (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    author_id uuid NOT NULL REFERENCES users (id),
    topic varchar(160) NOT NULL,
    advice varchar(2000) NOT NULL,
    medication varchar(160),
    medication_note varchar(400),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_expert_advice_tenant ON expert_advice (tenant_id, created_at DESC);
