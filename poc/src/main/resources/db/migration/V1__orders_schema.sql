-- Schema da PoC de Pedidos (CLAUDE.md, seção 5).

CREATE SEQUENCE orders_legacy_id_seq;

CREATE TABLE orders (
    id                 uuid           NOT NULL,
    legacy_id          bigint         NOT NULL,
    country            char(2)        NOT NULL,
    caller_id          varchar(128)   NOT NULL,
    partner_id         varchar(64),
    external_reference varchar(64),
    channel            varchar(32)    NOT NULL,
    status             varchar(32)    NOT NULL,
    version            integer        NOT NULL CHECK (version >= 1),
    customer_id        varchar(64)    NOT NULL,
    customer_name      varchar(200),
    customer_email     varchar(320),
    total_amount       numeric(19, 4) NOT NULL,
    currency           char(3)        NOT NULL,
    created_at         timestamptz    NOT NULL,
    updated_at         timestamptz    NOT NULL,
    CONSTRAINT orders_pk PRIMARY KEY (id),
    CONSTRAINT orders_legacy_id_uk UNIQUE (legacy_id)
);

-- ADR-005: no máximo um pedido por (parceiro, externalReference).
CREATE UNIQUE INDEX orders_partner_external_reference_uk
    ON orders (partner_id, external_reference)
    WHERE partner_id IS NOT NULL;

CREATE INDEX orders_caller_idx ON orders (caller_id);

CREATE TABLE order_items (
    order_id        uuid           NOT NULL REFERENCES orders (id),
    line_no         integer        NOT NULL,
    sku             varchar(64)    NOT NULL,
    quantity        integer        NOT NULL CHECK (quantity >= 1),
    unit_price      numeric(19, 4) NOT NULL,
    currency        char(3)        NOT NULL,
    description     text           NOT NULL,
    catalog_version bigint         NOT NULL,
    captured_at     timestamptz    NOT NULL,
    snapshot_source varchar(16)    NOT NULL,
    CONSTRAINT order_items_pk PRIMARY KEY (order_id, line_no)
);

-- ADR-004: o snapshot do item é imutável.
CREATE FUNCTION reject_order_item_update() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'order_items is immutable (ADR-004)' USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER order_items_immutable
    BEFORE UPDATE ON order_items
    FOR EACH ROW EXECUTE FUNCTION reject_order_item_update();

CREATE TABLE idempotency_record (
    caller_id       varchar(128) NOT NULL,
    idem_key        varchar(64)  NOT NULL,
    request_hash    char(64)     NOT NULL,
    order_id        uuid         NOT NULL,
    response_status integer      NOT NULL,
    response_body   jsonb        NOT NULL,
    created_at      timestamptz  NOT NULL,
    expires_at      timestamptz  NOT NULL,
    CONSTRAINT idempotency_record_pk PRIMARY KEY (caller_id, idem_key)
);

CREATE TABLE outbox_event (
    event_id          uuid         NOT NULL,
    aggregate_type    varchar(32)  NOT NULL,
    aggregate_id      uuid         NOT NULL,
    aggregate_version integer      NOT NULL,
    event_type        varchar(64)  NOT NULL,
    payload           jsonb        NOT NULL,
    occurred_at       timestamptz  NOT NULL,
    published_at      timestamptz,
    CONSTRAINT outbox_event_pk PRIMARY KEY (event_id)
);

CREATE INDEX outbox_event_unpublished_idx ON outbox_event (occurred_at) WHERE published_at IS NULL;

-- Read model local de catálogo (ADR-003). Na PoC é carregado por dados sintéticos.
CREATE TABLE catalog_item_view (
    country         char(2)        NOT NULL,
    sku             varchar(64)    NOT NULL,
    price           numeric(19, 4) NOT NULL,
    currency        char(3)        NOT NULL,
    description     text           NOT NULL,
    sellable        boolean        NOT NULL,
    catalog_version bigint         NOT NULL,
    CONSTRAINT catalog_item_view_pk PRIMARY KEY (country, sku)
);
