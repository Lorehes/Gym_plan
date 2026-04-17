package com.gymplan.analytics.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * TC-005: Epley 1RM 계산 정확성
 * TC-009: reps > 30 신뢰도 플래그
 */
class EpleyCalculatorTest {

    @ParameterizedTest(name = "weight={0}, reps={1} → expected1RM={2}")
    @CsvSource({
            "80.0, 8,  101.3",
            "80.0, 1,   82.7",
            "70.0, 10,  93.3",
            "100.0, 3, 110.0",
    })
    @DisplayName("Epley 공식 계산 — 소수점 1자리 반올림")
    void calculate_epley(double weight, int reps, double expected) {
        double result = EpleyCalculator.calculate(weight, reps);
        assertThat(result).isCloseTo(expected, within(0.05));
    }

    @Test
    @DisplayName("reps=1 일 때 추정 1RM은 weight보다 약간 큼 (공식 적용)")
    void calculate_singleRep() {
        double result = EpleyCalculator.calculate(80.0, 1);
        assertThat(result).isGreaterThan(80.0);
    }

    @Test
    @DisplayName("reps ≤ 30 이면 isReliable=true")
    void isReliable_true() {
        assertThat(EpleyCalculator.isReliable(1)).isTrue();
        assertThat(EpleyCalculator.isReliable(15)).isTrue();
        assertThat(EpleyCalculator.isReliable(30)).isTrue();
    }

    @Test
    @DisplayName("TC-009: reps > 30 이면 isReliable=false")
    void isReliable_false_whenRepsExceedThirty() {
        assertThat(EpleyCalculator.isReliable(31)).isFalse();
        assertThat(EpleyCalculator.isReliable(40)).isFalse();
    }
}
