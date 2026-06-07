#!/usr/bin/env bash
#
# TLS material generator for the Mulligan AMQPS deployment.
#
# Produces:
#   infra/certs/ca.pem                 - self-signed CA (also imported into truststore)
#   infra/private-ca/ca-key.pem        - CA private key (never mounted at runtime)
#   infra/certs/server-cert.pem        - RabbitMQ server cert (SANs: rabbit-1/2/3, haproxy, localhost, optional MULLIGAN_TLS_EXTRA_IPS)
#   infra/certs/server-key.pem         - RabbitMQ server private key
#   infra/certs/truststore.p12         - Java PKCS12 truststore (Java clients trust ca.pem)
#
# Per-service client keystores (for optional mTLS):
#   infra/certs/client-customer.p12
#   infra/certs/client-peo.p12
#   infra/certs/client-mo.p12
#   infra/certs/client-server.p12
#
# Usage:
#   ./scripts/generate-certs.sh
#
# Override the keystore password with MULLIGAN_TLS_PASSWORD (default
# `mulligan_tls_pw`). The script is idempotent: if the full certificate set is
# already present it exits without regenerating; if an older partial set exists,
# it is regenerated so the mTLS client keystores are not silently missing.

set -euo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")"/.. && pwd)
OUT="$HERE/infra/certs"
PRIVATE_OUT="$HERE/infra/private-ca"
mkdir -p "$OUT"
mkdir -p "$PRIVATE_OUT"

PASS=${MULLIGAN_TLS_PASSWORD:-mulligan_tls_pw}
EXTRA_IPS=${MULLIGAN_TLS_EXTRA_IPS:-}

required=(
  ca.pem
  server-cert.pem
  server-key.pem
  truststore.p12
  client-customer.p12
  client-peo.p12
  client-mo.p12
  client-server.p12
)
missing=0
for file in "${required[@]}"; do
  [[ -f "$OUT/$file" ]] || missing=1
done

if [[ "$missing" -eq 0 ]]; then
  echo "TLS material already present in $OUT"
  exit 0
fi

if find "$OUT" -mindepth 1 -maxdepth 1 | read -r _; then
  echo "Incomplete TLS material found in $OUT; regenerating the generated certificate set."
  find "$OUT" -mindepth 1 -maxdepth 1 \
    \( -name 'ca*' -o -name 'server*' -o -name 'client-*' -o -name 'truststore.p12' -o -name '*.csr' -o -name '*.ext' -o -name '*.srl' \) \
    -exec rm -f {} +
fi

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command not found: $1"
    exit 2
  }
}
require_cmd openssl
require_cmd keytool

# -------- CA --------
openssl genrsa -out "$PRIVATE_OUT/ca-key.pem" 4096
openssl req -x509 -new -nodes -key "$PRIVATE_OUT/ca-key.pem" -sha256 -days 3650 \
  -subj "/CN=Mulligan Dev CA" -out "$OUT/ca.pem"

# -------- Server (RabbitMQ + HAProxy) --------
openssl genrsa -out "$OUT/server-key.pem" 4096
openssl req -new -key "$OUT/server-key.pem" \
  -subj "/CN=mulligan-rabbit" -out "$OUT/server.csr"

cat > "$OUT/v3.ext" <<'EOF'
subjectAltName = @alt_names
extendedKeyUsage = serverAuth
[alt_names]
DNS.1 = rabbit-1
DNS.2 = rabbit-2
DNS.3 = rabbit-3
DNS.4 = haproxy
DNS.5 = localhost
EOF

ip_index=1
if [[ -n "$EXTRA_IPS" ]]; then
  IFS=',' read -ra san_ips <<< "$EXTRA_IPS"
  for ip in "${san_ips[@]}"; do
    ip=$(echo "$ip" | xargs)
    if [[ -n "$ip" ]]; then
      echo "IP.$ip_index = $ip" >> "$OUT/v3.ext"
      ip_index=$((ip_index + 1))
    fi
  done
fi

openssl x509 -req -in "$OUT/server.csr" -CA "$OUT/ca.pem" -CAkey "$PRIVATE_OUT/ca-key.pem" -CAcreateserial \
  -out "$OUT/server-cert.pem" -days 825 -sha256 -extfile "$OUT/v3.ext"

# -------- Java truststore --------
keytool -importcert -alias mulligan-ca -file "$OUT/ca.pem" \
  -keystore "$OUT/truststore.p12" -storetype PKCS12 -storepass "$PASS" -noprompt

# -------- Per-service client certs (optional mTLS) --------
for svc in customer peo mo server; do
  openssl genrsa -out "$OUT/client-${svc}-key.pem" 4096
  openssl req -new -key "$OUT/client-${svc}-key.pem" \
    -subj "/CN=mulligan_${svc}" -out "$OUT/client-${svc}.csr"

  cat > "$OUT/client-${svc}.ext" <<EOF
extendedKeyUsage = clientAuth
subjectAltName = DNS:mulligan_${svc}
EOF

  openssl x509 -req -in "$OUT/client-${svc}.csr" -CA "$OUT/ca.pem" -CAkey "$PRIVATE_OUT/ca-key.pem" -CAcreateserial \
    -out "$OUT/client-${svc}-cert.pem" -days 825 -sha256 -extfile "$OUT/client-${svc}.ext"

  openssl pkcs12 -export \
    -inkey "$OUT/client-${svc}-key.pem" \
    -in "$OUT/client-${svc}-cert.pem" \
    -certfile "$OUT/ca.pem" \
    -name "mulligan_${svc}" \
    -out "$OUT/client-${svc}.p12" \
    -passout pass:"$PASS"
done

# Permissions: the key files are read-only to the user (and to the broker
# inside the container - it runs as the rabbitmq user but the file is
# bind-mounted with the host's owner).
chmod 600 "$OUT"/*key*.pem || true
chmod 644 "$OUT"/*cert*.pem "$OUT"/ca.pem || true

echo "Generated TLS material in $OUT"
echo "CA signing key kept outside runtime mounts: $PRIVATE_OUT/ca-key.pem"
echo "  truststore password = $PASS"
echo
echo "To activate the hardened Assignment 3 stack:"
echo "  docker compose up -d --build"
