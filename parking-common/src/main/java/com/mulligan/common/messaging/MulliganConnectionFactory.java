package com.mulligan.common.messaging;

import com.mulligan.common.cluster.ClusterEndpoints;
import com.mulligan.common.logging.SecureLogger;
import com.mulligan.common.security.TlsConfig;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.util.concurrent.TimeoutException;

/**
 * Builds {@link com.rabbitmq.client.ConnectionFactory ConnectionFactory}
 * instances that:
 * <ul>
 *   <li>connect over AMQPS when {@code MULLIGAN_QUEUE_TLS=true};</li>
 *   <li>cycle through a comma-separated list of {@code MULLIGAN_QUEUE_HOSTS}
 *       so a single node failure is transparent to the publisher;</li>
 *   <li>recover automatically using the AMQP client's built-in topology
 *       recovery (so quorum-queue subscribers reattach after a node restart).</li>
 * </ul>
 *
 * <p>This is the failover-aware successor to the duplicated
 * {@code QueueConfig.createFactory()} that lived in each UI module.
 */
public final class MulliganConnectionFactory {

    private static final int DEFAULT_AMQP_PORT = 5672;
    private static final int DEFAULT_AMQPS_PORT = 5671;

    private final ConnectionFactory factory;
    private final Address[] addresses;
    private final SecureLogger logger;

    private MulliganConnectionFactory(ConnectionFactory factory, Address[] addresses, SecureLogger logger) {
        this.factory = factory;
        this.addresses = addresses;
        this.logger = logger;
    }

    /**
     * Constructs a factory using the standard {@code MULLIGAN_QUEUE_*}
     * environment variables. The caller passes the service username and
     * password so each UI uses its own least-privilege RabbitMQ user.
     *
     * @param serviceUser RabbitMQ username (per-service, never {@code guest})
     * @param servicePassword RabbitMQ password
     * @param logger secure logger for operational events
     * @return cluster-aware connection factory
     */
    public static MulliganConnectionFactory create(String serviceUser, String servicePassword, SecureLogger logger) {
        boolean tls = isTrue(resolve("mulligan.queue.tls", "MULLIGAN_QUEUE_TLS"));
        int defaultPort = tls ? DEFAULT_AMQPS_PORT : DEFAULT_AMQP_PORT;

        String hostsRaw = resolve("mulligan.queue.hosts", "MULLIGAN_QUEUE_HOSTS");
        if (hostsRaw == null || hostsRaw.isBlank()) {
            // Backwards compatible with the Assignment 1 single-host config.
            String single = resolve("mulligan.queue.host", "MULLIGAN_QUEUE_HOST");
            if (single == null || single.isBlank()) {
                single = firstDbHostOrLocalhost();
            }
            String port = resolve("mulligan.queue.port", "MULLIGAN_QUEUE_PORT");
            hostsRaw = port == null || port.isBlank() ? single : single + ":" + port;
        }
        ClusterEndpoints endpoints = ClusterEndpoints.parse(hostsRaw, null, defaultPort);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername(serviceUser);
        factory.setPassword(servicePassword);
        String vhost = resolve("mulligan.queue.vhost", "MULLIGAN_QUEUE_VHOST");
        factory.setVirtualHost(vhost == null || vhost.isBlank() ? "/" : vhost);

        // Topology recovery so the AMQP client re-declares queues and resumes
        // consumers after a node fails over.
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(2000);
        factory.setRequestedHeartbeat(15);

        if (tls) {
            factory.useSslProtocol(TlsConfig.buildAmqpsContext());
            factory.enableHostnameVerification();
        }

        Address[] addresses = endpoints.asList().stream()
            .map(e -> new Address(e.host, e.port))
            .toArray(Address[]::new);

        if (logger != null) {
            logger.operational("AMQP factory built tls=" + tls + " hosts=" + endpoints);
        }
        return new MulliganConnectionFactory(factory, addresses, logger);
    }

    /**
     * Opens a new connection, attempting each cluster node in turn until one
     * succeeds. Uses the AMQP client's built-in multi-host
     * {@link ConnectionFactory#newConnection(Address[])} behaviour.
     *
     * @return a live connection
     * @throws java.io.IOException if no node accepts the connection
     */
    public Connection newConnection() throws java.io.IOException, TimeoutException {
        try {
            return factory.newConnection(addresses);
        } catch (java.io.IOException ioe) {
            if (logger != null) {
                logger.operational("AMQP connect failed for all nodes: " + ioe.getMessage());
            }
            throw ioe;
        }
    }

    public Address[] addresses() {
        return addresses.clone();
    }

    private static String resolve(String prop, String env) {
        String value = System.getProperty(prop);
        if (value == null || value.isBlank()) {
            value = System.getenv(env);
        }
        return value;
    }

    private static String firstDbHostOrLocalhost() {
        String dbHosts = resolve("mulligan.db.hosts", "MULLIGAN_DB_HOSTS");
        if (dbHosts == null || dbHosts.isBlank()) {
            dbHosts = resolve("mulligan.db.host", "MULLIGAN_DB_HOST");
        }
        if (dbHosts == null || dbHosts.isBlank()) {
            return "localhost";
        }
        String first = dbHosts.split(",")[0].trim();
        int colon = first.indexOf(':');
        return colon >= 0 ? first.substring(0, colon).trim() : first;
    }

    private static boolean isTrue(String value) {
        return value != null && (value.equalsIgnoreCase("true") || value.equals("1") || value.equalsIgnoreCase("yes"));
    }
}
