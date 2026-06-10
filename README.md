# Mulligan Parking System - Assignment 3

- Course: SE 424: Distributed Systems
- Semester: Semester 2, 5786 (2025-2026)

## Team

| Student Name    | Student ID | Main Task in Assignment 3 | Hours |
| --------------- | ---------- | ------------------------- | ----: |
| Mohammad Drwish | 319043402  | Task 5 — Blue Teaming, Defense.md, security layer (parking-common) | 12 |
| Hady Amasha     | 326347564  | Task 1 — UI/CLI cluster adaptation (Customer, PEO, MO) | 10 |
| Fares Elias     | 324932474  | Task 2 — RabbitMQ 3-node cluster + quorum queues | 18 |
| Rojeh Safieh    | 212793824  | Task 3 — Patroni Postgres cluster, Task 4 — DevOps/Docker/Tests | 10 |

## Scope

This repository contains the Assignment 3 system:

- Customer, PEO, and MO JavaFX/CLI applications.
- PostgreSQL replicated database cluster.
- RabbitMQ quorum queue cluster with mTLS.
- Three recommender nodes using majority consensus.
- A 12-computer classroom deployment runbook.

## Modules

```text
parking-common/             shared database, messaging, TLS, validation, security
parking-server/             queue consumer and signed-message verifier
parking-system-CustomerUI/  customer UI and CLI
parking-system-PEOUI/       parking enforcement officer UI and CLI
parking-system-MOUI/        municipality officer UI and CLI
parking-recommender/        3-node recommender and consensus service
infra/postgres/             database schema, seed data, cluster bootstrap
infra/rabbitmq/             RabbitMQ definitions and TLS/mTLS configs
infra/haproxy/              HAProxy config
scripts/                    setup, TLS, test, and deployment helpers
```

## Demo Accounts

| Role | Username | Password |
| --- | --- | --- |
| Customer | `CUST-1001` | `Cust1001!` |
| PEO | `PEO-1001` | `Peo1001!` |
| MO | `MO-1001` | `Mo1001!` |

## Build And Test

```powershell
.\gradlew.bat clean build --no-daemon
docker compose config --quiet
docker compose -f docker-compose.12-computers.yml --env-file .env.12-computers.example config --quiet
```

## Local Docker Run

Generate TLS material once:

```powershell
.\scripts\generate-certs.ps1
```

Start the local stack:

```powershell
docker compose up --build
```

The local stack includes Postgres, RabbitMQ, the queue server, all three UI
containers, and three recommender nodes.

## Recommender Consensus

The customer app supports parking recommendation. It calls one recommender
node, and that node asks the other two nodes for their result. A recommendation
is accepted only when a strict majority agrees.

CLI examples:

```powershell
.\gradlew.bat :parking-system-CustomerUI:runCli --args="recommend S003" --no-daemon
.\gradlew.bat :parking-recommender:runCli --args="mode http://localhost:8081 malicious" --no-daemon
```

Protocol details are in [CONSENSUS_DESIGN.md](CONSENSUS_DESIGN.md).

## 12-Computer Classroom Run

Use [LAB_12_COMPUTERS.md](LAB_12_COMPUTERS.md) for the full classroom setup.
The 12-computer deployment has 9 Docker server computers and 3 Java UI
computers. The 9 Docker computers can be started through the shared
`docker-compose.12-computers.yml` profile file or through the per-role wrapper
files `docker-compose-db1.yml` ... `docker-compose-rec3.yml`. The UI computers
use `scripts\run-ui.ps1`, so they do not need their own compose files.

Short role map:

| Computer | Role | Command |
| --- | --- | --- |
| 1 | PostgreSQL node 1 | `.\scripts\start-12-computer-node.ps1 -Role db1` |
| 2 | PostgreSQL node 2 | `.\scripts\start-12-computer-node.ps1 -Role db2` |
| 3 | PostgreSQL node 3 | `.\scripts\start-12-computer-node.ps1 -Role db3` |
| 4 | RabbitMQ node 1 | `.\scripts\start-12-computer-node.ps1 -Role rmq1` |
| 5 | RabbitMQ node 2 | `.\scripts\start-12-computer-node.ps1 -Role rmq2` |
| 6 | RabbitMQ node 3 | `.\scripts\start-12-computer-node.ps1 -Role rmq3` |
| 7 | Recommender node 1 | `.\scripts\start-12-computer-node.ps1 -Role rec1` |
| 8 | Recommender node 2 | `.\scripts\start-12-computer-node.ps1 -Role rec2` |
| 9 | Recommender node 3 | `.\scripts\start-12-computer-node.ps1 -Role rec3` |
| 10 | Customer UI | `.\scripts\run-ui.ps1 -App customer` |
| 11 | PEO UI | `.\scripts\run-ui.ps1 -App peo` |
| 12 | MO UI | `.\scripts\run-ui.ps1 -App mo` |

## Important Environment Files

- Copy `.env.12-computers.example` to `.env` before running classroom nodes.
- Keep generated TLS files out of Git. They are ignored under `infra/certs/`
  and `infra/private-ca/`.
- Use the same `.env` values on all 12 computers.

## Documentation

- [CONSENSUS_DESIGN.md](CONSENSUS_DESIGN.md)
- [LAB_12_COMPUTERS.md](LAB_12_COMPUTERS.md)
- [DATABASE_DESIGN.md](DATABASE_DESIGN.md)
- [QUEUE_DESIGN.md](QUEUE_DESIGN.md)
- [DEPLOY.md](DEPLOY.md)
- [Defense.md](Defense.md)
- [TESTING.md](TESTING.md)
