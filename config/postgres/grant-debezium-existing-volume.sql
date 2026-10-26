-- Execute manually as a PostgreSQL administrator after Flyway V2 has run.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'debezium') THEN
    CREATE ROLE debezium WITH LOGIN REPLICATION PASSWORD 'debezium-local-only';
  END IF;
END $$;
GRANT CONNECT ON DATABASE appdb TO debezium;
GRANT USAGE ON SCHEMA public TO debezium;
GRANT SELECT ON TABLE public.outbox TO debezium;
ALTER DEFAULT PRIVILEGES FOR ROLE app IN SCHEMA public
  GRANT SELECT ON TABLES TO debezium;
