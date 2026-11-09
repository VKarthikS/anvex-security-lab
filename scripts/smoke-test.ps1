<#
.SYNOPSIS
    ANVEX local smoke test: launches both real Java services and validates the
    Java-to-Java vertical slice end to end.

.DESCRIPTION
    Starts the packaged defender and simulator JARs, waits for their health
    endpoints, then exercises:
      * defender health and identity
      * ANVEX identity/version handshake from the simulator
      * a valid dummy login (accepted)
      * an invalid dummy login (generic failure)
      * a simulator manual run through the real Java HttpClient
      * a disconnected-target run (handled failure, no crash)

    The services are stopped again before the script exits.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
#>
[CmdletBinding()]
param(
    [int]$DefenderPort = 8080,
    [int]$SimulatorPort = 8081,
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$defenderJar = Join-Path $root 'defender\target\anvex-defender.jar'
$simulatorJar = Join-Path $root 'simulator\target\anvex-simulator.jar'
$logDir = Join-Path $root 'logs'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null

if (-not $JavaHome) {
    $fallback = Join-Path $env:USERPROFILE '.local\tools\jdk21'
    if (Test-Path -LiteralPath (Join-Path $fallback 'bin\java.exe')) {
        $JavaHome = $fallback
    }
}
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) {
    throw 'Java 21 not found. Set JAVA_HOME to a Java 21 installation.'
}
$java = Join-Path $JavaHome 'bin\java.exe'

if (-not $SkipBuild) {
    Write-Host '[smoke] Building packaged JARs...'
    & (Join-Path $root 'mvnw.cmd') -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }
}
foreach ($jar in @($defenderJar, $simulatorJar)) {
    if (-not (Test-Path -LiteralPath $jar)) { throw "Missing JAR: $jar" }
}

$defender = $null
$simulator = $null
$results = New-Object System.Collections.Generic.List[string]

function Add-Result([string]$name, [bool]$ok, [string]$detail) {
    $mark = if ($ok) { 'PASS' } else { 'FAIL' }
    $results.Add("$mark`t$name`t$detail")
    Write-Host ("[{0}] {1} - {2}" -f $mark, $name, $detail)
}

function Wait-ForHealth([string]$url, [int]$seconds = 60) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RestMethod -Uri $url -TimeoutSec 3
            if ($response.status -eq 'UP') { return $true }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    return $false
}

try {
    Write-Host "[smoke] Starting defender on port $DefenderPort..."
    $defender = Start-Process -FilePath $java `
        -ArgumentList '-jar', $defenderJar, "--server.port=$DefenderPort", '--anvex.data-dir=./data' `
        -WorkingDirectory $root -PassThru `
        -RedirectStandardOutput (Join-Path $logDir 'smoke-defender.out.log') `
        -RedirectStandardError (Join-Path $logDir 'smoke-defender.err.log')

    Write-Host "[smoke] Starting simulator on port $SimulatorPort..."
    $simulator = Start-Process -FilePath $java `
        -ArgumentList '-jar', $simulatorJar, "--server.port=$SimulatorPort", "--anvex.simulator.target-base-url=http://127.0.0.1:$DefenderPort" `
        -WorkingDirectory $root -PassThru `
        -RedirectStandardOutput (Join-Path $logDir 'smoke-simulator.out.log') `
        -RedirectStandardError (Join-Path $logDir 'smoke-simulator.err.log')

    $defenderHealth = Wait-ForHealth "http://127.0.0.1:$DefenderPort/api/v1/health"
    Add-Result 'defender-health' $defenderHealth "http://127.0.0.1:$DefenderPort/api/v1/health"

    $simulatorHealth = Wait-ForHealth "http://127.0.0.1:$SimulatorPort/api/v1/health"
    Add-Result 'simulator-health' $simulatorHealth "http://127.0.0.1:$SimulatorPort/api/v1/health"

    $handshake = Invoke-RestMethod -Uri "http://127.0.0.1:$SimulatorPort/api/v1/target/test" -Method Post -TimeoutSec 10
    Add-Result 'identity-handshake' ([bool]$handshake.compatible) ("product={0} version={1} protocol={2}" -f $handshake.product, $handshake.version, $handshake.protocol)

    $validBody = @{ username = 'asha.raman'; password = 'Asha!2345' } | ConvertTo-Json
    $valid = Invoke-RestMethod -Uri "http://127.0.0.1:$DefenderPort/api/v1/auth/login" -Method Post -Body $validBody -ContentType 'application/json' -TimeoutSec 10
    Add-Result 'valid-login' ($valid.status -eq 'authenticated') ("status={0} requestId={1}" -f $valid.status, $valid.requestId)

    $invalidBody = @{ username = 'asha.raman'; password = 'not-the-password' } | ConvertTo-Json
    $invalid = Invoke-RestMethod -Uri "http://127.0.0.1:$DefenderPort/api/v1/auth/login" -Method Post -Body $invalidBody -ContentType 'application/json' -TimeoutSec 10
    Add-Result 'invalid-login' ($invalid.status -eq 'invalid_credentials') ("status={0}" -f $invalid.status)

    $manual = Invoke-RestMethod -Uri "http://127.0.0.1:$SimulatorPort/api/v1/runs/manual" -Method Post `
        -Body (@{ username = 'rohan.mehta'; password = 'Rohan!2345' } | ConvertTo-Json) -ContentType 'application/json' -TimeoutSec 15
    Add-Result 'simulator-manual-login' ($manual.counters.authenticationSuccess -eq 1) ("state={0} success={1} failure={2}" -f $manual.state, $manual.counters.authenticationSuccess, $manual.counters.authenticationFailure)

    $manualBad = Invoke-RestMethod -Uri "http://127.0.0.1:$SimulatorPort/api/v1/runs/manual" -Method Post `
        -Body (@{ username = 'rohan.mehta'; password = 'nope' } | ConvertTo-Json) -ContentType 'application/json' -TimeoutSec 15
    Add-Result 'simulator-manual-failure' ($manualBad.counters.authenticationFailure -eq 1) ("state={0} failure={1}" -f $manualBad.state, $manualBad.counters.authenticationFailure)

    Invoke-RestMethod -Uri "http://127.0.0.1:$SimulatorPort/api/v1/target" -Method Put `
        -Body (@{ baseUrl = 'http://127.0.0.1:9' } | ConvertTo-Json) -ContentType 'application/json' -TimeoutSec 10 | Out-Null
    $offline = Invoke-RestMethod -Uri "http://127.0.0.1:$SimulatorPort/api/v1/runs" -Method Post `
        -Body (@{ scenarioId = 'repeated-guessing'; ratePerSecond = 2; durationSeconds = 2; targetUsernames = @('asha.raman'); requestLimit = 4; concurrency = 2 } | ConvertTo-Json) `
        -ContentType 'application/json' -TimeoutSec 20
    Add-Result 'disconnected-target-handled' ($offline.state -eq 'FAILED') ("state={0} message={1}" -f $offline.state, $offline.message)
}
finally {
    foreach ($process in @($simulator, $defender)) {
        if ($process -and -not $process.HasExited) {
            $process.Kill()
            $process.WaitForExit(10000) | Out-Null
        }
    }
}

$failed = @($results | Where-Object { $_ -like 'FAIL*' })
Write-Host ''
Write-Host "[smoke] $($results.Count - $failed.Count)/$($results.Count) checks passed."
if ($failed.Count -gt 0) {
    $failed | ForEach-Object { Write-Host $_ }
    exit 1
}
exit 0
