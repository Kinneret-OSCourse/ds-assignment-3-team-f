# Testing Plan - Assignment 3

This document maps the required parking-system use cases, security checks,
failover checks, and Assignment 3 recommender consensus behavior to concrete
test evidence.

## 1. Automated Build and Unit Tests

| ID | Artifact | Preconditions | Test steps | Expected result | Evidence |
|---|---|---|---|---|---|
| UT-01 | All Gradle modules | Java 21 installed | `.\gradlew.bat clean build` | Build succeeds; all JUnit tests pass | Gradle console `BUILD SUCCESSFUL` |
| UT-02 | Security primitives | Java 21 installed | `.\gradlew.bat :parking-common:test --tests com.mulligan.common.SecurityLayerTest` | HMAC, replay, stale timestamp, invalid input tests pass | JUnit report under `parking-common/build/reports/tests/test` |
| UT-03 | Integration tests against Docker | Docker stack running, env vars point to host ports | `.\gradlew.bat cleanTest test` | Service/controller tests pass against live DB/RabbitMQ where enabled | Gradle console |
| UT-04 | Recommender algorithm | Java 21 installed | `.\gradlew.bat :parking-recommender:test` | Minimum citation, nearest tie, and all-busy cases pass | JUnit report |
| UT-05 | Customer recommender path | Java 21 installed | `.\gradlew.bat :parking-system-CustomerUI:test` | Customer controller formats the consensus result safely | JUnit report |

## 2. Acceptance Tests for Use Cases

### SUC-1: Starting a Parking Event

| ID | Artifact tested | Preconditions | Test steps | Expected result | Passed? |
|---|---|---|---|---|---|
| AT-01 | Customer CLI | Stack running; `CUST-1001` owns `604-95-839`; space `S001` exists | `docker compose -p mulligan-a2 exec -T customer-ui java -cp /app/lib/* com.mulligan.customer.cli.CustomerCLI start CUST-1001 604-95-839 S001` | Output contains `Parking started successfully`, event ID, zone, start time | Yes |
| AT-02 | Customer service branch B | Same vehicle already has a started event | Run `start ... S001`, then `start ... S002` | First event is auto-stopped and billed; second event is `STARTED` | Covered by JUnit and manual smoke |
| AT-03 | Customer validation branch | Stack running; invalid/unknown space | `... CustomerCLI start CUST-1001 604-95-839 NOSUCHSPACE` | Safe validation error, no stack trace | Yes |

### SUC-2: Stopping a Parking Event

| ID | Artifact tested | Preconditions | Test steps | Expected result | Passed? |
|---|---|---|---|---|---|
| AT-04 | Customer CLI | Started parking event exists | `... CustomerCLI stop CUST-1001 604-95-839` | Output contains `Parking stopped successfully`, transaction ID, amount, stop time | Yes |
| AT-05 | Transaction event | Rabbit quorum queues exist | Stop a parking event, then inspect reports/logs | Payment transaction is persisted and signed queue message is accepted by parking-server | Yes |
| AT-06 | No active event branch | No started event for vehicle | `... CustomerCLI stop CUST-1001 604-95-839` after already stopped | Safe error, no stack trace | Covered by JUnit/manual |

### SUC-3: Customer Parking Events List

| ID | Artifact tested | Preconditions | Test steps | Expected result | Passed? |
|---|---|---|---|---|---|
| AT-07 | Customer CLI | At least one stopped event exists | `... CustomerCLI events CUST-1001 604-95-839` | Lists parking events and total paid | Yes |
| AT-08 | Empty/unknown branch | Unknown vehicle | `... CustomerCLI events CUST-1001 UNKNOWN-VEHICLE` | Empty/safe response, no stack trace | Covered by tests |

### SUC-4: PEO Vehicle Investigation

| ID | Artifact tested | Preconditions | Test steps | Expected result | Passed? |
|---|---|---|---|---|---|
| AT-09 | PEO CLI | Vehicle/space exist and recent started event exists | `docker compose -p mulligan-a2 exec -T peo-ui java -cp /app/lib/* com.mulligan.peo.cli.PEOCLI check 604-95-839 S001` | `Parking Ok` or `Parking Not Ok` based on DB state | Yes |
| AT-10 | PEO log | PEO check performed | Inspect service log/security log | Query is recorded with vehicle, space, response | Covered by service logging |
| AT-11 | Invalid input branch | Unknown vehicle or bad amount | Run check/cite with invalid values | Safe validation error, no stack trace | Yes |

