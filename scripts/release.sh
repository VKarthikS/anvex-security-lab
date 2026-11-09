#!/usr/bin/env bash
set -euo pipefail

VERSION="${VERSION:-1.0.0}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if ! command -v java >/dev/null 2>&1; then
  echo "No Java runtime found. Install Java 21 or set JAVA_HOME." >&2
  exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "npm is required to build the frontend before creating a release." >&2
  exit 1
fi
npm ci --prefix frontend
npm run build --prefix frontend

echo "Building ANVEX with tests..."
bash ./mvnw -q clean package

RELEASE_ROOT="$ROOT/release"
STAGE="$RELEASE_ROOT/ANVEX-$VERSION"
rm -rf "$STAGE"
mkdir -p "$STAGE/defender" "$STAGE/simulator" "$STAGE/docs" "$STAGE/scripts"

cp defender/target/anvex-defender.jar "$STAGE/defender/anvex-defender.jar"
cp simulator/target/anvex-simulator.jar "$STAGE/simulator/anvex-simulator.jar"
cp README.md ARCHITECTURE.md SECURITY.md "$STAGE/"
for doc in OPERATIONS API_CONTRACT VERIFICATION DESIGN DEMO_SCRIPT JAVA_CONCEPTS; do
  [ -f "docs/$doc.md" ] && cp "docs/$doc.md" "$STAGE/docs/"
done
for script in dev-start.sh dev-start.ps1 connectivity-check.sh connectivity-check.ps1 smoke-test.ps1; do
  [ -f "scripts/$script" ] && cp "scripts/$script" "$STAGE/scripts/"
done

printf '%s\n' "$VERSION" > "$STAGE/VERSION"

cat > "$STAGE/README-FIRST.txt" <<'EOF'
ANVEX - classroom release
=========================

1. Copy the whole folder to the target laptop.
2. Install Java 21 (JRE or JDK). Nothing else is needed - no Maven, no Node, no internet.
3. Laptop 1 (defender):  java -jar defender/anvex-defender.jar
   Laptop 2 (simulator): java -jar simulator/anvex-simulator.jar
4. Open http://127.0.0.1:8080 (defender) and http://127.0.0.1:8081 (simulator).
5. On Laptop 2, set the target to the LAN address of Laptop 1, for example
   http://192.168.1.50:8080, and press 'Test connection'.
6. See docs/OPERATIONS.md for firewall rules, troubleshooting and the demo script.

Runtime data (H2 database, logs) is created next to the JARs in data/ and logs/.
EOF

(
  cd "$STAGE"
  find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS
)

ZIP="$RELEASE_ROOT/ANVEX-$VERSION.zip"
rm -f "$ZIP"
if command -v zip >/dev/null 2>&1; then
  (cd "$RELEASE_ROOT" && zip -qr "ANVEX-$VERSION.zip" "ANVEX-$VERSION")
else
  python3 - "$RELEASE_ROOT" "$VERSION" <<'PY'
import shutil, sys, os
root, version = sys.argv[1], sys.argv[2]
shutil.make_archive(os.path.join(root, f"ANVEX-{version}"), "zip", os.path.join(root, f"ANVEX-{version}"))
PY
fi

echo ""
echo "Release ready: $ZIP"
echo "Staged folder : $STAGE"
