# 9-Computer Classroom Runbook

This file is the exact from-zero run plan for the distributed Assignment 2
deployment. It uses:

- 3 PostgreSQL computers
- 3 RabbitMQ computers
- 3 UI computers: Customer, PEO, MO

For a 3-computer smoke test, run computer 1 as `db1 + rmq1`, computer 2 as
`db2 + rmq2`, and computer 3 as `db3 + rmq3`.

## 0. Install On Every Computer

Install these before class:

1. Java 21 JDK
2. Docker Desktop
3. Git for Windows
4. The project folder from GitHub

Open PowerShell in the project folder:

```powershell
cd "C:\Users\user\OneDrive\Desktop\ds-assignment-2-team-f"
```

If PowerShell blocks scripts, run this once in the same terminal:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

## 1. Use Fixed IP Addresses

Example IP plan:

| Computer | Role | IP |
| --- | --- | --- |
| Computer 1 | PostgreSQL node 1 | `10.0.201.11` |
| Computer 2 | PostgreSQL node 2 | `10.0.201.10` |
| Computer 3 | PostgreSQL node 3 | `10.0.201.9` |
| Computer 4 | RabbitMQ node 1 | `10.0.201.21` |
| Computer 5 | RabbitMQ node 2 | `10.0.201.20` |
| Computer 6 | RabbitMQ node 3 | `10.0.201.19` |
| Computer 7 | Customer UI | outbound only |
| Computer 8 | PEO UI | outbound only |
| Computer 9 | MO UI | outbound only |

For the 3-computer smoke test, use this instead:

| Computer | Roles | IP |
| --- | --- | --- |
| Computer 1 | `db1`, `rmq1` | `10.0.201.11` |
| Computer 2 | `db2`, `rmq2` | `10.0.201.10` |
| Computer 3 | `db3`, `rmq3` | `10.0.201.9` |

Write `10.0.201.9`, not `10.0.201.09`.

## 2. Create `.env` On Every Computer

Copy `.env.9-laptops.example` to `.env`, then fill in the same values on every
computer.

For the 3-computer smoke test:

```powershell
@'
MULLIGAN_DB_PASSWORD=pass159357
MULLIGAN_REPMGR_PASSWORD=repmgr159357
MULLIGAN_QUEUE_ADMIN_PASSWORD=admin159357
MULLIGAN_QUEUE_CUSTOMER_PASSWORD=customer159357
MULLIGAN_QUEUE_PEO_PASSWORD=peo159357
MULLIGAN_QUEUE_MO_PASSWORD=mo159357
MULLIGAN_RABBITMQ_ERLANG_COOKIE=mulligan-shared-cookie-123456789
MULLIGAN_MESSAGE_HMAC_SECRET=mulligan-hmac-secret-123456789012345
MULLIGAN_TLS_STORE_PASSWORD=changeit
MULLIGAN_QUEUE_ALLOW_PLAINTEXT=false

LAB_DB1_IP=10.0.201.11
LAB_DB2_IP=10.0.201.10
LAB_DB3_IP=10.0.201.9
LAB_RMQ1_IP=10.0.201.11
LAB_RMQ2_IP=10.0.201.10
LAB_RMQ3_IP=10.0.201.9

MULLIGAN_DB_HOSTS=10.0.201.11:5432,10.0.201.10:5432,10.0.201.9:5432
MULLIGAN_QUEUE_HOSTS=10.0.201.11:5671,10.0.201.10:5671,10.0.201.9:5671
MULLIGAN_TLS_EXTRA_IPS=10.0.201.11,10.0.201.10,10.0.201.9
'@ | Set-Content -Encoding ASCII .env
```

For the real 9-computer run, replace the six `LAB_*_IP` values and the
`MULLIGAN_DB_HOSTS`, `MULLIGAN_QUEUE_HOSTS`, and `MULLIGAN_TLS_EXTRA_IPS`
lists with the real DB and RabbitMQ computer IPs.

## 3. Generate TLS Files Once

Run this once on computer 1, after `.env` is ready:

```powershell
$env:MULLIGAN_TLS_STORE_PASSWORD="changeit"
$env:MULLIGAN_TLS_EXTRA_IPS="10.0.201.11,10.0.201.10,10.0.201.9"
.\scripts\generate-rabbitmq-certs.ps1
```

For 9 computers, use the three RabbitMQ IPs in `MULLIGAN_TLS_EXTRA_IPS`.

After generation, copy this folder to every RabbitMQ computer and every UI
computer:

```text
infra\rabbitmq\certs
```

The UI apps need `client_truststore.jks` and `client_keystore.p12`. The
RabbitMQ servers need `ca_certificate.pem`, `server_certificate.pem`, and
`server_key.pem`.

## 4. Open Firewall Ports

Run PowerShell as Administrator.

On every PostgreSQL computer:

```powershell
New-NetFirewallRule -DisplayName "Mulligan PostgreSQL" -Direction Inbound -Protocol TCP -LocalPort 5432 -Action Allow
```

On every RabbitMQ computer:

