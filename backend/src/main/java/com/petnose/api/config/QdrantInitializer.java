package com.petnose.api.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Iterator;
import java.util.Map;

/**
 * 앱 기동 시 Qdrant 컬렉션 존재 여부를 확인하고 없으면 생성합니다.
 * 연결 실패 시 에러를 로그에 남기지만 앱 기동을 중단하지 않습니다.
 */
@Slf4j
@Component
public class QdrantInitializer implements ApplicationRunner {

    @Value("${qdrant.host}")
    private String qdrantHost;

    @Value("${qdrant.port}")
    private int qdrantPort;

    @Value("${qdrant.collection}")
    private String collection;

    @Value("${qdrant.vector-dimension}")
    private int vectorDimension;

    @Value("${qdrant.distance:Cosine}")
    private String vectorDistance;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void run(ApplicationArguments args) {
        String baseUrl = "http://" + qdrantHost + ":" + qdrantPort;
        String collectionUrl = baseUrl + "/collections/" + collection;

        try {
            String responseBody = restTemplate.getForObject(collectionUrl, String.class);
            log.info("[Qdrant] 컬렉션 '{}' 이미 존재합니다.", collection);
            validateCollectionContract(responseBody);
        } catch (HttpClientErrorException.NotFound e) {
            createCollection(collectionUrl);
        } catch (ResourceAccessException e) {
            log.warn("[Qdrant] 연결 실패 — 나중에 수동으로 컬렉션을 생성해야 합니다. host={}, port={}, error={}",
                    qdrantHost, qdrantPort, e.getMessage());
        } catch (Exception e) {
            log.warn("[Qdrant] 초기화 중 예외 발생 (앱 기동은 계속됩니다): {}", e.getMessage());
        }
    }

    private void createCollection(String collectionUrl) {
        String body = """
                {
                  "vectors": {
                    "size": %d,
                    "distance": "%s"
                  }
                }
                """.formatted(vectorDimension, vectorDistance);
        try {
            restTemplate.put(collectionUrl, body);
            log.info("[Qdrant] 컬렉션 '{}' 생성 완료 (dimension={}, distance={})",
                    collection, vectorDimension, vectorDistance);
        } catch (Exception e) {
            log.warn("[Qdrant] 컬렉션 생성 실패: {}", e.getMessage());
        }
    }

    private void validateCollectionContract(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            log.warn("[Qdrant] 컬렉션 '{}' 메타데이터를 읽지 못했습니다. 수동 점검이 필요합니다.", collection);
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode vectorsNode = root.path("result").path("config").path("params").path("vectors");

            Integer actualSize = null;
            String actualDistance = null;

            if (vectorsNode.has("size") && vectorsNode.has("distance")) {
                actualSize = vectorsNode.path("size").asInt();
                actualDistance = vectorsNode.path("distance").asText();
            } else if (vectorsNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = vectorsNode.fields();
                if (fields.hasNext()) {
                    JsonNode namedVector = fields.next().getValue();
                    if (namedVector != null && namedVector.has("size") && namedVector.has("distance")) {
                        actualSize = namedVector.path("size").asInt();
                        actualDistance = namedVector.path("distance").asText();
                    }
                }
            }

            if (actualSize == null || actualDistance == null) {
                log.warn("[Qdrant] 컬렉션 '{}'의 vectors 설정을 파싱하지 못했습니다. response={}", collection, responseBody);
                return;
            }

            boolean sizeMatches = actualSize == vectorDimension;
            boolean distanceMatches = vectorDistance.equalsIgnoreCase(actualDistance);

            if (sizeMatches && distanceMatches) {
                log.info("[Qdrant] 컬렉션 '{}' 계약 검증 통과 (dimension={}, distance={})",
                        collection, actualSize, actualDistance);
                return;
            }

            log.warn("[Qdrant] 컬렉션 '{}' 계약 불일치 감지 - expected(dimension={}, distance={}), actual(dimension={}, distance={})",
                    collection, vectorDimension, vectorDistance, actualSize, actualDistance);
        } catch (Exception e) {
            log.warn("[Qdrant] 컬렉션 '{}' 메타데이터 파싱 실패: {}", collection, e.getMessage());
        }
    }
}
