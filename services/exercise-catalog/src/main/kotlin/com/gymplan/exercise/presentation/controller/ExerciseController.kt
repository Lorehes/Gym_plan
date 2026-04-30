package com.gymplan.exercise.presentation.controller

import com.gymplan.common.dto.ApiResponse
import com.gymplan.common.dto.PageResponse
import com.gymplan.common.security.CurrentUserId
import com.gymplan.exercise.application.dto.CreateExerciseRequest
import com.gymplan.exercise.application.dto.ExerciseDetailResponse
import com.gymplan.exercise.application.dto.ExerciseSummaryResponse
import com.gymplan.exercise.application.service.ExerciseSearchService
import com.gymplan.exercise.application.service.ExerciseService
import com.gymplan.exercise.domain.vo.Equipment
import com.gymplan.exercise.domain.vo.MuscleGroup
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 운동 종목 REST 엔드포인트.
 *
 * 참조: docs/api/exercise-catalog.md, docs/specs/exercise-catalog.md
 *
 * - GET  /api/v1/exercises              → 종목 검색 (ES)
 * - GET  /api/v1/exercises/{exerciseId} → 종목 상세 조회
 * - POST /api/v1/exercises              → 커스텀 종목 생성
 * - GET  /api/v1/exercises/muscle-groups → 부위 목록 (인증 불필요)
 */
@RestController
@RequestMapping("/api/v1/exercises")
@Validated
class ExerciseController(
    private val exerciseService: ExerciseService,
    private val exerciseSearchService: ExerciseSearchService,
) {
    @GetMapping
    fun search(
        @CurrentUserId userId: Long,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) muscle: MuscleGroup?,
        @RequestParam(required = false) equipment: Equipment?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(50) size: Int,
    ): ApiResponse<PageResponse<ExerciseSummaryResponse>> =
        ApiResponse.success(
            exerciseSearchService.search(
                query = q,
                muscle = muscle,
                equipment = equipment,
                page = page,
                size = size,
            ),
        )

    @GetMapping("/{exerciseId}")
    fun getById(
        @CurrentUserId userId: Long,
        @PathVariable exerciseId: Long,
    ): ApiResponse<ExerciseDetailResponse> = ApiResponse.success(exerciseService.getById(exerciseId))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentUserId userId: Long,
        @Valid @RequestBody request: CreateExerciseRequest,
    ): ApiResponse<ExerciseDetailResponse> = ApiResponse.success(exerciseService.create(request, userId))

    @GetMapping("/muscle-groups")
    fun getMuscleGroups(): ApiResponse<List<MuscleGroup>> = ApiResponse.success(MuscleGroup.entries.toList())
}
