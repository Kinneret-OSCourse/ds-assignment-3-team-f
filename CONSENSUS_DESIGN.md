# Consensus Protocol Design - Mulligan Recommender Cluster

## Goal

The Assignment 3 recommender cluster contains three independent recommender
servers: `rec-1`, `rec-2`, and `rec-3`. A customer may contact any one of
them. The contacted node becomes the coordinator for that request, asks every
reachable peer for its local recommendation vote, and returns a result only
when a strict majority selected the exact same ordered list.

## Recommendation Rule

Each recommender reads the current PostgreSQL snapshot for the requested
parking space's zone.

1. Ignore inactive spaces and spaces with an active `STARTED` parking event.
2. Count citations per available space.
3. Keep only spaces with the minimum citation count.
4. If the requested space is in that minimum set, return only that space.
5. Otherwise return the closest space by numeric distance from the requested
   space, preserving ties.
6. If no spaces are available, return an empty list.

The output format matches the assignment examples: `S003;1` or
`S002;3, S004;3`.

## Messages

External customer request:

```text
GET /recommend?space=S003
```

Internal peer vote:

```text
GET /internal/vote?space=S003
```

Malicious-mode test switch:

```text
GET /mode?malicious=true
GET /mode?malicious=false
```

Votes are compared as exact compact strings. Empty recommendations are encoded
as `EMPTY`.

## Consensus Steps

1. The coordinator computes its local vote.
2. The coordinator sends `/internal/vote` to each configured peer with a
   3-second timeout.
3. Missing or failed peer responses are ignored.
4. The coordinator counts identical vote strings.
5. In a three-node cluster, at least two identical votes are required.
6. If a majority exists, the coordinator returns the agreed list.
7. If no majority exists, it returns `No majority consensus.`

## Malicious Mode

Each node can be switched independently through GUI, CLI, environment, or the
HTTP mode endpoint. In malicious mode, the node returns an intentionally wrong
vote (`MAL-<node>;999`) so the team can demonstrate one malicious recommender,
two malicious recommenders, and missing-node cases.

CLI examples:

```powershell
.\gradlew.bat :parking-recommender:runCli --args="serve rec-1 8081 http://rec-2:8082,http://rec-3:8083 false"
.\gradlew.bat :parking-recommender:runCli --args="mode http://localhost:8081 malicious"
.\gradlew.bat :parking-system-CustomerUI:runCli --args="recommend S003"
```

GUI example:

```powershell
.\scripts\run-malicious-ui.ps1
```
