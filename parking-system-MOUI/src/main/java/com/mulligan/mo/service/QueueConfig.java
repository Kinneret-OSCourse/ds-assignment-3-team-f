package com.mulligan.mo.service;

import com.mulligan.common.logging.SecureLogger;
import com.mulligan.common.messaging.MulliganConnectionFactory;
import com.mulligan.common.messaging.QueueTopology;

/**
 * MO UI queue configuration. Thin facade over the shared
 * {@link MulliganConnectionFactory}. The MO UI authenticates with the broker
 * as the per-service user {@code mulligan_mo}, whose RabbitMQ permissions
 * allow consuming from both {@code Transactions} and {@code Citations} queues
 * but publishing to neither.
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
     * @return cluster-aware connection factory with the MO UI's per-service
     *     credentials.
     */
    public static MulliganConnectionFactory createFactory() {
        String user = envOr("MULLIGAN_QUEUE_USER_MO", "mulligan_mo");
        String pass = envOr("MULLIGAN_QUEUE_PASSWORD_MO", envOr("MULLIGAN_QUEUE_PASSWORD", ""));
        return MulliganConnectionFactory.create(user, pass, new SecureLogger("mo-ui"));
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = System.getProperty(name);
        }
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
