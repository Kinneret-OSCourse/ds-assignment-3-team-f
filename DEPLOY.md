# Deployment Guide - Assignment 3

## Prerequisites

- Docker Engine 24+ and Docker Compose v2
- ~4 GB of free RAM (etcd + 3 Patroni + 3 RabbitMQ + 4 application containers)
- Java 21 (only for local non-Docker runs)
- `openssl` and `keytool` for `generate-certs.sh`; Java 21 is enough for the
  Windows `generate-certs.ps1` path.

## Environment variables

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `MULLIGAN_HMAC_KEY` | yes (production) | dev placeholder | 32+ byte HMAC-SHA256 secret |
| `MULLIGAN_DB_HOSTS` | no | `haproxy:5432` | Multi-host JDBC list |
| `MULLIGAN_DB_NAME` | no | `mulligan_db` | Database name |
| `MULLIGAN_DB_USER` / `MULLIGAN_DB_PASSWORD` | no | `mulligan_app` / `mulligan_app_pw` | App role |
| `MULLIGAN_DB_TLS` | no | `false` | Client-side JDBC TLS flag; requires DB/HAProxy TLS listener configuration |
| `MULLIGAN_QUEUE_HOSTS` | no | the 3 rabbit nodes | Comma-separated list |
| `MULLIGAN_QUEUE_TLS` | no | `true` | Client-side AMQPS flag; default compose uses RabbitMQ mTLS on 5671 |
| `MULLIGAN_QUEUE_USER_{CUSTOMER,PEO,MO,SERVER}` | no | per-service users | RabbitMQ login |
| `MULLIGAN_QUEUE_PASSWORD_{CUSTOMER,PEO,MO,SERVER}` | no | matching dev passwords | RabbitMQ password |
| `MULLIGAN_TLS_TRUSTSTORE` / `MULLIGAN_TLS_TRUSTSTORE_PASSWORD` | with AMQPS | `/etc/mulligan/certs/truststore.p12` / `mulligan_tls_pw` | AMQPS trust material |
| `MULLIGAN_TLS_KEYSTORE` / `MULLIGAN_TLS_KEYSTORE_PASSWORD` | with mTLS | per-service `client-<svc>.p12` / `mulligan_tls_pw` | AMQPS client certificate material |
| `MULLIGAN_SECURITY_LOG` | no | `logs/security.log` | Append-only audit log |

## Single-machine deployment (recommended for grading)

```bash
git clone <repository>
cd ds-assignment-3-team-f
./scripts/generate-certs.sh
export MULLIGAN_HMAC_KEY=$(openssl rand -hex 32)
docker compose up --build
./scripts/grow-quorum-queues.sh
```

PowerShell on Windows:

```powershell
$env:MULLIGAN_HMAC_KEY='0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
.\scripts\generate-certs.ps1
docker compose up -d --build
.\scripts\grow-quorum-queues.ps1
```

Health checks:

```bash
docker compose ps
docker compose exec rabbit-1 rabbitmqctl cluster_status | grep "Running Nodes"
docker compose exec patroni-1 patronictl list
```

Expected: three Running Nodes for RabbitMQ; one Patroni leader and two
replicas.

## CLI usage

```bash
docker compose exec customer-ui java -cp /app/lib/* com.mulligan.customer.cli.CustomerCLI start CUST-1001 604-95-839 S001
docker compose exec customer-ui java -cp /app/lib/* com.mulligan.customer.cli.CustomerCLI stop CUST-1001 604-95-839
docker compose exec customer-ui java -cp /app/lib/* com.mulligan.customer.cli.CustomerCLI events CUST-1001 604-95-839
docker compose exec peo-ui      java -cp /app/lib/* com.mulligan.peo.cli.PEOCLI      check 604-95-839 S001
docker compose exec peo-ui      java -cp /app/lib/* com.mulligan.peo.cli.PEOCLI      cite  604-95-839 S001 250
docker compose exec mo-ui       java -cp /app/lib/* com.mulligan.mo.cli.MOCLI        transactions
docker compose exec mo-ui       java -cp /app/lib/* com.mulligan.mo.cli.MOCLI        citations
```

## Classroom multi-host deployment

Run the compose stack on a single "server" PC. The three UI computers
launch the CLI JARs directly:

