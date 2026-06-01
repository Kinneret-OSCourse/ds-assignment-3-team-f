package com.mulligan.service;

import com.mulligan.common.logging.SecureLogger;
import com.mulligan.common.messaging.MulliganConnectionFactory;
import com.mulligan.common.messaging.QueueTopology;

/**
 * Customer UI queue configuration. Now a thin facade over the shared
 * {@link MulliganConnectionFactory} + {@link QueueTopology} in
 * {@code parking-common} so the Customer UI uses the same multi-host,
 * AMQPS-capable, HMAC-aware messaging stack as the rest of the system.
 *
 * <p>The Customer UI authenticates with the broker as the per-service user
 * {@code mulligan_customer}, whose RabbitMQ permissions allow only publishing
 * to the {@code Transactions} queue (Assignment 2 §1.1 RabbitMQ Hardening).
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
     * @return a cluster-aware connection factory pre-configured with the
     *     Customer UI's per-service RabbitMQ credentials.
     */
    public static MulliganConnectionFactory createFactory() {
        String user = envOr("MULLIGAN_QUEUE_USER_CUSTOMER", "mulligan_customer");
        String pass = envOr("MULLIGAN_QUEUE_PASSWORD_CUSTOMER", envOr("MULLIGAN_QUEUE_PASSWORD", ""));
        return MulliganConnectionFactory.create(user, pass, new SecureLogger("customer-ui"));
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = System.getProperty(name);
        }
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
