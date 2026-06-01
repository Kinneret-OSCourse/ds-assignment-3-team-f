param(
    [string]$Project = $(if ($env:MULLIGAN_COMPOSE_PROJECT) { $env:MULLIGAN_COMPOSE_PROJECT } else { "mulligan" })
)

$ErrorActionPreference = "Stop"

$queues = @("Transactions", "Citations")
$nodes = @("rabbit@rabbit-2", "rabbit@rabbit-3")

foreach ($queue in $queues) {
    foreach ($node in $nodes) {
        docker compose -p $Project exec -T rabbit-1 rabbitmq-queues add_member -p / $queue $node --membership voter
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Replica may already exist for $queue on $node; continuing."
        }
    }
}

docker compose -p $Project exec -T rabbit-1 rabbitmqctl list_queues name type online members
