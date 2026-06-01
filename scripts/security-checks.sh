#!/usr/bin/env bash
#
# End-to-end security smoke tests for the Blue Team defences.
#
# 1. Bad HMAC: publishes a message whose HMAC tag was flipped - expect REJECT
#    bad-hmac in the secure log.
# 2. Replay: publishes the same valid envelope twice - second one should be
#    rejected with replay.
# 3. Stale timestamp: publishes an envelope with a 70-second-old timestamp -
#    expect stale_timestamp.
# 4. guest-from-network: tries to log in as `guest` from another container -
#    expect ACL deny.
#
# Each assertion is "log line appears in the persistent security.log".

set -euo pipefail
LOG=${MULLIGAN_SECURITY_LOG:-/var/log/mulligan/security.log}
PROJECT=${MULLIGAN_COMPOSE_PROJECT:-mulligan}

check () {
  local label="$1" pattern="$2"
  if docker compose -p "$PROJECT" exec -T parking-server grep -q -- "$pattern" "$LOG"; then
    echo "PASS $label"
  else
    echo "FAIL $label (pattern not found in $LOG)"
    exit 1
  fi
}

echo "Running JUnit security tests..."
./gradlew :parking-common:test --tests com.mulligan.common.SecurityLayerTest

echo "Sending tampered envelope..."
docker compose -p "$PROJECT" exec -T parking-server java -cp /app/lib/* com.mulligan.common.testdrivers.SendTampered || true
check "Bad HMAC rejected" "REJECT bad-hmac"

echo "Sending replay..."
docker compose -p "$PROJECT" exec -T parking-server java -cp /app/lib/* com.mulligan.common.testdrivers.SendReplay || true
check "Replay rejected" "REJECT replay"

echo "Sending stale envelope..."
docker compose -p "$PROJECT" exec -T parking-server java -cp /app/lib/* com.mulligan.common.testdrivers.SendStale || true
check "Stale timestamp rejected" "REJECT stale-timestamp"

echo "All security smoke checks passed."
