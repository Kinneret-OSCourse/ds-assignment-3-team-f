param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("db1", "db2", "db3", "rmq1", "rmq2", "rmq3")]
    [string]$Role,

    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $RepoRoot

if (-not (Test-Path ".env")) {
    throw "Missing .env. Copy .env.9-laptops.example to .env and fill in the real lab IPs and secrets first."
}

if ($Role.StartsWith("rmq") -and -not (Test-Path "infra\rabbitmq\certs\server_certificate.pem")) {
    throw "Missing RabbitMQ TLS files. Run scripts\generate-rabbitmq-certs.ps1 once, then copy infra\rabbitmq\certs to all RabbitMQ and UI computers."
}

$composeArgs = @("compose", "-f", "docker-compose.9-laptops.yml", "--profile", $Role)

if ($Clean) {
    & docker @composeArgs down --remove-orphans
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

& docker @composeArgs up -d
exit $LASTEXITCODE
