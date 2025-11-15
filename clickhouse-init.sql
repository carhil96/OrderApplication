CREATE DATABASE IF NOT EXISTS default;

CREATE TABLE IF NOT EXISTS default.orders (
    id UInt64,
    customer String,
    amount Float64,
    status String,
    created_at DateTime
) ENGINE = MergeTree()
ORDER BY (id, created_at);
