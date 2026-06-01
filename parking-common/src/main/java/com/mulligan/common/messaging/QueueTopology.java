package com.mulligan.common.messaging;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Declares the Mulligan queue topology in a single place. Both the server's
 * boot routine and any client that publishes/consumes can call
 * {@link #declare(Channel)} - it is idempotent.
 *
 * <p>Two named quorum queues, as required by Assignment 2 §5.2:
 * <ul>
 *   <li>{@code Transactions} - completed parking transactions from Customer UI</li>
 *   <li>{@code Citations} - issued citations from PEO UI</li>
 * </ul>
 *
 * <p>Quorum queues are declared by setting {@code x-queue-type=quorum} so each
 * queue is replicated across the three RabbitMQ cluster members and tolerates
 * a single node failure.
 */
public final class QueueTopology {

    public static final String EXCHANGE_NAME = "mulligan.exchange";
    public static final String TRANSACTIONS_QUEUE = "Transactions";
    public static final String CITATIONS_QUEUE = "Citations";
    public static final String TRANSACTION_ROUTING_KEY = "transaction.completed";
    public static final String CITATION_ROUTING_KEY = "citation.issued";

    private QueueTopology() {
    }

    /**
     * Declares the exchange, the two quorum queues, and the bindings.
     * Idempotent: safe to call from every client and from the server.
     *
     * @param channel an open RabbitMQ channel
     * @throws IOException if the broker rejects the declaration
     */
    public static void declare(Channel channel) throws IOException {
        channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.DIRECT, true);

        Map<String, Object> quorumArgs = new HashMap<>();
        quorumArgs.put("x-queue-type", "quorum");

        channel.queueDeclare(TRANSACTIONS_QUEUE, true, false, false, quorumArgs);
        channel.queueBind(TRANSACTIONS_QUEUE, EXCHANGE_NAME, TRANSACTION_ROUTING_KEY);

        channel.queueDeclare(CITATIONS_QUEUE, true, false, false, quorumArgs);
        channel.queueBind(CITATIONS_QUEUE, EXCHANGE_NAME, CITATION_ROUTING_KEY);
    }
}
