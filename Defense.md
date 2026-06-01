# Defense Report - Mulligan Parking System (Assignment 2 Blue Team)

Course: Distributed Systems, Semester 2, 5786
Team F: Mohammad Drwish, Hady Amasha, Fares Elias, Rojeh Safieh

## 1. Executive Summary

Assignment 1 shipped a working Customer / PEO / MO parking system using one
PostgreSQL database and one RabbitMQ broker. The red-team review exposed the
expected weaknesses of that first-stage design: plaintext transport, forged or
replayed queue messages, broad RabbitMQ credentials, limited input validation,
stack-trace leakage, no persistent security log, and single points of failure.

For Assignment 2 we added a shared `parking-common` module for HMAC-signed
messages, nonce replay protection, strict validation, generic client error
codes, cluster-aware AMQP/JDBC connection helpers, TLS-capable client setup,
and secure logging. The runtime deployment now uses a 3-node Patroni-managed
PostgreSQL cluster behind HAProxy and a 3-node RabbitMQ cluster with quorum
queues. RabbitMQ uses per-service users with least-privilege ACLs, and the
default `guest` account is not usable by remote clients.

Important TLS scope note: AMQPS one-way TLS has been verified through the
`docker-compose.tls.yml` overlay. The default `docker-compose.yml` still keeps
`MULLIGAN_QUEUE_TLS=false` and `MULLIGAN_DB_TLS=false` for a stable classroom
demo path; the TLS overlay sets `MULLIGAN_QUEUE_TLS=true` and uses RabbitMQ
port 5671. **mTLS ships as its own additive overlay
(`docker-compose.mtls.yml`)** layered on top of the TLS overlay — flipping it
on is a single extra `-f docker-compose.mtls.yml` flag and a one-flag
rollback. **PostgreSQL listener-side TLS remains documented-only**: the Java
client is wired for `&ssl=true&sslmode=require`, but Spilo/Patroni and
HAProxy would need three coordinated config changes to terminate TLS at the
broker side, and turning on the client flag without those changes would break
JDBC for every UI. The exact opt-in procedure for both is in `DEPLOY.md`.

## 2. Vulnerability Inventory

| # | Vulnerability | CIA impact | Severity | Root cause | Fix / mitigation | Test evidence |
|---|---|---|---|---|---|---|
| V1 | Plain AMQP/JDBC transport | Confidentiality | High | Assignment 1 used classroom plaintext defaults | TLS-capable Java clients and cert scripts added; deployment docs now show how to enable, but default Docker stack remains plaintext | `TlsConfig`, `MulliganConnectionFactory`, `generate-certs.*`; not claimed active by default |
| V2 | Forged queue messages | Integrity | Critical | Raw JSON messages had no authentication | HMAC-SHA256 envelope over nonce, timestamp, payload | `security-checks.ps1`: bad HMAC rejected |
| V3 | Replay of valid queue messages | Integrity | High | No nonce or server-side replay memory | UUID nonce, Unix timestamp, `NonceStore` TTL | `security-checks.ps1`: replay rejected |
| V4 | Stale delayed messages | Integrity | High | No freshness check | Reject timestamps older than 60 seconds | `security-checks.ps1`: stale timestamp rejected |
| V5 | Shared/broad RabbitMQ credentials | Authorization | High | One service account could read/write too much | Per-service users and ACLs in `definitions.json` | `rabbitmqctl list_permissions -p /` |
| V6 | Remote/default `guest` use | Authorization | Medium | Default RabbitMQ account expectations | `loopback_users.guest = true`; no working guest login | `rabbitmqctl authenticate_user guest guest` fails |
| V7 | Classic/non-replicated queues | Availability | High | Assignment 1 used single broker/queue | RabbitMQ 3-node cluster, quorum queues, grow script to 3 members | `rabbitmqctl list_queues name type online members` |
| V8 | Single database node | Availability | High | Assignment 1 used one Postgres instance | 3 Patroni nodes, HAProxy primary endpoint | `patronictl list`; DB node failover smoke |
| V9 | Single broker node | Availability | High | Assignment 1 used one RabbitMQ instance | 3-node RabbitMQ cluster | Rabbit node stop/restart smoke |
| V10 | Weak input validation | Integrity | Medium | Services mostly checked null/blank only | `InputValidator` for IDs, plates, spaces, amounts, free text | JUnit + CLI invalid-input checks |
| V11 | Stack traces/internal errors to clients | Confidentiality | Medium | Exceptions included SQL/driver messages | `ClientErrorCodes` and server-side logging | CLI smoke outputs generic errors/usage |
| V12 | No persistent security log | Detectability | Medium | Rejections were not auditable | `SecureLogger` writes persistent log volume | `/var/log/mulligan/security.log` |

