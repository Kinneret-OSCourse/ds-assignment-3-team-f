package com.mulligan.common.testdrivers;

import com.mulligan.common.logging.SecureLogger;
import com.mulligan.common.messaging.MulliganConnectionFactory;
import com.mulligan.common.messaging.QueueTopology;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.nio.charset.StandardCharsets;

final class AttackPublisherSupport {

    private static final SecureLogger LOG = new SecureLogger("security-check");

    private AttackPublisherSupport() {
    }

    static void publishTransaction(String envelopeJson) throws Exception {
        String user = env("MULLIGAN_QUEUE_USER_SERVER", "mulligan_server");
        String pass = env("MULLIGAN_QUEUE_PASSWORD_SERVER", "mulligan_server_pw");
        MulliganConnectionFactory factory = MulliganConnectionFactory.create(user, pass, LOG);

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .appId("security-check")
                    .contentType("application/json")
                    .build();
            channel.basicPublish(
                    QueueTopology.EXCHANGE_NAME,
                    QueueTopology.TRANSACTION_ROUTING_KEY,
                    props,
                    envelopeJson.getBytes(StandardCharsets.UTF_8)
            );
        }
    }

    static String env(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
