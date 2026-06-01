package com.mulligan.service;

import com.mulligan.common.logging.SecureLogger;
import com.mulligan.common.messaging.MulliganConnectionFactory;
import com.mulligan.common.messaging.QueueTopology;

/**
 * PEO UI queue configuration. Thin facade over the shared
 * {@link MulliganConnectionFactory}. The PEO UI authenticates with the broker
 * as the per-service user {@code mulligan_peo}, whose RabbitMQ permissions
 * allow only publishing to the {@code Citations} queue.
 */
public final class QueueConfig {

    public static final String EXCHANGE_NAME = QueueTopology.EXCHANGE_NAME;
    public static final String TRANSACTION_ROUTING_KEY = QueueTopology.TRANSACTION_ROUTING_KEY;
    public static final String CITATION_ROUTING_KEY = QueueTopology.CITATION_ROUTING_KEY;
    public static final String TRANSACTIONS_QUEUE = QueueTopology.TRANSACTIONS_QUEUE;
    public static final String CITATIONS_QUEUE = QueueTopology.CITATIONS_QUEUE;

    private QueueConfig() {
    }

    /**
     * @return cluster-aware factory pre-configured with the PEO UI's
     *     per-service RabbitMQ credentials.
     */
    public static MulliganConnectionFactory createFactory() {
        String user = envOr("MULLIGAN_QUEUE_USER_PEO", "mulligan_peo");
        String pass = envOr("MULLIGAN_QUEUE_PASSWORD_PEO", envOr("MULLIGAN_QUEUE_PASSWORD", ""));
        return MulliganConnectionFactory.create(user, pass, new SecureLogger("peo-ui"));
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = System.getProperty(name);
        }
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