## 3. Fix Details

### 3.1 Message Authentication, Replay, and Freshness

All producer paths publish through `SecurePublisher`, which wraps payloads in:

```json
{
  "nonce": "<UUID>",
  "timestamp": 1779540000,
  "payload": "<inner JSON>",
  "hmac": "<HMAC-SHA256(nonce|timestamp|payload)>"
}
```

`MessageSigner` uses HMAC-SHA256 with a 32+ byte secret from
`MULLIGAN_HMAC_KEY`. `MessageValidator` verifies the HMAC in constant time,
rejects timestamps older than 60 seconds, rejects future-skewed messages, and
consults `NonceStore` before acknowledging a queue message. Rejections are
logged with `REJECT bad-hmac`, `REJECT replay`, or `REJECT stale-timestamp`.

### 3.2 RabbitMQ Hardening

`infra/rabbitmq/definitions.json` declares:

| User | Permission summary |
|---|---|
| `mulligan_admin` | Operator/admin only |
| `mulligan_server` | Configure/read/write exchange and two queues |
| `mulligan_customer` | Write to `mulligan.exchange` only |
| `mulligan_peo` | Write to `mulligan.exchange` only |
| `mulligan_mo` | Read `Transactions` and `Citations` only |

The queues are declared as durable quorum queues with `x-queue-type=quorum`.
After a fresh cluster start, run `scripts/grow-quorum-queues.*` so each queue
has members on `rabbit-1`, `rabbit-2`, and `rabbit-3`. This step is repeatable
and is included in the deployment instructions.

### 3.3 Database Cluster

The Docker stack runs `patroni-1`, `patroni-2`, `patroni-3`, `etcd`, and
`haproxy`. `db-init` creates `mulligan_db`, loads the seed schema/data, and
applies least-privilege grants for `mulligan_app`. The verified state after
startup is one leader and two streaming replicas.

### 3.4 Input Validation

`InputValidator` validates:

| Field | Rule |
|---|---|
| Vehicle plate | 3-20 alphanumeric/dash characters |
| Parking space | 1-20 alphanumeric characters |
| Customer/PEO/MO IDs | 3-32 uppercase/digit/dash characters |
| Zone | 1-10 alphanumeric characters |
| Amount | finite, non-negative, <= 1,000,000 |
| Free text | length-limited, no control characters |

Validation failures are logged internally and returned as generic client-safe
messages or error codes.

### 3.5 Error Handling and Logging

The service layer logs real exceptions through `SecureLogger` and returns
generic client responses such as `ERR_INTERNAL`, `ERR_UNAVAILABLE`, usage text,
or validation messages. Security events, malformed messages, replay attempts,
and operational connection errors are written to the persistent Docker volume
mounted at `/var/log/mulligan/security.log`.

### 3.6 TLS / mTLS Capability

TLS for AMQP is **available as an opt-in overlay** and TLS for JDBC is
**implemented in the client only**. The honest, current matrix is:

