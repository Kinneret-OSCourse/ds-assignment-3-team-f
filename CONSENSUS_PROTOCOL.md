# Mulligan Recommender Consensus Protocol

Course: Distributed Systems, Semester 2 5786

## Purpose

The recommender cluster returns a parking-space recommendation only after a majority of the configured recommender nodes agree on the exact same ordered recommendation list.

## Nodes

Each recommender node runs the same Java server:

- `recommender-1`
- `recommender-2`
- `recommender-3`

Every node can receive the customer request. The node that receives `/recommend?space=<spaceId>` acts as the leader for that request.

## Messages

| Message | Direction | Endpoint | Body |
| --- | --- | --- | --- |
| Client recommendation request | Customer UI/CLI to any recommender | `GET /recommend?space=S003` | none |
| Peer vote request | Leader to peer recommender | `GET /internal/recommend?space=S003` | none |
| Peer vote response | Peer to leader | HTTP 200 text | `S003;1`, `S002;3,S004;3`, or `EMPTY` |
| Client success response | Leader to Customer UI/CLI | HTTP 200 text | Majority vote text |
| Client failure response | Leader to Customer UI/CLI | HTTP 409 text | `CONSENSUS_FAILURE` |

## Recommendation Rule

Each node independently:

1. Validates the requested parking space.
2. Finds the requested space's parking zone.
3. Filters to active spaces in the same zone that do not have an open `STARTED` parking event.
4. Finds the minimum citation count among those available spaces.
5. If the requested space is available and has that minimum count, returns only the requested space.
6. Otherwise returns the minimum-citation space or spaces with the smallest absolute numeric distance from the requested space.
7. If no spaces are available, returns `EMPTY`.

## Majority Decision

The leader collects its own vote and peer votes. Missing peer responses are ignored, but the majority threshold is based on the configured cluster size, not on the number of responses received.

For a 3-node cluster, a recommendation succeeds only when at least 2 nodes return the exact same encoded list. If no list receives 2 votes, the leader returns `CONSENSUS_FAILURE`.

## Malicious Mode

Each recommender can be configured independently:

- Docker: `MULLIGAN_RECOMMENDER_1_MALICIOUS=true`, `MULLIGAN_RECOMMENDER_2_MALICIOUS=true`, or `MULLIGAN_RECOMMENDER_3_MALICIOUS=true`
- Local CLI: `./gradlew :recommender-server:run --args="--node-id=recommender-1 --port=8081 --peers=http://localhost:8082,http://localhost:8083 --malicious=true"`

In malicious mode the node still receives the same request, but intentionally changes its vote. This lets the team demonstrate that one malicious node is outvoted and two malicious nodes can force a wrong majority or consensus failure depending on whether they agree.