```bash
export MULLIGAN_DB_HOSTS=<server-pc>:5432
export MULLIGAN_QUEUE_HOSTS=<server-pc>:5672
export MULLIGAN_HMAC_KEY=<same key as server>
java -jar customer-ui-1.0.jar
java -cp 'lib/*' com.mulligan.customer.cli.CustomerCLI events CUST-1001 604-95-839
```

Open the inbound firewall ports on the server PC:

```powershell
New-NetFirewallRule -DisplayName "Mulligan PostgreSQL (HAProxy)" -Direction Inbound -Protocol TCP -LocalPort 5432 -Action Allow
New-NetFirewallRule -DisplayName "Mulligan RabbitMQ AMQPS" -Direction Inbound -Protocol TCP -LocalPort 5671 -Action Allow
New-NetFirewallRule -DisplayName "Mulligan RabbitMQ Management" -Direction Inbound -Protocol TCP -LocalPort 15672 -Action Allow
```

## TLS deployment

The default `docker-compose.yml` runs RabbitMQ application traffic over
AMQPS/mTLS on port `5671`, disables the plain AMQP listener, and still protects
queue integrity with HMAC, nonce replay protection, and least-privilege
RabbitMQ users. PostgreSQL TLS is implemented client-side only and is NOT
enabled in the verified stack; the Patroni and HAProxy listeners must be
reconfigured before `MULLIGAN_DB_TLS=true` is safe to use.

### Step 1 — generate certificates and per-service mTLS keystores

```bash
./scripts/generate-certs.sh
# or on Windows
.\scripts\generate-certs.ps1
```

Both scripts produce, under `infra/certs/`:

- `ca.pem` — self-signed CA certificate used for trust.
- `infra/private-ca/ca-key.pem` — CA signing key kept outside runtime mounts.
- `server-cert.pem`, `server-key.pem` — RabbitMQ server cert, SANs
  `rabbit-1`, `rabbit-2`, `rabbit-3`, `haproxy`, `localhost`.
- `truststore.p12` — Java PKCS12 truststore that trusts the CA above.
- `client-customer.p12`, `client-peo.p12`, `client-mo.p12`,
  `client-server.p12` — per-service mTLS keystores. Off by default; used
  when you choose to enable mTLS (see Step 3).

Password (override with `MULLIGAN_TLS_PASSWORD` env): `mulligan_tls_pw`.

### Step 2 — bring the stack up with the AMQPS overlay (one-way TLS)

```bash
export MULLIGAN_HMAC_KEY=$(openssl rand -hex 32)
docker compose -f docker-compose.yml -f docker-compose.tls.yml up -d --build
./scripts/grow-quorum-queues.sh
```

PowerShell:

```powershell
$env:MULLIGAN_HMAC_KEY='0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
docker compose -f docker-compose.yml -f docker-compose.tls.yml up -d --build
.\scripts\grow-quorum-queues.ps1
```

What the overlay changes:

- Each `rabbit-*` container mounts `rabbitmq-tls.conf` (instead of the
  plain `rabbitmq.conf`) and the `infra/certs/` directory at
  `/etc/rabbitmq/certs/`.
- Port `5671` is exposed on the host. Plain AMQP on `5672` is disabled by `rabbitmq-tls.conf` and `rabbitmq-mtls.conf`.
- The four application services (`parking-server`, `customer-ui`,
  `peo-ui`, `mo-ui`) get `MULLIGAN_QUEUE_TLS=true`,
  `MULLIGAN_QUEUE_HOSTS=rabbit-1:5671,rabbit-2:5671,rabbit-3:5671`,
  `MULLIGAN_TLS_TRUSTSTORE=/etc/mulligan/certs/truststore.p12`, and the
  matching truststore password.

Verify the TLS listener and the client connection:

```bash
docker compose -p mulligan-a2 exec rabbit-1 rabbitmq-diagnostics listeners
# Expected: amqp/ssl 5671 + http 15672; no amqp 5672 listener
docker compose -p mulligan-a2 logs parking-server | grep "AMQP factory built tls=true"
docker compose -p mulligan-a2 exec parking-server tail /var/log/mulligan/security.log
```

### Step 3 — activate mTLS (optional, after Step 2 works)

mTLS is shipped as an additive overlay (`docker-compose.mtls.yml`) that is
layered on top of the one-way TLS overlay. The mTLS overlay does two things:

1. Mounts `infra/rabbitmq/rabbitmq-mtls.conf` into each `rabbit-*` container
   instead of `rabbitmq-tls.conf`. The mTLS conf flips:

   ```
   ssl_options.verify              = verify_peer
   ssl_options.fail_if_no_peer_cert = true
   ```

