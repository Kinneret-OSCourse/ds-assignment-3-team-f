$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$packageDir = Join-Path $root "build\red-team-package"
$zipPath = Join-Path $root "Parking-System-Red-Team-Assignment2.zip"

if (Test-Path $packageDir) {
    Remove-Item -LiteralPath $packageDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $packageDir | Out-Null

function Copy-FileToPackage {
    param([string]$Source, [string]$Destination)
    $target = Join-Path $packageDir $Destination
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    Copy-Item -LiteralPath (Join-Path $root $Source) -Destination $target -Force
}

function Copy-DirToPackage {
    param([string]$Source, [string]$Destination)
    $target = Join-Path $packageDir $Destination
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    Copy-Item -LiteralPath (Join-Path $root $Source) -Destination $target -Recurse -Force
}

Copy-FileToPackage "parking-system-CustomerUI\build\libs\parking-system-CustomerUI-1.0-SNAPSHOT.jar" "customer-ui-1.0.jar"
Copy-FileToPackage "parking-system-PEOUI\build\libs\parking-system-PEOUI-1.0-SNAPSHOT.jar" "peo-ui-1.0.jar"
Copy-FileToPackage "parking-system-MOUI\build\libs\parking-system-MOUI-1.0-SNAPSHOT.jar" "mo-ui-1.0.jar"
Copy-FileToPackage "parking-server\build\libs\parking-server-1.0-SNAPSHOT.jar" "parking-server-1.0.jar"

Copy-DirToPackage "parking-system-CustomerUI\build\distributions" "distributions\customer-ui"
Copy-DirToPackage "parking-system-PEOUI\build\distributions" "distributions\peo-ui"
Copy-DirToPackage "parking-system-MOUI\build\distributions" "distributions\mo-ui"
Copy-DirToPackage "parking-server\build\distributions" "distributions\parking-server"

Copy-FileToPackage "docker-compose.yml" "docker-compose.yml"
Copy-FileToPackage "docker-compose.tls.yml" "docker-compose.tls.yml"
Copy-FileToPackage "parking-server\Dockerfile" "parking-server\Dockerfile"
Copy-FileToPackage "parking-system-CustomerUI\Dockerfile" "parking-system-CustomerUI\Dockerfile"
Copy-FileToPackage "parking-system-PEOUI\Dockerfile" "parking-system-PEOUI\Dockerfile"
Copy-FileToPackage "parking-system-MOUI\Dockerfile" "parking-system-MOUI\Dockerfile"

Copy-DirToPackage "infra\postgres" "infra\postgres"
Copy-DirToPackage "infra\rabbitmq" "infra\rabbitmq"
Copy-DirToPackage "infra\haproxy" "infra\haproxy"
Copy-DirToPackage "scripts" "scripts"
Copy-DirToPackage "setup-data" "setup-data"
Copy-DirToPackage "local-libs" "local-libs"

foreach ($doc in @(
    "README.md",
    "DEPLOY.md",
    "Defense.md",
    "DATABASE_DESIGN.md",
    "QUEUE_DESIGN.md",
    "TESTING.md",
    "RED_TEAM_PACKAGE.md"
)) {
    Copy-FileToPackage $doc $doc
}

if (Test-Path $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}
Compress-Archive -Path (Join-Path $packageDir "*") -DestinationPath $zipPath -Force

Write-Host "Created $zipPath"
Write-Host "Package contents:"
Get-ChildItem -LiteralPath $packageDir -Recurse | Where-Object { -not $_.PSIsContainer } |
    ForEach-Object { $_.FullName.Substring($packageDir.Length + 1) } |
    Sort-Object