```powershell
New-NetFirewallRule -DisplayName "Mulligan RabbitMQ TLS" -Direction Inbound -Protocol TCP -LocalPort 5671 -Action Allow
New-NetFirewallRule -DisplayName "Mulligan RabbitMQ Erlang PMD" -Direction Inbound -Protocol TCP -LocalPort 4369 -Action Allow
New-NetFirewallRule -DisplayName "Mulligan RabbitMQ Cluster" -Direction Inbound -Protocol TCP -LocalPort 25672 -Action Allow
New-NetFirewallRule -DisplayName "Mulligan RabbitMQ HTTPS Management" -Direction Inbound -Protocol TCP -LocalPort 15671 -Action Allow
```

## 5. Clean Old Docker Containers

Run on each Docker computer before a fresh test:

```powershell
docker compose -f docker-compose.9-laptops.yml --profile db1 --profile db2 --profile db3 --profile rmq1 --profile rmq2 --profile rmq3 down --remove-orphans
docker container prune -f
```

If you want to delete all old database and queue data:

```powershell
docker compose -f docker-compose.9-laptops.yml --profile db1 --profile db2 --profile db3 --profile rmq1 --profile rmq2 --profile rmq3 down -v --remove-orphans
docker container prune -f
docker volume prune -f
```

## 6. Start Every Computer

### Computer 1

Make it the first database server:

```powershell
.\scripts\start-9-laptop-node.ps1 -Role db1
```

For the 3-computer smoke test, also make it the first RabbitMQ server:

```powershell
.\scripts\start-9-laptop-node.ps1 -Role rmq1
```

### Computer 2

Make it the second database server:

```powershell
.\scripts\start-9-laptop-node.ps1 -Role db2
```

For the 3-computer smoke test, also make it the second RabbitMQ server:

```powershell
.\scripts\start-9-laptop-node.ps1 -Role rmq2
```

### Computer 3

Make it the third database server:

```powershell
.\scripts\start-9-laptop-node.ps1 -Role db3
```

For the 3-computer smoke test, also make it the third RabbitMQ server:

```powershell
.\scripts\start-9-laptop-node.ps1 -Role rmq3
```

### Computer 4

For the real 9-computer run, make it the first RabbitMQ server:

```powershell
.\scripts\start-9-laptop-node.ps1 -Role rmq1
```

### Computer 5

For the real 9-computer run, make it the second RabbitMQ server:

```powershell
.\scripts\start-9-laptop-node.ps1 -Role rmq2
```

### Computer 6

For the real 9-computer run, make it the third RabbitMQ server:

```powershell
.\scripts\start-9-laptop-node.ps1 -Role rmq3
```

### Computer 7

Run the Customer UI:

```powershell
.\scripts\run-ui.ps1 -App customer
```

Customer CLI:

```powershell
.\scripts\run-ui.ps1 -App customer -Cli
```

### Computer 8

Run the PEO UI:

```powershell
.\scripts\run-ui.ps1 -App peo
```

PEO CLI:

```powershell
.\scripts\run-ui.ps1 -App peo -Cli
```

### Computer 9

Run the MO UI:

```powershell
.\scripts\run-ui.ps1 -App mo
```

MO CLI:

```powershell
.\scripts\run-ui.ps1 -App mo -Cli
```

## 7. Check Network Before Running UIs

For the 3-computer smoke test:

```powershell
.\scripts\lab-preflight.ps1 -DatabaseHosts 10.0.201.11,10.0.201.10,10.0.201.9 -RabbitHosts 10.0.201.11,10.0.201.10,10.0.201.9
```

Manual checks:

```powershell
Test-NetConnection 10.0.201.11 -Port 5432
Test-NetConnection 10.0.201.10 -Port 5432
Test-NetConnection 10.0.201.9 -Port 5432
Test-NetConnection 10.0.201.11 -Port 5671
Test-NetConnection 10.0.201.10 -Port 5671
Test-NetConnection 10.0.201.9 -Port 5671
```

Each result should show `TcpTestSucceeded : True`.

## 8. Verify Clusters

On the first PostgreSQL computer:

```powershell
docker compose -f docker-compose.9-laptops.yml exec postgres-node1 repmgr cluster show
```

On the first RabbitMQ computer:

```powershell
docker compose -f docker-compose.9-laptops.yml exec rabbitmq1 rabbitmq-diagnostics listeners
docker compose -f docker-compose.9-laptops.yml exec rabbitmq1 rabbitmqctl cluster_status
docker compose -f docker-compose.9-laptops.yml exec rabbitmq1 rabbitmqctl list_queues name type leader members
docker compose -f docker-compose.9-laptops.yml exec rabbitmq1 rabbitmq-queues quorum_status Transactions
docker compose -f docker-compose.9-laptops.yml exec rabbitmq1 rabbitmq-queues quorum_status Citations
```

Expected:

- RabbitMQ has `amqp/ssl` on port `5671`.
- `Transactions` and `Citations` are quorum queues.
- RabbitMQ cluster status shows three running nodes.
- PostgreSQL shows one primary and two standby nodes.

## 9. Common Mistakes

- Do not use `localhost` on UI computers.
- Do not write `10.0.201.09`; write `10.0.201.9`.
- Generate certificates again if RabbitMQ IPs change.
- Copy `infra\rabbitmq\certs` to every RabbitMQ and UI computer.
- Run Gradle from the repository root, or use `scripts\run-ui.ps1`.
