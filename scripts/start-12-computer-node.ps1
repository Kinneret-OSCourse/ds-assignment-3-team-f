param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("db1", "db2", "db3", "rmq1", "rmq2", "rmq3", "rec1", "rec2", "rec3")]
    [string]$Role,

    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
& (Join-Path $RepoRoot "scripts\start-9-laptop-node.ps1") -Role $Role -Clean:$Clean
exit $LASTEXITCODE
