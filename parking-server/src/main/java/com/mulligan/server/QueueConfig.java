package com.mulligan.server;

import com.mulligan.common.messaging.QueueTopology;

/**
 * Backwards-compatible constants. The Assignment 2 server uses
 * {@link com.mulligan.common.messaging.QueueTopology} directly, and the
 * cluster-aware {@link com.mulligan.common.messaging.MulliganConnectionFactory}
 * for connection setup. This class is retained only so the previously
 * compiled JAR layout remains source-compatible.
 *
 * @deprecated Use {@link QueueTopology} instead.
 */
@Deprecated
public final class QueueConfig {

    public static final String EXCHANGE_NAME = QueueTopology.EXCHANGE_NAME;
    public static final String TRANSACTION_ROUTING_KEY = QueueTopology.TRANSACTION_ROUTING_KEY;
    public static final String CITATION_ROUTING_KEY = QueueTopology.CITATION_ROUTING_KEY;
    public static final String TRANSACTIONS_QUEUE = QueueTopology.TRANSACTIONS_QUEUE;
    public static final String CITATIONS_QUEUE = QueueTopology.CITATIONS_QUEUE;

    private QueueConfig() {
    }
}
