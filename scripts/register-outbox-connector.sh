#!/bin/sh
set -eu

CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
curl -fsS -X PUT -H 'Content-Type: application/json' \
  --data-binary @config/debezium/orders-outbox-connector.json \
  "$CONNECT_URL/connectors/orders-outbox/config"
printf '\n'
