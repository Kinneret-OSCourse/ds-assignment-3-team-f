#!/usr/bin/env bash
#
# Expands the Assignment 2 quorum queues to all three RabbitMQ nodes after the
# cluster and parking-server have started. The add_member command is safe to
# re-run; RabbitMQ reports existing members and the script continues.

set -euo pipefail

PROJECT=${MULLIGAN_COMPOSE_PROJECT:-mulligan}

for queue in Transactions Citations; do
  for node in rabbit@rabbit-2 rabbit@rabbit-3; do
    docker compose -p "$PROJECT" exec -T rabbit-1 \
      rabbitmq-queues add_member -p / "$queue" "$node" --membership voter \
      || echo "Replica may already exist for $queue on $node; continuing."
  done
done

docker compose -p "$PROJECT" exec -T rabbit-1 rabbitmqctl list_queues name type online members
