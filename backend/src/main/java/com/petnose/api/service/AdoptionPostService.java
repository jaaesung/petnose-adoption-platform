package com.petnose.api.service;

import com.petnose.api.client.EmbedClient;
import com.petnose.api.client.QdrantDogVectorClient;
import com.petnose.api.domain.entity.AdoptionPost;
import com.petnose.api.domain.entity.Dog;
import com.petnose.api.domain.entity.DogImage;
import com.petnose.api.domain.entity.NoseVerificationAttempt;
import com.petnose.api.domain.entity.User;
import com.petnose.api.domain.entity.VerificationLog;
import com.petnose.api.domain.enums.AdoptionPostStatus;
import com.petnose.api.domain.enums.DogGender;
import com.petnose.api.domain.enums.DogImageType;
import com.petnose.api.domain.enums.DogStatus;
import com.petnose.api.domain.enums.VerificationResult;
import com.petnose.api.dto.adoption.AdoptionPostCreateRequest;
import com.petnose.api.dto.adoption.AdoptionPostCreateResponse;
import com.petnose.api.dto.adoption.AdoptionPostDetailResponse;
import com.petnose.api.dto.adoption.AdoptionPostListItemResponse;
import com.petnose.api.dto.adoption.AdoptionPostListResponse;
import com.petnose.api.dto.adoption.AdoptionPostOwnerListItemResponse;
import com.petnose.api.dto.adoption.AdoptionPostOwnerListResponse;
import com.petnose.api.dto.adoption.AdoptionPostStatusUpdateRequest;
import com.petnose.api.dto.adoption.AdoptionPostStatusUpdateResponse;
import com.petnose.api.exception.ApiException;
import com.petnose.api.repository.AdoptionPostRepository;
import com.petnose.api.repository.DogImageRepository;
import com.petnose.api.repository.DogRepository;
import com.petnose.api.repository.NoseVerificationAttemptRepository;
import com.petnose.api.repository.UserRepository;
import com.petnose.api.repository.VerificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdoptionPostService {

    private static final int DEFAULT_PUBLIC_PAGE = 0;
    private static final int DEFAULT_PUBLIC_PAGE_SIZE = 20;
    private static final int MAX_PUBLIC_PAGE_SIZE = 100;

    private static final List<AdoptionPostStatus> ACTIVE_STATUSES = List.of(
            AdoptionPostStatus.DRAFT,
            AdoptionPostStatus.OPEN,
            AdoptionPostStatus.RESERVED
    );

    private final UserRepository userRepository;
    private final DogRepository dogRepository;
    private final VerificationLogRepository verificationLogRepository;
    private final DogImageRepository dogImageRepository;
    private final AdoptionPostRepository adoptionPostRepository;
    private final NoseVerificationAttemptRepository noseVerificationAttemptRepository;
    private final FileStorageService fileStorageService;
    private final EmbedClient embedClient;
    private final QdrantDogVectorClient qdrantDogVectorClient;

    @Value("${qdrant.vector-dimension}")
    private int expectedVectorDimension;

    @Transactional(readOnly = true)
    public AdoptionPostListResponse findPublicPosts(String statusParam, String pageParam, String sizeParam) {
        int page = parsePageParameter(pageParam, DEFAULT_PUBLIC_PAGE);
        int size = parsePageParameter(sizeParam, DEFAULT_PUBLIC_PAGE_SIZE);
        return findPublicPosts(statusParam, page, size);
    }

    @Transactional(readOnly = true)
    public AdoptionPostListResponse findPublicPosts(String statusParam, int page, int size) {
        validatePageRequest(page, size);
        AdoptionPostStatus status = AdoptionPostStatus.fromPublicQuery(statusParam);

        Page<AdoptionPost> posts = adoptionPostRepository.findPublicPageByStatus(
                status,
                PageRequest.of(page, size)
        );
        PublicPostContext context = loadPublicPostContext(posts.getContent());
        List<AdoptionPostListItemResponse> items = posts.getContent().stream()
                .map(post -> toListItemResponse(post, context))
                .toList();

        return new AdoptionPostListResponse(items, page, size, posts.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdoptionPostOwnerListResponse findOwnerPosts(
            Long currentUserId,
            String statusParam,
            String pageParam,
            String sizeParam
    ) {
        int page = parsePageParameter(pageParam, DEFAULT_PUBLIC_PAGE);
        int size = parsePageParameter(sizeParam, DEFAULT_PUBLIC_PAGE_SIZE);
        return findOwnerPosts(currentUserId, statusParam, page, size);
    }

    @Transactional(readOnly = true)
    public AdoptionPostOwnerListResponse findOwnerPosts(
            Long currentUserId,
            String statusParam,
            int page,
            int size
    ) {
        validatePageRequest(page, size);
        AdoptionPostStatus status = AdoptionPostStatus.fromOwnerQuery(statusParam);
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<AdoptionPost> posts = status == null
                ? adoptionPostRepository.findByAuthorUserIdOrderByCreatedAtDescIdDesc(currentUserId, pageRequest)
                : adoptionPostRepository.findByAuthorUserIdAndStatusOrderByCreatedAtDescIdDesc(currentUserId, status, pageRequest);

        OwnerPostContext context = loadOwnerPostContext(posts.getContent());
        List<AdoptionPostOwnerListItemResponse> items = posts.getContent().stream()
                .map(post -> toOwnerListItemResponse(post, context))
                .toList();

        return new AdoptionPostOwnerListResponse(items, page, size, posts.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdoptionPostDetailResponse findPublicPost(Long postId) {
        AdoptionPost post = adoptionPostRepository.findById(postId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "Adoption post was not found."));
        if (!post.getStatus().isPublicVisible()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_PUBLIC", "Adoption post is not public.");
        }

        PublicPostContext context = loadPublicPostContext(List.of(post));
        return toDetailResponse(post, context);
    }

    @Transactional
    public AdoptionPostCreateResponse create(Long currentUserId, AdoptionPostCreateRequest request) {
        validateRequest(request);
        AdoptionPostStatus requestedStatus = AdoptionPostStatus.fromCreateRequest(request.status());
        Instant now = Instant.now();

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."));
        validateUser(currentUser);

        NoseVerificationAttempt attempt = validateNoseVerificationAttempt(currentUser, request.noseVerificationId(), now);
        Dog dog = createDog(currentUser.getId(), request);
        DogImage noseImage = createNoseImage(dog.getId(), attempt);
        VerificationLog verificationLog = createVerificationLog(currentUser.getId(), dog.getId(), noseImage.getId(), attempt);
        saveRequiredProfileImage(dog.getId(), request.profileImage());

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
        attempt.setConsumedAt(now);
        attempt.setConsumedByPostId(saved.getId());
        noseVerificationAttemptRepository.save(attempt);
        upsertDogVector(dog, attempt);

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

    @Transactional
    public AdoptionPostStatusUpdateResponse updateStatus(
            Long currentUserId,
            Long postId,
            AdoptionPostStatusUpdateRequest request
    ) {
        AdoptionPost post = adoptionPostRepository.findById(postId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "Adoption post was not found."));

        if (!post.getAuthorUserId().equals(currentUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "POST_OWNER_MISMATCH", "현재 사용자의 게시글이 아닙니다.");
        }

        AdoptionPostStatus targetStatus = parseStatusUpdateRequest(request);
        AdoptionPostStatus currentStatus = post.getStatus();
        if (currentStatus == targetStatus) {
            return toStatusUpdateResponse(post);
        }
        validateStatusTransition(currentStatus, targetStatus);

        LocalDateTime now = LocalDateTime.now();
        applyStatusTransition(currentUserId, post, currentStatus, targetStatus, now);
        adoptionPostRepository.flush();
        return toStatusUpdateResponse(post);
    }

    private void validateRequest(AdoptionPostCreateRequest request) {
        if (request == null) {
            throw validationFailed("request는 필수입니다.");
        }
        if (request.noseVerificationId() == null) {
            throw validationFailed("nose_verification_id는 필수입니다.");
        }
        if (request.dogName() == null || request.dogName().isBlank()) {
            throw validationFailed("dog_name은 필수입니다.");
        }
        if (request.breed() == null || request.breed().isBlank()) {
            throw validationFailed("breed는 필수입니다.");
        }
        DogGender.from(request.gender());
        if (request.title() == null || request.title().isBlank()) {
            throw validationFailed("title은 필수입니다.");
        }
        if (request.title().trim().length() > 200) {
            throw validationFailed("title은 최대 200자까지 입력할 수 있습니다.");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw validationFailed("content는 필수입니다.");
        }
        if (request.profileImage() == null || request.profileImage().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROFILE_IMAGE_REQUIRED", "profile_image는 필수입니다.");
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

    private NoseVerificationAttempt validateNoseVerificationAttempt(
            User currentUser,
            Long noseVerificationId,
            Instant now
    ) {
        NoseVerificationAttempt attempt = noseVerificationAttemptRepository.findByIdForUpdate(noseVerificationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOSE_VERIFICATION_NOT_FOUND", "비문 검증 정보를 찾을 수 없습니다."));
        if (!currentUser.getId().equals(attempt.getRequestedByUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOSE_VERIFICATION_OWNER_MISMATCH", "현재 사용자의 비문 검증 정보가 아닙니다.");
        }
        if (attempt.getConsumedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "NOSE_VERIFICATION_ALREADY_CONSUMED", "이미 분양글 작성에 사용된 비문 검증입니다.");
        }
        if (attempt.getExpiresAt().isBefore(now) || attempt.getExpiresAt().equals(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOSE_VERIFICATION_EXPIRED", "비문 검증 유효 시간이 만료되었습니다.");
        }
        if (attempt.getResult() == VerificationResult.DUPLICATE_SUSPECTED) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "DUPLICATE_DOG_CANNOT_BE_POSTED",
                    "중복 의심 비문 검증은 분양 게시글을 생성할 수 없습니다."
            );
        }
        if (attempt.getResult() != VerificationResult.PASSED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DOG_NOT_VERIFIED", "비문 인증을 통과한 강아지만 게시할 수 있습니다.");
        }
        return attempt;
    }

    private Dog createDog(Long ownerUserId, AdoptionPostCreateRequest request) {
        Dog dog = new Dog();
        dog.setId(java.util.UUID.randomUUID().toString());
        dog.setOwnerUserId(ownerUserId);
        dog.setName(request.dogName().trim());
        dog.setBreed(request.breed().trim());
        dog.setGender(DogGender.from(request.gender()));
        dog.setBirthDate(parseBirthDate(request.birthDate()));
        dog.setDescription(blankToNull(request.description()));
        dog.setStatus(DogStatus.REGISTERED);
        return dogRepository.save(dog);
    }

    private DogImage createNoseImage(String dogId, NoseVerificationAttempt attempt) {
        DogImage image = new DogImage();
        image.setDogId(dogId);
        image.setImageType(DogImageType.NOSE);
        image.setFilePath(attempt.getNoseImagePath());
        image.setMimeType(attempt.getNoseImageMimeType());
        image.setFileSize(attempt.getNoseImageFileSize());
        image.setSha256(attempt.getNoseImageSha256());
        return dogImageRepository.save(image);
    }

    private VerificationLog createVerificationLog(Long currentUserId, String dogId, Long noseImageId, NoseVerificationAttempt attempt) {
        VerificationLog log = new VerificationLog();
        log.setDogId(dogId);
        log.setDogImageId(noseImageId);
        log.setRequestedByUserId(currentUserId);
        log.setResult(VerificationResult.PASSED);
        log.setSimilarityScore(attempt.getSimilarityScore());
        log.setCandidateDogId(attempt.getCandidateDogId());
        log.setModel(attempt.getModel());
        log.setDimension(attempt.getDimension());
        log.setFailureReason(attempt.getFailureReason());
        return verificationLogRepository.save(log);
    }

    private void upsertDogVector(Dog dog, NoseVerificationAttempt attempt) {
        FileStorageService.StoredFile noseStored = fileStorageService.readStoredImage(
                attempt.getNoseImagePath(),
                attempt.getNoseImageMimeType(),
                attempt.getNoseImageFileSize(),
                attempt.getNoseImageSha256()
        );
        EmbedClient.EmbedResponse embedResponse = requestEmbeddingOrFail(noseStored);
        validateEmbeddingDimensionOrFail(embedResponse);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dog_id", dog.getId());
        payload.put("user_id", dog.getOwnerUserId());
        payload.put("breed", dog.getBreed());
        payload.put("nose_image_path", attempt.getNoseImagePath());
        payload.put("registered_at", Instant.now().toString());
        payload.put("is_active", true);

        try {
            qdrantDogVectorClient.upsert(dog.getId(), embedResponse.vector(), payload);
        } catch (QdrantDogVectorClient.QdrantClientException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "QDRANT_UPSERT_FAILED", "벡터 인덱스 동기화에 실패했습니다.");
        }
    }

    private EmbedClient.EmbedResponse requestEmbeddingOrFail(FileStorageService.StoredFile noseStored) {
        try {
            return embedClient.embed(noseStored.bytes(), noseStored.originalFilename(), noseStored.mimeType());
        } catch (EmbedClient.EmbedClientException e) {
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

    private void validateNoOtherActivePost(String dogId, Long currentPostId) {
        if (adoptionPostRepository.existsByDogIdAndStatusInAndIdNot(dogId, ACTIVE_STATUSES, currentPostId)) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_POST_ALREADY_EXISTS", "이미 활성 분양 게시글이 있습니다.");
        }
    }

    private void saveRequiredProfileImage(String dogId, MultipartFile profileImage) {
        FileStorageService.StoredFile stored = fileStorageService.storeProfileImage(dogId, profileImage);
        DogImage image = new DogImage();
        image.setDogId(dogId);
        image.setImageType(DogImageType.PROFILE);
        image.setFilePath(stored.relativePath());
        image.setMimeType(stored.mimeType());
        image.setFileSize(stored.fileSize());
        image.setSha256(stored.sha256());
        dogImageRepository.save(image);
    }

    private LocalDate parseBirthDate(String birthDate) {
        if (birthDate == null || birthDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(birthDate);
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BIRTH_DATE", "birth_date는 YYYY-MM-DD 형식이어야 합니다.");
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private AdoptionPostStatus parseStatusUpdateRequest(AdoptionPostStatusUpdateRequest request) {
        return AdoptionPostStatus.fromStatusUpdateRequest(request == null ? null : request.status());
    }

    private void validateStatusTransition(AdoptionPostStatus currentStatus, AdoptionPostStatus targetStatus) {
        boolean allowed = switch (currentStatus) {
            case DRAFT -> targetStatus == AdoptionPostStatus.OPEN || targetStatus == AdoptionPostStatus.CLOSED;
            case OPEN -> targetStatus == AdoptionPostStatus.RESERVED
                    || targetStatus == AdoptionPostStatus.COMPLETED
                    || targetStatus == AdoptionPostStatus.CLOSED;
            case RESERVED -> targetStatus == AdoptionPostStatus.OPEN
                    || targetStatus == AdoptionPostStatus.COMPLETED
                    || targetStatus == AdoptionPostStatus.CLOSED;
            case COMPLETED, CLOSED -> false;
        };

        if (!allowed) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_STATUS_TRANSITION",
                    "요청한 게시글 상태 변경은 허용되지 않습니다."
            );
        }
    }

    private void applyStatusTransition(
            Long currentUserId,
            AdoptionPost post,
            AdoptionPostStatus currentStatus,
            AdoptionPostStatus targetStatus,
            LocalDateTime now
    ) {
        if (currentStatus == AdoptionPostStatus.DRAFT && targetStatus == AdoptionPostStatus.OPEN) {
            validatePublishEligibilityForExistingPost(currentUserId, post);
            post.setStatus(AdoptionPostStatus.OPEN);
            if (post.getPublishedAt() == null) {
                post.setPublishedAt(now);
            }
            post.setClosedAt(null);
            return;
        }

        if (currentStatus == AdoptionPostStatus.RESERVED && targetStatus == AdoptionPostStatus.OPEN) {
            post.setStatus(AdoptionPostStatus.OPEN);
            if (post.getPublishedAt() == null) {
                post.setPublishedAt(now);
            }
            post.setClosedAt(null);
            return;
        }

        if (targetStatus == AdoptionPostStatus.RESERVED) {
            post.setStatus(AdoptionPostStatus.RESERVED);
            post.setClosedAt(null);
            return;
        }

        if (targetStatus == AdoptionPostStatus.COMPLETED) {
            post.setStatus(AdoptionPostStatus.COMPLETED);
            post.setClosedAt(now);
            markDogAdopted(post.getDogId());
            return;
        }

        if (targetStatus == AdoptionPostStatus.CLOSED) {
            post.setStatus(AdoptionPostStatus.CLOSED);
            post.setClosedAt(now);
        }
    }

    private void validatePublishEligibilityForExistingPost(Long currentUserId, AdoptionPost post) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."));
        validateUser(currentUser);

        Dog dog = dogRepository.findById(post.getDogId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOG_NOT_FOUND", "강아지를 찾을 수 없습니다."));
        validateDogOwner(currentUser, dog);
        validateDogStatus(dog);
        validateLatestVerificationLog(dog.getId());
        validateNoOtherActivePost(dog.getId(), post.getId());
    }

    private void markDogAdopted(String dogId) {
        Dog dog = dogRepository.findById(dogId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOG_NOT_FOUND", "강아지를 찾을 수 없습니다."));
        dog.setStatus(DogStatus.ADOPTED);
    }

    private int parsePageParameter(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw invalidPageRequest();
        }
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0 || size <= 0 || size > MAX_PUBLIC_PAGE_SIZE) {
            throw invalidPageRequest();
        }
    }

    private ApiException invalidPageRequest() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_PAGE_REQUEST",
                "page must be greater than or equal to 0 and size must be between 1 and 100"
        );
    }

    private PublicPostContext loadPublicPostContext(List<AdoptionPost> posts) {
        Set<String> dogIds = posts.stream()
                .map(AdoptionPost::getDogId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> authorUserIds = posts.stream()
                .map(AdoptionPost::getAuthorUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Dog> dogsById = dogRepository.findAllById(dogIds).stream()
                .collect(Collectors.toMap(Dog::getId, Function.identity()));
        Map<Long, User> authorsById = userRepository.findAllById(authorUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return new PublicPostContext(
                dogsById,
                authorsById,
                loadProfileImageUrlsByDogId(dogIds),
                loadVerificationStatusesByDogId(dogIds)
        );
    }

    private OwnerPostContext loadOwnerPostContext(List<AdoptionPost> posts) {
        Set<String> dogIds = posts.stream()
                .map(AdoptionPost::getDogId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Dog> dogsById = dogRepository.findAllById(dogIds).stream()
                .collect(Collectors.toMap(Dog::getId, Function.identity()));

        return new OwnerPostContext(
                dogsById,
                loadProfileImageUrlsByDogId(dogIds),
                loadVerificationStatusesByDogId(dogIds)
        );
    }

    private Map<String, String> loadProfileImageUrlsByDogId(Set<String> dogIds) {
        Map<String, String> urlsByDogId = new HashMap<>();
        if (dogIds.isEmpty()) {
            return urlsByDogId;
        }

        dogImageRepository.findByDogIdInAndImageTypeOrderByDogIdAscUploadedAtDescIdDesc(dogIds, DogImageType.PROFILE)
                .forEach(image -> urlsByDogId.putIfAbsent(image.getDogId(), toFileUrl(image.getFilePath())));
        return urlsByDogId;
    }

    private Map<String, String> loadVerificationStatusesByDogId(Set<String> dogIds) {
        Map<String, String> statusesByDogId = new HashMap<>();
        if (dogIds.isEmpty()) {
            return statusesByDogId;
        }

        verificationLogRepository.findByDogIdInOrderByDogIdAscCreatedAtDescIdDesc(dogIds)
                .forEach(log -> statusesByDogId.putIfAbsent(log.getDogId(), toPublicVerificationStatus(log.getResult())));
        return statusesByDogId;
    }

    private AdoptionPostListItemResponse toListItemResponse(AdoptionPost post, PublicPostContext context) {
        Dog dog = requiredDog(post, context);
        User author = requiredAuthor(post, context);

        return new AdoptionPostListItemResponse(
                post.getId(),
                dog.getId(),
                post.getTitle(),
                post.getStatus().name(),
                dog.getName(),
                dog.getBreed(),
                dog.getGender() == null ? null : dog.getGender().name(),
                dog.getBirthDate(),
                context.profileImageUrlsByDogId().get(dog.getId()),
                context.verificationStatusesByDogId().getOrDefault(dog.getId(), "PENDING"),
                author.getDisplayName(),
                author.getRegion(),
                post.getPublishedAt(),
                post.getCreatedAt()
        );
    }

    private AdoptionPostOwnerListItemResponse toOwnerListItemResponse(AdoptionPost post, OwnerPostContext context) {
        Dog dog = requiredDog(post, context);

        return new AdoptionPostOwnerListItemResponse(
                post.getId(),
                dog.getId(),
                post.getTitle(),
                post.getStatus().name(),
                dog.getName(),
                dog.getBreed(),
                dog.getGender() == null ? null : dog.getGender().name(),
                dog.getBirthDate(),
                context.profileImageUrlsByDogId().get(dog.getId()),
                context.verificationStatusesByDogId().getOrDefault(dog.getId(), "PENDING"),
                post.getPublishedAt(),
                post.getClosedAt(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    private AdoptionPostStatusUpdateResponse toStatusUpdateResponse(AdoptionPost post) {
        return new AdoptionPostStatusUpdateResponse(
                post.getId(),
                post.getDogId(),
                post.getTitle(),
                post.getContent(),
                post.getStatus().name(),
                post.getPublishedAt(),
                post.getClosedAt(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    private AdoptionPostDetailResponse toDetailResponse(AdoptionPost post, PublicPostContext context) {
        Dog dog = requiredDog(post, context);
        User author = requiredAuthor(post, context);

        return new AdoptionPostDetailResponse(
                post.getId(),
                dog.getId(),
                post.getTitle(),
                post.getContent(),
                post.getStatus().name(),
                dog.getName(),
                dog.getBreed(),
                dog.getGender() == null ? null : dog.getGender().name(),
                dog.getBirthDate(),
                dog.getDescription(),
                context.profileImageUrlsByDogId().get(dog.getId()),
                context.verificationStatusesByDogId().getOrDefault(dog.getId(), "PENDING"),
                author.getDisplayName(),
                author.getContactPhone(),
                author.getRegion(),
                post.getPublishedAt(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    private Dog requiredDog(AdoptionPost post, PublicPostContext context) {
        Dog dog = context.dogsById().get(post.getDogId());
        if (dog == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "POST_DATA_INTEGRITY_ERROR", "Post dog data is missing.");
        }
        return dog;
    }

    private Dog requiredDog(AdoptionPost post, OwnerPostContext context) {
        Dog dog = context.dogsById().get(post.getDogId());
        if (dog == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "POST_DATA_INTEGRITY_ERROR", "Post dog data is missing.");
        }
        return dog;
    }

    private User requiredAuthor(AdoptionPost post, PublicPostContext context) {
        User author = context.authorsById().get(post.getAuthorUserId());
        if (author == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "POST_DATA_INTEGRITY_ERROR", "Post author data is missing.");
        }
        return author;
    }

    private String toFileUrl(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }

        String normalized = filePath.trim().replace('\\', '/');
        if (normalized.startsWith("/files/")) {
            return normalized;
        }
        if (normalized.startsWith("files/")) {
            return "/" + normalized;
        }
        if (normalized.startsWith("/")) {
            return "/files" + normalized;
        }
        return "/files/" + normalized;
    }

    private String toPublicVerificationStatus(VerificationResult result) {
        if (result == null) {
            return "PENDING";
        }
        return switch (result) {
            case PASSED -> "VERIFIED";
            case DUPLICATE_SUSPECTED -> "DUPLICATE_SUSPECTED";
            case PENDING -> "PENDING";
            case EMBED_FAILED, QDRANT_SEARCH_FAILED, QDRANT_UPSERT_FAILED -> "FAILED";
        };
    }

    private record PublicPostContext(
            Map<String, Dog> dogsById,
            Map<Long, User> authorsById,
            Map<String, String> profileImageUrlsByDogId,
            Map<String, String> verificationStatusesByDogId
    ) {
    }

    private record OwnerPostContext(
            Map<String, Dog> dogsById,
            Map<String, String> profileImageUrlsByDogId,
            Map<String, String> verificationStatusesByDogId
    ) {
    }
}