### SUC-5: Citation Issuance

| ID | Artifact tested | Preconditions | Test steps | Expected result | Passed? |
|---|---|---|---|---|---|
| AT-12 | PEO CLI | Vehicle and space exist | `... PEOCLI cite 604-95-839 S001 250` | Output contains citation ID, zone, inspection time, amount | Yes |
| AT-13 | Citation event | Rabbit quorum queues exist | Issue citation and inspect report/log | Citation is persisted and signed queue message is accepted by parking-server | Yes |
| AT-14 | Amount validation | Vehicle/space exist | `... PEOCLI cite 604-95-839 S001 -5` | `Citation amount must be positive`, no stack trace | Yes |

### SUC-6: MO Transaction Report

| ID | Artifact tested | Preconditions | Test steps | Expected result | Passed? |
|---|---|---|---|---|---|
| AT-15 | MO CLI | At least one transaction exists | `docker compose -p mulligan-a2 exec -T mo-ui java -cp /app/lib/* com.mulligan.mo.cli.MOCLI transactions` | Lists transaction ID, vehicle, space, zone, start/stop, amount | Yes |
| AT-16 | Empty branch | No transactions in a clean DB | Run transactions command before any stop events | Prints `No transactions found.` | Covered by test |

Implementation note: Assignment 1 says MO retrieves reports from queues. In the
verified Assignment 2 design, producers still send signed queue messages and
parking-server validates them, but MO reports read the durable PostgreSQL report
tables so reports remain available after the validation consumer acknowledges
queue messages.

### SUC-7: MO Citation Report

| ID | Artifact tested | Preconditions | Test steps | Expected result | Passed? |
|---|---|---|---|---|---|
| AT-17 | MO CLI | At least one citation exists | `docker compose -p mulligan-a2 exec -T mo-ui java -cp /app/lib/* com.mulligan.mo.cli.MOCLI citations` | Lists citation ID, vehicle, space, zone, inspection time, amount | Yes |
| AT-18 | Empty branch | No citations in a clean DB | Run citations command before citations are issued | Prints `No citations found.` | Covered by test |

### SUC-8: Parking Space Recommendation

| ID | Artifact tested | Preconditions | Test steps | Expected result | Passed? |
|---|---|---|---|---|---|
| AT-19 | Customer CLI | Three recommender nodes running | `docker compose exec customer-ui java -cp /app/lib/* com.mulligan.customer.cli.CustomerCLI recommend S003` | Returns `Recommendation result:` plus agreed list or `Empty List` | Covered by controller and recommender tests |
| AT-20 | Consensus with 1 malicious node | Recommenders running | `.\gradlew.bat :parking-recommender:runCli --args="mode http://localhost:8081 malicious"` then recommend | Honest majority still returns the two-node agreed list | Manual smoke |
| AT-21 | Consensus with 2 malicious nodes | Recommenders running | Put two nodes in malicious mode, then recommend | No majority consensus is returned | Manual smoke |
| AT-22 | Missing recommender | Recommenders running | Stop one recommender, then recommend | Two remaining identical votes still return a result | Manual smoke |
| AT-23 | Two missing recommenders | Recommenders running | Stop two recommenders, then recommend | No majority consensus is returned | Manual smoke |

## 3. Cluster and Failover Tests

### Database Cluster

| ID | Artifact tested | Preconditions | Test steps | Expected result | Passed? |
|---|---|---|---|---|---|
| DB-01 | Patroni cluster | Stack running | `docker compose -p mulligan-a2 exec -T patroni-1 patronictl list` | One leader, two replicas, lag 0 or near 0 | Yes |
| DB-02 | App write through cluster | Stack running | Customer start/stop flow | Writes succeed through HAProxy/current primary | Yes |
| DB-03 | Single replica failure | Three DB nodes running | `docker compose -p mulligan-a2 stop patroni-1`; run Customer start/stop; restart node | App still works; node rejoins as streaming replica | Yes |
| DB-04 | Leader failure/recovery | Three DB nodes running | Stop current leader, wait for election, run flow after new leader visible, restart old leader | New leader elected; old leader rejoins | Yes |

### RabbitMQ Cluster

