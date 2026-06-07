# Mulligan Parking System  Assignment 3

Course: Distributed Systems
Semester: 2, 5786

The old Round 2 exploit package and generated evidence were removed from the
source tree. Assignment 3 keeps the blue-team fixes, implementation, deployment
files, and design/test documentation needed to build and grade the system


## Team

| Student Name    | Student ID | Main Task in Red Teaming 2 | Hours |
| --------------- | ---------- | -------------------------- | ----: |
| Mohammad Drwish | 319043402  | Report assembly and evidence review | 3 |
| Hady Amasha     | 326347564  | Target deployment and screenshot evidence | 3 |
| Fares Elias     | 324932474  | RabbitMQ attack validation | 3 |
| Rojeh Safieh    | 212793824  | Database/TLS attack validation | 3 |



Hour estimates are pre-grading; final tally is in the team retro.

## What changed since Assignment 1

| Area | Assignment 1 | Assignment 2 |
| --- | --- | --- |
| Database | Single PostgreSQL container | 3-node Patroni cluster behind HAProxy |
| Queue | Single RabbitMQ container, classic queues | 3-node RabbitMQ cluster, quorum queues, per-service users |
| Transport | Plain AMQP, plain JDBC | TLS-capable, configurable per env |
| Messages | Raw JSON | HMAC-SHA256 signed envelopes (UUID nonce + Unix timestamp) |
| Replay protection | None | Server-side `NonceStore` (60 s TTL) |
| Credentials | Hardcoded `mulligan:mulligan123`, `postgres:pass159357` | Env-only; per-service users with least-privilege ACLs |
| Error responses | `RuntimeException("Database error: " + ...)` | `ClientErrorCodes` (no stack trace leaves the server) |
| Secure log | None | Persistent append-only log via `SecureLogger` |
| Failover | None | Multi-host JDBC URL + cluster-aware AMQP client |

## Modules

```
parking-common/         shared security, messaging, validation, cluster helpers
parking-server/         queue server (HMAC verifier + NonceStore consumer)
parking-system-CustomerUI/  Customer JavaFX UI + CLI
parking-system-PEOUI/       PEO JavaFX UI + CLI
parking-system-MOUI/        MO JavaFX UI + CLI
parking-recommender/        3-node recommender server cluster + GUI/CLI
infra/postgres/         init.sql, full-seed.sql, cluster-bootstrap.sql
infra/rabbitmq/         rabbitmq.conf, definitions.json, join-cluster.sh
infra/haproxy/          haproxy.cfg
infra/certs/            (generated) TLS material for AMQPS / mTLS
scripts/                cert generation, quorum growth, failover, security, package scripts
```

## Documented Demo Accounts

The seeded demo accounts below are loaded by `infra/postgres/init.sql` with
password hashes, so passwords are not stored in plain text in the database.

| Role     | Username (`*_id`) | Password    | Notes |
| -------- | ----------------- | ----------- | ----- |
| Customer | `CUST-1001`       | `Cust1001!` | Pre-assigned vehicle `604-95-839` |
| PEO      | `PEO-1001`        | `Peo1001!`  | Can issue citations on any space |
| MO       | `MO-1001`         | `Mo1001!`   | Reads `Transactions` and `Citations` queues |

The Postgres-side hashes for these accounts are already in `init.sql`. RabbitMQ
broker accounts (per-service, used by the apps themselves, not by end users):

| RabbitMQ user      | Used by         | Permissions |
| ------------------ | --------------- | ----------- |
| `mulligan_admin`   | operators only  | full access |
| `mulligan_server`  | parking-server  | consume Transactions+Citations, write to exchange |
| `mulligan_customer`| Customer UI/CLI | publish to `transaction.completed` only |
| `mulligan_peo`     | PEO UI/CLI      | publish to `citation.issued` only |
| `mulligan_mo`      | MO UI/CLI       | consume Transactions+Citations only |

Passwords are stored in environment variables (`MULLIGAN_QUEUE_*_PASSWORD`)
- they default to development values in `docker-compose.yml`. Override them
in production by setting the matching env or .env file.

## Required environment

`MULLIGAN_HMAC_KEY` must be set to a 32+ byte secret on every host that
publishes or verifies queue messages. Generate one with:

```bash
openssl rand -hex 32
```

All other variables have safe defaults in `docker-compose.yml` for local
development.

## Build locally

```bash
./gradlew clean build
```

Runs the JUnit security suite under `parking-common`, builds every module,
and produces installable distributions under `<module>/build/install/`.

## Run locally (no Docker)

```bash
export MULLIGAN_HMAC_KEY=$(openssl rand -hex 32)
./gradlew :parking-server:run            &  # consumes queues
./gradlew :parking-system-CustomerUI:run &  # JavaFX UI
./gradlew :parking-system-PEOUI:run      &
./gradlew :parking-system-MOUI:run       &
./gradlew :parking-system-CustomerUI:runCli --args="start CUST-1001 604-95-839 S001"
```

A locally-running Postgres + RabbitMQ on `localhost:5432` / `localhost:5672`
is enough for the Gradle `:run` tasks if you don't want the full Docker
cluster.

## Run with Docker (3-node clusters)

```bash
export MULLIGAN_HMAC_KEY=$(openssl rand -hex 32)
./scripts/generate-certs.sh                # optional, for AMQPS
docker compose up --build
./scripts/grow-quorum-queues.sh
```

