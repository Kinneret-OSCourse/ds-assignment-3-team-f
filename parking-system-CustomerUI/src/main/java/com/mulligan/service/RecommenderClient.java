package com.mulligan.service;

import com.mulligan.common.logging.SecureLogger;
import com.mulligan.common.validation.InputValidator;
import com.mulligan.common.validation.ValidationException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Client-side adapter that asks any available recommender node for a
 * consensus-backed parking space recommendation.
 */
public class RecommenderClient {

    private static final SecureLogger LOG = new SecureLogger("customer-recommender");
    private final HttpClient httpClient;
    private final List<String> endpoints;

    /**
     * Creates a client using {@code MULLIGAN_RECOMMENDER_ENDPOINTS}.
     */
    public RecommenderClient() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        String raw = envOr("MULLIGAN_RECOMMENDER_ENDPOINTS", "http://localhost:8081,http://localhost:8082,http://localhost:8083");
        this.endpoints = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    /**
     * Requests a recommendation from the first reachable recommender node.
     *
     * @param requestedSpace requested parking space number
     * @return recommendation text in Assignment 3 compact format
     */
    public String recommend(String requestedSpace) {
        validateSpace(requestedSpace);
        String encoded = URLEncoder.encode(requestedSpace.trim(), StandardCharsets.UTF_8);
        for (String endpoint : endpoints) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(trimSlash(endpoint) + "/recommend?space=" + encoded))
                        .timeout(Duration.ofSeconds(4))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return response.body();
                }
                if (response.statusCode() == 409) {
                    return "No majority consensus.";
                }
            } catch (Exception e) {
                LOG.operational("Recommender endpoint failed endpoint=" + endpoint + " err=" + e.getClass().getSimpleName());
            }
        }
        return "No recommender node is reachable.";
    }

    private void validateSpace(String requestedSpace) {
        try {
            InputValidator.requireSpace(requestedSpace);
        } catch (ValidationException e) {
            throw new IllegalArgumentException("Invalid parking space.");
        }
    }

    private String trimSlash(String endpoint) {
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }

    private String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
