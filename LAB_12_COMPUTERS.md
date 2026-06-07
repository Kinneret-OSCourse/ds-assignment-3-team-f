# 12-Computer Classroom Runbook

This is the from-zero run plan for Assignment 3.

## Computer Roles

| Computer | Role | Example IP |
| --- | --- | --- |
| 1 | PostgreSQL node 1 | `10.0.201.11` |
| 2 | PostgreSQL node 2 | `10.0.201.12` |
| 3 | PostgreSQL node 3 | `10.0.201.13` |
| 4 | RabbitMQ node 1 | `10.0.201.21` |
| 5 | RabbitMQ node 2 | `10.0.201.22` |
| 6 | RabbitMQ node 3 | `10.0.201.23` |
| 7 | Recommender node 1 | `10.0.201.31` |
| 8 | Recommender node 2 | `10.0.201.32` |
| 9 | Recommender node 3 | `10.0.201.33` |
| 10 | Customer UI | outbound only |
| 11 | PEO UI | outbound only |
| 12 | MO UI | outbound only |

Use fixed LAN IPs. Do not use leading zeroes in IP addresses.

## 1. Install On Every Computer

Install:

1. Java 21 JDK
2. Docker Desktop
3. Git for Windows

Open PowerShell in the repository root:

```powershell
cd "C:\path\to\ds-assignment-3-team-f"
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

## 2. Create `.env` On Every Computer

Copy the example file:

```powershell
Copy-Item .env.12-computers.example .env
```

Edit `.env` and use the same values on all 12 computers. The example contains
all required variables: database passwords, RabbitMQ passwords, TLS password,
cluster IPs, UI connection strings, and recommender endpoints.

For the IP plan above, the important host lists are:

```text
MULLIGAN_DB_HOSTS=10.0.201.11:5432,10.0.201.12:5432,10.0.201.13:5432
MULLIGAN_QUEUE_HOSTS=10.0.201.21:5671,10.0.201.22:5671,10.0.201.23:5671
MULLIGAN_RECOMMENDER_ENDPOINTS=http://10.0.201.31:8081,http://10.0.201.32:8082,http://10.0.201.33:8083
MULLIGAN_TLS_EXTRA_IPS=10.0.201.21,10.0.201.22,10.0.201.23
```

`MULLIGAN_TLS_EXTRA_IPS` must include every RabbitMQ IP because the Java clients
verify TLS hostnames.

## 3. Generate TLS Files Once

Run this on computer 1 after `.env` is ready:

```powershell
.\scripts\generate-certs.ps1
```

Copy these generated folders to every RabbitMQ computer and every UI computer:

```text
infra\certs
```

Keep this folder only on computer 1:

```text
infra\private-ca
```

The CA private key is not needed at runtime.

## 4. Open Firewall Ports

Run PowerShell as Administrator.

On computers 1-3:

```powershell
New-NetFirewallRule -DisplayName "Mulligan PostgreSQL" -Direction Inbound -Protocol TCP -LocalPort 5432 -Action Allow
```

On computers 4-6:

```powershell
New-NetFirewallRule -DisplayName "Mulligan RabbitMQ TLS" -Direction Inbound -Protocol TCP -LocalPort 5671 -Action Allow
New-NetFirewallRule -DisplayName "Mulligan RabbitMQ Erlang PMD" -Direction Inbound -Protocol TCP -LocalPort 4369 -Action Allow
New-NetFirewallRule -DisplayName "Mulligan RabbitMQ Cluster" -Direction Inbound -Protocol TCP -LocalPort 25672 -Action Allow
New-NetFirewallRule -DisplayName "Mulligan RabbitMQ HTTPS Management" -Direction Inbound -Protocol TCP -LocalPort 15671 -Action Allow
```

On computers 7-9:

```powershell
New-NetFirewallRule -DisplayName "Mulligan Recommender" -Direction Inbound -Protocol TCP -LocalPort 8081,8082,8083 -Action Allow
```

## 5. Clean Old Containers

Run on each Docker computer before a fresh test:

```powershell
docker compose -f docker-compose.12-computers.yml --profile db1 --profile db2 --profile db3 --profile rmq1 --profile rmq2 --profile rmq3 --profile rec1 --profile rec2 --profile rec3 down --remove-orphans
docker container prune -f
```

To remove old database and queue data too:

```powershell
docker compose -f docker-compose.12-computers.yml --profile db1 --profile db2 --profile db3 --profile rmq1 --profile rmq2 --profile rmq3 --profile rec1 --profile rec2 --profile rec3 down -v --remove-orphans
docker volume prune -f
```

## 6. Start The 12 Computers

Run one command on each computer:

```powershell
.\scripts\start-12-computer-node.ps1 -Role db1
.\scripts\start-12-computer-node.ps1 -Role db2
.\scripts\start-12-computer-node.ps1 -Role db3
.\scripts\start-12-computer-node.ps1 -Role rmq1
.\scripts\start-12-computer-node.ps1 -Role rmq2
.\scripts\start-12-computer-node.ps1 -Role rmq3
.\scripts\start-12-computer-node.ps1 -Role rec1
.\scripts\start-12-computer-node.ps1 -Role rec2
.\scripts\start-12-computer-node.ps1 -Role rec3
.\scripts\run-ui.ps1 -App customer
.\scripts\run-ui.ps1 -App peo
.\scripts\run-ui.ps1 -App mo
```

CLI mode for the UI computers:

```powershell
.\scripts\run-ui.ps1 -App customer -Cli
.\scripts\run-ui.ps1 -App peo -Cli
.\scripts\run-ui.ps1 -App mo -Cli
```

## 7. Network Checks

From each UI computer:

```powershell
Test-NetConnection 10.0.201.11 -Port 5432
Test-NetConnection 10.0.201.12 -Port 5432
Test-NetConnection 10.0.201.13 -Port 5432
Test-NetConnection 10.0.201.21 -Port 5671
Test-NetConnection 10.0.201.22 -Port 5671
Test-NetConnection 10.0.201.23 -Port 5671
Test-NetConnection 10.0.201.31 -Port 8081
Test-NetConnection 10.0.201.32 -Port 8082
Test-NetConnection 10.0.201.33 -Port 8083
```

Each result should show `TcpTestSucceeded : True`.

## 8. Verify Clusters

On computer 1:

```powershell
docker compose -f docker-compose.12-computers.yml exec postgres-node1 repmgr cluster show
```

On computer 4:

```powershell
docker compose -f docker-compose.12-computers.yml exec rabbitmq1 rabbitmq-diagnostics listeners
docker compose -f docker-compose.12-computers.yml exec rabbitmq1 rabbitmqctl cluster_status
docker compose -f docker-compose.12-computers.yml exec rabbitmq1 rabbitmqctl list_queues name type leader members
```

Expected:

- PostgreSQL shows one primary and two standby nodes.
- RabbitMQ listens on `amqp/ssl` port `5671`.
- `Transactions` and `Citations` are quorum queues.
- RabbitMQ cluster status shows three running nodes.
- Customer recommendations still work when one recommender node is malicious.

## 9. Common Mistakes

- Do not use `localhost` on UI computers.
- Regenerate certificates if RabbitMQ IPs change.
- Copy `infra\certs` to every RabbitMQ and UI computer.
- Keep `infra\private-ca` private.
- Use the same `.env` on every computer.
