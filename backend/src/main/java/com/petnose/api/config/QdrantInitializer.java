package com.petnose.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

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

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void run(ApplicationArguments args) {
        String baseUrl = "http://" + qdrantHost + ":" + qdrantPort;
        String collectionUrl = baseUrl + "/collections/" + collection;

        try {
            restTemplate.getForEntity(collectionUrl, String.class);
            log.info("[Qdrant] 컬렉션 '{}' 이미 존재합니다.", collection);
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
                    "distance": "Cosine"
                  }
                }
                """.formatted(vectorDimension);
        try {
            restTemplate.put(collectionUrl, body);
            log.info("[Qdrant] 컬렉션 '{}' 생성 완료 (dimension={}, distance=Cosine)",
                    collection, vectorDimension);
        } catch (Exception e) {
            log.warn("[Qdrant] 컬렉션 생성 실패: {}", e.getMessage());
        }
    }
}
