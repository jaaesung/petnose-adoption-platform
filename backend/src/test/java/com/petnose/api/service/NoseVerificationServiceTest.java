package com.petnose.api.service;

import com.petnose.api.client.EmbedClient;
import com.petnose.api.client.QdrantDogVectorClient;
import com.petnose.api.domain.entity.NoseVerificationAttempt;
import com.petnose.api.domain.entity.User;
import com.petnose.api.domain.enums.EmbeddingStatus;
import com.petnose.api.domain.enums.VerificationResult;
import com.petnose.api.dto.nose.NoseVerificationRequest;
import com.petnose.api.dto.nose.NoseVerificationResponse;
import com.petnose.api.dto.registration.QdrantSearchResult;
import com.petnose.api.repository.NoseVerificationAttemptRepository;
import com.petnose.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoseVerificationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private NoseVerificationAttemptRepository noseVerificationAttemptRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private EmbedClient embedClient;
    @Mock
    private QdrantDogVectorClient qdrantDogVectorClient;

    private final Map<Long, NoseVerificationAttempt> attempts = new HashMap<>();
    private final AtomicLong attemptIds = new AtomicLong(100);

    private NoseVerificationService service;

    @BeforeEach
    void setUp() {
        service = new NoseVerificationService(
                userRepository,
                noseVerificationAttemptRepository,
                fileStorageService,
                embedClient,
                qdrantDogVectorClient,
                new NoseVerificationPolicy(0.70),
                transactionTemplate()
        );
        ReflectionTestUtils.setField(service, "expectedVectorDimension", 2048);

        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setPasswordHash("hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        when(noseVerificationAttemptRepository.save(any(NoseVerificationAttempt.class))).thenAnswer(invocation -> {
            NoseVerificationAttempt attempt = invocation.getArgument(0);
            if (attempt.getId() == null) {
                attempt.setId(attemptIds.getAndIncrement());
            }
            attempts.put(attempt.getId(), attempt);
            return attempt;
        });

        when(fileStorageService.storeNoseVerificationImage(anyString(), any()))
                .thenReturn(storedFile("nose-verifications/attempt-1/nose/nose.jpg"));
        when(fileStorageService.toPublicUrl(anyString())).thenAnswer(invocation -> "/files/" + invocation.getArgument(0));
    }

    @Test
    void verifyStoresPassedAttemptWithoutCreatingDogOrVectorPoint() {
        Instant before = Instant.now();
        List<Double> vector = List.of(0.1, 0.2, 0.3);
        when(embedClient.embed(any(byte[].class), anyString(), anyString()))
                .thenReturn(new EmbedClient.EmbedResponse(vector, 2048, "dog-nose-identification2:s101_224"));
        when(qdrantDogVectorClient.search(vector))
                .thenReturn(List.of(new QdrantSearchResult("other-point", "other-dog", 0.12345, "Jindo", "dogs/other/nose.jpg")));

        NoseVerificationResponse response = service.verify(request());

        NoseVerificationAttempt attempt = onlyAttempt();
        assertThat(attempt.getRequestedByUserId()).isEqualTo(1L);
        assertThat(attempt.getNoseImagePath()).isEqualTo("nose-verifications/attempt-1/nose/nose.jpg");
        assertThat(attempt.getResult()).isEqualTo(VerificationResult.PASSED);
        assertThat(attempt.getSimilarityScore()).isEqualByComparingTo(new BigDecimal("0.12345"));
        assertThat(attempt.getCandidateDogId()).isEqualTo("other-dog");
        assertThat(attempt.getConsumedAt()).isNull();
        assertThat(attempt.getConsumedByPostId()).isNull();
        assertThat(Duration.between(before, attempt.getExpiresAt())).isGreaterThan(Duration.ofHours(23));

        assertThat(response.noseVerificationId()).isEqualTo(attempt.getId());
        assertThat(response.registrationAllowed()).isTrue();
        assertThat(response.verificationStatus()).isEqualTo("VERIFIED");
        assertThat(response.embeddingStatus()).isEqualTo(EmbeddingStatus.COMPLETED.name());
        assertThat(response.expiresAt()).isEqualTo(attempt.getExpiresAt());
        assertThat(response.noseImageUrl()).isEqualTo("/files/nose-verifications/attempt-1/nose/nose.jpg");
        assertThat(response.topMatch()).isNull();

        verify(qdrantDogVectorClient).search(vector);
        verify(qdrantDogVectorClient, never()).upsert(anyString(), anyList(), anyMap());
    }

    @Test
    void verifyStoresDuplicateSuspectedAttemptAndSkipsVectorUpsert() {
        List<Double> vector = List.of(0.4, 0.5, 0.6);
        when(embedClient.embed(any(byte[].class), anyString(), anyString()))
                .thenReturn(new EmbedClient.EmbedResponse(vector, 2048, "dog-nose-identification2:s101_224"));
        when(qdrantDogVectorClient.search(vector))
                .thenReturn(List.of(new QdrantSearchResult("candidate-point", "candidate-dog", 0.98765, "Maltese", "dogs/candidate/nose.jpg")));

        NoseVerificationResponse response = service.verify(request());

        NoseVerificationAttempt attempt = onlyAttempt();
        assertThat(attempt.getResult()).isEqualTo(VerificationResult.DUPLICATE_SUSPECTED);
        assertThat(attempt.getSimilarityScore()).isEqualByComparingTo(new BigDecimal("0.98765"));
        assertThat(attempt.getCandidateDogId()).isEqualTo("candidate-dog");
        assertThat(attempt.getConsumedAt()).isNull();

        assertThat(response.noseVerificationId()).isEqualTo(attempt.getId());
        assertThat(response.registrationAllowed()).isFalse();
        assertThat(response.verificationStatus()).isEqualTo("DUPLICATE_SUSPECTED");
        assertThat(response.topMatch().dogId()).isEqualTo("candidate-dog");
        assertThat(response.topMatch().similarityScore()).isEqualTo(0.98765);
        assertThat(response.topMatch().breed()).isEqualTo("Maltese");

        verify(qdrantDogVectorClient, never()).upsert(anyString(), anyList(), anyMap());
    }

    private NoseVerificationAttempt onlyAttempt() {
        assertThat(attempts).hasSize(1);
        return attempts.values().iterator().next();
    }

    private NoseVerificationRequest request() {
        return new NoseVerificationRequest(1L, multipart("nose_image", "nose.jpg"));
    }

    private static MockMultipartFile multipart(String name, String filename) {
        return new MockMultipartFile(name, filename, "image/jpeg", new byte[]{1, 2, 3});
    }

    private static FileStorageService.StoredFile storedFile(String relativePath) {
        return new FileStorageService.StoredFile(
                relativePath,
                "image/jpeg",
                3L,
                "hash",
                "image.jpg",
                new byte[]{1, 2, 3}
        );
    }

    private static TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });
    }
}
