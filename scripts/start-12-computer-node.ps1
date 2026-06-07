param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("db1", "db2", "db3", "rmq1", "rmq2", "rmq3", "rec1", "rec2", "rec3")]
    [string]$Role,

    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $RepoRoot

if (-not (Test-Path ".env")) {
    throw "Missing .env. Copy .env.12-computers.example to .env and fill in the real lab IPs and secrets first."
}

if ($Role.StartsWith("rmq") -and -not (Test-Path "infra\certs\server-cert.pem")) {
    throw "Missing RabbitMQ TLS files. Run scripts\generate-certs.ps1 once, then copy infra\certs to all RabbitMQ and UI computers."
}

if ($Role.StartsWith("rec") -and -not (Test-Path "parking-recommender\Dockerfile")) {
    throw "Missing parking-recommender module. Run from the Assignment 3 repository root."
}

$composeArgs = @("compose", "-f", "docker-compose.12-computers.yml", "--profile", $Role)

if ($Clean) {
    & docker @composeArgs down --remove-orphans
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

& docker @composeArgs up -d --build
exit $LASTEXITCODE
