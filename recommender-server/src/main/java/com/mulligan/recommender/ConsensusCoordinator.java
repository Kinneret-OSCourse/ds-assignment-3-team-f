package com.mulligan.recommender;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Coordinates majority voting across the recommender cluster.
 */
public class ConsensusCoordinator {

    private final String nodeId;
    private final List<String> peerBaseUrls;
    private final RecommendationEngine engine;
    private final boolean maliciousMode;
    private final HttpClient httpClient;
    private final Duration timeout;

    /**
     * Creates a coordinator for one recommender node.
     *
     * @param nodeId stable node name
     * @param peerBaseUrls peer recommender base URLs
     * @param engine local recommendation engine
     * @param maliciousMode true to intentionally return wrong votes
     */
    public ConsensusCoordinator(String nodeId, List<String> peerBaseUrls, RecommendationEngine engine, boolean maliciousMode) {
        this.nodeId = Objects.requireNonNull(nodeId);
        this.peerBaseUrls = List.copyOf(peerBaseUrls);
        this.engine = Objects.requireNonNull(engine);
        this.maliciousMode = maliciousMode;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        this.timeout = Duration.ofSeconds(3);
    }

    /**
     * Performs the three-step protocol: local calculation, peer collection, and majority vote.
     *
     * @param requestedSpace customer requested parking space
     * @return consensus result
     */
    public ConsensusResult recommendWithConsensus(String requestedSpace) {
        Map<String, String> votes = new LinkedHashMap<>();
        votes.put(nodeId, localVote(requestedSpace));

        for (String peer : peerBaseUrls) {
            queryPeer(peer, requestedSpace).ifPresent(vote -> votes.put(peer, vote));
        }

        int clusterSize = peerBaseUrls.size() + 1;
        int requiredMajority = (clusterSize / 2) + 1;
        return votes.values().stream()
                .distinct()
                .filter(vote -> countVotes(votes, vote) >= requiredMajority)
                .findFirst()
                .map(vote -> ConsensusResult.success(vote, votes))
                .orElseGet(() -> ConsensusResult.failure(votes));
    }

    /**
     * Computes this node's individual vote without querying peers.
     *
     * @param requestedSpace customer requested parking space
     * @return encoded vote
     */
    public String localVote(String requestedSpace) {
        List<RecommendationOption> recommendation = engine.recommend(requestedSpace);
        if (maliciousMode) {
            return RecommendationEngine.maliciousEncode(recommendation);
        }
        return ConsensusResult.encodeVote(recommendation);
    }

    private Optional<String> queryPeer(String peerBaseUrl, String requestedSpace) {
        try {
            String encodedSpace = URLEncoder.encode(requestedSpace, StandardCharsets.UTF_8);
            URI uri = URI.create(trimTrailingSlash(peerBaseUrl) + "/internal/recommend?space=" + encodedSpace);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && !response.body().isBlank()) {
                return Optional.of(response.body().trim());
            }
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return Optional.empty();
    }

    private static long countVotes(Map<String, String> votes, String candidate) {
        return votes.values().stream().filter(candidate::equals).count();
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
