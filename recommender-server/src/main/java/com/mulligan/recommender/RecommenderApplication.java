package com.mulligan.recommender;

import java.util.Arrays;
import java.util.List;

/**
 * Command-line entry point for a recommender node.
 */
public final class RecommenderApplication {

    private RecommenderApplication() {
    }

    /**
     * Starts one recommender node. Configuration may be supplied through
     * environment variables or Java system properties:
     * {@code MULLIGAN_RECOMMENDER_NODE_ID}, {@code MULLIGAN_RECOMMENDER_PORT},
     * {@code MULLIGAN_RECOMMENDER_PEERS}, and {@code MULLIGAN_RECOMMENDER_MALICIOUS}.
     *
     * @param args optional {@code --node-id=}, {@code --port=}, {@code --peers=}, {@code --malicious=} overrides
     * @throws Exception when the node cannot start
     */
    public static void main(String[] args) throws Exception {
        Config config = Config.from(args);
        RecommendationEngine engine = new RecommendationEngine(new JdbcParkingSnapshotProvider());
        ConsensusCoordinator coordinator = new ConsensusCoordinator(
                config.nodeId(),
                config.peers(),
                engine,
                config.malicious()
        );
        RecommenderNodeServer server = new RecommenderNodeServer(config.port(), coordinator);
        server.start();
        System.out.printf("Recommender node %s listening on port %d malicious=%s peers=%s%n",
                config.nodeId(), config.port(), config.malicious(), config.peers());
    }

    private record Config(String nodeId, int port, List<String> peers, boolean malicious) {
        static Config from(String[] args) {
            String nodeId = value("mulligan.recommender.nodeId", "MULLIGAN_RECOMMENDER_NODE_ID", "recommender-1");
            int port = Integer.parseInt(value("mulligan.recommender.port", "MULLIGAN_RECOMMENDER_PORT", "8080"));
            String peersRaw = value("mulligan.recommender.peers", "MULLIGAN_RECOMMENDER_PEERS", "");
            boolean malicious = isTrue(value("mulligan.recommender.malicious", "MULLIGAN_RECOMMENDER_MALICIOUS", "false"));

            for (String arg : args) {
                if (arg.startsWith("--node-id=")) {
                    nodeId = arg.substring("--node-id=".length());
                } else if (arg.startsWith("--port=")) {
                    port = Integer.parseInt(arg.substring("--port=".length()));
                } else if (arg.startsWith("--peers=")) {
                    peersRaw = arg.substring("--peers=".length());
                } else if (arg.startsWith("--malicious=")) {
                    malicious = isTrue(arg.substring("--malicious=".length()));
                }
            }

            List<String> peers = peersRaw.isBlank()
                    ? List.of()
                    : Arrays.stream(peersRaw.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
            return new Config(nodeId, port, peers, malicious);
        }

        private static String value(String prop, String env, String fallback) {
            String value = System.getProperty(prop);
            if (value == null || value.isBlank()) {
                value = System.getenv(env);
            }
            return value == null || value.isBlank() ? fallback : value;
        }

        private static boolean isTrue(String value) {
            return value != null && (value.equalsIgnoreCase("true") || value.equals("1") || value.equalsIgnoreCase("yes"));
        }
    }
}
