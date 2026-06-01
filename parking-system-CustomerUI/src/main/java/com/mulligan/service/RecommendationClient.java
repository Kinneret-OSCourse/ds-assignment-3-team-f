package com.mulligan.service;

import com.mulligan.common.security.ClientErrorCodes;
import com.mulligan.common.validation.InputValidator;
import com.mulligan.common.validation.ValidationException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Calls the recommender cluster from the Customer UI and CLI.
 */
public class RecommendationClient {

    private final HttpClient httpClient;
    private final String baseUrl;

    /**
     * Creates a client using {@code MULLIGAN_RECOMMENDER_URL}, defaulting to localhost.
     */
    public RecommendationClient() {
        this(envOr("MULLIGAN_RECOMMENDER_URL", "http://localhost:8081"));
    }

    /**
     * Creates a client for a specific recommender node URL.
     *
     * @param baseUrl recommender node base URL
     */
    public RecommendationClient(String baseUrl) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    /**
     * Requests a consensus-backed recommendation for a parking space.
     *
     * @param spaceNumber desired parking space
     * @return assignment-formatted recommendation text
     */
    public String recommend(String spaceNumber) {
        String validated = validateSpace(spaceNumber);
        String encoded = URLEncoder.encode(validated, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/recommend?space=" + encoded))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body().trim();
            if (response.statusCode() == 200) {
                return body.isBlank() ? "Empty List" : body;
            }
            if (response.statusCode() == 400) {
                throw new IllegalArgumentException("Invalid parking space.");
            }
            if (response.statusCode() == 409) {
                throw new IllegalStateException("Consensus failure. No majority recommendation was reached.");
            }
            throw new IllegalStateException(ClientErrorCodes.UNAVAILABLE);
        } catch (IOException e) {
            throw new IllegalStateException(ClientErrorCodes.UNAVAILABLE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ClientErrorCodes.UNAVAILABLE, e);
        }
    }

    private static String validateSpace(String spaceNumber) {
        try {
            return InputValidator.requireSpace(spaceNumber);
        } catch (ValidationException e) {
            throw new IllegalArgumentException("Invalid parking space.");
        }
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = System.getProperty(name);
        }
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
