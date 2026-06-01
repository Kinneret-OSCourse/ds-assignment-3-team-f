param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("customer", "peo", "mo")]
    [string]$App,

    [switch]$Cli
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $RepoRoot

function Read-DotEnv {
    param([string]$Path)
    $values = @{}
    if (-not (Test-Path $Path)) {
        return $values
    }
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith("#")) {
            return
        }
        $idx = $line.IndexOf("=")
        if ($idx -le 0) {
            return
        }
        $key = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim().Trim('"').Trim("'")
        $values[$key] = $value
    }
    return $values
}

function Use-Value {
    param([hashtable]$Values, [string]$Name, [string]$Fallback)
    $current = [Environment]::GetEnvironmentVariable($Name, "Process")
    if (-not [string]::IsNullOrWhiteSpace($current)) {
        return $current
    }
    if ($Values.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace($Values[$Name])) {
        return $Values[$Name]
    }
    return $Fallback
}

function First-Host {
    param([string]$Hosts)
    if ([string]::IsNullOrWhiteSpace($Hosts)) {
        return ""
    }
    return $Hosts.Split(",")[0].Trim().Split(":")[0].Trim()
}

$envFile = Read-DotEnv ".env"

$dbHosts = Use-Value $envFile "MULLIGAN_DB_HOSTS" ""
$queueHosts = Use-Value $envFile "MULLIGAN_QUEUE_HOSTS" ""
if ([string]::IsNullOrWhiteSpace($dbHosts) -or [string]::IsNullOrWhiteSpace($queueHosts)) {
    throw "Set MULLIGAN_DB_HOSTS and MULLIGAN_QUEUE_HOSTS in .env before running a UI."
}

$env:MULLIGAN_DB_HOSTS = $dbHosts
$env:MULLIGAN_DB_HOST = First-Host $dbHosts
$env:MULLIGAN_DB_PORT = "5432"
$env:MULLIGAN_DB_NAME = Use-Value $envFile "MULLIGAN_DB_NAME" "mulligan_db"
$env:MULLIGAN_DB_USER = Use-Value $envFile "MULLIGAN_DB_USER" "mulligan_app"
$env:MULLIGAN_DB_PASSWORD = Use-Value $envFile "MULLIGAN_DB_PASSWORD" "mulligan_app_pw"

$env:MULLIGAN_QUEUE_HOSTS = $queueHosts
$env:MULLIGAN_QUEUE_HOST = First-Host $queueHosts
$env:MULLIGAN_QUEUE_PORT = Use-Value $envFile "MULLIGAN_QUEUE_PORT" "5672"
$env:MULLIGAN_QUEUE_TLS = Use-Value $envFile "MULLIGAN_QUEUE_TLS" "false"
$env:MULLIGAN_HMAC_KEY = Use-Value $envFile "MULLIGAN_HMAC_KEY" "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

if ($env:MULLIGAN_QUEUE_TLS -eq "true") {
    $storePassword = Use-Value $envFile "MULLIGAN_TLS_STORE_PASSWORD" "changeit"
    $trustStore = Join-Path $RepoRoot "infra\rabbitmq\certs\client_truststore.jks"
    $keyStore = Join-Path $RepoRoot "infra\rabbitmq\certs\client_keystore.p12"
    if (-not (Test-Path $trustStore) -or -not (Test-Path $keyStore)) {
        throw "Missing client TLS stores. Copy infra\rabbitmq\certs from the certificate generator computer to this UI computer."
    }
    $env:JAVA_TOOL_OPTIONS = "-Djavax.net.ssl.trustStore=`"$trustStore`" -Djavax.net.ssl.trustStorePassword=$storePassword -Djavax.net.ssl.keyStore=`"$keyStore`" -Djavax.net.ssl.keyStorePassword=$storePassword -Djavax.net.ssl.keyStoreType=PKCS12"
}

switch ($App) {
    "customer" {
        $env:MULLIGAN_QUEUE_USER_CUSTOMER = "mulligan_customer"
        $env:MULLIGAN_QUEUE_PASSWORD = Use-Value $envFile "MULLIGAN_QUEUE_CUSTOMER_PASSWORD" ""
        $env:MULLIGAN_QUEUE_PASSWORD_CUSTOMER = $env:MULLIGAN_QUEUE_PASSWORD
        $project = ":parking-system-CustomerUI"
    }
    "peo" {
        $env:MULLIGAN_QUEUE_USER_PEO = "mulligan_peo"
        $env:MULLIGAN_QUEUE_PASSWORD = Use-Value $envFile "MULLIGAN_QUEUE_PEO_PASSWORD" ""
        $env:MULLIGAN_QUEUE_PASSWORD_PEO = $env:MULLIGAN_QUEUE_PASSWORD
        $project = ":parking-system-PEOUI"
    }
    "mo" {
        $env:MULLIGAN_QUEUE_USER_MO = "mulligan_mo"
        $env:MULLIGAN_QUEUE_PASSWORD = Use-Value $envFile "MULLIGAN_QUEUE_MO_PASSWORD" ""
        $env:MULLIGAN_QUEUE_PASSWORD_MO = $env:MULLIGAN_QUEUE_PASSWORD
        $project = ":parking-system-MOUI"
    }
}

if ([string]::IsNullOrWhiteSpace($env:MULLIGAN_QUEUE_PASSWORD)) {
    throw "Missing RabbitMQ password for $App in .env."
}

$task = if ($Cli) { "runCli" } else { "run" }
& ".\gradlew.bat" "$project`:$task" "--no-daemon"
exit $LASTEXITCODE
