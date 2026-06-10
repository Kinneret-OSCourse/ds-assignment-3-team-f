# Defense Report - Mulligan Parking System (Assignment 3)

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

Assignment 3 changes the default full-stack deployment from "TLS-capable" to
"RabbitMQ mTLS by default": application clients use AMQPS on 5671, the plain
AMQP listener is disabled, database and management host ports are bound to
localhost in the single-machine Compose file, generated CA private keys are
kept outside runtime certificate mounts, and queue replay nonces can be stored
in PostgreSQL so every validator rejects the same nonce.

Important TLS scope note: the default `docker-compose.yml` uses RabbitMQ mTLS
for application messaging. RabbitMQ mounts `rabbitmq-mtls.conf`, disables the
plain AMQP listener, exposes AMQPS on `127.0.0.1:5671`, and gives each
application service its matching client keystore. **PostgreSQL listener-side
TLS remains documented-only**: the Java client is wired for
`&ssl=true&sslmode=require`, but Spilo/Patroni and HAProxy need coordinated
listener changes before that flag can be enabled without breaking JDBC. The
exact opt-in procedure is in `DEPLOY.md`.

## 2. Vulnerability Inventory

| # | Vulnerability | CIA impact | Severity | Root cause | Fix / mitigation | Test evidence |
|---|---|---|---|---|---|---|
| V1 | Plain AMQP/JDBC transport | Confidentiality | High | Assignment 1 used classroom plaintext defaults | RabbitMQ application traffic now uses AMQPS/mTLS by default; PostgreSQL TLS remains a documented listener-side follow-up | `TlsConfig`, `MulliganConnectionFactory`, `generate-certs.*`, `rabbitmq-mtls.conf` |
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
| V13 | Runtime CA signing key exposure | Confidentiality/Integrity | Critical | Generated `ca-key.pem` lived beside runtime certs | `generate-certs.*` stores the signing key under ignored `infra/private-ca/`; containers mount only `infra/certs/` | runtime cert mount contains no CA private key |
| V14 | Node-local replay memory | Integrity | High | In-memory nonce cache did not synchronize across validators | `MULLIGAN_NONCE_STORE=jdbc` stores nonce claims in `security_nonces` with a primary key | duplicate nonce insert is rejected across nodes |

## 3. Root Cause Analysis

The per-vulnerability root causes are listed in the "Root cause" column of the
inventory above. Grouped, they trace back to three design decisions:

1. **Classroom defaults were shipped to production.** Assignment 1 used
   plaintext transport, the default `guest` account expectations, one broad
   service credential, and a single broker and database node (V1, V5, V6, V7,
   V8, V9). Convenience defaults became the attack surface.
2. **Messages were trusted because they arrived, not because they were
   authenticated.** Raw JSON without signatures, nonces, or freshness checks
   allowed forging, replaying, and delaying messages, and the first fix kept
   replay memory node-local so the cluster did not share rejections (V2, V3,
   V4, V14).
3. **Failure paths leaked more than the success paths.** Thin validation,
   stack traces in client responses, no persistent security log, and a CA
   signing key stored beside runtime certificates all came from treating
   error handling and key custody as afterthoughts (V10, V11, V12, V13).

## 4. Fix Details

### 4.1 Message Authentication, Replay, and Freshness

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

### 4.2 RabbitMQ Hardening

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

### 4.3 Database Cluster

The Docker stack runs `patroni-1`, `patroni-2`, `patroni-3`, `etcd`, and
`haproxy`. `db-init` creates `mulligan_db`, loads the seed schema/data, and
applies least-privilege grants for `mulligan_app`. The verified state after
startup is one leader and two streaming replicas.

### 4.4 Input Validation

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

### 4.5 Error Handling and Logging

The service layer logs real exceptions through `SecureLogger` and returns
generic client responses such as `ERR_INTERNAL`, `ERR_UNAVAILABLE`, usage text,
or validation messages. Security events, malformed messages, replay attempts,
and operational connection errors are written to the persistent Docker volume
mounted at `/var/log/mulligan/security.log`.

### 4.6 TLS / mTLS Capability

RabbitMQ mTLS is enabled in the recommended default Compose stack. TLS for JDBC is implemented in the client only until the Patroni and HAProxy listeners are configured for TLS. The current matrix is:

