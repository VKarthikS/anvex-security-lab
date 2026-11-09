param(
    [string]$Version = '1.0.0',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Resolve-JavaHome {
    $portable = Join-Path $env:USERPROFILE '.local\tools\jdk21'
    if ((-not $env:JAVA_HOME) -and (Test-Path -LiteralPath (Join-Path $portable 'bin\java.exe'))) {
        $env:JAVA_HOME = $portable
    }
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        $env:PATH = "$(Join-Path $env:JAVA_HOME 'bin');$env:PATH"
        return
    }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        throw 'No Java runtime found. Install Java 21 or set JAVA_HOME.'
    }
}

Resolve-JavaHome

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

if (-not $SkipBuild) {
    if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) { throw 'Node.js/npm is required to build the frontend.' }
    & npm.cmd ci --prefix frontend
    if ($LASTEXITCODE -ne 0) { throw 'Frontend install failed.' }
    & npm.cmd run build --prefix frontend
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    Write-Host 'Building ANVEX with tests...' -ForegroundColor Cyan
    & (Join-Path $root 'mvnw.cmd') -q clean package
    if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
}

$defenderJar = Join-Path $root 'defender\target\anvex-defender.jar'
$simulatorJar = Join-Path $root 'simulator\target\anvex-simulator.jar'
foreach ($jar in @($defenderJar, $simulatorJar)) {
    if (-not (Test-Path -LiteralPath $jar)) { throw "Missing $jar" }
}

$releaseRoot = Join-Path $root 'release'
$stage = Join-Path $releaseRoot "ANVEX-$Version"
if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }

New-Item -ItemType Directory -Path $stage | Out-Null
New-Item -ItemType Directory -Path (Join-Path $stage 'defender') | Out-Null
New-Item -ItemType Directory -Path (Join-Path $stage 'simulator') | Out-Null
New-Item -ItemType Directory -Path (Join-Path $stage 'docs') | Out-Null
$stageScripts = Join-Path $stage 'scripts'
New-Item -ItemType Directory -Path $stageScripts | Out-Null

Copy-Item -LiteralPath $defenderJar -Destination (Join-Path $stage 'defender\anvex-defender.jar')
Copy-Item -LiteralPath $simulatorJar -Destination (Join-Path $stage 'simulator\anvex-simulator.jar')

Copy-Item -LiteralPath (Join-Path $root 'README.md') -Destination $stage
Copy-Item -LiteralPath (Join-Path $root 'SECURITY.md') -Destination $stage
Copy-Item -LiteralPath (Join-Path $root 'ARCHITECTURE.md') -Destination $stage
Copy-Item -LiteralPath (Join-Path $root 'docs\OPERATIONS.md') -Destination (Join-Path $stage 'docs')
Copy-Item -LiteralPath (Join-Path $root 'docs\API_CONTRACT.md') -Destination (Join-Path $stage 'docs')
Copy-Item -LiteralPath (Join-Path $root 'docs\VERIFICATION.md') -Destination (Join-Path $stage 'docs')
Copy-Item -LiteralPath (Join-Path $root 'docs\DESIGN.md') -Destination (Join-Path $stage 'docs')
if (Test-Path -LiteralPath (Join-Path $root 'docs\DEMO_SCRIPT.md')) {
    Copy-Item -LiteralPath (Join-Path $root 'docs\DEMO_SCRIPT.md') -Destination (Join-Path $stage 'docs')
}
if (Test-Path -LiteralPath (Join-Path $root 'docs\JAVA_CONCEPTS.md')) {
    Copy-Item -LiteralPath (Join-Path $root 'docs\JAVA_CONCEPTS.md') -Destination (Join-Path $stage 'docs')
}

Copy-Item -LiteralPath (Join-Path $root 'scripts\dev-start.ps1') -Destination $stageScripts
Copy-Item -LiteralPath (Join-Path $root 'scripts\dev-start.sh') -Destination $stageScripts
Copy-Item -LiteralPath (Join-Path $root 'scripts\connectivity-check.ps1') -Destination $stageScripts
Copy-Item -LiteralPath (Join-Path $root 'scripts\connectivity-check.sh') -Destination $stageScripts
Copy-Item -LiteralPath (Join-Path $root 'scripts\smoke-test.ps1') -Destination $stageScripts

Set-Content -LiteralPath (Join-Path $stage 'VERSION') -Value $Version -Encoding ASCII
Set-Content -LiteralPath (Join-Path $stage 'README-FIRST.txt') -Value @"
ANVEX $Version - classroom release
================================

1. Copy the whole folder to the target laptop.
2. Install Java 21 (JRE or JDK). Nothing else is needed - no Maven, no Node, no internet.
3. Laptop 1 (defender):  java -jar defender\anvex-defender.jar
   Laptop 2 (simulator): java -jar simulator\anvex-simulator.jar
4. Open http://127.0.0.1:8080 (defender) and http://127.0.0.1:8081 (simulator).
5. On Laptop 2, set the target to the LAN address of Laptop 1, for example
   http://192.168.1.50:8080, and press 'Test connection'.
6. See docs\OPERATIONS.md for firewall rules, troubleshooting and the demo script.

Runtime data (H2 database, logs) is created next to the JARs in data\ and logs\.
"@ -Encoding UTF8

$hashes = Get-ChildItem -LiteralPath $stage -Recurse -File |
    Where-Object { $_.Name -ne 'SHA256SUMS' } |
    ForEach-Object {
        $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $relative = $_.FullName.Substring($stage.Length + 1).Replace('\', '/')
        "$hash  $relative"
    }
Set-Content -LiteralPath (Join-Path $stage 'SHA256SUMS') -Value $hashes -Encoding ASCII

$zip = Join-Path $releaseRoot "ANVEX-$Version.zip"
if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip -Force }

# The archive is written with forward-slash entry names so that it unzips correctly on
# Windows, Linux and macOS alike.
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open($zip, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    Get-ChildItem -LiteralPath $stage -Recurse -File | ForEach-Object {
        $relative = $_.FullName.Substring($stage.Length + 1).Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $archive, $_.FullName, $relative, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally {
    $archive.Dispose()
}

$sizeMb = [math]::Round((Get-Item -LiteralPath $zip).Length / 1MB, 1)
Write-Host ''
Write-Host "Release ready: $zip ($sizeMb MB)" -ForegroundColor Green
Write-Host "Staged folder : $stage" -ForegroundColor Green
