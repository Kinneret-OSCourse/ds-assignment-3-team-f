package com.mulligan.server;

import com.mulligan.common.logging.SecureLogger;
import com.mulligan.common.messaging.MulliganConnectionFactory;
import com.mulligan.common.messaging.QueueTopology;
import com.mulligan.common.security.ClientErrorCodes;
import com.mulligan.common.security.MessageSigner;
import com.mulligan.common.security.MessageValidator;
import com.mulligan.common.security.NonceStore;
import com.mulligan.common.security.SecureMessage;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * Mulligan Queue Server (Assignment 2 hardened edition).
 *
 * <p>Boots a long-running consumer for both quorum queues. Every delivered
 * message is required to be a {@link SecureMessage} envelope; messages that
 * fail HMAC verification, fail the timestamp window, or repeat a nonce are
 * rejected with {@code basicNack(requeue=false)} and written to the secure
 * audit log. Generic {@link ClientErrorCodes} are surfaced; stack traces
 * never reach the client side.
 *
 * <p>Operational notes:
 * <ul>
 *   <li>Connects through {@link MulliganConnectionFactory} which honours the
 *       {@code MULLIGAN_QUEUE_HOSTS} list, so any cluster node failure is
 *       transparent.</li>
 *   <li>Declares topology via {@link QueueTopology}, which sets
 *       {@code x-queue-type=quorum} on both queues.</li>
 * </ul>
 */
public final class MulliganQueueServer {

    private static final int MAX_STARTUP_ATTEMPTS = 12;
    private static final long RETRY_DELAY_MILLIS = 5000;

    private MulliganQueueServer() {
    }

    /**
     * Boots the queue server.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        SecureLogger logger = new SecureLogger("queue-server");
        MessageSigner signer = new MessageSigner();
        NonceStore nonces = new NonceStore();
        MessageValidator validator = new MessageValidator(signer, nonces, logger);

        String user = env("MULLIGAN_QUEUE_USER_SERVER", "mulligan_server");
        String pass = env("MULLIGAN_QUEUE_PASSWORD_SERVER", null);
        if (pass == null) {
            logger.security("FATAL missing MULLIGAN_QUEUE_PASSWORD_SERVER - refusing to start");
            System.err.println(ClientErrorCodes.INTERNAL + ": server password not configured");
            System.exit(2);
        }

        MulliganConnectionFactory factory = MulliganConnectionFactory.create(user, pass, logger);

        for (int attempt = 1; attempt <= MAX_STARTUP_ATTEMPTS; attempt++) {
            try (Connection connection = factory.newConnection();
                 Channel channel = connection.createChannel()) {

                QueueTopology.declare(channel);
                channel.basicQos(32);

                attachConsumer(channel, QueueTopology.TRANSACTIONS_QUEUE, validator, logger);
                attachConsumer(channel, QueueTopology.CITATIONS_QUEUE, validator, logger);

                logger.operational("Queue server started, listening on "
                    + QueueTopology.TRANSACTIONS_QUEUE + " and " + QueueTopology.CITATIONS_QUEUE);
                System.out.println("Mulligan Queue Server started.");

                // Park the main thread; consumers run on the channel's worker pool.
                while (true) {
                    Thread.sleep(5000);
                }
            } catch (IOException | TimeoutException e) {
                logger.operational("Queue server connect attempt " + attempt + "/" + MAX_STARTUP_ATTEMPTS
                    + " failed: " + e.getMessage());
                if (attempt == MAX_STARTUP_ATTEMPTS) {
                    logger.security("FATAL queue server could not reach cluster after retries");
                    System.err.println(ClientErrorCodes.UNAVAILABLE + ": queue cluster unreachable");
                    return;
                }
                sleepBeforeRetry();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.operational("Queue server interrupted, shutting down");
                return;
            }
        }
    }

    private static void attachConsumer(Channel channel, String queue,
                                       MessageValidator validator, SecureLogger logger) throws IOException {
        channel.basicConsume(queue, false, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope,
                                       AMQP.BasicProperties properties, byte[] body) throws IOException {
                long tag = envelope.getDeliveryTag();
                String source = properties == null ? "unknown" : String.valueOf(properties.getAppId());
                try {
                    String json = new String(body, StandardCharsets.UTF_8);
                    SecureMessage msg = SecureMessage.fromJson(json);
                    MessageValidator.Result result = validator.verify(msg, source);
                    if (!result.accepted) {
                        // Permanently reject - do not requeue, this would just replay the bad message.
                        channel.basicNack(tag, false, false);
                        return;
                    }
                    // Future: route to downstream report processors. For Assignment 2
                    // the server's job is to validate and acknowledge; the MO UI
                    // pulls messages directly from the queue for reporting.
                    channel.basicAck(tag, false);
                    logger.operational("ACCEPT queue=" + queue + " nonce=" + msg.nonce);
                } catch (IllegalArgumentException malformed) {
                    logger.security("REJECT malformed-envelope queue=" + queue + " err=" + malformed.getMessage());
                    channel.basicNack(tag, false, false);
                } catch (Exception unexpected) {
                    // Never propagate internal exception details.
                    logger.security("REJECT unexpected-exception queue=" + queue + " err=" + unexpected.getClass().getSimpleName());
                    channel.basicNack(tag, false, true);
                }
            }
        });
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = System.getProperty(name.toLowerCase().replace('_', '.'));
        }
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry queue connection.", e);
        }
    }
}
