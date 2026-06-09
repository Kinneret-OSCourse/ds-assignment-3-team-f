# Queue Server Design - Assignment 3

## 1. Topology

RabbitMQ 3.13, deployed as a **3-node cluster** (`rabbit-1`, `rabbit-2`,
`rabbit-3`) joined by a shared Erlang cookie. All queues used by the
application are **quorum queues** with replication factor 3.

```
                   ┌─────────────────────────────────────┐
                   │           mulligan.exchange         │
                   │              (direct)               │
                   └──┬──────────────────────────────┬───┘
   routing_key:       │                              │   routing_key:
   transaction.       │                              │   citation.issued
   completed          │                              │
                      ▼                              ▼
          ┌──────────────────────┐      ┌──────────────────────┐
          │  Transactions queue  │      │  Citations queue     │
          │  type=quorum, durable│      │  type=quorum, durable│
          │  replicated 3/3      │      │  replicated 3/3      │
          └──────────────────────┘      └──────────────────────┘
```

The exchange, queues, bindings and the `mulligan-quorum` policy are
declared declaratively in
[`infra/rabbitmq/definitions.json`](infra/rabbitmq/definitions.json) which
RabbitMQ loads at boot. The same topology is re-declared idempotently in
`com.mulligan.common.messaging.QueueTopology.declare(Channel)` so a client
can run against a fresh broker too.

## 2. Cluster Formation

- All three RabbitMQ nodes boot with `infra/rabbitmq/rabbitmq-mtls.conf`,
  which loads `/etc/rabbitmq/definitions.json` and uses static cluster
  formation for `rabbit@rabbit-1`, `rabbit@rabbit-2`, and `rabbit@rabbit-3`.
- `cluster_partition_handling = pause_minority` is set in
  `rabbitmq-mtls.conf` so a network split pauses the side without quorum
  rather than diverging.

## 3. Per-Service Users (Hardened ACL)

| User | Tags | Configure | Write | Read | Topic ACL |
| --- | --- | --- | --- | --- | --- |
| `mulligan_admin`    | administrator | `.*` | `.*` | `.*` | n/a |
| `mulligan_server`   | — | exchange + 2 queues | exchange | `Transactions|Citations` | n/a |
| `mulligan_customer` | — | none | `mulligan.exchange` | none | publish `transaction.completed` only |
| `mulligan_peo`      | — | none | `mulligan.exchange` | none | publish `citation.issued` only |
| `mulligan_mo`       | — | none | none | `Transactions|Citations` | n/a |

The default `guest` user is restricted to loopback by
`loopback_users.guest = true` in `rabbitmq-mtls.conf`.

## 4. Message Format (HMAC envelope)

Every publish/consume goes through `SecureMessage`:

```json
{
  "nonce":     "<UUID v4>",
  "timestamp": <unix seconds>,
  "payload":   "<inner JSON>",
  "hmac":      "<lowercase hex HMAC-SHA256 over `${nonce}|${timestamp}|${payload}`>"
}
```

Inner payload formats (Customer transaction):

```json
{
  "transactionId": "...",
  "vehicleNumber": "...",
  "spaceId":       "...",
  "zone":          "...",
  "startTime":     "<ISO local datetime>",
  "endTime":       "<ISO local datetime>",
  "amount":        <number>
}
```

Inner payload for PEO citations:

```json
{
  "citationId":     "...",
  "vehicleNumber":  "...",
  "spaceId":        "...",
  "zone":           "...",
  "inspectionTime": "<ISO local datetime>",
  "amount":         <number>
}
```

`parking-server` verifies HMAC, timestamp window (60 s) and nonce
uniqueness on every delivery via `MessageValidator`. Failures are
`basicNack(requeue=false)` and audited in the secure log.

## 5. Connection Failover

`MulliganConnectionFactory.create()` builds a `ConnectionFactory` with
automatic recovery enabled and calls
`factory.newConnection(Address[])` with the comma-separated
`MULLIGAN_QUEUE_HOSTS` list. The official AMQP client rotates through the
list on connect and reconnects automatically when an active connection
drops. Consumers re-attach to their queue after a reconnect because
`topologyRecoveryEnabled` is on.

## 6. Failover Test

`./scripts/failover-rabbit.sh` stops one cluster member, publishes a
probe message through another, restarts the stopped member and asserts
it rejoins. See `TESTING.md` for the full procedure.
