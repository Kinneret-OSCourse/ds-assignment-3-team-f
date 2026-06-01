# Database Design — Assignment 2

## 1. Engine and Cluster

PostgreSQL 16 managed by **Patroni** (Spilo image), with **etcd** as the
DCS. Three Patroni members form the cluster: `patroni-1`, `patroni-2`,
`patroni-3`. HAProxy in front of the cluster proxies clients to the
current primary by inspecting Patroni's REST `/master` endpoint.

```
client ──► HAProxy :5432 ──► patroni-{1,2,3}:5432 ──► PG primary
                                    │
                                    └──► PG synchronous + async replicas
```

Application JDBC URL (built by `PostgresClusterConnection`):

```
jdbc:postgresql://patroni-1:5432,patroni-2:5432,patroni-3:5432/mulligan_db
    ?targetServerType=primary&connectTimeout=5&socketTimeout=30&tcpKeepAlive=true
```

Failover behaviour:

- Patroni election handles primary failure; etcd lease holds the leadership.
- HAProxy's TCP listener accepts only the node whose `/master` returns 200.
- The JDBC URL targets `primary` so a client that bypasses HAProxy and
  contacts a stale replica directly gets a clear connect error and rotates
  to the next candidate.

## 2. Roles

| Role | Purpose | Source |
| --- | --- | --- |
| `postgres` | Superuser, Patroni-only | Spilo bootstrap |
| `admin`    | Administrative, Patroni-only | Spilo bootstrap |
| `standby`  | Replication user | Spilo bootstrap |
| `mulligan_app` | Application role with least privilege | `infra/postgres/cluster-bootstrap.sql` |

`mulligan_app` is granted `CONNECT, USAGE, SELECT/INSERT/UPDATE/DELETE` on
the public schema, but not `SUPERUSER`, not `CREATEROLE`, not
`REPLICATION`. All three UIs and the parking-server log in as
`mulligan_app`.

## 3. Schema (selected tables)

The full DDL is in `infra/postgres/init.sql`. Key tables:

```
vehicles(vehicle_id PK, plate_number UNIQUE, owner_name)
parking_zones(zone_id PK, zone_code UNIQUE, hourly_rate, max_parking_minutes)
parking_spaces(space_id PK, space_number UNIQUE, zone_id FK, street_name, is_active)
parking_events(event_id PK, vehicle_id FK, space_id FK, customer_id, status, start_time, end_time, calculated_amount)
payment_transactions(transaction_id PK, event_id FK, vehicle_id FK, space_id FK, zone_code, start_time, stop_time, amount)
citations(citation_id PK, vehicle_id FK, space_id FK, zone_code, inspection_time, amount)
customers(customer_id PK, vehicle_id UNIQUE FK, display_name, password_hash, password_salt, password_iterations, is_active)
peo_users(user_id PK, display_name, password_hash, password_salt, password_iterations, is_active)
mo_users(user_id PK, display_name, password_hash, password_salt, password_iterations, is_active)
```

Indexes accelerate the queries that drive the SUC use cases:

| Index | Purpose |
| --- | --- |
| `idx_vehicles_plate_number` | vehicle lookup by plate |
| `idx_spaces_space_number` | space lookup by number |
| `idx_events_customer_active` (partial: `end_time IS NULL`) | "is there an active session" |
| `idx_payment_transactions_vehicle_id` | customer history list |
| `idx_payment_transactions_stop_time DESC` | MO report ordering |
| `idx_citations_vehicle_id`, `idx_citations_inspection_time DESC` | PEO/MO reports |

## 4. Important Queries

Customer "list parking events":

```sql
SELECT pe.event_id, pe.start_time, pe.end_time, pe.status, pe.calculated_amount,
       ps.space_number, pz.zone_code
FROM parking_events pe
JOIN parking_spaces ps ON pe.space_id = ps.space_id
JOIN parking_zones pz ON ps.zone_id = pz.zone_id
WHERE pe.vehicle_id = ?
ORDER BY pe.start_time DESC;
```

PEO "check legality":

```sql
SELECT pe.event_id, pe.status, ps.space_number, pz.zone_code
FROM parking_events pe
JOIN parking_spaces ps ON pe.space_id = ps.space_id
JOIN parking_zones pz ON ps.zone_id = pz.zone_id
WHERE pe.vehicle_id = (SELECT vehicle_id FROM vehicles WHERE plate_number = ?)
  AND ps.space_number = ?
  AND pe.status = 'STARTED'
  AND pe.end_time IS NULL
LIMIT 1;
```

PEO "issue citation":

```sql
INSERT INTO citations (citation_id, vehicle_id, space_id, zone_code, inspection_time, amount)
VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?);
```

Auto-stop-then-start (Customer SUC-1):

```sql
UPDATE parking_events
SET end_time = ?, status = 'STOPPED', calculated_amount = ?
WHERE event_id = ?;

INSERT INTO parking_events (vehicle_id, space_id, customer_id, start_time, status)
VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'STARTED')
RETURNING event_id, start_time;
```

Both statements run inside a single `BEGIN / COMMIT` so the customer can
never have two `STARTED` rows for the same vehicle.

## 5. Failover Test

`./scripts/failover-db.sh` kills the current Patroni leader, waits for a
new leader to be elected, and asserts that an `INSERT` through HAProxy
succeeds. The killed node is restarted at the end of the script so the
cluster returns to three members.

## 6. Backup and Restore

Patroni's Spilo image ships with `pg_basebackup` based replication; for a
production deployment you would point Spilo at WAL-E / WAL-G or pgBackRest
storage. For the assignment grading flow the Docker volumes
(`patroni{1,2,3}_data`) persist between restarts; a fresh `docker compose
down -v` removes them.