| ID | Artifact tested | Preconditions | Test steps | Expected result | Passed? |
|---|---|---|---|---|---|
| MQ-01 | RabbitMQ cluster | Stack running | `docker compose -p mulligan-a2 exec -T rabbit-1 rabbitmqctl cluster_status` | Three running nodes, no alarms/partitions | Yes |
| MQ-02 | Quorum queues | Run grow script | `docker compose -p mulligan-a2 exec -T rabbit-1 rabbitmqctl list_queues name type online members` | `Transactions` and `Citations`, type `quorum`, 3 members | Yes |
| MQ-03 | Single RabbitMQ node failure | Queues have 3 members | Stop `rabbit-2`, issue PEO citation, restart `rabbit-2` | Citation flow works; queue online members drop to 2 then recover to 3 | Yes |

Commands:

```powershell
.\scripts\grow-quorum-queues.ps1 -Project mulligan-a2
docker compose -p mulligan-a2 exec -T rabbit-1 rabbitmqctl list_queues name type online members
```

## 4. Security Tests

| ID | Requirement | Preconditions | Test steps | Expected result | Passed? |
|---|---|---|---|---|---|
| SEC-01 | HMAC rejects tampering | Stack running; `MULLIGAN_HMAC_KEY` set | `.\scripts\security-checks.ps1 -Project mulligan-a2` | `PASS Bad HMAC rejected`; log has `REJECT bad-hmac` | Yes |
| SEC-02 | Nonce replay rejected | Same | Same script | `PASS Replay rejected`; log has `REJECT replay` | Yes |
| SEC-03 | Stale timestamp rejected | Same | Same script | `PASS Stale timestamp rejected`; log has `REJECT stale-timestamp` | Yes |
| SEC-04 | Invalid input rejected | Stack running | `... PEOCLI cite 604-95-839 S001 -5` | Safe validation error | Yes |
| SEC-05 | No stack traces to clients | Stack running | Invalid CLI commands and transient DB failure checks | No Java stack trace or SQL driver text in CLI output | Yes |
| SEC-06 | RabbitMQ guest blocked | Stack running | `docker compose -p mulligan-a2 exec -T rabbit-1 rabbitmqctl authenticate_user guest guest` | Authentication fails | Yes |
| SEC-07 | Least privilege users | Stack running | `rabbitmqctl list_permissions -p /` | Customer/PEO publish only; MO read only; server scoped to queues/exchange | Yes |
| SEC-08 | AMQPS one-way TLS active | Stack started with `-f docker-compose.tls.yml` | `docker compose -p mulligan-a2 exec -T rabbit-1 rabbitmq-diagnostics listeners` | Listener `amqp/ssl 5671` is reported alongside `amqp 5672`; the four app services log `AMQP factory built tls=true` | Yes (opt-in via overlay) |
| SEC-09 | mTLS rejects clients without cert | Stack started with the mTLS overlay (`-f docker-compose.yml -f docker-compose.tls.yml -f docker-compose.mtls.yml`) | `docker compose -p mulligan-a2 exec -T rabbit-1 openssl s_client -connect rabbit-1:5671 -CAfile /etc/rabbitmq/certs/ca.pem </dev/null` | Handshake fails with `tlsv13 alert certificate required` or `peer did not return a certificate`; rabbit-1 logs the rejection | Configurable, OFF in verified stack |
| SEC-10 | mTLS accepts clients with the right cert | Same overlay | Same `openssl s_client` command plus `-cert /etc/rabbitmq/certs/client-server-cert.pem -key /etc/rabbitmq/certs/client-server-key.pem` | Handshake succeeds, `Verify return code: 0 (ok)`; the four app services still publish and consume after the overlay restart | Configurable, OFF in verified stack |

### Optional: AMQPS + mTLS verification (additive overlay)

mTLS is shipped as an additive overlay; the default verified path (plain
AMQP) and the one-way TLS path (`-f docker-compose.tls.yml`) are unchanged.
The mTLS path requires the cert generator to have produced
`client-<svc>.p12` keystores, which both `generate-certs.sh` and
`generate-certs.ps1` do automatically.

```powershell
.\scripts\generate-certs.ps1
docker compose -p mulligan-a2 `
  -f docker-compose.yml `
  -f docker-compose.tls.yml `
  -f docker-compose.mtls.yml up -d --build
.\scripts\grow-quorum-queues.ps1 -Project mulligan-a2

# Listener verification
docker compose -p mulligan-a2 exec -T rabbit-1 rabbitmq-diagnostics listeners

# Rejection: openssl with no client cert.
docker compose -p mulligan-a2 exec -T rabbit-1 sh -c "openssl s_client -connect rabbit-1:5671 -CAfile /etc/rabbitmq/certs/ca.pem </dev/null 2>&1 | tail"
# Look for "tlsv13 alert certificate required" or "peer did not return a certificate"

