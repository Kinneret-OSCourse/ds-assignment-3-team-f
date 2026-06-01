package com.mulligan.common.cluster;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Parses comma-separated host lists used by the cluster-aware factories in
 * Assignment 2. The same parser handles both the database list (HAProxy is in
 * front for local Docker, but the JDBC client also accepts a multi-host URL
 * pointing at all three Patroni replicas) and the RabbitMQ list (the AMQP
 * client takes an {@code Address[]} and rotates through it on connect).
 *
 * <p>Each entry can be {@code host} or {@code host:port}.
 */
public final class ClusterEndpoints {

    private final List<Endpoint> endpoints;

    private ClusterEndpoints(List<Endpoint> endpoints) {
        this.endpoints = Collections.unmodifiableList(endpoints);
    }

    /**
     * Parses the value of the supplied env/property. If {@code raw} is null or
     * blank the {@code fallback} is used. The {@code defaultPort} is applied
     * to entries that do not specify {@code host:port}.
     *
     * @param raw raw comma-separated host list, possibly null
     * @param fallback fallback to use when {@code raw} is empty
     * @param defaultPort default port for entries that omit one
     * @return non-empty endpoint list
     */
    public static ClusterEndpoints parse(String raw, String fallback, int defaultPort) {
        String value = (raw == null || raw.isBlank()) ? fallback : raw;
        List<Endpoint> endpoints = new ArrayList<>();
        for (String chunk : value.split(",")) {
            String token = chunk.trim();
            if (token.isEmpty()) {
                continue;
            }
            int colon = token.indexOf(':');
            if (colon == -1) {
                endpoints.add(new Endpoint(token, defaultPort));
            } else {
                String host = token.substring(0, colon);
                int port;
                try {
                    port = Integer.parseInt(token.substring(colon + 1));
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("Bad port in cluster list: " + token);
                }
                endpoints.add(new Endpoint(host, port));
            }
        }
        if (endpoints.isEmpty()) {
            throw new IllegalArgumentException("No cluster endpoints configured");
        }
        return new ClusterEndpoints(endpoints);
    }

    public List<Endpoint> all() {
        return endpoints;
    }

    public Endpoint first() {
        return endpoints.get(0);
    }

    public int size() {
        return endpoints.size();
    }

    @Override
    public String toString() {
        return endpoints.toString();
    }

    /** A single host/port pair. */
    public static final class Endpoint {
        public final String host;
        public final int port;

        public Endpoint(String host, int port) {
            this.host = Objects.requireNonNull(host);
            this.port = port;
        }

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    /**
     * Convenience: list members joined as {@code host:port,host:port,...}
     * which is the format Postgres JDBC URLs accept.
     */
    public String asJdbcHostList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < endpoints.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            Endpoint e = endpoints.get(i);
            sb.append(e.host).append(':').append(e.port);
        }
        return sb.toString();
    }

    /** Snapshot copy of the underlying list as a {@link String} array of hosts. */
    public String[] hostsOnly() {
        return endpoints.stream().map(e -> e.host).toArray(String[]::new);
    }

    /** Snapshot copy of the host list. */
    public List<Endpoint> asList() {
        return new ArrayList<>(endpoints);
    }

    /** Convenience: parse with an int default port and no fallback list. */
    public static ClusterEndpoints parseStrict(String raw, int defaultPort) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Cluster list must not be empty");
        }
        return parse(raw, null, defaultPort);
    }

    /** Used by tests to materialise the list. */
    public static ClusterEndpoints of(List<Endpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalArgumentException("endpoints must not be empty");
        }
        return new ClusterEndpoints(new ArrayList<>(endpoints));
    }

    public static ClusterEndpoints of(Endpoint... endpoints) {
        return of(Arrays.asList(endpoints));
    }
}
