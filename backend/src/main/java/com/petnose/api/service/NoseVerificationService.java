package com.petnose.api.service;

import com.petnose.api.client.EmbedClient;
import com.petnose.api.client.QdrantDogVectorClient;
import com.petnose.api.domain.entity.NoseVerificationAttempt;
import com.petnose.api.domain.entity.User;
import com.petnose.api.domain.enums.EmbeddingStatus;
import com.petnose.api.domain.enums.VerificationResult;
import com.petnose.api.dto.nose.NoseVerificationRequest;
import com.petnose.api.dto.nose.NoseVerificationResponse;
import com.petnose.api.dto.registration.DuplicateCandidateResponse;
import com.petnose.api.dto.registration.QdrantSearchResult;
import com.petnose.api.exception.ApiException;
import com.petnose.api.repository.NoseVerificationAttemptRepository;
import com.petnose.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoseVerificationService {

    private static final Duration ATTEMPT_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final NoseVerificationAttemptRepository noseVerificationAttemptRepository;
    private final FileStorageService fileStorageService;
    private final EmbedClient embedClient;
    private final QdrantDogVectorClient qdrantDogVectorClient;
    private final NoseVerificationPolicy noseVerificationPolicy;
    private final TransactionTemplate transactionTemplate;

    @Value("${qdrant.vector-dimension}")
    private int expectedVectorDimension;

    public NoseVerificationResponse verify(NoseVerificationRequest request) {
        validateRequiredFields(request);

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "존재하지 않는 user_id 입니다."));

        FileStorageService.StoredFile noseStored = fileStorageService.storeNoseVerificationImage(
                UUID.randomUUID().toString(),
                request.noseImage()
        );

        EmbedClient.EmbedResponse embedResponse = requestEmbeddingOrFail(user.getId(), noseStored);
        validateEmbeddingDimensionOrFail(user.getId(), noseStored, embedResponse);

        List<QdrantSearchResult> searchResults = searchFromQdrantOrFail(user.getId(), noseStored, embedResponse);
        NoseVerificationPolicy.VerificationDecision decision = noseVerificationPolicy.evaluate(searchResults);

        VerificationResult result = decision.duplicate()
                ? VerificationResult.DUPLICATE_SUSPECTED
                : VerificationResult.PASSED;
        NoseVerificationAttempt attempt = transactionTemplate.execute(status -> createAttempt(
                user.getId(),
                noseStored,
                result,
                decision.maxScore(),
                decision.topMatch() == null ? null : decision.topMatch().dogId(),
                embedResponse.model(),
                embedResponse.dimension(),
                null
        ));
        if (attempt == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "VERIFICATION_ATTEMPT_CREATE_FAILED", "비문 검증 결과 저장에 실패했습니다.");
        }

        if (result == VerificationResult.DUPLICATE_SUSPECTED) {
            return buildDuplicateResponse(attempt, decision);
        }
        return buildPassedResponse(attempt, decision.maxScore());
    }

    private EmbedClient.EmbedResponse requestEmbeddingOrFail(Long userId, FileStorageService.StoredFile noseStored) {
        try {
            return embedClient.embed(noseStored.bytes(), noseStored.originalFilename(), noseStored.mimeType());
        } catch (EmbedClient.EmbedClientException e) {
            log.warn("[NoseVerification] embed 실패: userId={}, upstreamStatus={}, message={}",
                    userId, e.getUpstreamStatus(), e.getMessage());
            transactionTemplate.executeWithoutResult(status -> createAttempt(
                    userId,
                    noseStored,
                    VerificationResult.EMBED_FAILED,
                    null,
                    null,
                    null,
                    null,
                    "embed 실패: " + e.getMessage()
            ));

            if (e.getUpstreamStatus() != null && e.getUpstreamStatus() == 400) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_NOSE_IMAGE", "비문 이미지 처리에 실패했습니다.");
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMBED_SERVICE_UNAVAILABLE", "임베딩 서비스를 사용할 수 없습니다.");
        }
    }

    private void validateEmbeddingDimensionOrFail(
            Long userId,
            FileStorageService.StoredFile noseStored,
            EmbedClient.EmbedResponse embedResponse
    ) {
        if (embedResponse.vector() == null || embedResponse.vector().isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> createAttempt(
                    userId,
                    noseStored,
                    VerificationResult.EMBED_FAILED,
                    null,
                    null,
                    embedResponse.model(),
                    embedResponse.dimension(),
                    "embed vector가 비어 있습니다."
            ));
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMPTY_EMBEDDING", "임베딩 결과가 비어 있습니다.");
        }
        if (embedResponse.dimension() != expectedVectorDimension) {
            transactionTemplate.executeWithoutResult(status -> createAttempt(
                    userId,
                    noseStored,
                    VerificationResult.EMBED_FAILED,
                    null,
                    null,
                    embedResponse.model(),
                    embedResponse.dimension(),
                    "dimension mismatch: expected=%d actual=%d".formatted(expectedVectorDimension, embedResponse.dimension())
            ));
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMBEDDING_DIMENSION_MISMATCH", "임베딩 차원이 시스템 설정과 일치하지 않습니다.");
        }
    }

    private List<QdrantSearchResult> searchFromQdrantOrFail(
            Long userId,
            FileStorageService.StoredFile noseStored,
            EmbedClient.EmbedResponse embedResponse
    ) {
        try {
            return qdrantDogVectorClient.search(embedResponse.vector());
        } catch (QdrantDogVectorClient.QdrantClientException e) {
            log.warn("[NoseVerification] qdrant search 실패: userId={}, status={}, message={}",
                    userId, e.getUpstreamStatus(), e.getMessage());
            transactionTemplate.executeWithoutResult(status -> createAttempt(
                    userId,
                    noseStored,
                    VerificationResult.QDRANT_SEARCH_FAILED,
                    null,
                    null,
                    embedResponse.model(),
                    embedResponse.dimension(),
                    "qdrant search 실패: " + e.getMessage()
            ));
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "QDRANT_SEARCH_FAILED", "중복 검증 검색에 실패했습니다.");
        }
    }

    private NoseVerificationAttempt createAttempt(
            Long userId,
            FileStorageService.StoredFile noseStored,
            VerificationResult result,
            Double maxScore,
            String candidateDogId,
            String model,
            Integer dimension,
            String failureReason
    ) {
        NoseVerificationAttempt attempt = new NoseVerificationAttempt();
        attempt.setRequestedByUserId(userId);
        attempt.setNoseImagePath(noseStored.relativePath());
        attempt.setNoseImageMimeType(noseStored.mimeType());
        attempt.setNoseImageFileSize(noseStored.fileSize());
        attempt.setNoseImageSha256(noseStored.sha256());
        attempt.setResult(result);
        attempt.setSimilarityScore(maxScore == null ? null : toScore(maxScore));
        attempt.setCandidateDogId(candidateDogId);
        attempt.setModel(model);
        attempt.setDimension(dimension);
        attempt.setFailureReason(failureReason);
        attempt.setExpiresAt(Instant.now().plus(ATTEMPT_TTL));
        return noseVerificationAttemptRepository.save(attempt);
    }

    private NoseVerificationResponse buildPassedResponse(NoseVerificationAttempt attempt, double maxScore) {
        return new NoseVerificationResponse(
                attempt.getId(),
                true,
                VerificationResult.PASSED.name(),
                "VERIFIED",
                EmbeddingStatus.COMPLETED.name(),
                maxScore,
                fileStorageService.toPublicUrl(attempt.getNoseImagePath()),
                null,
                attempt.getExpiresAt(),
                "비문 인증을 통과했습니다. 분양글 작성을 진행할 수 있습니다."
        );
    }

    private NoseVerificationResponse buildDuplicateResponse(
            NoseVerificationAttempt attempt,
            NoseVerificationPolicy.VerificationDecision decision
    ) {
        DuplicateCandidateResponse topMatch = null;
        if (decision.topMatch() != null) {
            QdrantSearchResult t = decision.topMatch();
            topMatch = new DuplicateCandidateResponse(
                    t.dogId(),
                    t.score(),
                    t.breed()
            );
        }

        return new NoseVerificationResponse(
                attempt.getId(),
                false,
                VerificationResult.DUPLICATE_SUSPECTED.name(),
                "DUPLICATE_SUSPECTED",
                EmbeddingStatus.SKIPPED_DUPLICATE.name(),
                decision.maxScore(),
                fileStorageService.toPublicUrl(attempt.getNoseImagePath()),
                topMatch,
                attempt.getExpiresAt(),
                "기존 등록견과 동일 개체로 의심되어 분양글 작성이 제한됩니다."
        );
    }

    private void validateRequiredFields(NoseVerificationRequest request) {
        if (request.userId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_ID_REQUIRED", "user_id는 필수입니다.");
        }
        if (request.noseImage() == null || request.noseImage().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOSE_IMAGE_REQUIRED", "nose_image는 필수입니다.");
        }
    }

    private BigDecimal toScore(double score) {
        return BigDecimal.valueOf(score).setScale(5, RoundingMode.HALF_UP);
    }
}