# Acceptance: openssl with the matching client cert.
docker compose -p mulligan-a2 exec -T rabbit-1 sh -c "openssl s_client -connect rabbit-1:5671 -CAfile /etc/rabbitmq/certs/ca.pem -cert /etc/rabbitmq/certs/client-server-cert.pem -key /etc/rabbitmq/certs/client-server-key.pem </dev/null 2>&1 | grep 'Verify return code'"
# Look for "Verify return code: 0 (ok)"

# App-level proof that the four services successfully present their client certs:
docker compose -p mulligan-a2 logs parking-server | Select-String "AMQP factory built tls=true"
.\scripts\security-checks.ps1 -Project mulligan-a2
```

Rollback to the verified one-way TLS path is a single flag removal:

```powershell
docker compose -p mulligan-a2 `
  -f docker-compose.yml `
  -f docker-compose.tls.yml up -d --build
```

## 5. Proof Checklist for Submission / Demo

Capture screenshots or saved terminal output for:

| Proof | Command/output to capture |
|---|---|
| Build success | `.\gradlew.bat clean build` ending with `BUILD SUCCESSFUL` |
| Docker services | `docker compose -p mulligan-a2 ps` |
| DB cluster | `docker compose -p mulligan-a2 exec -T patroni-1 patronictl list` |
| Rabbit cluster | `docker compose -p mulligan-a2 exec -T rabbit-1 rabbitmqctl cluster_status` |
| Quorum queues | `docker compose -p mulligan-a2 exec -T rabbit-1 rabbitmqctl list_queues name type online members` |
| Rabbit ACLs | `rabbitmqctl list_users` and `rabbitmqctl list_permissions -p /` |
| Customer flow | Customer CLI start, stop, events outputs |
| PEO flow | PEO CLI check and cite outputs |
| MO flow | MO CLI transactions and citations outputs |
| Security smoke | `.\scripts\security-checks.ps1 -Project mulligan-a2` PASS lines |
| Secure log | `docker compose -p mulligan-a2 exec -T parking-server tail -n 100 /var/log/mulligan/security.log` |
| DB failover | Before/after `patronictl list` plus app flow while one node is down |
| Rabbit failover | Before/after `cluster_status` and `list_queues`, plus PEO citation while one node is down |
| Recommender consensus | Customer `recommend` output plus malicious-mode majority/no-majority demonstrations |

## 6. Live Demo Command Block

```powershell
$env:MULLIGAN_HMAC_KEY='0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
$env:MULLIGAN_DB_PORT='55432'
$env:MULLIGAN_RABBIT_AMQP_PORT='5673'
$env:MULLIGAN_RABBIT_MANAGEMENT_PORT='15673'
$env:MULLIGAN_HAPROXY_STATS_PORT='7001'

.\gradlew.bat clean build
.\scripts\generate-certs.ps1
docker compose -p mulligan-a2 up -d --build
.\scripts\grow-quorum-queues.ps1 -Project mulligan-a2

docker compose -p mulligan-a2 ps
docker compose -p mulligan-a2 exec -T patroni-1 patronictl list
docker compose -p mulligan-a2 exec -T rabbit-1 rabbitmqctl list_queues name type online members

docker compose -p mulligan-a2 exec -T customer-ui java -cp /app/lib/* com.mulligan.customer.cli.CustomerCLI start CUST-1001 604-95-839 S001
docker compose -p mulligan-a2 exec -T customer-ui java -cp /app/lib/* com.mulligan.customer.cli.CustomerCLI stop CUST-1001 604-95-839
docker compose -p mulligan-a2 exec -T customer-ui java -cp /app/lib/* com.mulligan.customer.cli.CustomerCLI events CUST-1001 604-95-839
docker compose -p mulligan-a2 exec -T peo-ui java -cp /app/lib/* com.mulligan.peo.cli.PEOCLI check 604-95-839 S001
docker compose -p mulligan-a2 exec -T peo-ui java -cp /app/lib/* com.mulligan.peo.cli.PEOCLI cite 604-95-839 S001 250
docker compose -p mulligan-a2 exec -T mo-ui java -cp /app/lib/* com.mulligan.mo.cli.MOCLI transactions
docker compose -p mulligan-a2 exec -T mo-ui java -cp /app/lib/* com.mulligan.mo.cli.MOCLI citations
.\scripts\security-checks.ps1 -Project mulligan-a2
```
