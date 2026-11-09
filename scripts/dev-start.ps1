param(
    [int]$DefenderPort = 8080,
    [int]$SimulatorPort = 8081,
    [string]$TargetBaseUrl = '',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$portableJdk = Join-Path $env:USERPROFILE '.local\tools\jdk21'
if ((-not $env:JAVA_HOME) -and (Test-Path -LiteralPath (Join-Path $portableJdk 'bin\java.exe'))) {
    $env:JAVA_HOME = $portableJdk
}
if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $env:PATH = "$(Join-Path $env:JAVA_HOME 'bin');$env:PATH"
}

function Find-Java {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    $candidates += (Join-Path $env:USERPROFILE '.local\tools\jdk21\bin\java.exe')
    $onPath = Get-Command java -ErrorAction SilentlyContinue
    if ($onPath) { $candidates += $onPath.Source }
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) { return $candidate }
    }
    throw 'No Java runtime found. Install Java 21 or set JAVA_HOME.'
}

$java = Find-Java

if (-not $SkipBuild) {
    if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) { throw 'Node.js/npm is required to build the frontend.' }
    Write-Host 'Installing and building ANVEX frontend...' -ForegroundColor Cyan
    & npm.cmd ci --prefix frontend
    if ($LASTEXITCODE -ne 0) { throw 'Frontend install failed.' }
    & npm.cmd run build --prefix frontend
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    Write-Host 'Building ANVEX Java services...' -ForegroundColor Cyan
    & (Join-Path $root 'mvnw.cmd') -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
}

$defenderJar = Join-Path $root "defender\target\anvex-defender.jar"
$simulatorJar = Join-Path $root "simulator\target\anvex-simulator.jar"
if (-not (Test-Path -LiteralPath $defenderJar)) { throw "Missing $defenderJar" }
if (-not (Test-Path -LiteralPath $simulatorJar)) { throw "Missing $simulatorJar" }

$logDir = Join-Path $root 'logs'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null

Write-Host "Starting defender on port $DefenderPort..." -ForegroundColor Green
$defenderArgs = @(
    '-jar', $defenderJar,
    "--server.port=$DefenderPort",
    "--server.address=0.0.0.0"
)
$defenderProcess = Start-Process -FilePath $java -ArgumentList $defenderArgs -WorkingDirectory (Join-Path $root 'defender') `
    -RedirectStandardOutput (Join-Path $logDir 'defender-console.log') -RedirectStandardError (Join-Path $logDir 'defender-error.log') -PassThru

$simulatorArgs = @(
    '-jar', $simulatorJar,
    "--server.port=$SimulatorPort",
    "--server.address=127.0.0.1"
)
if ($TargetBaseUrl) { $simulatorArgs += "--anvex.simulator.target-base-url=$TargetBaseUrl" }
Write-Host "Starting simulator on port $SimulatorPort..." -ForegroundColor Green
$simulatorProcess = Start-Process -FilePath $java -ArgumentList $simulatorArgs -WorkingDirectory (Join-Path $root 'simulator') `
    -RedirectStandardOutput (Join-Path $logDir 'simulator-console.log') -RedirectStandardError (Join-Path $logDir 'simulator-error.log') -PassThru

function Wait-ForHealth {
    param([string]$Url, [int]$TimeoutSeconds = 60)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RestMethod -Uri $Url -TimeoutSec 3
            if ($response.status -eq 'UP') { return $true }
        } catch {
        }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

Write-Host 'Waiting for the defender...' -ForegroundColor Cyan
if (-not (Wait-ForHealth -Url "http://127.0.0.1:$DefenderPort/api/v1/health")) {
    Stop-Process -Id $defenderProcess.Id -Force -ErrorAction SilentlyContinue
    throw 'The defender did not become healthy in time.'
}

Write-Host 'Waiting for the simulator...' -ForegroundColor Cyan
if (-not (Wait-ForHealth -Url "http://127.0.0.1:$SimulatorPort/api/v1/health")) {
    Stop-Process -Id $simulatorProcess.Id -Force -ErrorAction SilentlyContinue
    Stop-Process -Id $defenderProcess.Id -Force -ErrorAction SilentlyContinue
    throw 'The simulator did not become healthy in time.'
}

Write-Host ''
Write-Host 'ANVEX is running.' -ForegroundColor Green
Write-Host "  Defender console : http://127.0.0.1:$DefenderPort"
Write-Host "  Simulator station: http://127.0.0.1:$SimulatorPort"
Write-Host "  Logs             : $logDir"
Write-Host ''
Write-Host 'Press Ctrl+C to stop both services.' -ForegroundColor Yellow

try {
    while ($defenderProcess.HasExited -eq $false -and $simulatorProcess.HasExited -eq $false) {
        Start-Sleep -Seconds 2
    }
} finally {
    Write-Host 'Stopping ANVEX...' -ForegroundColor Yellow
    Stop-Process -Id $simulatorProcess.Id -Force -ErrorAction SilentlyContinue
    Stop-Process -Id $defenderProcess.Id -Force -ErrorAction SilentlyContinue
}
