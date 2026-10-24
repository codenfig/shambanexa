CREATE TABLE adviser_profiles (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    user_id uuid NOT NULL REFERENCES users (id),
    talents varchar(500) NOT NULL DEFAULT '',
    availability varchar(20) NOT NULL,
    availability_note varchar(240),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, user_id)
);

CREATE TABLE adviser_ratings (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    adviser_id uuid NOT NULL REFERENCES users (id),
    rater_id uuid NOT NULL REFERENCES users (id),
    score integer NOT NULL,
    note varchar(400),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (adviser_id, rater_id)
);

CREATE TABLE adviser_recommendations (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    adviser_id uuid NOT NULL REFERENCES users (id),
    recommender_id uuid NOT NULL REFERENCES users (id),
    note varchar(400),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (adviser_id, recommender_id)
);

CREATE INDEX idx_adviser_ratings_adviser ON adviser_ratings (tenant_id, adviser_id);
CREATE INDEX idx_adviser_recommendations_adviser ON adviser_recommendations (tenant_id, adviser_id);
