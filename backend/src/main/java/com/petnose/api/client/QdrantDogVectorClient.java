package com.petnose.api.client;

import com.petnose.api.dto.registration.QdrantSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class QdrantDogVectorClient {

    private final WebClient webClient;
    private final String collection;
    private final int searchTopK;
    private final double searchScoreThreshold;
    private final long searchTimeoutMs;
    private final long upsertTimeoutMs;

    public QdrantDogVectorClient(
            WebClient.Builder webClientBuilder,
            @Value("${qdrant.host}") String host,
            @Value("${qdrant.port}") int port,
            @Value("${qdrant.collection}") String collection,
            @Value("${qdrant.search-top-k:5}") int searchTopK,
            @Value("${qdrant.search-score-threshold:0.70}") double searchScoreThreshold,
            @Value("${qdrant.search-timeout-ms:3000}") long searchTimeoutMs,
            @Value("${qdrant.upsert-timeout-ms:3000}") long upsertTimeoutMs
    ) {
        this.webClient = webClientBuilder
                .baseUrl("http://" + host + ":" + port)
                .build();
        this.collection = collection;
        this.searchTopK = searchTopK;
        this.searchScoreThreshold = searchScoreThreshold;
        this.searchTimeoutMs = searchTimeoutMs;
        this.upsertTimeoutMs = upsertTimeoutMs;
    }

    @SuppressWarnings("unchecked")
    public List<QdrantSearchResult> search(List<Double> vector) {
        return search(vector, searchTopK, searchScoreThreshold);
    }

    public List<QdrantSearchResult> search(List<Double> vector, int limit) {
        return search(vector, limit, null);
    }

    public List<QdrantSearchResult> searchExpectedDog(List<Double> vector, String expectedDogId) {
        return search(vector, 1, null, expectedDogFilter(expectedDogId));
    }

    public List<QdrantSearchResult> search(List<Double> vector, int limit, Double scoreThreshold) {
        return search(vector, limit, scoreThreshold, activeOnlyFilter());
    }

    @SuppressWarnings("unchecked")
    private List<QdrantSearchResult> search(
            List<Double> vector,
            int limit,
            Double scoreThreshold,
            Map<String, Object> filter
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("vector", vector);
        request.put("limit", Math.max(1, limit));
        if (scoreThreshold != null) {
            request.put("score_threshold", scoreThreshold);
        }
        request.put("with_payload", true);
        request.put("filter", filter);

        Map<String, Object> response;
        try {
            response = webClient.post()
                    .uri("/collections/{collection}/points/search", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofMillis(searchTimeoutMs));
        } catch (WebClientResponseException e) {
            throw new QdrantClientException(
                    "Qdrant search 실패: status=%d body=%s".formatted(e.getStatusCode().value(), e.getResponseBodyAsString()),
                    QdrantOperation.SEARCH,
                    e.getStatusCode().value(),
                    e.getResponseBodyAsString(),
                    e
            );
        } catch (Exception e) {
            throw new QdrantClientException(
                    "Qdrant search 실패: " + e.getMessage(),
                    QdrantOperation.SEARCH,
                    null,
                    null,
                    e
            );
        }

        if (response == null || response.get("result") == null) {
            throw new QdrantClientException("Qdrant search 응답이 비어 있습니다.", QdrantOperation.SEARCH, null, null, null);
        }

        List<Map<String, Object>> points = (List<Map<String, Object>>) response.get("result");
        List<QdrantSearchResult> results = new ArrayList<>();
        for (Map<String, Object> point : points) {
            Number scoreNumber = (Number) point.get("score");
            double score = scoreNumber == null ? 0.0 : scoreNumber.doubleValue();
            String pointId = String.valueOf(point.get("id"));

            Map<String, Object> payload = (Map<String, Object>) point.get("payload");
            String dogId = payload == null ? pointId : String.valueOf(payload.getOrDefault("dog_id", pointId));
            String breed = payload == null ? null : valueOrNull(payload.get("breed"));
            String noseImagePath = payload == null ? null : valueOrNull(payload.get("nose_image_path"));

            results.add(new QdrantSearchResult(pointId, dogId, score, breed, noseImagePath));
        }
        return results;
    }

    private Map<String, Object> activeOnlyFilter() {
        return Map.of("must", List.of(matchCondition("is_active", true)));
    }

    private Map<String, Object> expectedDogFilter(String expectedDogId) {
        return Map.of("must", List.of(
                matchCondition("is_active", true),
                matchCondition("dog_id", expectedDogId)
        ));
    }

    private Map<String, Object> matchCondition(String key, Object value) {
        return Map.of(
                "key", key,
                "match", Map.of("value", value)
        );
    }

    public void upsert(String pointId, List<Double> vector, Map<String, Object> payload) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", pointId);
        point.put("vector", vector);
        point.put("payload", payload);

        Map<String, Object> request = Map.of("points", List.of(point));

        try {
            webClient.put()
                    .uri("/collections/{collection}/points?wait=true", collection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofMillis(upsertTimeoutMs));
        } catch (WebClientResponseException e) {
            throw new QdrantClientException(
                    "Qdrant upsert 실패: status=%d body=%s".formatted(e.getStatusCode().value(), e.getResponseBodyAsString()),
                    QdrantOperation.UPSERT,
                    e.getStatusCode().value(),
                    e.getResponseBodyAsString(),
                    e
            );
        } catch (Exception e) {
            throw new QdrantClientException(
                    "Qdrant upsert 실패: " + e.getMessage(),
                    QdrantOperation.UPSERT,
                    null,
                    null,
                    e
            );
        }
    }

    private static String valueOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public enum QdrantOperation {
        SEARCH,
        UPSERT
    }

    public static class QdrantClientException extends RuntimeException {
        private final QdrantOperation operation;
        private final Integer upstreamStatus;
        private final String upstreamBody;

        public QdrantClientException(
                String message,
                QdrantOperation operation,
                Integer upstreamStatus,
                String upstreamBody,
                Throwable cause
        ) {
            super(message, cause);
            this.operation = operation;
            this.upstreamStatus = upstreamStatus;
            this.upstreamBody = upstreamBody;
        }

        public QdrantOperation getOperation() {
            return operation;
        }

        public Integer getUpstreamStatus() {
            return upstreamStatus;
        }

        public String getUpstreamBody() {
            return upstreamBody;
        }
    }
}
