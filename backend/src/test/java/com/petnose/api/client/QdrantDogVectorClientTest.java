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
        AtomicReference<String> requestMethod = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/collections/test_collection/points/search", exchange -> {
            requestMethod.set(exchange.getRequestMethod());
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
        assertThat(requestMethod.get()).isEqualTo("POST");
        assertThat(request.get("score_threshold").asDouble()).isEqualTo(0.70);
        assertThat(request.get("limit").asInt()).isEqualTo(5);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().score()).isEqualTo(0.71);
        assertThat(results.getFirst().dogId()).isEqualTo("dog-1");
    }

    @Test
    void expectedDogSearchFiltersToExpectedDogWithoutScoreThreshold() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> requestMethod = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/collections/test_collection/points/search", exchange -> {
            requestMethod.set(exchange.getRequestMethod());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"result":[{"id":"expected-dog","score":0.80630887,"payload":{"dog_id":"expected-dog","breed":"Maltese","nose_image_path":"dogs/expected-dog/nose.jpg"}}]}
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

        List<QdrantSearchResult> results = client.searchExpectedDog(List.of(0.1, 0.2, 0.3), "expected-dog");

        JsonNode request = objectMapper.readTree(requestBody.get());
        assertThat(requestMethod.get()).isEqualTo("POST");
        assertThat(request.get("vector").size()).isEqualTo(3);
        assertThat(request.get("vector").get(0).asDouble()).isEqualTo(0.1);
        assertThat(request.get("limit").asInt()).isEqualTo(1);
        assertThat(request.get("with_payload").asBoolean()).isTrue();
        assertThat(request.has("score_threshold")).isFalse();
        JsonNode must = request.path("filter").path("must");
        assertThat(must.size()).isEqualTo(2);
        assertThat(hasBooleanMatch(must, "is_active", true)).isTrue();
        assertThat(hasTextMatch(must, "dog_id", "expected-dog")).isTrue();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().pointId()).isEqualTo("expected-dog");
        assertThat(results.getFirst().dogId()).isEqualTo("expected-dog");
        assertThat(results.getFirst().score()).isEqualTo(0.80630887);
    }

    private static boolean hasBooleanMatch(JsonNode conditions, String key, boolean value) {
        for (JsonNode condition : conditions) {
            if (key.equals(condition.path("key").asText())
                    && condition.path("match").path("value").isBoolean()
                    && condition.path("match").path("value").asBoolean() == value) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTextMatch(JsonNode conditions, String key, String value) {
        for (JsonNode condition : conditions) {
            if (key.equals(condition.path("key").asText())
                    && value.equals(condition.path("match").path("value").asText())) {
                return true;
            }
        }
        return false;
    }
}
