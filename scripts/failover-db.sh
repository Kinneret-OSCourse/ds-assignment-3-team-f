#!/usr/bin/env bash
#
# Kills the current Patroni primary, waits for the cluster to elect a new
# leader, and asserts that the application can still write through HAProxy.
# Used by Assignment 2 §5.4 testing requirement (Failure and recovery of one
# database node).
#
# Usage:
#   ./scripts/failover-db.sh
#

set -euo pipefail

PROJECT=${MULLIGAN_COMPOSE_PROJECT:-mulligan}

LEADER=$(docker compose -p "$PROJECT" exec -T patroni-1 patronictl list | awk '/Leader/ {print $2; exit}') || true

if [[ -z "${LEADER:-}" ]]; then
  echo "Could not determine current leader; defaulting to patroni-1"
  LEADER=patroni-1
fi

PEER=patroni-1
if [[ "$LEADER" == "patroni-1" ]]; then
  PEER=patroni-2
fi

echo "Killing current leader: $LEADER"
docker compose -p "$PROJECT" stop "$LEADER"

echo "Waiting 40s for new leader election and HAProxy refresh..."
sleep 40

docker compose -p "$PROJECT" exec -T "$PEER" patronictl list
NEW=$(docker compose -p "$PROJECT" exec -T "$PEER" patronictl list | awk '/Leader/ {print $2; exit}') || true
echo "New leader: ${NEW:-unknown}"

echo "Sanity-write through the Customer CLI..."
docker compose -p "$PROJECT" exec -T customer-ui java -cp /app/lib/* com.mulligan.customer.cli.CustomerCLI start CUST-1001 604-95-839 S010
docker compose -p "$PROJECT" exec -T customer-ui java -cp /app/lib/* com.mulligan.customer.cli.CustomerCLI stop CUST-1001 604-95-839

echo "Bringing the killed node back..."
docker compose -p "$PROJECT" start "$LEADER"
sleep 20
docker compose -p "$PROJECT" exec -T "$PEER" patronictl list

echo "OK: failover succeeded and node $LEADER is restarting"
