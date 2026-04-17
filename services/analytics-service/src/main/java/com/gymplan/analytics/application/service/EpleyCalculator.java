package com.gymplan.analytics.application.service;

/**
 * Epley 1RM 추정 공식: estimated1RM = weight × (1 + reps / 30.0)
 *
 * 참조: O'Conner et al., 1989
 * 명세: docs/specs/analytics-service.md §1RM 추정 공식
 */
public final class EpleyCalculator {

    private static final int MAX_RELIABLE_REPS = 30;

    private EpleyCalculator() {}

    public static double calculate(double weightKg, int reps) {
        double raw = weightKg * (1.0 + reps / 30.0);
        return Math.round(raw * 10.0) / 10.0;
    }

    /**
     * reps ≤ 30 이면 신뢰 가능한 추정값.
     * reps > 30 이면 공식의 신뢰도가 급격히 낮아짐.
     */
    public static boolean isReliable(int reps) {
        return reps <= MAX_RELIABLE_REPS;
    }
}
