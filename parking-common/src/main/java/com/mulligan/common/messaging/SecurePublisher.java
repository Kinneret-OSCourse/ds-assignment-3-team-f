package com.mulligan.common.messaging;

import com.mulligan.common.logging.SecureLogger;
import com.mulligan.common.security.MessageSigner;
import com.mulligan.common.security.SecureMessage;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.nio.charset.StandardCharsets;

/**
 * Wraps {@link com.rabbitmq.client.Channel#basicPublish} so that every queue
 * message carries the HMAC + nonce + timestamp envelope required by
 * Assignment 2.
 *
 * <p>Each call opens a short-lived connection and channel using the supplied
 * {@link MulliganConnectionFactory}. That is intentionally simple - it
 * matches the existing service code which opens one channel per publish - and
 * still benefits from the factory's multi-host failover.
 */
public final class SecurePublisher {

    private final MulliganConnectionFactory factory;
    private final MessageSigner signer;
    private final SecureLogger logger;

    public SecurePublisher(MulliganConnectionFactory factory, MessageSigner signer, SecureLogger logger) {
        if (factory == null || signer == null || logger == null) {
            throw new IllegalArgumentException("factory, signer, logger must all be non-null");
        }
        this.factory = factory;
        this.signer = signer;
        this.logger = logger;
    }

    /**
     * Publishes the supplied payload to the named routing key inside the
     * Mulligan exchange. Wraps it in a {@link SecureMessage} envelope first.
     *
     * @param routingKey one of the routing keys defined in {@link QueueTopology}
     * @param payload application JSON payload, already validated and serialised
     * @throws java.io.IOException if the cluster cannot accept the message
     */
    public void publishSigned(String routingKey, String payload) throws java.io.IOException {
        SecureMessage envelope = SecureMessage.wrap(payload, signer);
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
            .contentType("application/json")
            .deliveryMode(2) // persistent
            .build();

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            channel.basicPublish(
                QueueTopology.EXCHANGE_NAME,
                routingKey,
                props,
                envelope.toJson().getBytes(StandardCharsets.UTF_8)
            );
        } catch (java.util.concurrent.TimeoutException te) {
            logger.operational("AMQP publish timeout routingKey=" + routingKey);
            throw new java.io.IOException("Timeout publishing to broker", te);
        }
    }
}