2. Sets `MULLIGAN_TLS_KEYSTORE` and `MULLIGAN_TLS_KEYSTORE_PASSWORD` per
   application service so each container presents its own client cert
   (`client-customer.p12`, `client-peo.p12`, `client-mo.p12`,
   `client-server.p12`). The Java `TlsConfig` class already reads these env
   vars and wires the keystore into the SSL context automatically.

Run it like this:

```bash
./scripts/generate-certs.sh
docker compose -p mulligan-a2 \
  -f docker-compose.yml \
  -f docker-compose.tls.yml \
  -f docker-compose.mtls.yml up -d --build
./scripts/grow-quorum-queues.sh -Project mulligan-a2
```

PowerShell:

```powershell
.\scripts\generate-certs.ps1
docker compose -p mulligan-a2 `
  -f docker-compose.yml `
  -f docker-compose.tls.yml `
  -f docker-compose.mtls.yml up -d --build
.\scripts\grow-quorum-queues.ps1 -Project mulligan-a2
```

Verify the mTLS handshake actually requires a client cert:

```bash
# Should succeed — service has its keystore mounted by the overlay.
docker compose -p mulligan-a2 logs parking-server | grep "AMQP factory built tls=true"

# Should fail at the TLS handshake (no client cert presented):
docker compose -p mulligan-a2 exec rabbit-1 openssl s_client -connect rabbit-1:5671 -CAfile /etc/rabbitmq/certs/ca.pem </dev/null
# Look for "alert handshake failure" or "tlsv13 alert certificate required" in the output.

# Should succeed (client presents its cert):
docker compose -p mulligan-a2 exec rabbit-1 openssl s_client -connect rabbit-1:5671 \
  -CAfile /etc/rabbitmq/certs/ca.pem \
  -cert /etc/rabbitmq/certs/client-server-cert.pem \
  -key /etc/rabbitmq/certs/client-server-key.pem </dev/null
# Look for "Verify return code: 0 (ok)" near the bottom.
```

Rollback to one-way TLS is a single flag removal:

```bash
docker compose -p mulligan-a2 \
  -f docker-compose.yml \
  -f docker-compose.tls.yml up -d --build
