package com.petnose.api.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Python embed service 호출 클라이언트.
 * Spring Boot가 이 클라이언트를 통해서만 python-embed를 호출합니다.
 */
@Slf4j
@Component
public class EmbedClient {

    private final WebClient webClient;

    public EmbedClient(@Qualifier("embedWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * 비문 이미지 바이트를 전송하고 임베딩 벡터를 반환합니다.
     *
     * @param imageBytes 이미지 파일 바이트
     * @param filename   원본 파일명 (확장자 포함)
     * @return EmbedResponse (vector, dimension, model)
     */
    @SuppressWarnings("unchecked")
    public EmbedResponse embed(byte[] imageBytes, String filename) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("image", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        Map<String, Object> response = webClient.post()
                .uri("/embed")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            throw new RuntimeException("embed service 응답이 null입니다.");
        }

        List<Double> vector = (List<Double>) response.get("vector");
        int dimension = (int) response.get("dimension");
        String model = (String) response.get("model");

        log.debug("[EmbedClient] 임베딩 완료: dimension={}, model={}", dimension, model);
        return new EmbedResponse(vector, dimension, model);
    }

    /**
     * Python embed service health 확인.
     */
    public boolean isHealthy() {
        try {
            Map<?, ?> response = webClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return response != null && "ok".equals(response.get("status"));
        } catch (Exception e) {
            log.warn("[EmbedClient] health check 실패: {}", e.getMessage());
            return false;
        }
    }

    public record EmbedResponse(List<Double> vector, int dimension, String model) {}
}
