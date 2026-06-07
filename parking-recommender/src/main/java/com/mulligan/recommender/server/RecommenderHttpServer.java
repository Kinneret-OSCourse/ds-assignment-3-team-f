package com.mulligan.recommender.server;

import com.mulligan.recommender.consensus.ConsensusCoordinator;
import com.mulligan.recommender.model.RecommendationResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lightweight HTTP surface for customer recommendation requests and internal
 * recommender votes.
 */
public class RecommenderHttpServer {

    private final int port;
    private final ConsensusCoordinator coordinator;
    private final AtomicBoolean maliciousMode;
    private HttpServer server;

    /**
     * Creates an HTTP server wrapper.
     *
     * @param port TCP port to bind
     * @param coordinator consensus coordinator
     * @param maliciousMode mutable malicious mode flag
     */
    public RecommenderHttpServer(int port, ConsensusCoordinator coordinator, AtomicBoolean maliciousMode) {
        this.port = port;
        this.coordinator = coordinator;
        this.maliciousMode = maliciousMode;
    }

    /**
     * Starts the HTTP server.
     *
     * @throws IOException if the port cannot be bound
     */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", exchange -> respond(exchange, 200, "OK"));
        server.createContext("/recommend", this::handleRecommend);
        server.createContext("/internal/vote", this::handleVote);
        server.createContext("/mode", this::handleMode);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
    }

    /**
     * Stops the HTTP server.
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handleRecommend(HttpExchange exchange) throws IOException {
        try {
            String space = query(exchange).get("space");
            RecommendationResponse response = coordinator.recommendWithConsensus(space);
            respond(exchange, response.success() ? 200 : 409,
                    response.success() ? response.compactOptions() : response.message());
        } catch (IllegalArgumentException e) {
            respond(exchange, 400, e.getMessage());
        } catch (Exception e) {
            respond(exchange, 503, "ERR_UNAVAILABLE");
        }
    }

    private void handleVote(HttpExchange exchange) throws IOException {
        try {
            String space = query(exchange).get("space");
            String body = ConsensusCoordinator.serializeOptions(coordinator.voteLocal(space));
            respond(exchange, 200, body.isBlank() ? "EMPTY" : body);
        } catch (IllegalArgumentException e) {
            respond(exchange, 400, e.getMessage());
        } catch (Exception e) {
            respond(exchange, 503, "ERR_UNAVAILABLE");
        }
    }

    private void handleMode(HttpExchange exchange) throws IOException {
        Map<String, String> query = query(exchange);
        String value = query.getOrDefault("malicious", "false");
        maliciousMode.set("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value));
        respond(exchange, 200, maliciousMode.get() ? "MALICIOUS" : "NORMAL");
    }

    private Map<String, String> query(HttpExchange exchange) {
        Map<String, String> values = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        for (String pair : raw.split("&")) {
            String[] pieces = pair.split("=", 2);
            if (pieces.length == 2) {
                values.put(
                        URLDecoder.decode(pieces[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(pieces[1], StandardCharsets.UTF_8)
                );
            }
        }
        return values;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
