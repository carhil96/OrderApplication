-- ClickHouse-specific preparation script; it is not a Flyway migration.
-- Preparation for an existing ClickHouse volume: run before registering the
-- connector so the version tables exist when the initial Outbox snapshot arrives.
-- The final rename/view cutover at the bottom must wait until the snapshot is
-- complete and row counts have been checked. The legacy table is retained.
CREATE TABLE IF NOT EXISTS default.customer_versions (
    id UInt64, name String, version UInt64, is_deleted UInt8
) ENGINE = MergeTree() ORDER BY (id, version);

CREATE TABLE IF NOT EXISTS default.order_versions (
    id UInt64, customer_id UInt64, amount Decimal(12, 2), status String,
    created_at DateTime64(3, 'UTC'), status_order DateTime64(3, 'UTC'), version UInt64
) ENGINE = MergeTree() ORDER BY (id, version);

CREATE VIEW IF NOT EXISTS default.customers_current AS
SELECT id, tupleElement(state, 1) AS name, tupleElement(state, 2) AS version
FROM (
    SELECT id, argMax(tuple(name, version, is_deleted), version) AS state
    FROM default.customer_versions GROUP BY id
)
WHERE tupleElement(state, 3) = 0;

CREATE VIEW IF NOT EXISTS default.orders_current AS
SELECT latest.id, c.name AS customer, latest.amount, latest.status,
       latest.created_at, latest.status_order
FROM (
    SELECT id,
           tupleElement(state, 1) AS customer_id,
           tupleElement(state, 2) AS amount,
           tupleElement(state, 3) AS status,
           tupleElement(state, 4) AS created_at,
           tupleElement(state, 5) AS status_order
    FROM (
        SELECT id,
               argMax(tuple(customer_id, amount, status, created_at, status_order), version) AS state
        FROM default.order_versions GROUP BY id
    )
) AS latest
INNER JOIN default.customers_current c ON c.id = latest.customer_id;

-- One-time cutover: run only after verifying orders_current is complete.
-- RENAME TABLE default.orders TO default.orders_legacy;
-- CREATE VIEW default.orders AS
--   SELECT id, customer, amount, status, created_at FROM default.orders_current;
