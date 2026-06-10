-- Cluster bootstrap script.
--
-- Patroni creates the database and admin users; this script then creates the
-- least-privilege application user (`mulligan_app`) used by all four UI
-- services. Replicated automatically to the two standby nodes via Postgres
-- streaming replication.

\set app_password `if [ -n "$MULLIGAN_DB_APP_PASSWORD" ]; then printf %s "$MULLIGAN_DB_APP_PASSWORD"; else printf %s "mulligan_app_pw"; fi`

SELECT format('CREATE ROLE mulligan_app LOGIN PASSWORD %L', :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'mulligan_app') \gexec

ALTER ROLE mulligan_app WITH PASSWORD :'app_password';
ALTER ROLE mulligan_app SET search_path = public;

-- Grant just enough privileges for the application workload. No SUPERUSER,
-- no CREATEROLE, no replication.
GRANT CONNECT ON DATABASE mulligan_db TO mulligan_app;
GRANT USAGE ON SCHEMA public TO mulligan_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO mulligan_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO mulligan_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO mulligan_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO mulligan_app;

CREATE TABLE IF NOT EXISTS security_nonces (
    nonce VARCHAR(128) PRIMARY KEY,
    first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_security_nonces_first_seen ON security_nonces(first_seen);
GRANT SELECT, INSERT, DELETE ON security_nonces TO mulligan_app;
