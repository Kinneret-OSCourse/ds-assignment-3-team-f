# Assignment 2 Red-Team Package

The official Assignment 2 handoff package is:

```text
Parking-System-Red-Team-Assignment2.zip
```

It is generated from the current repository after a successful Java 21 build.
The package is intended for other teams and graders who should be able to run
the system without source-code access.

## Required Contents

| Path in ZIP | Purpose |
|---|---|
| `customer-ui-1.0.jar` | Current Customer UI/CLI JAR |
| `peo-ui-1.0.jar` | Current PEO UI/CLI JAR |
| `mo-ui-1.0.jar` | Current MO UI/CLI JAR |
| `parking-server-1.0.jar` | Current queue server JAR |
| `distributions/` | Current Gradle distribution ZIP/TAR artifacts with dependency scripts |
| `local-libs/` | Local AMQP library dependency used by Gradle/modules |
| `docker-compose.yml` | Assignment 2 Docker cluster deployment |
| `docker-compose.tls.yml` | Optional AMQPS overlay used to enable one-way TLS |
| `parking-server/Dockerfile` | Server container build |
| `parking-system-CustomerUI/Dockerfile` | Customer CLI container build |
| `parking-system-PEOUI/Dockerfile` | PEO CLI container build |
| `parking-system-MOUI/Dockerfile` | MO CLI container build |
| `infra/postgres/` | DB schema, full seed data, app role bootstrap |
| `infra/rabbitmq/` | RabbitMQ users, ACLs, cluster config, enabled plugins |
| `infra/haproxy/` | HAProxy primary routing config |
| `scripts/` | Cert generation, quorum grow, security, failover scripts |
| `setup-data/` | Assignment setup-data SQL |
| `README.md` | Course/team/accounts/build/run overview |
| `DEPLOY.md` | Local/Docker/TLS deployment instructions |
| `Defense.md` | Blue-team defense report |
| `DATABASE_DESIGN.md` | Database schema/cluster design |
| `QUEUE_DESIGN.md` | RabbitMQ topology/security design |
| `TESTING.md` | Acceptance, failover, security tests and proof checklist |
| `RED_TEAM_PACKAGE.md` | This package manifest |

## Documented Accounts

| Role | Username | Password |
|---|---|---|
| Customer | `CUST-1001` | `Cust1001!` |
| PEO | `PEO-1001` | `Peo1001!` |
| MO | `MO-1001` | `Mo1001!` |

RabbitMQ service users are documented in `README.md` and provisioned by
`infra/rabbitmq/definitions.json`.

## Generation Command

After `.\gradlew.bat clean build` succeeds:

```powershell
.\scripts\make-red-team-package.ps1
```

The script recreates `Parking-System-Red-Team-Assignment2.zip` from current
build outputs and current documentation/configuration files.
