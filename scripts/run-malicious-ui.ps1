$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $RepoRoot

& ".\gradlew.bat" ":parking-recommender:run" "--no-daemon"
exit $LASTEXITCODE
