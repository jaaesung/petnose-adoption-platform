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
import com.petnose.api.domain.enums.DogImageType;
import com.petnose.api.domain.enums.DogStatus;
import com.petnose.api.domain.enums.VerificationResult;
import com.petnose.api.dto.adoption.AdoptionPostCreateRequest;
import com.petnose.api.exception.ApiException;
import com.petnose.api.repository.AdoptionPostRepository;
import com.petnose.api.repository.DogImageRepository;
import com.petnose.api.repository.DogRepository;
import com.petnose.api.repository.NoseVerificationAttemptRepository;
import com.petnose.api.repository.UserRepository;
import com.petnose.api.repository.VerificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdoptionPostServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DogRepository dogRepository;

    @Mock
    private VerificationLogRepository verificationLogRepository;

    @Mock
    private DogImageRepository dogImageRepository;

    @Mock
    private AdoptionPostRepository adoptionPostRepository;

    @Mock
    private NoseVerificationAttemptRepository noseVerificationAttemptRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private EmbedClient embedClient;

    @Mock
    private QdrantDogVectorClient qdrantDogVectorClient;

    @InjectMocks
    private AdoptionPostService adoptionPostService;

    private final AtomicLong dogImageIds = new AtomicLong(1);
    private final List<DogImage> dogImages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adoptionPostService, "expectedVectorDimension", 128);
    }

    @Test
    void createOpenPostCreatesDogImagesLogPostConsumesAttemptAndUpsertsDogVector() {
        User user = user();
        NoseVerificationAttempt attempt = passedAttempt(user.getId());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(noseVerificationAttemptRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(attempt));
        when(dogRepository.save(any(Dog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dogImageRepository.save(any(DogImage.class))).thenAnswer(invocation -> {
            DogImage image = invocation.getArgument(0);
            image.setId(dogImageIds.getAndIncrement());
            dogImages.add(image);
            return image;
        });
        when(verificationLogRepository.save(any(VerificationLog.class))).thenAnswer(invocation -> {
            VerificationLog log = invocation.getArgument(0);
            log.setId(50L);
            return log;
        });
        when(fileStorageService.storeProfileImage(anyString(), any()))
                .thenReturn(storedFile("dogs/generated/profile/profile.jpg", "image/jpeg", "profile.jpg"));
        when(fileStorageService.readStoredImage("nose-verifications/attempt-100/nose/nose.jpg", "image/jpeg", 3L, "nosehash"))
                .thenReturn(storedFile("nose-verifications/attempt-100/nose/nose.jpg", "image/jpeg", "nose.jpg"));
        when(embedClient.embed(any(byte[].class), anyString(), anyString()))
                .thenReturn(new EmbedClient.EmbedResponse(List.of(0.1, 0.2, 0.3), 128, "dog-nose-identification2:s101_224"));
        when(adoptionPostRepository.save(any(AdoptionPost.class))).thenAnswer(invocation -> {
            AdoptionPost post = invocation.getArgument(0);
            post.setId(10L);
            post.prePersist();
            return post;
        });

        var response = adoptionPostService.create(1L, validRequest("OPEN", profileImage()));

        assertThat(response.postId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.publishedAt()).isNotNull();
        assertThat(response.dogId()).isNotBlank();
        assertThat(attempt.getConsumedAt()).isNotNull();
        assertThat(attempt.getConsumedByPostId()).isEqualTo(10L);

        ArgumentCaptor<Dog> dogCaptor = ArgumentCaptor.forClass(Dog.class);
        verify(dogRepository).save(dogCaptor.capture());
        Dog dog = dogCaptor.getValue();
        assertThat(dog.getOwnerUserId()).isEqualTo(1L);
        assertThat(dog.getName()).isEqualTo("초코");
        assertThat(dog.getBreed()).isEqualTo("Maltese");
        assertThat(dog.getStatus()).isEqualTo(DogStatus.REGISTERED);

        assertThat(dogImages).hasSize(2);
        assertThat(dogImages).extracting(DogImage::getImageType)
                .containsExactly(DogImageType.NOSE, DogImageType.PROFILE);
        assertThat(dogImages.get(0).getFilePath()).isEqualTo("nose-verifications/attempt-100/nose/nose.jpg");
        assertThat(dogImages.get(1).getFilePath()).isEqualTo("dogs/generated/profile/profile.jpg");

        ArgumentCaptor<VerificationLog> logCaptor = ArgumentCaptor.forClass(VerificationLog.class);
        verify(verificationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getDogId()).isEqualTo(dog.getId());
        assertThat(logCaptor.getValue().getDogImageId()).isEqualTo(dogImages.get(0).getId());
        assertThat(logCaptor.getValue().getResult()).isEqualTo(VerificationResult.PASSED);
        assertThat(logCaptor.getValue().getSimilarityScore()).isEqualByComparingTo(new BigDecimal("0.12345"));

        verify(qdrantDogVectorClient).upsert(anyString(), anyList(), anyMap());
        ArgumentCaptor<String> pointIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(qdrantDogVectorClient).upsert(pointIdCaptor.capture(), anyList(), payloadCaptor.capture());
        assertThat(pointIdCaptor.getValue()).isEqualTo(dog.getId());
        assertThat(payloadCaptor.getValue()).containsEntry("dog_id", dog.getId());
    }

    @Test
    void createRequiresProfileImageWithDedicatedErrorCode() {
        assertThatThrownBy(() -> adoptionPostService.create(1L, validRequest("OPEN", null)))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo("PROFILE_IMAGE_REQUIRED");
    }

    @Test
    void duplicateSuspectedAttemptBlocksCreate() {
        User user = user();
        NoseVerificationAttempt attempt = attempt(user.getId(), VerificationResult.DUPLICATE_SUSPECTED, null, Instant.now().plusSeconds(3600));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(noseVerificationAttemptRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> adoptionPostService.create(1L, validRequest("OPEN", profileImage())))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo("DUPLICATE_DOG_CANNOT_BE_POSTED");

        verify(dogRepository, never()).save(any());
        verify(qdrantDogVectorClient, never()).upsert(anyString(), anyList(), anyMap());
    }

    @Test
    void consumedAttemptCannotBeReused() {
        User user = user();
        NoseVerificationAttempt attempt = attempt(user.getId(), VerificationResult.PASSED, Instant.now(), Instant.now().plusSeconds(3600));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(noseVerificationAttemptRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> adoptionPostService.create(1L, validRequest("OPEN", profileImage())))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo("NOSE_VERIFICATION_ALREADY_CONSUMED");
    }

    @Test
    void attemptOwnedByAnotherUserCannotBeUsed() {
        User user = user();
        NoseVerificationAttempt attempt = passedAttempt(999L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(noseVerificationAttemptRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> adoptionPostService.create(1L, validRequest("OPEN", profileImage())))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo("NOSE_VERIFICATION_OWNER_MISMATCH");
    }

    @Test
    void expiredAttemptCannotBeUsed() {
        User user = user();
        NoseVerificationAttempt attempt = attempt(user.getId(), VerificationResult.PASSED, null, Instant.now().minusSeconds(1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(noseVerificationAttemptRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> adoptionPostService.create(1L, validRequest("OPEN", profileImage())))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo("NOSE_VERIFICATION_EXPIRED");
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setDisplayName("초코보호자");
        user.setActive(true);
        return user;
    }

    private NoseVerificationAttempt passedAttempt(Long requestedByUserId) {
        return attempt(requestedByUserId, VerificationResult.PASSED, null, Instant.now().plusSeconds(3600));
    }

    private NoseVerificationAttempt attempt(
            Long requestedByUserId,
            VerificationResult result,
            Instant consumedAt,
            Instant expiresAt
    ) {
        NoseVerificationAttempt attempt = new NoseVerificationAttempt();
        attempt.setId(100L);
        attempt.setRequestedByUserId(requestedByUserId);
        attempt.setNoseImagePath("nose-verifications/attempt-100/nose/nose.jpg");
        attempt.setNoseImageMimeType("image/jpeg");
        attempt.setNoseImageFileSize(3L);
        attempt.setNoseImageSha256("nosehash");
        attempt.setResult(result);
        attempt.setSimilarityScore(new BigDecimal("0.12345"));
        attempt.setCandidateDogId(result == VerificationResult.DUPLICATE_SUSPECTED ? "existing-dog" : null);
        attempt.setModel("dog-nose-identification2:s101_224");
        attempt.setDimension(128);
        attempt.setExpiresAt(expiresAt);
        attempt.setConsumedAt(consumedAt);
        attempt.setConsumedByPostId(consumedAt == null ? null : 999L);
        return attempt;
    }

    private AdoptionPostCreateRequest validRequest(String status, MockMultipartFile profileImage) {
        return new AdoptionPostCreateRequest(
                100L,
                "초코",
                "Maltese",
                "UNKNOWN",
                null,
                "밝은 아이입니다.",
                "제목",
                "내용",
                status,
                profileImage
        );
    }

    private static MockMultipartFile profileImage() {
        return new MockMultipartFile("profile_image", "profile.jpg", "image/jpeg", new byte[]{4, 5, 6});
    }

    private static FileStorageService.StoredFile storedFile(String relativePath, String mimeType, String originalFilename) {
        return new FileStorageService.StoredFile(
                relativePath,
                mimeType,
                3L,
                "storedhash",
                originalFilename,
                new byte[]{1, 2, 3}
        );
    }
}
