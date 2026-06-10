#!/bin/bash
# Purpose: runs once on the first boot of postgres-node1 in 12-computer lab
# mode, after the seed and cluster-bootstrap SQL scripts. It aligns the
# mulligan_app role password with MULLIGAN_DB_APP_PASSWORD from .env so the
# UI and recommender computers can log in with the shared lab password.
# Input: MULLIGAN_DB_APP_PASSWORD and POSTGRESQL_PASSWORD environment values.
# Output: none (exits non-zero when the ALTER ROLE statement fails).
set -e

PGPASSWORD="${POSTGRESQL_PASSWORD}" psql -U postgres -h 127.0.0.1 -p 5432 -d mulligan_db \
    -v ON_ERROR_STOP=1 \
    -c "ALTER ROLE mulligan_app WITH LOGIN PASSWORD '${MULLIGAN_DB_APP_PASSWORD:-mulligan_app_pw}';"