On Windows PowerShell, use `.\scripts\generate-certs.ps1` and
`.\scripts\grow-quorum-queues.ps1`. If local ports are already busy, set
`MULLIGAN_DB_PORT`, `MULLIGAN_RABBIT_AMQP_PORT`,
`MULLIGAN_RABBIT_MANAGEMENT_PORT`, and `MULLIGAN_HAPROXY_STATS_PORT` before
`docker compose up`.

The compose file brings up etcd + 3 Patroni Postgres nodes + HAProxy + 3
RabbitMQ nodes + parking-server + the three UI containers.

## Recommender and Consensus

Customer GUI and CLI support "Recommend Parking". The customer app calls one
of the three recommender nodes; that node coordinates a strict-majority vote
with its peers and returns the agreed list in the assignment format:
`S003;1` or `S002;3, S004;3`.

```bash
./gradlew :parking-system-CustomerUI:runCli --args="recommend S003"
./gradlew :parking-recommender:runCli --args="mode http://localhost:8081 malicious"
```

The protocol details are in [`CONSENSUS_DESIGN.md`](CONSENSUS_DESIGN.md).

## 9-Computer Classroom Run

Assignment 3 expands this to a 12-computer run: 3 PostgreSQL nodes, 3 RabbitMQ
nodes, 3 recommender nodes, and 3 UI machines. Use
`.\scripts\start-12-computer-node.ps1 -Role rec1`, `rec2`, and `rec3` for the
new recommender machines.

Use [LAB_9_LAPTOPS.md](LAB_9_LAPTOPS.md) as the authoritative from-zero classroom runbook. It includes fixed IP setup, `.env` values, TLS certificate generation, firewall rules, Docker cleanup, exact commands for computers 1 through 9, and the 3-computer smoke-test mapping.

Short version:

| Computer | Role | Command |
| --- | --- | --- |
| 1 | PostgreSQL node 1 | `.\scripts\start-9-laptop-node.ps1 -Role db1` |
| 2 | PostgreSQL node 2 | `.\scripts\start-9-laptop-node.ps1 -Role db2` |
| 3 | PostgreSQL node 3 | `.\scripts\start-9-laptop-node.ps1 -Role db3` |
| 4 | RabbitMQ node 1 | `.\scripts\start-9-laptop-node.ps1 -Role rmq1` |
| 5 | RabbitMQ node 2 | `.\scripts\start-9-laptop-node.ps1 -Role rmq2` |
| 6 | RabbitMQ node 3 | `.\scripts\start-9-laptop-node.ps1 -Role rmq3` |
| 7 | Recommender node 1 | `.\scripts\start-12-computer-node.ps1 -Role rec1` |
| 8 | Recommender node 2 | `.\scripts\start-12-computer-node.ps1 -Role rec2` |
| 9 | Recommender node 3 | `.\scripts\start-12-computer-node.ps1 -Role rec3` |
| 10 | Customer UI | `.\scripts\run-ui.ps1 -App customer` |
| 11 | PEO UI | `.\scripts\run-ui.ps1 -App peo` |
| 12 | MO UI | `.\scripts\run-ui.ps1 -App mo` |

For a 3-computer smoke test, run computer 1 as `db1 + rmq1`, computer 2 as `db2 + rmq2`, and computer 3 as `db3 + rmq3`.

CLI access while the cluster is running:

```bash
docker compose exec customer-ui java -cp /app/lib/* com.mulligan.customer.cli.CustomerCLI events CUST-1001 604-95-839
docker compose exec peo-ui      java -cp /app/lib/* com.mulligan.peo.cli.PEOCLI check 604-95-839 S001
docker compose exec mo-ui       java -cp /app/lib/* com.mulligan.mo.cli.MOCLI transactions
```

Management UI: `http://localhost:15672` (`mulligan_admin` / `mulligan_admin_pw`).
HAProxy stats:  `http://localhost:7000`.

## Test failover

Database (kills the current Patroni leader, asserts a write through HAProxy
still works, then restarts the killed node):

```bash
./scripts/failover-db.sh
```

RabbitMQ (stops `rabbit-2`, publishes through `rabbit-1`, restarts
`rabbit-2`):

```bash
./scripts/failover-rabbit.sh
```

## Run security tests

```bash
./gradlew :parking-common:test --tests com.mulligan.common.SecurityLayerTest
./scripts/security-checks.sh    # cluster-wide smoke (bad HMAC, replay, stale)
```

PowerShell equivalent: `.\scripts\security-checks.ps1`.

Expected output: every test prints `PASS`, the secure log under
`/var/log/mulligan/security.log` contains one `REJECT` line per intentional
failure.

## Documentation

- [`Defense.md`](Defense.md) ג€” Blue Team report (executive summary,
  vulnerability inventory, root cause, fixes, security architecture, test
  results, lessons learned).
- [`DEPLOY.md`](DEPLOY.md) ג€” single-machine and 3-host deployment.
- [`LAB_9_LAPTOPS.md`](LAB_9_LAPTOPS.md) ג€” from-zero 9-computer classroom
  deployment and 3-computer smoke test.
- [`DATABASE_DESIGN.md`](DATABASE_DESIGN.md) ג€” Postgres schema and cluster
  topology.
- [`QUEUE_DESIGN.md`](QUEUE_DESIGN.md) ג€” RabbitMQ topology, quorum queues,
  per-service ACL.
- [`TESTING.md`](TESTING.md) ג€” acceptance tests, failover tests, security
  tests.

