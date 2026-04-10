package com.gymplan.exercise.presentation.controller

import com.gymplan.common.dto.PageResponse
import com.gymplan.common.exception.GlobalExceptionHandler
import com.gymplan.exercise.application.dto.ExerciseDetailResponse
import com.gymplan.exercise.application.dto.ExerciseSummaryResponse
import com.gymplan.exercise.application.service.ExerciseSearchService
import com.gymplan.exercise.application.service.ExerciseService
import com.gymplan.exercise.domain.vo.Difficulty
import com.gymplan.exercise.domain.vo.Equipment
import com.gymplan.exercise.domain.vo.MuscleGroup
import com.gymplan.exercise.presentation.security.CurrentUserIdArgumentResolver
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ExerciseControllerValidationTest {
    private val exerciseService: ExerciseService = org.mockito.kotlin.mock()
    private val exerciseSearchService: ExerciseSearchService = org.mockito.kotlin.mock()
    private val mockMvc: MockMvc =
        MockMvcBuilders.standaloneSetup(
            ExerciseController(exerciseService, exerciseSearchService),
        )
            .setCustomArgumentResolvers(CurrentUserIdArgumentResolver())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

    // ─────────────── TC-EC-001: 종목 검색 ───────────────

    @Test
    @DisplayName("TC-EC-001: 키워드 검색 성공")
    fun search_success() {
        val pageResponse =
            PageResponse(
                content =
                    listOf(
                        ExerciseSummaryResponse(
                            exerciseId = 10,
                            name = "벤치프레스",
                            nameEn = "Bench Press",
                            muscleGroup = MuscleGroup.CHEST,
                            equipment = Equipment.BARBELL,
                            difficulty = Difficulty.INTERMEDIATE,
                        ),
                    ),
                page = 0,
                size = 20,
                totalElements = 1,
                totalPages = 1,
                last = true,
            )
        whenever(
            exerciseSearchService.search(
                query = anyOrNull(),
                muscle = anyOrNull(),
                equipment = anyOrNull(),
                page = any(),
                size = any(),
            ),
        ).thenReturn(pageResponse)

        mockMvc
            .perform(get("/api/v1/exercises").param("q", "벤치"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].name").value("벤치프레스"))
            .andExpect(jsonPath("$.data.totalElements").value(1))
    }

    // ─────────────── TC-EC-008: 종목 상세 조회 ───────────────

    @Test
    @DisplayName("TC-EC-008: 종목 상세 조회 성공")
    fun getById_success() {
        whenever(exerciseService.getById(10L)).thenReturn(
            ExerciseDetailResponse(
                exerciseId = 10,
                name = "벤치프레스",
                nameEn = "Bench Press",
                muscleGroup = MuscleGroup.CHEST,
                equipment = Equipment.BARBELL,
                difficulty = Difficulty.INTERMEDIATE,
                description = "가슴 운동의 기본",
                videoUrl = "https://cdn.gymplan.io/videos/bench-press.mp4",
                isCustom = false,
                createdBy = null,
            ),
        )

        mockMvc
            .perform(get("/api/v1/exercises/10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.exerciseId").value(10))
            .andExpect(jsonPath("$.data.description").value("가슴 운동의 기본"))
    }

    // ─────────────── TC-EC-010: 커스텀 종목 생성 ───────────────

    @Test
    @DisplayName("TC-EC-010: 커스텀 종목 생성 성공")
    fun create_success() {
        whenever(exerciseService.create(any(), eq(42L))).thenReturn(
            ExerciseDetailResponse(
                exerciseId = 301,
                name = "하프 스쿼트",
                nameEn = "Half Squat",
                muscleGroup = MuscleGroup.LEGS,
                equipment = Equipment.BARBELL,
                difficulty = Difficulty.BEGINNER,
                description = null,
                videoUrl = null,
                isCustom = true,
                createdBy = 42,
            ),
        )

        val body =
            """
            {
              "name": "하프 스쿼트",
              "nameEn": "Half Squat",
              "muscleGroup": "LEGS",
              "equipment": "BARBELL",
              "difficulty": "BEGINNER"
            }
            """.trimIndent()

        mockMvc
            .perform(
                post("/api/v1/exercises")
                    .header("X-User-Id", "42")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.isCustom").value(true))
            .andExpect(jsonPath("$.data.createdBy").value(42))
    }

    // ─────────────── TC-EC-011: 필수 필드 누락 ───────────────

    @Test
    @DisplayName("TC-EC-011: name 누락 시 VALIDATION_FAILED")
    fun create_missingName() {
        val body =
            """
            {
              "muscleGroup": "LEGS",
              "equipment": "BARBELL",
              "difficulty": "BEGINNER"
            }
            """.trimIndent()

        mockMvc
            .perform(
                post("/api/v1/exercises")
                    .header("X-User-Id", "42")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
    }

    // ─────────────── TC-EC-012: 미인증 상태에서 생성 ───────────────

    @Test
    @DisplayName("TC-EC-012: X-User-Id 없이 POST 시 401")
    fun create_unauthorized() {
        val body =
            """
            {
              "name": "하프 스쿼트",
              "muscleGroup": "LEGS",
              "equipment": "BARBELL",
              "difficulty": "BEGINNER"
            }
            """.trimIndent()

        mockMvc
            .perform(
                post("/api/v1/exercises")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"))
    }

    // ─────────────── TC-EC-013: 부위 목록 조회 ───────────────

    @Test
    @DisplayName("TC-EC-013: 부위 목록 조회 — 인증 불필요, 7개 항목")
    fun getMuscleGroups_success() {
        mockMvc
            .perform(get("/api/v1/exercises/muscle-groups"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(7))
            .andExpect(jsonPath("$.data[0]").value("CHEST"))
    }

    // ─────────────── X-User-Id 보안 검증 ───────────────

    @Test
    @DisplayName("X-User-Id 가 0이면 401")
    fun create_zeroUserId() {
        val body =
            """
            {
              "name": "테스트",
              "muscleGroup": "LEGS",
              "equipment": "BARBELL",
              "difficulty": "BEGINNER"
            }
            """.trimIndent()

        mockMvc
            .perform(
                post("/api/v1/exercises")
                    .header("X-User-Id", "0")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            )
            .andExpect(status().isUnauthorized)
    }
}
