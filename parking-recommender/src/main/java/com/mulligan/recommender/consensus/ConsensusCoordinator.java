package com.mulligan.recommender.consensus;

import com.mulligan.recommender.model.RecommendationOption;
import com.mulligan.recommender.model.RecommendationResponse;
import com.mulligan.recommender.service.RecommenderDatabase;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Coordinates the assignment's simple majority consensus protocol among three
 * recommender nodes.
 */
public class ConsensusCoordinator {

    private final String nodeId;
    private final RecommenderDatabase database;
    private final List<String> peerUrls;
    private final HttpClient client;
    private final AtomicBoolean maliciousMode;

    /**
     * Creates a coordinator for one recommender node.
     *
     * @param nodeId logical node identifier
     * @param database local recommendation calculator
     * @param peerUrls peer base URLs, not including this node
     * @param maliciousMode shared malicious-mode flag for testing
     */
    public ConsensusCoordinator(String nodeId, RecommenderDatabase database, List<String> peerUrls,
                                AtomicBoolean maliciousMode) {
        this.nodeId = nodeId;
        this.database = database;
        this.peerUrls = List.copyOf(peerUrls);
        this.maliciousMode = maliciousMode;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    /**
     * Requests local and peer votes and returns the majority result when a
     * strict majority chose the exact same ordered list.
     *
     * @param requestedSpace requested parking space number
     * @return consensus response
     */
    public RecommendationResponse recommendWithConsensus(String requestedSpace) {
        List<List<RecommendationOption>> votes = new ArrayList<>();
        votes.add(voteLocal(requestedSpace));

        for (String peerUrl : peerUrls) {
            fetchPeerVote(peerUrl, requestedSpace).ifPresent(votes::add);
        }

        int clusterSize = peerUrls.size() + 1;
        int majority = (clusterSize / 2) + 1;
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, List<RecommendationOption>> parsed = new LinkedHashMap<>();

        for (List<RecommendationOption> vote : votes) {
            String key = serializeOptions(vote);
            counts.merge(key, 1, Integer::sum);
            parsed.putIfAbsent(key, vote);
        }

        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() >= majority)
                .findFirst()
                .map(entry -> RecommendationResponse.ok(parsed.get(entry.getKey())))
                .orElseGet(() -> RecommendationResponse.fail("No majority consensus."));
    }

    /**
     * Returns this node's vote without contacting peers.
     *
     * @param requestedSpace requested parking space number
     * @return local normal or malicious vote
     */
    public List<RecommendationOption> voteLocal(String requestedSpace) {
        if (maliciousMode.get()) {
            return List.of(new RecommendationOption("MAL-" + nodeId, 999));
        }
        return database.recommendLocal(requestedSpace);
    }

    private java.util.Optional<List<RecommendationOption>> fetchPeerVote(String peerUrl, String requestedSpace) {
        try {
            String encoded = URLEncoder.encode(requestedSpace, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(peerUrl) + "/internal/vote?space=" + encoded))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(parseOptions(response.body()));
        } catch (Exception ignored) {
            return java.util.Optional.empty();
        }
    }

    /**
     * Serializes a vote for exact-list equality in the consensus phase.
     *
     * @param options options to serialize
     * @return deterministic compact form
     */
    public static String serializeOptions(List<RecommendationOption> options) {
        return options.stream()
                .map(RecommendationOption::compact)
                .collect(Collectors.joining(","));
    }

    /**
     * Parses a compact vote body.
     *
     * @param body compact vote body
     * @return parsed options
     */
    public static List<RecommendationOption> parseOptions(String body) {
        if (body == null || body.isBlank() || "EMPTY".equalsIgnoreCase(body.trim())) {
            return List.of();
        }
        List<RecommendationOption> options = new ArrayList<>();
        for (String part : body.split(",")) {
            String[] pieces = part.trim().split(";");
            if (pieces.length == 2) {
                options.add(new RecommendationOption(pieces[0].trim(), Integer.parseInt(pieces[1].trim())));
            }
        }
        return options;
    }

    private String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
