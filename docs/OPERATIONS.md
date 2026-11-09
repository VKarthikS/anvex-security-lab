# ANVEX Operations Guide

Everything here refers to files that exist in this repository. Commands are given for Windows PowerShell and for Linux/macOS.

## 1. Requirements

| Purpose | Requirement |
|---|---|
| Build | Java 21 (JDK), Maven Wrapper (bundled — no Maven install needed), Node.js 20+ (frontend build only) |
| Packaged runtime | Java 21 and a browser only. No Node, no Maven, no network access, no CDN |
| Network | Laptop 1 ↔ Laptop 2 over the local network, TCP 8080 (defender) and TCP 8081 (simulator, loopback only) |

The defender binds `0.0.0.0:8080` by default so it can be reached from Laptop 2; the simulator binds `127.0.0.1:8081` so its controls are never exposed to the network.

## 2. Development environment

Install a JDK 21 and Node.js 20.19+ with npm. The Maven Wrapper in this repository downloads Maven automatically on first use. Make sure `java -version` reports Java 21.

If Java is installed but not on the PATH, set `JAVA_HOME` to the actual directory containing the JDK. Do not copy a path from another developer's machine.

## 3. Build

The frontend build writes both web apps into the Java modules' static resources, **so run it before packaging**.

```powershell
# Windows PowerShell
npm ci --prefix frontend
npm run typecheck --prefix frontend
npm run build --prefix frontend
.\mvnw.cmd clean verify
```

```bash
# macOS / Linux
npm ci --prefix frontend
npm run typecheck --prefix frontend
npm run build --prefix frontend
bash ./mvnw clean verify
```

The `scripts/dev-start.*` helpers automate the frontend build and Java packaging. No existing `target/` or generated frontend output is committed to the repository.

## 4. Run both services locally

```powershell
# Windows PowerShell
powershell -ExecutionPolicy Bypass -File scripts\dev-start.ps1
```

```bash
# Linux / macOS
bash scripts/dev-start.sh
```

Manual equivalent:

```powershell
# Set JAVA_HOME to your installed JDK 21 path if Java is not already on PATH
java -jar defender\target\anvex-defender.jar --server.port=8080
java -jar simulator\target\anvex-simulator.jar --server.port=8081 --anvex.simulator.target-base-url=http://127.0.0.1:8080
```

Then open <http://127.0.0.1:8080> (defender) and <http://127.0.0.1:8081> (simulator).

## 5. Runtime configuration

| Variable | Service | Default | Meaning |
|---|---|---|---|
| `ANVEX_DEFENDER_PORT` | defender | `8080` | HTTP port |
| `ANVEX_DEFENDER_ADDRESS` | defender | `0.0.0.0` | Bind interface (`127.0.0.1` for a loopback-only installation) |
| `ANVEX_DEFENDER_DATA_DIR` | defender | `./data` | Directory that holds the file-backed H2 database |
| `ANVEX_DEFENDER_LOG_FILE` | defender | `./logs/defender.log` | Log file (mutable data stays outside the JAR) |
| `ANVEX_SIMULATOR_PORT` | simulator | `8081` | HTTP port |
| `ANVEX_SIMULATOR_ADDRESS` | simulator | `127.0.0.1` | Bind interface (keep loopback) |
| `ANVEX_TARGET_BASE_URL` | simulator | `http://127.0.0.1:8080` | Defender base URL used at start-up (the operator can change it in the UI) |

Detection defaults (also editable by the analyst through the UI, which creates a new policy version):

| Rule | Default |
|---|---|
| Suspected repeated guessing | ≥ 8 failed authentications for one source/account within 30 s |
| Suspected password spraying | ≥ 10 failures covering ≥ 8 distinct usernames from one source within 60 s |
| Unusually rapid requests | ≥ 30 incoming login requests from one source within 5 s |
| Token bucket (protection ON) | 5 requests/second, burst 10 |
| Temporary source block | 30 s |
| Initial protection mode | OFF (persisted afterwards) |

## 6. Seeded dummy accounts

Twelve accounts are seeded on first start and stored as salted BCrypt hashes. The demo passwords are intentionally published — this is a classroom dummy application.

| Username | Display name | Role | Demo password |
|---|---|---|---|
| `asha.raman` | Asha Raman | STUDENT | `Asha!2345` |
| `rohan.mehta` | Rohan Mehta | STUDENT | `Rohan!2345` |
| `meera.iyer` | Meera Iyer | STUDENT | `Meera!2345` |
| `kabir.singh` | Kabir Singh | STUDENT | `Kabir!2345` |
| `zoya.khan` | Zoya Khan | STUDENT | `Zoya!2345` |
| `arjun.nair` | Arjun Nair | STUDENT | `Arjun!2345` |
| `priya.desai` | Priya Desai | STUDENT | `Priya!2345` |
| `vikram.rao` | Vikram Rao | STUDENT | `Vikram!2345` |
| `neha.joshi` | Neha Joshi | STUDENT | `Neha!2345` |
| `aditya.pillai` | Aditya Pillai | STUDENT | `Aditya!2345` |
| `sara.khan` | Sara Khan | STUDENT | `Sara!2345` |
| `lab.admin` | Lab Administrator | ANALYST | `Admin!2345` |