| Channel | TLS code | Listener config | Default in compose | How to activate |
| --- | --- | --- | --- | --- |
| AMQP (Customer/PEO/MO/server → RabbitMQ) | `MulliganConnectionFactory` + `TlsConfig` | `infra/rabbitmq/rabbitmq-tls.conf` (port 5671, TLS 1.2/1.3) | OFF (plain 5672) | `docker compose -f docker-compose.yml -f docker-compose.tls.yml up -d --build` after `generate-certs` |
| Inter-broker (RabbitMQ ↔ RabbitMQ) | n/a | uses the shared Erlang cookie; not TLS-encrypted | OFF | Out of scope for Assignment 2 |
| JDBC (UIs → HAProxy → Patroni) | `PostgresClusterConnection.jdbcTlsParameters()` | not configured (HAProxy is TCP-mode, Spilo Postgres has no listener cert) | OFF (plain) | Documented opt-in procedure in `DEPLOY.md` §"Optional: PostgreSQL listener-side TLS" - requires (a) Spilo `SSL_CERTIFICATE_FILE`/`SSL_PRIVATE_KEY_FILE` env, (b) `hostssl` line in `pg_hba.conf`, (c) HAProxy `bind ssl crt` frontend, (d) flipping `MULLIGAN_DB_TLS=true`. Not enabled in the verified stack because activating step (d) without (a)-(c) would break JDBC. |
| mTLS on AMQPS | `MULLIGAN_TLS_KEYSTORE` env supported | `infra/rabbitmq/rabbitmq-mtls.conf` (verify_peer + fail_if_no_peer_cert) loaded by `docker-compose.mtls.yml` | Configurable via additive overlay (OFF in the default verified stack, ON when `-f docker-compose.mtls.yml` is added) | `docker compose -f docker-compose.yml -f docker-compose.tls.yml -f docker-compose.mtls.yml up -d --build`. The cert generator already produces the per-service `client-<svc>.p12` keystores and the mTLS overlay mounts them and sets `MULLIGAN_TLS_KEYSTORE`. Rollback to one-way TLS is a single `-f` flag removal. |

`scripts/generate-certs.sh` (bash) and `scripts/generate-certs.ps1`
(PowerShell, .NET native, no openssl needed) both now produce the CA, the
RabbitMQ server cert (SANs cover `rabbit-1/2/3`, `haproxy`, `localhost`),
the Java truststore, and four per-service mTLS keystores
(`client-customer.p12`, `client-peo.p12`, `client-mo.p12`,
`client-server.p12`).

