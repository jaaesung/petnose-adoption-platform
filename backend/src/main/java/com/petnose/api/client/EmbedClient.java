package com.petnose.api.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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
    public EmbedResponse embed(byte[] imageBytes, String filename) {
        return embed(imageBytes, filename, MediaType.IMAGE_PNG_VALUE);
    }

    /**
     * 비문 이미지 바이트를 multipart/form-data(image 파트)로 전송합니다.
     */
    @SuppressWarnings("unchecked")
    public EmbedResponse embed(byte[] imageBytes, String filename, String contentType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        MultipartBodyBuilder.PartBuilder imagePart = builder.part("image", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        imagePart.filename(filename);
        imagePart.contentType(MediaType.parseMediaType(contentType));

        Map<String, Object> response;
        try {
            response = webClient.post()
                    .uri("/embed")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw new EmbedClientException(
                    "embed service 호출 실패: status=%d body=%s".formatted(
                            e.getStatusCode().value(),
                            e.getResponseBodyAsString()
                    ),
                    e.getStatusCode().value(),
                    e.getResponseBodyAsString(),
                    e
            );
        } catch (Exception e) {
            throw new EmbedClientException("embed service 호출 실패: " + e.getMessage(), null, null, e);
        }

        if (response == null) {
            throw new RuntimeException("embed service 응답이 null입니다.");
        }

        List<Number> vectorNumbers = (List<Number>) response.get("vector");
        if (vectorNumbers == null) {
            throw new EmbedClientException("embed service 응답에 vector 필드가 없습니다.", null, String.valueOf(response), null);
        }
        List<Double> vector = vectorNumbers.stream().map(Number::doubleValue).toList();

        Object dimensionObj = response.get("dimension");
        if (!(dimensionObj instanceof Number number)) {
            throw new EmbedClientException("embed service 응답의 dimension 필드가 숫자가 아닙니다.", null, String.valueOf(response), null);
        }
        int dimension = number.intValue();

        String model = (String) response.get("model");
        if (model == null) {
            throw new EmbedClientException("embed service 응답에 model 필드가 없습니다.", null, String.valueOf(response), null);
        }

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

    public static class EmbedClientException extends RuntimeException {
        private final Integer upstreamStatus;
        private final String upstreamBody;

        public EmbedClientException(String message, Integer upstreamStatus, String upstreamBody, Throwable cause) {
            super(message, cause);
            this.upstreamStatus = upstreamStatus;
            this.upstreamBody = upstreamBody;
        }

        public Integer getUpstreamStatus() {
            return upstreamStatus;
        }

        public String getUpstreamBody() {
            return upstreamBody;
        }
    }
}
