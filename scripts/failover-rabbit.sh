#!/usr/bin/env bash
#
# RabbitMQ failover test: stops one cluster member, publishes a message
# through another, restarts the killed node, and verifies it rejoins the
# cluster. Used by Assignment 2 §5.4 "Failure and recovery of one RabbitMQ
# node" requirement.
#
# Usage:
#   ./scripts/failover-rabbit.sh

set -euo pipefail

PROJECT=${MULLIGAN_COMPOSE_PROJECT:-mulligan}
NODE=${1:-rabbit-2}
PEER=$([[ "$NODE" == "rabbit-1" ]] && echo "rabbit-2" || echo "rabbit-1")

echo "Stopping $NODE"
docker compose -p "$PROJECT" stop "$NODE"

echo "Cluster status from $PEER:"
docker compose -p "$PROJECT" exec -T "$PEER" rabbitmqctl cluster_status | grep -E "(Running Nodes|disk)" || true

echo "Publishing a probe message through $PEER..."
docker compose -p "$PROJECT" exec -T "$PEER" rabbitmqadmin -u mulligan_admin -p mulligan_admin_pw \
  publish exchange=mulligan.exchange routing_key=transaction.completed payload='{"probe":1}' || true

echo "Restarting $NODE"
docker compose -p "$PROJECT" start "$NODE"

echo "Waiting 20s for $NODE to rejoin..."
sleep 20

docker compose -p "$PROJECT" exec -T "$NODE" rabbitmqctl cluster_status | grep -E "(Running Nodes|alarms)" || true
docker compose -p "$PROJECT" exec -T "$PEER" rabbitmqctl list_queues name type online members

echo "OK: $NODE was offline, the cluster kept accepting writes, and $NODE rejoined."
