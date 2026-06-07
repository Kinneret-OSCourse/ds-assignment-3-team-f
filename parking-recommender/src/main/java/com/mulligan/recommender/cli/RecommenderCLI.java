package com.mulligan.recommender.cli;

import com.mulligan.recommender.consensus.ConsensusCoordinator;
import com.mulligan.recommender.server.RecommenderHttpServer;
import com.mulligan.recommender.service.RecommenderDatabase;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Command-line entry point for the recommender component.
 */
public final class RecommenderCLI {

    private RecommenderCLI() {
    }

    /**
     * Dispatches recommender CLI commands.
     *
     * @param args command and arguments
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }
        try {
            switch (args[0].toLowerCase()) {
                case "serve" -> serve(args);
                case "recommend" -> recommend(args);
                case "mode" -> mode(args);
                default -> printUsage();
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void serve(String[] args) throws Exception {
        String nodeId = args.length > 1 ? args[1] : envOr("MULLIGAN_RECOMMENDER_NODE_ID", "rec-1");
        int port = Integer.parseInt(args.length > 2 ? args[2] : envOr("MULLIGAN_RECOMMENDER_PORT", "8081"));
        String peersRaw = args.length > 3 ? args[3] : envOr("MULLIGAN_RECOMMENDER_PEERS", "");
        boolean malicious = args.length > 4
                ? Boolean.parseBoolean(args[4])
                : Boolean.parseBoolean(envOr("MULLIGAN_RECOMMENDER_MALICIOUS", "false"));
        List<String> peers = Arrays.stream(peersRaw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();

        AtomicBoolean maliciousMode = new AtomicBoolean(malicious);
        ConsensusCoordinator coordinator = new ConsensusCoordinator(
                nodeId, new RecommenderDatabase(), peers, maliciousMode);
        RecommenderHttpServer server = new RecommenderHttpServer(port, coordinator, maliciousMode);
        server.start();
        System.out.println("Recommender " + nodeId + " listening on port " + port
                + " peers=" + peers + " mode=" + (malicious ? "malicious" : "normal"));
        new CountDownLatch(1).await();
    }

    private static void recommend(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Usage: recommend <baseUrl> <spaceNumber>");
            return;
        }
        String encoded = URLEncoder.encode(args[2], StandardCharsets.UTF_8);
        String body = get(args[1] + "/recommend?space=" + encoded);
        System.out.println(body);
    }

    private static void mode(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Usage: mode <baseUrl> <normal|malicious>");
            return;
        }
        boolean malicious = "malicious".equalsIgnoreCase(args[2]) || "true".equalsIgnoreCase(args[2]);
        String body = get(args[1] + "/mode?malicious=" + malicious);
        System.out.println(body);
    }

    private static String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  serve [nodeId] [port] [peerUrlCsv] [malicious]");
        System.out.println("  recommend <baseUrl> <spaceNumber>");
        System.out.println("  mode <baseUrl> <normal|malicious>");
    }
}
