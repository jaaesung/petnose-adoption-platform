package com.petnose.api.service;

import com.petnose.api.dto.registration.QdrantSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoseVerificationPolicyTest {

    private final NoseVerificationPolicy policy = new NoseVerificationPolicy(0.70);

    @Test
    void scoreAtThresholdIsDuplicate() {
        NoseVerificationPolicy.VerificationDecision decision = policy.evaluate(List.of(
                result("point-1", "dog-1", 0.70)
        ));

        assertThat(decision.duplicate()).isTrue();
        assertThat(decision.maxScore()).isEqualTo(0.70);
        assertThat(decision.threshold()).isEqualTo(0.70);
    }

    @Test
    void scoreAboveThresholdIsDuplicate() {
        NoseVerificationPolicy.VerificationDecision decision = policy.evaluate(List.of(
                result("point-1", "dog-1", 0.70001)
        ));

        assertThat(decision.duplicate()).isTrue();
        assertThat(decision.maxScore()).isEqualTo(0.70001);
    }

    @Test
    void scoreBelowThresholdIsNotDuplicate() {
        NoseVerificationPolicy.VerificationDecision decision = policy.evaluate(List.of(
                result("point-1", "dog-1", 0.69999)
        ));

        assertThat(decision.duplicate()).isFalse();
        assertThat(decision.maxScore()).isEqualTo(0.69999);
    }

    @Test
    void emptyResultsAreNotDuplicateAndMaxScoreIsZero() {
        NoseVerificationPolicy.VerificationDecision decision = policy.evaluate(List.of());

        assertThat(decision.duplicate()).isFalse();
        assertThat(decision.maxScore()).isZero();
        assertThat(decision.topMatch()).isNull();
    }

    @Test
    void topMatchIsSelectedByHighestScoreNotInputOrder() {
        NoseVerificationPolicy.VerificationDecision decision = policy.evaluate(List.of(
                result("point-low", "dog-low", 0.80),
                result("point-high", "dog-high", 0.91),
                result("point-mid", "dog-mid", 0.85)
        ));

        assertThat(decision.duplicate()).isTrue();
        assertThat(decision.maxScore()).isEqualTo(0.91);
        assertThat(decision.topMatch().dogId()).isEqualTo("dog-high");
    }

    private static QdrantSearchResult result(String pointId, String dogId, double score) {
        return new QdrantSearchResult(pointId, dogId, score, "Jindo", null);
    }
}