```

Rollback to one-way TLS is removing only the mTLS overlay. The recommended
default compose path keeps AMQPS/mTLS enabled.

#### Why the mTLS overlay is still kept

The default compose file now runs RabbitMQ with mTLS. The separate
`docker-compose.tls.yml` and `docker-compose.mtls.yml` files remain for
compatibility with older one-way TLS verification paths. Keeping the overlay
files means:

* Reviewers can re-run the exact verified one-way TLS path with no change.
* mTLS is one extra `-f` away — no source edits needed.
* Rollback is a flag removal, not a `git revert`.

### What is NOT enabled in this revision

- **PostgreSQL listener-side TLS.** The Java client supports JDBC TLS today
  (`MULLIGAN_DB_TLS=true` makes the URL builder append
  `&ssl=true&sslmode=require`), but the broker side is intentionally left
  plaintext in the verified stack because:

  1. The Spilo `ghcr.io/zalando/spilo-16` image bootstraps PostgreSQL via
     Patroni without an SSL listener by default — `pg_hba.conf` does not
     enable `hostssl` and `postgresql.conf` does not set `ssl = on` or
     point at a server cert/key.
  2. HAProxy 2.9 fronts the cluster in TCP pass-through mode. To terminate
     TLS at HAProxy you have to switch the listener to
     `bind *:5432 ssl crt /usr/local/etc/haproxy/server.pem` (a combined
     PEM of cert + key), which is incompatible with TCP `httpchk` of the
     Patroni REST API on port 8008 unless re-encrypted to the backend.
  3. Re-encrypting end to end requires a server cert inside the Patroni
     container that matches the SAN list expected by the client, plus an
     `hostssl` `pg_hba.conf` rule for `mulligan_app`.

  Flipping `MULLIGAN_DB_TLS=true` today without first doing all three of
  those steps would break JDBC for every UI immediately — which would
  destabilise the verified stack. The optional enablement procedure is
  documented in the next section.
- **Erlang inter-broker TLS** for `rabbit-1`, `rabbit-2`, and `rabbit-3`
  traffic. The current hardening keeps distribution ports off the host network
  and rotates the cookie through environment configuration; enabling TLS for
  Erlang distribution is a separate RabbitMQ runtime change.

### Optional: PostgreSQL listener-side TLS (NOT in the verified stack)

The Java client and the cert generator already produce everything needed
except the Spilo/HAProxy listener wiring. If the operator wants to enable
listener-side TLS as a separate experiment, the procedure is:

1. **Server cert and key for Patroni.** Re-use `infra/certs/server-cert.pem`
   and `infra/certs/server-key.pem` from `generate-certs.{sh,ps1}` — the SAN
   list already covers `haproxy`, `localhost`, and `rabbit-1/2/3` and is easy
   to extend to `patroni-1/2/3` (add three more DNS entries to the `v3.ext`
   block in `scripts/generate-certs.sh` and regenerate).

2. **Mount the cert material into the Patroni containers.** In a separate
   compose overlay (e.g. `docker-compose.pgtls.yml`):

   ```yaml
   services:
     patroni-1:
       environment:
         SSL_CERTIFICATE_FILE: /etc/postgres/certs/server-cert.pem
         SSL_PRIVATE_KEY_FILE: /etc/postgres/certs/server-key.pem
         SSL_CA_FILE: /etc/postgres/certs/ca.pem
       volumes:
         - ./infra/certs:/etc/postgres/certs:ro
   # repeat for patroni-2, patroni-3
   ```

   Spilo's bootstrap picks up `SSL_CERTIFICATE_FILE` / `SSL_PRIVATE_KEY_FILE`
   when present and turns `ssl = on` in `postgresql.conf`.

3. **`pg_hba.conf` entry for the app user.** Spilo accepts an extra
   `pg_hba.conf` via the `PGAPPEND_PG_HBA` env or a templated overlay; the
   line is:

   ```
   hostssl mulligan_db mulligan_app 0.0.0.0/0 scram-sha-256
   ```

4. **HAProxy TLS frontend.** Replace the existing TCP `listen
   postgres_primary` with:

   ```
   frontend postgres_primary
       mode tcp
       bind *:5432 ssl crt /usr/local/etc/haproxy/server.pem
       option tcplog
       default_backend postgres_primary_be

   backend postgres_primary_be
       mode tcp
       option httpchk OPTIONS /master
       http-check expect status 200
       default-server inter 3s fall 3 rise 2 on-marked-down shutdown-sessions \
                      ssl verify required ca-file /usr/local/etc/haproxy/ca.pem
       server patroni-1 patroni-1:5432 maxconn 100 check port 8008
       server patroni-2 patroni-2:5432 maxconn 100 check port 8008
       server patroni-3 patroni-3:5432 maxconn 100 check port 8008
   ```

   The `server.pem` file is `cat server-cert.pem server-key.pem >
   server.pem`. The `verify required` on the backend is what makes the path
   end-to-end TLS; if you only want TLS termination at HAProxy, omit it.

5. **Flip the client flag.** Set `MULLIGAN_DB_TLS=true` for the four app
   services (env on compose) and the Java URL builder appends
   `&ssl=true&sslmode=require` automatically.

Verification (after enabling the overlay above):

```bash
docker compose -p mulligan-a2 exec patroni-1 \
  psql -h localhost -U postgres -d mulligan_db -c "SHOW ssl"
# Expected: ssl | on

docker compose -p mulligan-a2 logs customer-ui | grep "DB cluster JDBC URL"
# Expected URL ends with: ...&ssl=true&sslmode=require
```

This procedure is intentionally left as an opt-in experiment because step (2)
depends on the Spilo image revision honouring the SSL env vars on bootstrap;
when that is not the case the cert files have to be templated into
`postgresql.conf` directly, which is invasive enough to warrant its own
verification cycle.

### Rolling back to one-way TLS

```bash
docker compose -p mulligan-a2 down
docker compose -p mulligan-a2 -f docker-compose.yml -f docker-compose.tls.yml up -d --build
```

This keeps AMQPS enabled but removes the client-certificate requirement used
by the default hardened mTLS path.

## Common operations

```bash
# Tail the secure log
docker compose exec parking-server tail -f /var/log/mulligan/security.log

# Inspect the Patroni state
docker compose exec patroni-1 patronictl list

# Inspect a quorum queue
docker compose exec rabbit-1 rabbitmqctl list_queues name type messages members
```

## Tear down

```bash
docker compose down -v
```
