package com.mulligan.recommender;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP surface for one recommender node.
 */
public class RecommenderNodeServer {

    private final HttpServer server;
    private final ConsensusCoordinator coordinator;

    /**
     * Creates a node server on the given port.
     *
     * @param port listen port
     * @param coordinator consensus coordinator
     * @throws IOException when the port cannot be opened
     */
    public RecommenderNodeServer(int port, ConsensusCoordinator coordinator) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.coordinator = coordinator;
        server.createContext("/recommend", this::handleClientRecommendation);
        server.createContext("/internal/recommend", this::handleInternalRecommendation);
        server.createContext("/health", exchange -> write(exchange, 200, "OK"));
    }

    /**
     * Starts serving requests.
     */
    public void start() {
        server.start();
    }

    private void handleClientRecommendation(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            write(exchange, 405, "METHOD_NOT_ALLOWED");
            return;
        }
        String space = queryParams(exchange).get("space");
        if (space == null || space.isBlank()) {
            write(exchange, 400, "INVALID_SPACE");
            return;
        }
        try {
            ConsensusResult result = coordinator.recommendWithConsensus(space);
            write(exchange, result.success() ? 200 : 409, result.toClientText());
        } catch (IllegalArgumentException e) {
            write(exchange, 400, "INVALID_SPACE");
        } catch (RuntimeException e) {
            write(exchange, 503, "RECOMMENDER_UNAVAILABLE");
        }
    }

    private void handleInternalRecommendation(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            write(exchange, 405, "METHOD_NOT_ALLOWED");
            return;
        }
        String space = queryParams(exchange).get("space");
        if (space == null || space.isBlank()) {
            write(exchange, 400, "INVALID_SPACE");
            return;
        }
        try {
            write(exchange, 200, coordinator.localVote(space));
        } catch (IllegalArgumentException e) {
            write(exchange, 400, "INVALID_SPACE");
        } catch (RuntimeException e) {
            write(exchange, 503, "RECOMMENDER_UNAVAILABLE");
        }
    }

    private static Map<String, String> queryParams(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            if (separator > 0) {
                String key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }

    private static void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
