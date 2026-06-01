-- Cluster bootstrap script.
--
-- Patroni creates the database and admin users; this script then creates the
-- least-privilege application user (`mulligan_app`) used by all four UI
-- services. Replicated automatically to the two standby nodes via Postgres
-- streaming replication.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'mulligan_app') THEN
        CREATE ROLE mulligan_app LOGIN PASSWORD 'mulligan_app_pw';
    END IF;
END
$$;

ALTER ROLE mulligan_app SET search_path = public;

-- Grant just enough privileges for the application workload. No SUPERUSER,
-- no CREATEROLE, no replication.
GRANT CONNECT ON DATABASE mulligan_db TO mulligan_app;
GRANT USAGE ON SCHEMA public TO mulligan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO mulligan_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO mulligan_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO mulligan_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO mulligan_app;
