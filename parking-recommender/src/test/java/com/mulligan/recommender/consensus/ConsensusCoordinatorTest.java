package com.mulligan.recommender.consensus;

import com.mulligan.recommender.model.RecommendationOption;
import com.mulligan.recommender.service.RecommenderDatabase;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsensusCoordinatorTest {

    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void stopPeers() {
        servers.forEach(server -> server.stop(0));
        servers.clear();
    }

    @Test
    void returnsMajorityWhenOnePeerDisagrees() throws Exception {
        ConsensusCoordinator coordinator = coordinator(
                List.of(option("S003", 1)),
                List.of(peer("S003;1", 200), peer("S004;1", 200)),
                false
        );

        var response = coordinator.recommendWithConsensus("S003");

        assertTrue(response.success());
        assertEquals("S003;1", response.compactOptions());
    }

    @Test
    void returnsMajorityWhenOnePeerIsMissing() throws Exception {
        ConsensusCoordinator coordinator = coordinator(
                List.of(option("S003", 1)),
                List.of(peer("S003;1", 200), peer("ignored", 503)),
                false
        );

        var response = coordinator.recommendWithConsensus("S003");

        assertTrue(response.success());
        assertEquals("S003;1", response.compactOptions());
    }

    @Test
    void failsWhenOnlyOneNodeResponds() throws Exception {
        ConsensusCoordinator coordinator = coordinator(
                List.of(option("S003", 1)),
                List.of(peer("ignored", 503), peer("ignored", 503)),
                false
        );

        var response = coordinator.recommendWithConsensus("S003");

        assertFalse(response.success());
        assertEquals("No majority consensus.", response.message());
    }

    @Test
    void failsWhenThreeNodesDisagree() throws Exception {
        ConsensusCoordinator coordinator = coordinator(
                List.of(option("S003", 1)),
                List.of(peer("S004;1", 200), peer("S005;1", 200)),
                false
        );

        var response = coordinator.recommendWithConsensus("S003");

        assertFalse(response.success());
        assertEquals("No majority consensus.", response.message());
    }

    @Test
    void honestPeersOutvoteMaliciousLocalNode() throws Exception {
        ConsensusCoordinator coordinator = coordinator(
                List.of(option("S003", 1)),
                List.of(peer("S003;1", 200), peer("S003;1", 200)),
                true
        );

        var response = coordinator.recommendWithConsensus("S003");

        assertTrue(response.success());
        assertEquals("S003;1", response.compactOptions());
    }

    private ConsensusCoordinator coordinator(List<RecommendationOption> localVote, List<String> peers,
                                             boolean malicious) {
        return new ConsensusCoordinator(
                "rec-test",
                new StubRecommenderDatabase(localVote),
                peers,
                new AtomicBoolean(malicious)
        );
    }

    private String peer(String body, int status) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/vote", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        servers.add(server);
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static RecommendationOption option(String space, int citations) {
        return new RecommendationOption(space, citations);
    }

    private static final class StubRecommenderDatabase extends RecommenderDatabase {
        private final List<RecommendationOption> vote;

        private StubRecommenderDatabase(List<RecommendationOption> vote) {
            this.vote = vote;
        }

        @Override
        public List<RecommendationOption> recommendLocal(String requestedSpace) {
            return vote;
        }
    }
}
