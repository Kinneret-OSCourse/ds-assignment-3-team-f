param(
    [string]$Project = $(if ($env:MULLIGAN_COMPOSE_PROJECT) { $env:MULLIGAN_COMPOSE_PROJECT } else { "mulligan" }),
    [string]$LogPath = $(if ($env:MULLIGAN_SECURITY_LOG) { $env:MULLIGAN_SECURITY_LOG } else { "/var/log/mulligan/security.log" })
)

$ErrorActionPreference = "Stop"

function Assert-LogPattern {
    param([string]$Label, [string]$Pattern)

    docker compose -p $Project exec -T parking-server grep -q -- $Pattern $LogPath
    if ($LASTEXITCODE -eq 0) {
        Write-Host "PASS $Label"
    } else {
        throw "FAIL $Label (pattern not found in $LogPath)"
    }
}

Write-Host "Running JUnit security tests..."
.\gradlew.bat :parking-common:test --tests com.mulligan.common.SecurityLayerTest

Write-Host "Sending tampered envelope..."
docker compose -p $Project exec -T parking-server java -cp /app/lib/* com.mulligan.common.testdrivers.SendTampered
Start-Sleep -Seconds 2
Assert-LogPattern "Bad HMAC rejected" "REJECT bad-hmac"

Write-Host "Sending replay..."
docker compose -p $Project exec -T parking-server java -cp /app/lib/* com.mulligan.common.testdrivers.SendReplay
Start-Sleep -Seconds 2
Assert-LogPattern "Replay rejected" "REJECT replay"

Write-Host "Sending stale envelope..."
docker compose -p $Project exec -T parking-server java -cp /app/lib/* com.mulligan.common.testdrivers.SendStale
Start-Sleep -Seconds 2
Assert-LogPattern "Stale timestamp rejected" "REJECT stale-timestamp"

Write-Host "All security smoke checks passed."
