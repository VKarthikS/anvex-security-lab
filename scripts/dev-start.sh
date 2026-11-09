#!/usr/bin/env bash
set -euo pipefail

DEFENDER_PORT="${DEFENDER_PORT:-8080}"
SIMULATOR_PORT="${SIMULATOR_PORT:-8081}"
TARGET_BASE_URL="${TARGET_BASE_URL:-}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if ! command -v java >/dev/null 2>&1; then
  echo "No Java runtime found. Install Java 21 or set JAVA_HOME." >&2
  exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "npm is required to build the web interfaces. Install Node.js 20.19+." >&2
  exit 1
fi

echo "Installing frontend dependencies and building web interfaces..."
npm ci --prefix frontend
npm run build --prefix frontend

echo "Building ANVEX Java services..."
bash ./mvnw -q -DskipTests package

mkdir -p logs

echo "Starting defender on port $DEFENDER_PORT..."
java -jar defender/target/anvex-defender.jar \
  "--server.port=$DEFENDER_PORT" --server.address=0.0.0.0 \
  > logs/defender-console.log 2>&1 &
DEFENDER_PID=$!

echo "Starting simulator on port $SIMULATOR_PORT..."
SIM_ARGS=(-jar simulator/target/anvex-simulator.jar
  "--server.port=$SIMULATOR_PORT" --server.address=127.0.0.1)
if [ -n "$TARGET_BASE_URL" ]; then
  SIM_ARGS+=("--anvex.simulator.target-base-url=$TARGET_BASE_URL")
fi
java "${SIM_ARGS[@]}" > logs/simulator-console.log 2>&1 &
SIMULATOR_PID=$!

cleanup() {
  echo "Stopping ANVEX..."
  kill "$SIMULATOR_PID" "$DEFENDER_PID" 2>/dev/null || true
  wait "$SIMULATOR_PID" "$DEFENDER_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

wait_for_health() {
  local url="$1"
  for _ in $(seq 1 120); do
    if curl -fsS "$url" 2>/dev/null | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep 0.5
  done
  return 1
}

echo "Waiting for the defender..."
wait_for_health "http://127.0.0.1:$DEFENDER_PORT/api/v1/health"
echo "Waiting for the simulator..."
wait_for_health "http://127.0.0.1:$SIMULATOR_PORT/api/v1/health"

cat <<EOF

ANVEX is running.
  Defender console : http://127.0.0.1:$DEFENDER_PORT
  Simulator station: http://127.0.0.1:$SIMULATOR_PORT
  Logs             : $ROOT/logs

Press Ctrl+C to stop both services.

EOF

wait
