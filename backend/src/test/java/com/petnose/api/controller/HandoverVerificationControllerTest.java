package com.petnose.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petnose.api.client.EmbedClient;
import com.petnose.api.client.QdrantDogVectorClient;
import com.petnose.api.domain.entity.AdoptionPost;
import com.petnose.api.domain.entity.Dog;
import com.petnose.api.domain.entity.User;
import com.petnose.api.domain.enums.AdoptionPostStatus;
import com.petnose.api.domain.enums.DogGender;
import com.petnose.api.domain.enums.DogStatus;
import com.petnose.api.domain.enums.UserRole;
import com.petnose.api.dto.registration.QdrantSearchResult;
import com.petnose.api.repository.AdoptionPostRepository;
import com.petnose.api.repository.DogImageRepository;
import com.petnose.api.repository.DogRepository;
import com.petnose.api.repository.UserRepository;
import com.petnose.api.repository.VerificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HandoverVerificationControllerTest {

    private static final String JWT_SECRET = "test-petnose-jwt-secret-change-me-32bytes";
    private static final String MODEL = "dog-nose-identification2:s101_224";

    private static final Set<String> RESPONSE_FIELDS = Set.of(
            "post_id",
            "expected_dog_id",
            "matched",
            "decision",
            "similarity_score",
            "threshold",
            "ambiguous_threshold",
            "top_match_is_expected",
            "model",
            "dimension",
            "message"
    );

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DogRepository dogRepository;

    @Autowired
    private DogImageRepository dogImageRepository;

    @Autowired
    private VerificationLogRepository verificationLogRepository;

    @Autowired
    private AdoptionPostRepository adoptionPostRepository;

    @MockBean
    private EmbedClient embedClient;

    @MockBean
    private QdrantDogVectorClient qdrantDogVectorClient;

    private int sequence;

    @BeforeEach
    void setUp() {
        adoptionPostRepository.deleteAll();
        verificationLogRepository.deleteAll();
        dogImageRepository.deleteAll();
        dogRepository.deleteAll();
        userRepository.deleteAll();
        reset(embedClient, qdrantDogVectorClient);
        sequence = 0;
    }

    @Test
    void handoverVerificationRequiresAuthorization() throws Exception {
        handoverRequest(null, 1L)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"));

        verifyNoInteractions(embedClient, qdrantDogVectorClient);
    }

    @Test
    void handoverVerificationReturnsPostNotFoundWhenPostIsMissing() throws Exception {
        User user = saveUser(true);

        handoverRequest(tokenFor(user), 999999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("POST_NOT_FOUND"))
                .andExpect(jsonPath("$.details").value(nullValue()));

        verifyNoInteractions(embedClient, qdrantDogVectorClient);
    }

    @Test
    void handoverVerificationRejectsDraftPost() throws Exception {
        User user = saveUser(true);
        Dog dog = saveDog(user, DogStatus.REGISTERED);
        AdoptionPost post = savePost(user, dog, AdoptionPostStatus.DRAFT);

        handoverRequest(tokenFor(user), post.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("POST_NOT_VERIFIABLE"));

        verifyNoInteractions(embedClient, qdrantDogVectorClient);
    }

    @Test
    void handoverVerificationReturnsMatchedWhenExpectedDogIsTopResultAboveThreshold() throws Exception {
        User user = saveUser(true);
        Dog dog = saveDog(user, DogStatus.REGISTERED);
        AdoptionPost post = savePost(user, dog, AdoptionPostStatus.OPEN);
        mockEmbedding();
        when(qdrantDogVectorClient.search(anyList(), eq(5)))
                .thenReturn(List.of(new QdrantSearchResult(dog.getId(), dog.getId(), 0.98231, "Maltese", "secret/nose.jpg")));

        MvcResult result = handoverRequest(tokenFor(user), post.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.post_id").value(post.getId()))
                .andExpect(jsonPath("$.expected_dog_id").value(dog.getId()))
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.decision").value("MATCHED"))
                .andExpect(jsonPath("$.similarity_score").value(0.98231))
                .andExpect(jsonPath("$.threshold").value(0.92))
                .andExpect(jsonPath("$.ambiguous_threshold").value(0.88))
                .andExpect(jsonPath("$.top_match_is_expected").value(true))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.dimension").value(128))
                .andExpect(jsonPath("$.message").value("분양글에 등록된 강아지와 일치합니다."))
                .andReturn();

        assertResponseIsSafe(result);
        assertNoPersistenceOrStatusMutation(post, dog);
        verify(qdrantDogVectorClient).search(anyList(), eq(5));
    }

    @Test
    void handoverVerificationReturnsNotMatchedWithoutExposingAnotherDogId() throws Exception {
        User user = saveUser(true);
        Dog dog = saveDog(user, DogStatus.REGISTERED);
        AdoptionPost post = savePost(user, dog, AdoptionPostStatus.RESERVED);
        String otherDogId = UUID.randomUUID().toString();
        mockEmbedding();
        when(qdrantDogVectorClient.search(anyList(), eq(5)))
                .thenReturn(List.of(new QdrantSearchResult("other-point", otherDogId, 0.42103, "Jindo", "other/nose.jpg")));

        MvcResult result = handoverRequest(tokenFor(user), post.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.post_id").value(post.getId()))
                .andExpect(jsonPath("$.expected_dog_id").value(dog.getId()))
                .andExpect(jsonPath("$.matched").value(false))
                .andExpect(jsonPath("$.decision").value("NOT_MATCHED"))
                .andExpect(jsonPath("$.similarity_score").value(0.42103))
                .andExpect(jsonPath("$.top_match_is_expected").value(false))
                .andExpect(jsonPath("$.message").value("분양글에 등록된 강아지와 일치하지 않습니다. 거래 전 확인이 필요합니다."))
                .andReturn();

        assertResponseIsSafe(result);
        assertThat(responseBody(result)).doesNotContain(otherDogId, "other-point", "other/nose.jpg");
        assertNoPersistenceOrStatusMutation(post, dog);
    }

    @Test
    void handoverVerificationReturnsAmbiguousWhenExpectedDogScoreIsBetweenThresholds() throws Exception {
        User user = saveUser(true);
        Dog dog = saveDog(user, DogStatus.REGISTERED);
        AdoptionPost post = savePost(user, dog, AdoptionPostStatus.OPEN);
        mockEmbedding();
        when(qdrantDogVectorClient.search(anyList(), eq(5)))
                .thenReturn(List.of(new QdrantSearchResult(dog.getId(), dog.getId(), 0.90112, "Maltese", null)));

        MvcResult result = handoverRequest(tokenFor(user), post.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(false))
                .andExpect(jsonPath("$.decision").value("AMBIGUOUS"))
                .andExpect(jsonPath("$.similarity_score").value(0.90112))
                .andExpect(jsonPath("$.top_match_is_expected").value(true))
                .andExpect(jsonPath("$.message").value("유사도가 기준에 근접하지만 확정하기 어렵습니다. 비문 이미지를 다시 촬영해주세요."))
                .andReturn();

        assertResponseIsSafe(result);
        assertNoPersistenceOrStatusMutation(post, dog);
    }

    @Test
    void handoverVerificationReturnsNoMatchCandidateWhenQdrantReturnsNoCandidates() throws Exception {
        User user = saveUser(true);
        Dog dog = saveDog(user, DogStatus.REGISTERED);
        AdoptionPost post = savePost(user, dog, AdoptionPostStatus.OPEN);
        mockEmbedding();
        when(qdrantDogVectorClient.search(anyList(), eq(5))).thenReturn(List.of());

        MvcResult result = handoverRequest(tokenFor(user), post.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(false))
                .andExpect(jsonPath("$.decision").value("NO_MATCH_CANDIDATE"))
                .andExpect(jsonPath("$.similarity_score").value(nullValue()))
                .andExpect(jsonPath("$.top_match_is_expected").value(false))
                .andExpect(jsonPath("$.message").value("일치 후보를 찾지 못했습니다. 비문 이미지를 다시 촬영해주세요."))
                .andReturn();

        assertResponseIsSafe(result);
        assertNoPersistenceOrStatusMutation(post, dog);
    }

    private void mockEmbedding() {
        when(embedClient.embed(any(byte[].class), anyString(), anyString()))
                .thenReturn(new EmbedClient.EmbedResponse(List.of(0.1, 0.2, 0.3), 128, MODEL));
    }

    private ResultActions handoverRequest(String token, Long postId) throws Exception {
        var request = multipart("/api/adoption-posts/{post_id}/handover-verifications", postId)
                .file(noseImage());
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private MockMultipartFile noseImage() {
        return new MockMultipartFile(
                "nose_image",
                "handover.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
    }

    private User saveUser(boolean active) {
        User user = new User();
        user.setEmail("handover-%d@example.com".formatted(++sequence));
        user.setPasswordHash("hash");
        user.setRole(UserRole.USER);
        user.setDisplayName("Handover User");
        user.setContactPhone("010-0000-0000");
        user.setRegion("Seoul");
        user.setActive(active);
        return userRepository.saveAndFlush(user);
    }

    private Dog saveDog(User owner, DogStatus status) {
        Dog dog = new Dog();
        dog.setId(UUID.randomUUID().toString());
        dog.setOwnerUserId(owner.getId());
        dog.setName("Choco");
        dog.setBreed("Maltese");
        dog.setGender(DogGender.MALE);
        dog.setBirthDate(LocalDate.of(2023, 1, 1));
        dog.setDescription("Handover verification test dog.");
        dog.setStatus(status);
        return dogRepository.saveAndFlush(dog);
    }

    private AdoptionPost savePost(User author, Dog dog, AdoptionPostStatus status) {
        AdoptionPost post = new AdoptionPost();
        post.setAuthorUserId(author.getId());
        post.setDogId(dog.getId());
        post.setTitle("Handover post");
        post.setContent("Handover verification content.");
        post.setStatus(status);
        if (status == AdoptionPostStatus.OPEN || status == AdoptionPostStatus.RESERVED || status == AdoptionPostStatus.COMPLETED) {
            post.setPublishedAt(LocalDateTime.now());
        }
        if (status == AdoptionPostStatus.CLOSED || status == AdoptionPostStatus.COMPLETED) {
            post.setClosedAt(LocalDateTime.now());
        }
        return adoptionPostRepository.saveAndFlush(post);
    }

    private void assertResponseIsSafe(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(responseBody(result));
        assertThat(fieldNames(body)).containsExactlyInAnyOrderElementsOf(RESPONSE_FIELDS);
        assertThat(responseBody(result)).doesNotContain(
                "nose_image_url",
                "top_matched_dog_id",
                "author_user_id",
                "postId",
                "expectedDogId",
                "similarityScore",
                "topMatchIsExpected"
        );
    }

    private void assertNoPersistenceOrStatusMutation(AdoptionPost post, Dog dog) {
        assertThat(dogImageRepository.count()).isZero();
        assertThat(verificationLogRepository.count()).isZero();
        assertThat(adoptionPostRepository.findById(post.getId()).orElseThrow().getStatus()).isEqualTo(post.getStatus());
        assertThat(dogRepository.findById(dog.getId()).orElseThrow().getStatus()).isEqualTo(DogStatus.REGISTERED);
    }

    private String tokenFor(User user) throws Exception {
        String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
        String payload = encodeJson(Map.of(
                "sub", user.getId().toString(),
                "exp", Instant.now().plusSeconds(3600).getEpochSecond()
        ));
        String signingInput = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        return signingInput + "." + signature;
    }

    private String encodeJson(Map<String, Object> value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private String responseBody(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
