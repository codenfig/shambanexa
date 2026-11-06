CREATE TABLE access_roles (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    code varchar(40) NOT NULL,
    label varchar(80) NOT NULL,
    description varchar(400) NOT NULL,
    created_at timestamptz NOT NULL,
    UNIQUE (tenant_id, code)
);

CREATE INDEX access_roles_tenant_idx ON access_roles (tenant_id, label);

CREATE TABLE access_role_permissions (
    id uuid PRIMARY KEY,
    role_id uuid NOT NULL REFERENCES access_roles (id) ON DELETE CASCADE,
    permission_code varchar(40) NOT NULL,
    UNIQUE (role_id, permission_code)
);
