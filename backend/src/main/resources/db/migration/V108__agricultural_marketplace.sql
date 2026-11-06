CREATE TABLE market_seller (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    owner_id uuid NOT NULL REFERENCES users (id),
    name varchar(160) NOT NULL,
    region varchar(80) NOT NULL,
    district varchar(80) NOT NULL,
    phone varchar(40) NOT NULL,
    latitude numeric(9, 6) NOT NULL,
    longitude numeric(9, 6) NOT NULL,
    summary varchar(400) NOT NULL,
    approval varchar(20) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_market_seller_owner ON market_seller (tenant_id, owner_id);
CREATE INDEX idx_market_seller_tenant ON market_seller (tenant_id, approval);

CREATE TABLE market_product (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    seller_id uuid NOT NULL REFERENCES market_seller (id),
    name varchar(160) NOT NULL,
    category varchar(40) NOT NULL,
    image_key varchar(20) NOT NULL,
    description varchar(800) NOT NULL,
    quantity numeric(12, 2) NOT NULL,
    unit_label varchar(16) NOT NULL,
    price_amount numeric(12, 2) NOT NULL,
    currency_code varchar(3) NOT NULL,
    min_order numeric(12, 2) NOT NULL,
    grade varchar(80) NOT NULL,
    region varchar(80) NOT NULL,
    district varchar(80) NOT NULL,
    latitude numeric(9, 6) NOT NULL,
    longitude numeric(9, 6) NOT NULL,
    fulfillment varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_market_product_tenant ON market_product (tenant_id, status, category);

CREATE TABLE market_request (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    buyer_id uuid NOT NULL REFERENCES users (id),
    product_id uuid REFERENCES market_product (id),
    product_name varchar(160) NOT NULL,
    category varchar(40) NOT NULL,
    quantity numeric(12, 2) NOT NULL,
    unit_label varchar(16) NOT NULL,
    grade varchar(80) NOT NULL,
    target_price numeric(12, 2) NOT NULL,
    currency_code varchar(3) NOT NULL,
    region varchar(80) NOT NULL,
    district varchar(80) NOT NULL,
    latitude numeric(9, 6),
    longitude numeric(9, 6),
    needed_on date NOT NULL,
    note varchar(400),
    status varchar(32) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_market_request_tenant ON market_request (tenant_id, status, created_at DESC);

CREATE TABLE market_offer (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    request_id uuid NOT NULL REFERENCES market_request (id),
    seller_id uuid NOT NULL REFERENCES market_seller (id),
    product_id uuid REFERENCES market_product (id),
    quantity numeric(12, 2) NOT NULL,
    unit_label varchar(16) NOT NULL,
    price_amount numeric(12, 2) NOT NULL,
    currency_code varchar(3) NOT NULL,
    terms varchar(400) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_market_offer_request ON market_offer (tenant_id, request_id, status);

CREATE TABLE market_order (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    request_id uuid NOT NULL REFERENCES market_request (id),
    offer_id uuid NOT NULL REFERENCES market_offer (id),
    buyer_id uuid NOT NULL REFERENCES users (id),
    seller_id uuid NOT NULL REFERENCES market_seller (id),
    product_id uuid REFERENCES market_product (id),
    product_name varchar(160) NOT NULL,
    quantity numeric(12, 2) NOT NULL,
    unit_label varchar(16) NOT NULL,
    price_amount numeric(12, 2) NOT NULL,
    currency_code varchar(3) NOT NULL,
    fulfillment varchar(20) NOT NULL,
    status varchar(32) NOT NULL,
    stock_drawn boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_market_order_tenant ON market_order (tenant_id, status, created_at DESC);

CREATE TABLE market_message (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    request_id uuid REFERENCES market_request (id),
    order_id uuid REFERENCES market_order (id),
    sender_id uuid NOT NULL REFERENCES users (id),
    body varchar(1000) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_market_message_request ON market_message (tenant_id, request_id, created_at);

CREATE TABLE market_review (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    order_id uuid NOT NULL REFERENCES market_order (id),
    reviewer_id uuid NOT NULL REFERENCES users (id),
    seller_id uuid NOT NULL REFERENCES market_seller (id),
    score smallint NOT NULL,
    note varchar(400),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_market_review_order ON market_review (order_id);

CREATE TABLE market_notice (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants (id),
    user_id uuid NOT NULL REFERENCES users (id),
    title varchar(160) NOT NULL,
    detail varchar(400) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_market_notice_user ON market_notice (tenant_id, user_id, created_at DESC);
