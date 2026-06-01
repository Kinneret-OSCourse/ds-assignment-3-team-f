#!/usr/bin/env bash
#
# RabbitMQ secondary-node entrypoint. Starts in detached mode, joins the
# `rabbit@rabbit-1` seed node, then runs the normal foreground server so the
# container keeps running.
#
# The Erlang cookie and node name are supplied by the docker-compose
# environment. The script is idempotent: a node that has already joined will
# just resume normally on restart.

set -euo pipefail

SEED="rabbit@rabbit-1"
MAX_WAIT=120

# Wait until the seed node is reachable to avoid join races.
for i in $(seq 1 $MAX_WAIT); do
  if rabbitmq-diagnostics -n "$SEED" ping >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

# Start RabbitMQ in the background so we can run management commands.
rabbitmq-server -detached
for i in $(seq 1 $MAX_WAIT); do
  if rabbitmq-diagnostics -n "$RABBITMQ_NODENAME" ping >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! rabbitmq-diagnostics -n "$RABBITMQ_NODENAME" ping >/dev/null 2>&1; then
  echo "Local RabbitMQ node $RABBITMQ_NODENAME did not start in time" >&2
  exit 1
fi

# Stop the app to reset to a known state, join the seed, then start it again.
rabbitmqctl --node "$RABBITMQ_NODENAME" stop_app
rabbitmqctl --node "$RABBITMQ_NODENAME" reset || true
rabbitmqctl --node "$RABBITMQ_NODENAME" join_cluster --ram "$SEED" || rabbitmqctl --node "$RABBITMQ_NODENAME" join_cluster "$SEED" || true
rabbitmqctl --node "$RABBITMQ_NODENAME" start_app

# Foreground tail keeps the container alive.
exec tail -f /var/log/rabbitmq/*.log
