package com.gymplan.analytics.presentation.controller;

import com.gymplan.analytics.application.dto.AnalyticsPeriod;
import com.gymplan.analytics.application.dto.FrequencyDayData;
import com.gymplan.analytics.application.dto.PersonalRecordResponse;
import com.gymplan.analytics.application.dto.SummaryResponse;
import com.gymplan.analytics.application.dto.VolumeDataPoint;
import com.gymplan.analytics.application.service.AnalyticsQueryService;
import com.gymplan.common.dto.ApiResponse;
import com.gymplan.common.security.CurrentUserId;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 운동 통계 API.
 *
 * Base URL: /api/v1/analytics
 * 인증: Gateway가 X-User-Id 헤더 주입 → @CurrentUserId로 추출
 * 성능 목표: P95 < 500ms (docs/context/performance-goals.md)
 */
@RestController
@RequestMapping("/api/v1/analytics")
@Validated
public class AnalyticsController {

    private final AnalyticsQueryService queryService;

    public AnalyticsController(AnalyticsQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 주간/월간 운동 요약 통계.
     * GET /api/v1/analytics/summary?period=WEEK|MONTH
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<SummaryResponse>> getSummary(
            @CurrentUserId long userId,
            @RequestParam(defaultValue = "WEEK") AnalyticsPeriod period) {
        SummaryResponse data = queryService.getSummary(String.valueOf(userId), period);
        return ResponseEntity.ok(ApiResponse.Companion.success(data));
    }

    /**
     * 근육군별 볼륨 추이.
     * GET /api/v1/analytics/volume?period=WEEK|MONTH&muscle=CHEST
     */
    @GetMapping("/volume")
    public ResponseEntity<ApiResponse<List<VolumeDataPoint>>> getVolume(
            @CurrentUserId long userId,
            @RequestParam(defaultValue = "WEEK") AnalyticsPeriod period,
            @RequestParam(required = false) String muscle) {
        List<VolumeDataPoint> data = queryService.getVolume(String.valueOf(userId), period, muscle);
        return ResponseEntity.ok(ApiResponse.Companion.success(data));
    }

    /**
     * 월별 운동 빈도 캘린더.
     * GET /api/v1/analytics/frequency?year=2026&month=4
     */
    @GetMapping("/frequency")
    public ResponseEntity<ApiResponse<Map<String, FrequencyDayData>>> getFrequency(
            @CurrentUserId long userId,
            @RequestParam int year,
            @RequestParam @Min(1) @Max(12) int month) {
        Map<String, FrequencyDayData> data = queryService.getFrequency(String.valueOf(userId), year, month);
        return ResponseEntity.ok(ApiResponse.Companion.success(data));
    }

    /**
     * 종목별 개인 기록 (1RM 기준).
     * GET /api/v1/analytics/personal-records
     */
    @GetMapping("/personal-records")
    public ResponseEntity<ApiResponse<List<PersonalRecordResponse>>> getPersonalRecords(
            @CurrentUserId long userId) {
        List<PersonalRecordResponse> data = queryService.getPersonalRecords(String.valueOf(userId));
        return ResponseEntity.ok(ApiResponse.Companion.success(data));
    }
}