| Channel | TLS code | Listener config | Default in compose | How to activate |
| --- | --- | --- | --- | --- |
| AMQP (Customer/PEO/MO/server to RabbitMQ) | `MulliganConnectionFactory` + `TlsConfig` | `infra/rabbitmq/rabbitmq-mtls.conf` on port 5671, TLS 1.2/1.3, `verify_peer`, `fail_if_no_peer_cert` | ON, mTLS | Run `scripts/generate-certs.*`, set `MULLIGAN_HMAC_KEY`, then `docker compose up --build` |
| Inter-broker RabbitMQ clustering | broker config | RabbitMQ distribution is confined to the Compose network with a rotated cookie supplied by env | Network-restricted | Keep the cookie out of committed files and do not publish distribution ports |
| JDBC (UIs to HAProxy to Patroni) | `PostgresClusterConnection.jdbcTlsParameters()` | not configured yet; HAProxy is TCP-mode and Spilo Postgres has no listener cert | OFF, plaintext inside Compose network | Documented opt-in procedure in `DEPLOY.md` section "Optional: PostgreSQL listener-side TLS" |

`scripts/generate-certs.sh` (bash) and `scripts/generate-certs.ps1`
(PowerShell, .NET native, no openssl needed) both now produce the CA, the
RabbitMQ server cert (SANs cover `rabbit-1/2/3`, `haproxy`, `localhost`),
the Java truststore, and four per-service mTLS keystores
(`client-customer.p12`, `client-peo.p12`, `client-mo.p12`,
`client-server.p12`).

The hardened RabbitMQ configuration disables the plain `5672` listener. Any client or operator script must use AMQPS on `5671`; plaintext AMQP is treated as a failed security check rather than a migration fallback.

mTLS on AMQPS is part of the Assignment 3 hardened path. The default compose
environment points application services at AMQPS on port `5671`; the classroom
compose file mounts `rabbitmq-mtls.conf`, which sets
`ssl_options.verify = verify_peer` and
`ssl_options.fail_if_no_peer_cert = true`. Each of the four
application services has `MULLIGAN_TLS_KEYSTORE` set to its matching
`client-<svc>.p12` keystore. The Java `TlsConfig` already presents the
client cert during the TLS handshake when those env vars are set.

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

## 5. Updated Security Architecture

Default verified demo path:

```text
Customer/PEO/MO CLI
   | AMQPS 5671 (mTLS) with HMAC envelope, nonce, timestamp
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

## 6. Verified Test Evidence

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
| AMQPS/mTLS listener | `docker compose up -d --build` after `generate-certs`; then `docker compose exec rabbit-1 rabbitmq-diagnostics listeners` | listener `amqp/ssl 5671` reported; no plain AMQP listener | Implemented in default compose |
| Java AMQPS client | `MULLIGAN_QUEUE_TLS=true` + truststore + per-service keystore env | publish + consume work over 5671, `SecureLogger` records `tls=true` in the AMQP factory line | Implemented in default compose |
| mTLS client rejection | connect to 5671 without a client cert | handshake is refused; service containers with `client-<svc>.p12` connect successfully | Implemented in default compose |
| Postgres/JDBC TLS | not enabled in compose (listener not configured); `MULLIGAN_DB_TLS=true` is wired in the client | n/a in default; opt-in procedure in `DEPLOY.md` section "Optional: PostgreSQL listener-side TLS" | Client-side ready, listener side documented-only in this revision because Spilo bootstrap + HAProxy frontend changes are required together and were judged too risky to flip without dedicated verification of DB failover under TLS |

## 7. Lessons Learned

- Security claims must match the actual deployment profile. RabbitMQ mTLS is
  active in the recommended compose stack; PostgreSQL TLS should remain labeled
  as documented-only until its listener path is configured and tested.
- HMAC envelopes and nonce replay protection are cheap compared with trying to
  detect forged messages after they enter the system.
- Least-privilege RabbitMQ users make red-team scope much narrower and make
  demonstrations easier to explain.
- Durable database reporting is safer for MO reports than draining queues that
  are also consumed by the validation server; the queue path still exists for
  assignment communication and security validation.
