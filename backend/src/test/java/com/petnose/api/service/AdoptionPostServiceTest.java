package com.petnose.api.service;

import com.petnose.api.domain.entity.AdoptionPost;
import com.petnose.api.domain.entity.Dog;
import com.petnose.api.domain.entity.User;
import com.petnose.api.domain.entity.VerificationLog;
import com.petnose.api.domain.enums.AdoptionPostStatus;
import com.petnose.api.domain.enums.DogStatus;
import com.petnose.api.domain.enums.VerificationResult;
import com.petnose.api.dto.adoption.AdoptionPostCreateRequest;
import com.petnose.api.exception.ApiException;
import com.petnose.api.repository.AdoptionPostRepository;
import com.petnose.api.repository.DogImageRepository;
import com.petnose.api.repository.DogRepository;
import com.petnose.api.repository.UserRepository;
import com.petnose.api.repository.VerificationLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @InjectMocks
    private AdoptionPostService adoptionPostService;

    @Test
    void createOpenPostSavesAuthorDogAndPublishedAt() {
        User user = user();
        Dog dog = dog(user, DogStatus.REGISTERED);
        VerificationLog log = verificationLog(VerificationResult.PASSED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(dogRepository.findById("dog-1")).thenReturn(Optional.of(dog));
        when(verificationLogRepository.findFirstByDogIdOrderByCreatedAtDescIdDesc("dog-1")).thenReturn(Optional.of(log));
        when(adoptionPostRepository.existsByDogIdAndStatusIn(eq("dog-1"), any(Collection.class))).thenReturn(false);
        when(adoptionPostRepository.save(any(AdoptionPost.class))).thenAnswer(invocation -> {
            AdoptionPost post = invocation.getArgument(0);
            post.setId(10L);
            post.prePersist();
            return post;
        });

        var response = adoptionPostService.create(1L, new AdoptionPostCreateRequest("dog-1", "제목", "내용", "OPEN"));

        assertThat(response.postId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.publishedAt()).isNotNull();

        ArgumentCaptor<AdoptionPost> captor = ArgumentCaptor.forClass(AdoptionPost.class);
        verify(adoptionPostRepository).save(captor.capture());
        assertThat(captor.getValue().getAuthorUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getDogId()).isEqualTo("dog-1");
    }

    @Test
    void latestDuplicateVerificationResultBlocksCreate() {
        User user = user();
        Dog dog = dog(user, DogStatus.REGISTERED);
        VerificationLog log = verificationLog(VerificationResult.DUPLICATE_SUSPECTED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(dogRepository.findById("dog-1")).thenReturn(Optional.of(dog));
        when(verificationLogRepository.findFirstByDogIdOrderByCreatedAtDescIdDesc("dog-1")).thenReturn(Optional.of(log));

        assertThatThrownBy(() -> adoptionPostService.create(1L, new AdoptionPostCreateRequest("dog-1", "제목", "내용", "OPEN")))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo("DUPLICATE_DOG_CANNOT_BE_POSTED");
    }

    @Test
    void activeExistingPostBlocksCreate() {
        User user = user();
        Dog dog = dog(user, DogStatus.REGISTERED);
        VerificationLog log = verificationLog(VerificationResult.PASSED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(dogRepository.findById("dog-1")).thenReturn(Optional.of(dog));
        when(verificationLogRepository.findFirstByDogIdOrderByCreatedAtDescIdDesc("dog-1")).thenReturn(Optional.of(log));
        when(adoptionPostRepository.existsByDogIdAndStatusIn(eq("dog-1"), any(Collection.class))).thenReturn(true);

        assertThatThrownBy(() -> adoptionPostService.create(1L, new AdoptionPostCreateRequest("dog-1", "제목", "내용", "OPEN")))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo("ACTIVE_POST_ALREADY_EXISTS");
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setDisplayName("초코 보호자");
        user.setActive(true);
        return user;
    }

    private Dog dog(User owner, DogStatus status) {
        Dog dog = new Dog();
        dog.setId("dog-1");
        dog.setOwnerUserId(owner.getId());
        dog.setStatus(status);
        return dog;
    }

    private VerificationLog verificationLog(VerificationResult result) {
        VerificationLog log = new VerificationLog();
        log.setResult(result);
        return log;
    }
}
