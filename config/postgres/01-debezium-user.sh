#!/bin/sh
set -eu

psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=dbz_password="${DEBEZIUM_PASSWORD:-debezium-local-only}" <<'SQL'
CREATE ROLE debezium WITH LOGIN REPLICATION PASSWORD :'dbz_password';
GRANT CONNECT ON DATABASE appdb TO debezium;
GRANT USAGE ON SCHEMA public TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE app IN SCHEMA public
  GRANT SELECT ON TABLES TO debezium;
SQL
