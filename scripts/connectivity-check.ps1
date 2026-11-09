param(
    [Parameter(Mandatory = $true)]
    [string]$TargetBaseUrl,
    [int]$TimeoutSeconds = 10
)

$ErrorActionPreference = 'Continue'
$failed = 0

function Test-Step {
    param([string]$Name, [scriptblock]$Action)
    Write-Host "==> $Name" -ForegroundColor Cyan
    try {
        $result = & $Action
        Write-Host "    OK: $result" -ForegroundColor Green
    } catch {
        Write-Host "    FAILED: $($_.Exception.Message)" -ForegroundColor Red
        $script:failed++
    }
}

$base = $TargetBaseUrl.TrimEnd('/')

Test-Step 'TCP port reachable' {
    $uri = [System.Uri]$base
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync($uri.Host, $uri.Port)
        if (-not $task.Wait([TimeSpan]::FromSeconds($TimeoutSeconds))) { throw "Timeout after $TimeoutSeconds s" }
        if (-not $client.Connected) { throw 'Connection refused' }
        "connected to $($uri.Host):$($uri.Port)"
    } finally {
        $client.Dispose()
    }
}

Test-Step 'ANVEX identity handshake' {
    $identity = Invoke-RestMethod -Uri "$base/api/v1/identity" -TimeoutSec $TimeoutSeconds
    if ($identity.product -ne 'ANVEX') { throw "Unexpected product '$($identity.product)'" }
    if ($identity.protocol -ne 'anvex/1') { throw "Unexpected protocol '$($identity.protocol)'" }
    "$($identity.product) $($identity.version) ($($identity.protocol))"
}

Test-Step 'Defender health' {
    $health = Invoke-RestMethod -Uri "$base/api/v1/health" -TimeoutSec $TimeoutSeconds
    if ($health.status -ne 'UP') { throw "status is $($health.status)" }
    "status=$($health.status) java=$($health.javaVersion)"
}

Test-Step 'Public policy snapshot' {
    $policy = Invoke-RestMethod -Uri "$base/api/v1/policy" -TimeoutSec $TimeoutSeconds
    "policyVersion=$($policy.policyVersion) mode=$($policy.protectionMode)"
}

Test-Step 'Demo account catalogue' {
    $accounts = Invoke-RestMethod -Uri "$base/api/v1/demo/accounts" -TimeoutSec $TimeoutSeconds
    "$($accounts.Count) seeded account(s)"
}

Write-Host ''
if ($failed -eq 0) {
    Write-Host 'All connectivity checks passed.' -ForegroundColor Green
    exit 0
} else {
    Write-Host "$failed check(s) failed. See docs/OPERATIONS.md (troubleshooting)." -ForegroundColor Red
    exit 1
}
