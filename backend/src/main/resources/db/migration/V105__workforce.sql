CREATE TABLE farmer_requests (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    farm_id uuid NOT NULL REFERENCES farms (id),
    author_user_id uuid NOT NULL,
    author_name varchar(160) NOT NULL,
    subject varchar(160) NOT NULL,
    message varchar(2000) NOT NULL,
    status varchar(20) NOT NULL,
    reply varchar(2000),
    replied_by varchar(160),
    created_at timestamptz NOT NULL,
    answered_at timestamptz
);

CREATE INDEX farmer_requests_tenant_idx ON farmer_requests (tenant_id, created_at DESC);

CREATE TABLE staff_attendance (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    staff_id uuid NOT NULL REFERENCES staff_members (id),
    farm_id uuid NOT NULL,
    work_date date NOT NULL,
    mark varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    UNIQUE (tenant_id, staff_id, work_date)
);

CREATE TABLE staff_shifts (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    staff_id uuid NOT NULL REFERENCES staff_members (id),
    farm_id uuid NOT NULL,
    weekday smallint NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    starts time NOT NULL,
    ends time NOT NULL,
    UNIQUE (tenant_id, staff_id, weekday),
    CHECK (starts < ends)
);

CREATE TABLE staff_timesheets (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    staff_id uuid NOT NULL REFERENCES staff_members (id),
    farm_id uuid NOT NULL,
    work_date date NOT NULL,
    hours numeric(4, 1) NOT NULL CHECK (hours > 0 AND hours <= 24),
    note varchar(400),
    created_at timestamptz NOT NULL,
    UNIQUE (tenant_id, staff_id, work_date)
);

CREATE TABLE staff_reviews (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    staff_id uuid NOT NULL REFERENCES staff_members (id),
    farm_id uuid NOT NULL,
    score numeric(2, 1) NOT NULL CHECK (score >= 0 AND score <= 5),
    reviewed_on date NOT NULL,
    notes varchar(1000),
    reviewer_name varchar(160) NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE TABLE staff_training (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    staff_id uuid NOT NULL REFERENCES staff_members (id),
    farm_id uuid NOT NULL,
    title varchar(160) NOT NULL,
    completed_on date,
    created_at timestamptz NOT NULL
);

CREATE TABLE staff_goals (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    staff_id uuid NOT NULL REFERENCES staff_members (id),
    farm_id uuid NOT NULL,
    title varchar(160) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE TABLE staff_competencies (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    staff_id uuid NOT NULL REFERENCES staff_members (id),
    farm_id uuid NOT NULL,
    skill varchar(80) NOT NULL,
    level varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    UNIQUE (tenant_id, staff_id, skill)
);
