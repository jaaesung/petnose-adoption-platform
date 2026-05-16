package com.petnose.api.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petnose.api.dto.registration.QdrantSearchResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class QdrantDogVectorClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void defaultSearchSendsConfiguredScoreThreshold() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/collections/test_collection/points/search", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"result":[{"id":"point-1","score":0.71,"payload":{"dog_id":"dog-1","breed":"Jindo","nose_image_path":"dogs/dog-1/nose.jpg"}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        QdrantDogVectorClient client = new QdrantDogVectorClient(
                WebClient.builder(),
                "127.0.0.1",
                server.getAddress().getPort(),
                "test_collection",
                5,
                0.70,
                3000,
                3000
        );

        List<QdrantSearchResult> results = client.search(List.of(0.1, 0.2, 0.3));

        JsonNode request = objectMapper.readTree(requestBody.get());
        assertThat(request.get("score_threshold").asDouble()).isEqualTo(0.70);
        assertThat(request.get("limit").asInt()).isEqualTo(5);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().score()).isEqualTo(0.71);
        assertThat(results.getFirst().dogId()).isEqualTo("dog-1");
    }
}
