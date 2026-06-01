# Mulligan Parking System - Assignment 3

Course: Distributed Systems
Semester: 2, 5786
Assignment: 3 - Recommender and Consensus

## Assignment 3 Additions

| Student Name | Student ID | Main Task in Assignment 3 | Hours |
| --- | --- | --- | ---: |
| Mohammad Drwish | 319043402 | Task 5 - Blue Teaming, Defense.md, security hardening review | 12 |
| Hady Amasha | 326347564 | Task 1 - Customer GUI/CLI recommender interaction | 10 |
| Fares Elias | 324932474 | Task 2 - Recommender server and malicious mode | 14 |
| Rojeh Safieh | 212793824 | Task 3/4 - Consensus protocol, Docker/Gradle/tests/docs | 14 |

New module: `recommender-server/`.

New customer command:

```bash
MULLIGAN_RECOMMENDER_URL=http://localhost:8081 ./gradlew :parking-system-CustomerUI:runCli --args="recommend S003"
```

Start the recommender cluster locally in three terminals:

```bash
./gradlew :recommender-server:run --args="--node-id=recommender-1 --port=8081 --peers=http://localhost:8082,http://localhost:8083"
./gradlew :recommender-server:run --args="--node-id=recommender-2 --port=8082 --peers=http://localhost:8081,http://localhost:8083"
./gradlew :recommender-server:run --args="--node-id=recommender-3 --port=8083 --peers=http://localhost:8081,http://localhost:8082"
```

The Docker stack now includes `recommender-1`, `recommender-2`, and `recommender-3`. Per-node malicious mode is controlled with `MULLIGAN_RECOMMENDER_1_MALICIOUS`, `MULLIGAN_RECOMMENDER_2_MALICIOUS`, and `MULLIGAN_RECOMMENDER_3_MALICIOUS`.

See [CONSENSUS_PROTOCOL.md](CONSENSUS_PROTOCOL.md) for the protocol and [TESTING.md](TESTING.md) for the recommender/consensus test plan.

## Assignment 2 Baseline

Course: Distributed Systems
Semester: 2, 5786
Assignment: 2 - Distributed Data Storage + Blue Team Defenses

## Team

| Student Name    | Student ID | Main Task in Assignment 2 | Hours |
| --------------- | ---------- | ------------------------- | ----: |
| Mohammad Drwish | 319043402  | Task 5 - Blue Teaming, Defense.md, security layer (parking-common) | 12 |
| Hady Amasha     | 326347564  | Task 1 - UI/CLI cluster adaptation (Customer, PEO, MO) | 10 |
| Fares Elias     | 324932474  | Task 2 - RabbitMQ 3-node cluster + quorum queues | 18 |
| Rojeh Safieh    | 212793824  | Task 3 - Patroni Postgres cluster, Task 4 - DevOps/Docker/Tests | 10 |

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
infra/postgres/         init.sql, full-seed.sql, cluster-bootstrap.sql
infra/rabbitmq/         rabbitmq.conf, definitions.json, join-cluster.sh
infra/haproxy/          haproxy.cfg
infra/certs/            (generated) TLS material for AMQPS / mTLS
scripts/                cert generation, quorum growth, failover, security, package scripts
```

## Documented Accounts (Red-Team Pack)

Hand these to the red team along with
`Parking-System-Red-Team-Assignment2.zip`. Passwords follow the documented
seed below; full hashed records are loaded by
`infra/postgres/init.sql` so passwords are not stored in plain text in the
database.

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

## 9-Computer Classroom Run

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
| 7 | Customer UI | `.\scripts\run-ui.ps1 -App customer` |
| 8 | PEO UI | `.\scripts\run-ui.ps1 -App peo` |
| 9 | MO UI | `.\scripts\run-ui.ps1 -App mo` |

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

- [`Defense.md`](Defense.md) - Blue Team report (executive summary,
  vulnerability inventory, root cause, fixes, security architecture, test
  results, lessons learned).
- [`DEPLOY.md`](DEPLOY.md) - single-machine and 3-host deployment.
- [`LAB_9_LAPTOPS.md`](LAB_9_LAPTOPS.md) - from-zero 9-computer classroom
  deployment and 3-computer smoke test.
- [`DATABASE_DESIGN.md`](DATABASE_DESIGN.md) - Postgres schema and cluster
  topology.
- [`QUEUE_DESIGN.md`](QUEUE_DESIGN.md) - RabbitMQ topology, quorum queues,
  per-service ACL.
- [`TESTING.md`](TESTING.md) - acceptance tests, failover tests, security
  tests.

## Repository layout (Red-Team package)

```
Parking-System-Red-Team-Assignment2.zip
|-- customer-ui-1.0.jar
|-- peo-ui-1.0.jar
|-- mo-ui-1.0.jar
|-- parking-server-1.0.jar
|-- distributions/
|-- docker-compose.yml
|-- docker-compose.tls.yml
|-- infra/
|-- scripts/
|-- local-libs/
|-- setup-data/
|-- README.md
|-- DEPLOY.md
|-- Defense.md
|-- DATABASE_DESIGN.md
|-- QUEUE_DESIGN.md
|-- TESTING.md
`-- RED_TEAM_PACKAGE.md
```

Source code is **not** included in the red-team package, per the
assignment's red-team package rules.

Regenerate the package after a successful build:

```powershell
.\scripts\make-red-team-package.ps1
```