The same list is served by `GET /api/v1/demo/accounts` on the defender so the simulator can select targets.

## 7. Two-laptop setup

1. **Build the release** on the development laptop (section 8) and copy the archive to Laptop 2.
2. **Laptop 1 (defender)**: extract the release, run the defender, and note its LAN address.
3. **Laptop 2 (simulator)**: extract only the simulator components, run the simulator, and point it at the defender's LAN address, e.g. `http://192.168.1.50:8080` (UI: *Target*; start-up variable: `ANVEX_TARGET_BASE_URL`).
4. **Never copy the defender database to Laptop 2.** The simulator has no database.

### Finding the LAN address

```powershell
# Windows PowerShell
Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -notmatch 'Loopback' } |
    Select-Object InterfaceAlias, IPAddress
ipconfig
```

```bash
# Linux / macOS
ip -4 addr show      # Linux
ifconfig | grep 'inet '   # macOS
```

### Connectivity checks

```powershell
# From Laptop 2 (or from Laptop 1 against its own LAN address)
Test-NetConnection -ComputerName 192.168.1.50 -Port 8080
Invoke-RestMethod -Uri 'http://192.168.1.50:8080/api/v1/identity'
Invoke-RestMethod -Uri 'http://192.168.1.50:8080/api/v1/health'
```

```bash
# Linux / macOS
nc -vz 192.168.1.50 8080
curl -s http://192.168.1.50:8080/api/v1/identity
curl -s http://192.168.1.50:8080/api/v1/health
```

A browser cannot discover or manage the other laptop automatically — the target URL is always explicit.

### Narrow firewall rule (Windows)

Do not disable the firewall. If the defender's port is blocked, add an inbound rule for TCP 8080 restricted to the private profile:

```powershell
New-NetFirewallRule -DisplayName 'ANVEX Defender 8080 (Private)' `
    -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8080 `
    -Profile Private -RemoteAddress LocalSubnet
```

Remove it again with:

```powershell
Remove-NetFirewallRule -DisplayName 'ANVEX Defender 8080 (Private)'
```

### Narrow firewall rule (Linux, ufw)

```bash
sudo ufw allow from 192.168.1.0/24 to any port 8080 proto tcp   # add
sudo ufw delete allow from 192.168.1.0/24 to any port 8080 proto tcp  # remove
```

### Narrow firewall rule (macOS)

The macOS application firewall does not expose a simple per-port CLI rule; use *System Settings → Network → Firewall → Options…* and allow the Java process on the private network, or temporarily disable the firewall only for the demo if no scoped rule is possible.

## 8. Release build

```powershell
# Windows PowerShell
powershell -ExecutionPolicy Bypass -File scripts\release.ps1
```

```bash
# Linux / macOS
./scripts/release.sh
```

The script builds both JARs, assembles `release/ANVEX-1.0.0/` (defender and simulator folders, scripts, docs, `VERSION`, `SHA256SUMS`), and zips it to `release/ANVEX-1.0.0.zip`. The archive contains no database, no logs and no secrets; mutable data is created next to the JAR in `data/` and `logs/`.

Offline launch on Laptop 1:

```powershell
java -jar defender\anvex-defender.jar
```

Offline launch on Laptop 2:

```powershell
java -jar simulator\anvex-simulator.jar
```

Only Java 21 and a browser are required at runtime. The packaged JARs already contain the built frontend assets; no `npm install` or Maven resolution happens at runtime.

## 9. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Simulator reports `Could not reach the defender: ConnectException` | wrong address, defender down, firewall | verify the LAN address, run `Test-NetConnection`, add the scoped firewall rule |
| Connection times out only from Laptop 2 | Wi-Fi client isolation, wrong adapter, VPN routing | check that both laptops are on the same subnet, disable client isolation on the hotspot, inspect the VPN's local-network routing; disconnect the VPN only if diagnostics show it blocks local traffic |
| `403 analyst_loopback_required` | analyst control called from a remote address | open the analyst UI on the defender laptop itself (loopback) — this is the intended separation |
| Defender reports `service_overloaded` | the critical event queue is saturated | check `/api/v1/diagnostics` for queue depth and persistence errors; the service stays enforcing, but evidence writes are degraded |
| `409 run_in_progress` | a simulation is still running | press Stop and wait for the run to settle, or restart the simulator |
| Empty dashboard but traffic was sent | the simulator targeted a different address | compare the simulator's target with the defender's observed source IP (`X-ANVEX-Source-Ip`) |
| Port already in use | another process owns 8080/8081 | `netstat -ano | findstr :8080` (Windows) or `lsof -i :8080` (Linux/macOS), then stop that process or change the port |
| Versions differ between laptops | the two sides were built at different times | compare `GET /api/v1/identity` on both; the simulator refuses to run against an incompatible protocol |
