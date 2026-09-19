#!/usr/bin/env bash
# Starts the application, waits until it is ready, sends a fixed request load, then prints one
# markdown table row: time until /q/health/ready answers, the startup time Quarkus itself reports,
# and the resident memory of the process after the load.
#
# Usage: scripts/measure-startup.sh <label> <command> [args...]
# Expects the database and JWT settings (DB_URL, DB_USER, DB_PASSWORD, JWT_*_LOCATION) in the
# environment, and honours PORT (default 8090) and REQUESTS (default 300).
set -euo pipefail

label=$1
shift
port=${PORT:-8090}
requests=${REQUESTS:-300}
log="target/measure-${label}.log"

# An environment variable works for a jar and for a native binary alike; a -D option placed after
# "-jar" would be handed to the program instead of the JVM and silently ignored.
QUARKUS_HTTP_PORT="$port" "$@" >"$log" 2>&1 &
pid=$!
trap 'kill "$pid" 2>/dev/null || true' EXIT

start=$(date +%s%N)
until curl -sf "http://localhost:${port}/q/health/ready" >/dev/null; do
  if [ $(( ($(date +%s%N) - start) / 1000000000 )) -gt 120 ]; then
    echo "The application was not ready after 120 s:" >&2
    tail -n 40 "$log" >&2
    exit 1
  fi
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "The application exited before becoming ready:" >&2
    tail -n 40 "$log" >&2
    exit 1
  fi
  sleep 0.02
done
ready_ms=$(( ($(date +%s%N) - start) / 1000000 ))

# The same load for every mode: a health probe, the OpenAPI document and a secured endpoint
# (answers 401), which together exercise routing, serialization and the security stack.
for i in $(seq 1 "$requests"); do
  case $((i % 3)) in
    0) path=/q/health/ready ;;
    1) path=/q/openapi ;;
    2) path=/api/v1/products ;;
  esac
  curl -s -o /dev/null "http://localhost:${port}${path}"
done

reported=$(grep -o 'started in [0-9.]*s' "$log" | head -n 1 | grep -o '[0-9.]*' || echo "n/a")
rss_mb=$(awk '/VmRSS/ {printf "%d", $2 / 1024}' "/proc/${pid}/status")

echo "| ${label} | ${ready_ms} | ${reported} | ${rss_mb} |"
