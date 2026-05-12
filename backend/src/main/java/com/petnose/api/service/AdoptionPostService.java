package com.petnose.api.service;

import com.petnose.api.domain.entity.AdoptionPost;
import com.petnose.api.domain.entity.Dog;
import com.petnose.api.domain.entity.User;
import com.petnose.api.domain.entity.VerificationLog;
import com.petnose.api.domain.enums.AdoptionPostStatus;
import com.petnose.api.domain.enums.DogStatus;
import com.petnose.api.domain.enums.VerificationResult;
import com.petnose.api.dto.adoption.AdoptionPostCreateRequest;
import com.petnose.api.dto.adoption.AdoptionPostCreateResponse;
import com.petnose.api.exception.ApiException;
import com.petnose.api.repository.AdoptionPostRepository;
import com.petnose.api.repository.DogRepository;
import com.petnose.api.repository.UserRepository;
import com.petnose.api.repository.VerificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdoptionPostService {

    private static final List<AdoptionPostStatus> ACTIVE_STATUSES = List.of(
            AdoptionPostStatus.DRAFT,
            AdoptionPostStatus.OPEN,
            AdoptionPostStatus.RESERVED
    );

    private final UserRepository userRepository;
    private final DogRepository dogRepository;
    private final VerificationLogRepository verificationLogRepository;
    private final AdoptionPostRepository adoptionPostRepository;

    @Transactional
    public AdoptionPostCreateResponse create(Long currentUserId, AdoptionPostCreateRequest request) {
        validateRequest(request);
        AdoptionPostStatus requestedStatus = AdoptionPostStatus.fromCreateRequest(request.status());

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."));
        validateUser(currentUser);

        Dog dog = dogRepository.findById(request.dogId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOG_NOT_FOUND", "강아지를 찾을 수 없습니다."));
        validateDogOwner(currentUser, dog);
        validateDogStatus(dog);
        validateLatestVerificationLog(dog.getId());
        validateNoActivePost(dog.getId());

        AdoptionPost post = new AdoptionPost();
        post.setAuthorUserId(currentUser.getId());
        post.setDogId(dog.getId());
        post.setTitle(request.title().trim());
        post.setContent(request.content().trim());
        post.setStatus(requestedStatus);
        if (requestedStatus == AdoptionPostStatus.OPEN) {
            post.setPublishedAt(LocalDateTime.now());
        }
        post.setClosedAt(null);

        AdoptionPost saved = adoptionPostRepository.save(post);
        return new AdoptionPostCreateResponse(
                saved.getId(),
                saved.getDogId(),
                saved.getTitle(),
                saved.getContent(),
                saved.getStatus().name(),
                saved.getPublishedAt(),
                saved.getCreatedAt()
        );
    }

    private void validateRequest(AdoptionPostCreateRequest request) {
        if (request == null) {
            throw validationFailed("request body는 필수입니다.");
        }
        if (request.dogId() == null || request.dogId().isBlank()) {
            throw validationFailed("dog_id는 필수입니다.");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw validationFailed("title은 필수입니다.");
        }
        if (request.title().trim().length() > 200) {
            throw validationFailed("title은 최대 200자까지 입력할 수 있습니다.");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw validationFailed("content는 필수입니다.");
        }
    }

    private ApiException validationFailed(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    private void validateUser(User currentUser) {
        if (!currentUser.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_INACTIVE", "비활성 사용자입니다.");
        }
        if (currentUser.getDisplayName() == null || currentUser.getDisplayName().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_PROFILE_REQUIRED", "작성자 표시 이름이 필요합니다.");
        }
    }

    private void validateDogOwner(User currentUser, Dog dog) {
        if (!currentUser.getId().equals(dog.getOwnerUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DOG_OWNER_MISMATCH", "현재 사용자의 강아지가 아닙니다.");
        }
    }

    private void validateDogStatus(Dog dog) {
        if (dog.getStatus() == DogStatus.DUPLICATE_SUSPECTED) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "DUPLICATE_DOG_CANNOT_BE_POSTED",
                    "중복 의심 강아지는 분양 게시글을 생성할 수 없습니다."
            );
        }
        if (dog.getStatus() != DogStatus.REGISTERED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DOG_NOT_VERIFIED", "비문 인증을 통과한 강아지만 게시할 수 있습니다.");
        }
    }

    private void validateLatestVerificationLog(String dogId) {
        VerificationLog latest = verificationLogRepository.findFirstByDogIdOrderByCreatedAtDescIdDesc(dogId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "DOG_NOT_VERIFIED", "비문 인증 이력이 없습니다."));

        if (latest.getResult() == VerificationResult.DUPLICATE_SUSPECTED) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "DUPLICATE_DOG_CANNOT_BE_POSTED",
                    "최신 비문 인증 결과가 중복 의심입니다."
            );
        }
        if (latest.getResult() != VerificationResult.PASSED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DOG_NOT_VERIFIED", "최신 비문 인증 결과가 통과 상태가 아닙니다.");
        }
    }

    private void validateNoActivePost(String dogId) {
        if (adoptionPostRepository.existsByDogIdAndStatusIn(dogId, ACTIVE_STATUSES)) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_POST_ALREADY_EXISTS", "이미 활성 분양 게시글이 있습니다.");
        }
    }
}
