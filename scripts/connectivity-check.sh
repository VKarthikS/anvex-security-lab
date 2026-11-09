#!/usr/bin/env bash
set -uo pipefail

TARGET_BASE_URL="${1:-}"
TIMEOUT="${TIMEOUT:-10}"
FAILED=0

if [ -z "$TARGET_BASE_URL" ]; then
  echo "Usage: $0 http://<defender-host>:8080" >&2
  exit 2
fi

BASE="${TARGET_BASE_URL%/}"

check() {
  local name="$1"; shift
  echo "==> $name"
  if "$@"; then
    echo "    OK"
  else
    echo "    FAILED"
    FAILED=$((FAILED + 1))
  fi
}

check "TCP port reachable" bash -c "timeout $TIMEOUT bash -c '</dev/tcp/${BASE#http://}'"

check "ANVEX identity handshake" bash -c "curl -fsS --max-time $TIMEOUT '$BASE/api/v1/identity' | grep -q '\"product\":\"ANVEX\"'"

check "Defender health" bash -c "curl -fsS --max-time $TIMEOUT '$BASE/api/v1/health' | grep -q '\"status\":\"UP\"'"

check "Public policy snapshot" bash -c "curl -fsS --max-time $TIMEOUT '$BASE/api/v1/policy' | grep -q 'policyVersion'"

check "Demo account catalogue" bash -c "curl -fsS --max-time $TIMEOUT '$BASE/api/v1/demo/accounts' | grep -q 'username'"

echo ""
if [ "$FAILED" -eq 0 ]; then
  echo "All connectivity checks passed."
  exit 0
else
  echo "$FAILED check(s) failed. See docs/OPERATIONS.md (troubleshooting)."
  exit 1
fi
