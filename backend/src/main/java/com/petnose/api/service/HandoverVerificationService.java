package com.petnose.api.service;

import com.petnose.api.client.EmbedClient;
import com.petnose.api.client.QdrantDogVectorClient;
import com.petnose.api.config.HandoverVerificationProperties;
import com.petnose.api.domain.entity.AdoptionPost;
import com.petnose.api.domain.entity.Dog;
import com.petnose.api.domain.enums.AdoptionPostStatus;
import com.petnose.api.domain.enums.DogStatus;
import com.petnose.api.domain.enums.HandoverVerificationDecision;
import com.petnose.api.dto.adoption.HandoverVerificationResponse;
import com.petnose.api.dto.registration.QdrantSearchResult;
import com.petnose.api.exception.ApiException;
import com.petnose.api.repository.AdoptionPostRepository;
import com.petnose.api.repository.DogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HandoverVerificationService {

    private final AdoptionPostRepository adoptionPostRepository;
    private final DogRepository dogRepository;
    private final EmbedClient embedClient;
    private final QdrantDogVectorClient qdrantDogVectorClient;
    private final HandoverVerificationProperties properties;

    @Value("${qdrant.vector-dimension}")
    private int expectedVectorDimension;

    public HandoverVerificationResponse verify(Long postId, MultipartFile noseImage) {
        AdoptionPost post = adoptionPostRepository.findById(postId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "Adoption post was not found."));
        validatePostIsVerifiable(post);

        Dog expectedDog = dogRepository.findById(post.getDogId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOG_NOT_FOUND", "강아지를 찾을 수 없습니다."));
        validateExpectedDog(expectedDog);

        validateNoseImage(noseImage);
        EmbedClient.EmbedResponse embedResponse = requestEmbeddingOrFail(postId, noseImage);
        validateEmbeddingDimensionOrFail(embedResponse);

        List<QdrantSearchResult> searchResults = searchExpectedDogFromQdrantOrFail(
                postId,
                embedResponse,
                expectedDog.getId()
        );
        return evaluate(post.getId(), expectedDog.getId(), embedResponse, searchResults);
    }

    private void validatePostIsVerifiable(AdoptionPost post) {
        if (post.getStatus() != AdoptionPostStatus.OPEN && post.getStatus() != AdoptionPostStatus.RESERVED) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "POST_NOT_VERIFIABLE",
                    "인계 비문 확인은 OPEN 또는 RESERVED 상태의 분양글에서만 가능합니다."
            );
        }
    }

    private void validateExpectedDog(Dog dog) {
        if (dog.getStatus() != DogStatus.REGISTERED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DOG_NOT_VERIFIED", "비문 인증을 통과한 강아지만 확인할 수 있습니다.");
        }
    }

    private void validateNoseImage(MultipartFile noseImage) {
        if (noseImage == null || noseImage.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOSE_IMAGE_REQUIRED", "nose_image는 필수입니다.");
        }
    }

    private EmbedClient.EmbedResponse requestEmbeddingOrFail(Long postId, MultipartFile noseImage) {
        try {
            return embedClient.embed(
                    noseImage.getBytes(),
                    originalFilenameOrDefault(noseImage),
                    contentTypeOrDefault(noseImage)
            );
        } catch (IOException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_NOSE_IMAGE", "비문 이미지 처리에 실패했습니다.");
        } catch (EmbedClient.EmbedClientException e) {
            log.warn("[HandoverVerification] embed 실패: postId={}, upstreamStatus={}, message={}",
                    postId, e.getUpstreamStatus(), e.getMessage());
            if (e.getUpstreamStatus() != null && e.getUpstreamStatus() == 400) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_NOSE_IMAGE", "비문 이미지 처리에 실패했습니다.");
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMBED_SERVICE_UNAVAILABLE", "임베딩 서비스를 사용할 수 없습니다.");
        }
    }

    private void validateEmbeddingDimensionOrFail(EmbedClient.EmbedResponse embedResponse) {
        if (embedResponse.vector() == null || embedResponse.vector().isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMPTY_EMBEDDING", "임베딩 결과가 비어 있습니다.");
        }
        if (embedResponse.dimension() != expectedVectorDimension) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMBEDDING_DIMENSION_MISMATCH", "임베딩 차원이 시스템 설정과 일치하지 않습니다.");
        }
    }

    private List<QdrantSearchResult> searchExpectedDogFromQdrantOrFail(
            Long postId,
            EmbedClient.EmbedResponse embedResponse,
            String expectedDogId
    ) {
        try {
            return qdrantDogVectorClient.searchExpectedDog(embedResponse.vector(), expectedDogId);
        } catch (QdrantDogVectorClient.QdrantClientException e) {
            log.warn("[HandoverVerification] qdrant search 실패: postId={}, status={}, message={}",
                    postId, e.getUpstreamStatus(), e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "QDRANT_SEARCH_FAILED", "인계 비문 확인 검색에 실패했습니다.");
        }
    }

    private HandoverVerificationResponse evaluate(
            Long postId,
            String expectedDogId,
            EmbedClient.EmbedResponse embedResponse,
            List<QdrantSearchResult> searchResults
    ) {
        if (searchResults == null || searchResults.isEmpty()) {
            return response(
                    postId,
                    expectedDogId,
                    HandoverVerificationDecision.NO_MATCH_CANDIDATE,
                    null,
                    false,
                    embedResponse
            );
        }

        QdrantSearchResult topResult = searchResults.getFirst();
        boolean topMatchIsExpected = expectedDogId.equals(topResult.dogId());
        double score = topResult.score();
        if (!topMatchIsExpected) {
            log.warn(
                    "[HandoverVerification] expected-dog filtered search returned a different dog: postId={}, expectedDogId={}",
                    postId,
                    expectedDogId
            );
        }
        HandoverVerificationDecision decision = topMatchIsExpected && score >= properties.getMatchThreshold()
                ? HandoverVerificationDecision.MATCHED
                : HandoverVerificationDecision.NOT_MATCHED;

        return response(postId, expectedDogId, decision, score, topMatchIsExpected, embedResponse);
    }

    private HandoverVerificationResponse response(
            Long postId,
            String expectedDogId,
            HandoverVerificationDecision decision,
            Double similarityScore,
            boolean topMatchIsExpected,
            EmbedClient.EmbedResponse embedResponse
    ) {
        return new HandoverVerificationResponse(
                postId,
                expectedDogId,
                decision == HandoverVerificationDecision.MATCHED,
                decision,
                similarityScore,
                properties.getMatchThreshold(),
                properties.getAmbiguousThreshold(),
                topMatchIsExpected,
                embedResponse.model(),
                embedResponse.dimension(),
                message(decision)
        );
    }

    private String message(HandoverVerificationDecision decision) {
        return switch (decision) {
            case MATCHED -> "분양글에 등록된 강아지와 일치합니다.";
            case AMBIGUOUS -> "유사도가 기준에 근접하지만 확정하기 어렵습니다. 비문 이미지를 다시 촬영해주세요.";
            case NOT_MATCHED -> "분양글에 등록된 강아지와 일치하지 않습니다. 거래 전 확인이 필요합니다.";
            case NO_MATCH_CANDIDATE -> "일치 후보를 찾지 못했습니다. 비문 이미지를 다시 촬영해주세요.";
        };
    }

    private String originalFilenameOrDefault(MultipartFile noseImage) {
        String filename = noseImage.getOriginalFilename();
        return filename == null || filename.isBlank() ? "handover_nose_image" : filename;
    }

    private String contentTypeOrDefault(MultipartFile noseImage) {
        String contentType = noseImage.getContentType();
        return contentType == null || contentType.isBlank() ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType;
    }
}
