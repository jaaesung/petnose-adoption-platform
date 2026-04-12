package com.petnose.api.controller;

import com.petnose.api.client.EmbedClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * [DEV ONLY] 개발 및 연결 검증용 엔드포인트.
 * 이 컨트롤러는 도메인 로직과 무관하며, 연결 상태 확인 목적으로만 사용합니다.
 * dev 프로파일에서만 활성화됩니다. test/prod 환경에는 로드되지 않습니다.
 */
@Profile("dev")
@Slf4j
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DevController {

    private final EmbedClient embedClient;

    @Value("${spring.application.name:petnose-api}")
    private String appName;

    @Value("${qdrant.host}")
    private String qdrantHost;

    @Value("${qdrant.port}")
    private int qdrantPort;

    @Value("${qdrant.collection}")
    private String qdrantCollection;

    /** Spring Boot 기동 확인용 ping */
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "status", "ok",
                "service", appName,
                "timestamp", Instant.now().toString()
        );
    }

    /** Python embed service 연결 확인 */
    @GetMapping("/embed-ping")
    public Map<String, Object> embedPing() {
        boolean healthy = embedClient.isHealthy();
        return Map.of(
                "embed_service_healthy", healthy,
                "timestamp", Instant.now().toString()
        );
    }

    /**
     * 이미지 업로드 후 embed service 호출 테스트.
     * 실제 임베딩 결과(벡터 첫 5개 값, dimension)를 반환합니다.
     */
    @PostMapping("/embed-sample")
    public Map<String, Object> embedSample(@RequestParam("image") MultipartFile image) throws IOException {
        if (image.isEmpty()) {
            return Map.of("error", "이미지가 비어 있습니다.");
        }

        EmbedClient.EmbedResponse response = embedClient.embed(image.getBytes(), image.getOriginalFilename());

        return Map.of(
                "status", "ok",
                "dimension", response.dimension(),
                "model", response.model(),
                "vector_preview", response.vector().subList(0, Math.min(5, response.vector().size()))
        );
    }

    /** Qdrant 설정 확인 */
    @GetMapping("/qdrant-config")
    public Map<String, Object> qdrantConfig() {
        return Map.of(
                "host", qdrantHost,
                "port", qdrantPort,
                "collection", qdrantCollection
        );
    }
}