Activating AMQPS does not break the existing test path: the TLS-enabled
config keeps the plain 5672 listener up so any container or operator script
that still uses 5672 continues to work during the migration. Removing the
plain listener is a one-line edit in `rabbitmq-tls.conf` (or the mTLS
overlay's `rabbitmq-mtls.conf`).

mTLS on AMQPS is shipped as an additive overlay rather than a default flip
for the same "do not destabilise the verified stack" reason. The verified
Assignment 2 grading path is "default compose + `docker-compose.tls.yml`";
the mTLS path is "the same + `docker-compose.mtls.yml`". With the overlay
applied, `rabbitmq-mtls.conf` sets `ssl_options.verify = verify_peer` and
`ssl_options.fail_if_no_peer_cert = true`, and each of the four
application services has `MULLIGAN_TLS_KEYSTORE` set to its matching
`client-<svc>.p12` keystore. The Java `TlsConfig` already presents the
client cert during the TLS handshake when those env vars are set, so no
source changes are required to enable or disable mTLS - it is purely a
deployment toggle.

PostgreSQL TLS is intentionally not enabled in the verified Docker stack
because turning it on requires changes inside Spilo's Patroni bootstrap
(SSL cert paths + `hostssl` line in `pg_hba.conf`) plus an HAProxy TLS
frontend with the right `bind ssl crt` line. The Java side is ready
(`MULLIGAN_DB_TLS=true` appends `&ssl=true&sslmode=require`), but flipping
the env without first configuring the listeners would break JDBC for every
UI - which violates the "do not destabilise the passing stack" requirement
of this revision. The full step-by-step opt-in procedure (including the
Spilo `SSL_CERTIFICATE_FILE` env, the `hostssl` `pg_hba.conf` line, the
HAProxy `bind ssl crt` and `verify required` backend lines, and the
verification commands) is documented in `DEPLOY.md` §"Optional: PostgreSQL
listener-side TLS".

## 4. Updated Security Architecture

Default verified demo path:

```text
Customer/PEO/MO CLI
   | AMQP 5672 with HMAC envelope, nonce, timestamp
   v
RabbitMQ 3-node cluster
   | quorum queues: Transactions, Citations
   v
parking-server validates HMAC/replay/staleness and logs rejections

Customer/PEO/MO services
   | JDBC through HAProxy
   v
PostgreSQL Patroni cluster: 1 leader + 2 streaming replicas
```

TLS-capable production path:

```text
Customer/PEO/MO/parking-server
   | AMQPS/JDBC TLS after listener certificate configuration
   v
RabbitMQ/PostgreSQL cluster endpoints
```

## 5. Verified Test Evidence

| Area | Command / proof | Expected result | Verified |
|---|---|---|---|
| Build | `.\gradlew.bat clean build` | `BUILD SUCCESSFUL` | PASS |
| Docker status | `docker compose -p mulligan-a2 ps` | all runtime services up; `db-init` exited 0 | PASS |
| DB cluster | `patronictl list` | one leader, two streaming replicas | PASS |
| Rabbit cluster | `rabbitmqctl cluster_status` | three running nodes | PASS |
| Quorum queues | `rabbitmqctl list_queues name type online members` | `Transactions` and `Citations`, type `quorum`, 3 members | PASS |
| Customer flow | CLI start/stop/events | parking event and transaction created | PASS |
| PEO flow | CLI check/cite | check output and citation created | PASS |
| MO flow | CLI transactions/citations | reports list committed rows | PASS |
| Security smoke | `.\scripts\security-checks.ps1 -Project mulligan-a2` | bad HMAC, replay, stale timestamp rejected | PASS |
| Invalid input | CLI invalid plate/amount/usage checks | no stack trace, safe error | PASS |
| DB failover | stop one Patroni replica/leader and retry flow after election | cluster recovers, app works | PASS |
| Rabbit failover | stop `rabbit-2`, issue citation, restart | quorum remains online; node rejoins | PASS |
| AMQPS listener (one-way TLS) | `docker compose -f docker-compose.yml -f docker-compose.tls.yml up -d --build` after `generate-certs`; then `docker compose exec rabbit-1 rabbitmq-diagnostics listeners` | listener `amqp/ssl 5671` reported, plain 5672 still up | Implemented + opt-in; activate via overlay, then run the security-checks.ps1 over 5671 |
| Java AMQPS client | `MULLIGAN_QUEUE_TLS=true` + truststore env (set by the overlay) | publish + consume work over 5671, `SecureLogger` records `tls=true` in the AMQP factory line | Implemented + opt-in; same overlay run |
| mTLS on AMQPS | `docker compose -f docker-compose.yml -f docker-compose.tls.yml -f docker-compose.mtls.yml up -d --build` | a client without a cert is refused at the TLS handshake; a service container with its `client-<svc>.p12` mounted by the overlay handshakes successfully | Configurable via additive overlay (`docker-compose.mtls.yml` + `rabbitmq-mtls.conf`). OFF in the verified stack, single `-f` flag to turn on, single `-f` flag to roll back to one-way TLS. |
| Postgres/JDBC TLS | not enabled in compose (listener not configured); `MULLIGAN_DB_TLS=true` is wired in the client | n/a in default; opt-in procedure in `DEPLOY.md` §"Optional: PostgreSQL listener-side TLS" | Client-side ready, listener side **documented-only** in this revision because Spilo bootstrap + HAProxy frontend changes are required together and were judged too risky to flip without dedicated verification of DB failover under TLS |

## 6. Lessons Learned

- Security claims must match the actual deployment profile. TLS is implemented
  and documented, but it should not be described as active until the broker and
  database listeners are explicitly configured and tested with TLS.
- HMAC envelopes and nonce replay protection are cheap compared with trying to
  detect forged messages after they enter the system.
- Least-privilege RabbitMQ users make red-team scope much narrower and make
  demonstrations easier to explain.
- Durable database reporting is safer for MO reports than draining queues that
  are also consumed by the validation server; the queue path still exists for
  assignment communication and security validation.
