package com.gymplan.exercise.integration

import com.gymplan.exercise.domain.entity.Exercise
import com.gymplan.exercise.domain.repository.ExerciseRepository
import com.gymplan.exercise.domain.vo.Difficulty
import com.gymplan.exercise.domain.vo.Equipment
import com.gymplan.exercise.domain.vo.MuscleGroup
import com.gymplan.exercise.infrastructure.search.ExerciseDocument
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * exercise-catalog 통합 테스트.
 *
 * 실제 MySQL + Elasticsearch 를 Testcontainers 로 기동하여
 * API → Service → Repository → DB 전 계층을 검증한다.
 *
 * 커버리지:
 *   TC-EC-001: 키워드 검색 — 한글
 *   TC-EC-002: 키워드 검색 — 영문
 *   TC-EC-003: 부위 필터 검색
 *   TC-EC-004: 복합 필터 검색
 *   TC-EC-005: 검색 결과 없음
 *   TC-EC-006: 페이징
 *   TC-EC-007: 유효하지 않은 필터 값 → 400 (NEW)
 *   TC-EC-008: 종목 상세 조회 — 성공
 *   TC-EC-009: 종목 상세 조회 — 존재하지 않는 종목
 *   TC-EC-010: 커스텀 종목 생성 — MySQL 저장 + ES 색인 검증
 *   TC-EC-011: 커스텀 종목 생성 — 필수 필드 누락
 *   TC-EC-014: size=101 초과 → 400 (NEW)
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExerciseCatalogIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var exerciseRepository: ExerciseRepository

    @Autowired
    private lateinit var elasticsearchOperations: ElasticsearchOperations

    @BeforeEach
    fun setUp() {
        // 매 테스트 전 MySQL + ES 초기화
        exerciseRepository.deleteAll()
        try {
            elasticsearchOperations.indexOps(ExerciseDocument::class.java).delete()
            elasticsearchOperations.indexOps(ExerciseDocument::class.java).createWithMapping()
        } catch (_: Exception) {
            // 첫 실행 시 인덱스가 없어도 무방
        }
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private fun saveAndIndex(
        name: String,
        nameEn: String? = null,
        muscleGroup: MuscleGroup = MuscleGroup.CHEST,
        equipment: Equipment = Equipment.BARBELL,
        difficulty: Difficulty = Difficulty.INTERMEDIATE,
        description: String? = null,
        videoUrl: String? = null,
        isCustom: Boolean = false,
        createdBy: Long? = null,
    ): Exercise {
        val saved =
            exerciseRepository.save(
                Exercise(
                    name = name,
                    nameEn = nameEn,
                    muscleGroup = muscleGroup,
                    equipment = equipment,
                    difficulty = difficulty,
                    description = description,
                    videoUrl = videoUrl,
                    isCustom = isCustom,
                    createdBy = createdBy,
                ),
            )
        // SyncTaskExecutorConfig 로 인해 동기 실행되지만, 직접 색인하여 확실히 보장
        elasticsearchOperations.save(ExerciseDocument.from(saved))
        elasticsearchOperations.indexOps(ExerciseDocument::class.java).refresh()
        return saved
    }

    // ── TC-EC-001: 키워드 검색 — 한글 ──────────────────────────────────────────
    //
    // 참고: nori analyzer 는 "벤치프레스" → ["벤치", "프레스"] 로 형태소 분리하므로
    //       "벤치" 검색으로 2개를 찾는다. 테스트 환경(standard analyzer) 에서는
    //       단어 경계가 없어 "벤치프레스" 를 토큰 1개로 인덱싱하므로, 전체 단어인
    //       "벤치프레스" 로 쿼리해야 2개 문서가 모두 히트된다.

    @Test
    @DisplayName("TC-EC-001: 한글 키워드 검색 — '벤치프레스' 포함 종목 반환")
    fun search_korean_keyword() {
        saveAndIndex("벤치프레스", nameEn = "Bench Press", muscleGroup = MuscleGroup.CHEST)
        saveAndIndex("인클라인 벤치프레스", nameEn = "Incline Bench Press", muscleGroup = MuscleGroup.CHEST)
        saveAndIndex("스쿼트", nameEn = "Squat", muscleGroup = MuscleGroup.LEGS)

        mockMvc.get("/api/v1/exercises") {
            header("X-User-Id", "1")
            param("q", "벤치프레스") // 전체 토큰으로 검색 (standard analyzer 호환)
        }.andExpect {
            status { isOk() }
            jsonPath("$.success") { value(true) }
            jsonPath("$.data.totalElements") { value(2) }
            jsonPath("$.data.content[0].name") { isString() }
        }
    }

    // ── TC-EC-002: 키워드 검색 — 영문 ──────────────────────────────────────────

    @Test
    @DisplayName("TC-EC-002: 영문 키워드 검색 — 'bench' 포함 종목 반환")
    fun search_english_keyword() {
        saveAndIndex("벤치프레스", nameEn = "Bench Press", muscleGroup = MuscleGroup.CHEST)
        saveAndIndex("스쿼트", nameEn = "Squat", muscleGroup = MuscleGroup.LEGS)

        mockMvc.get("/api/v1/exercises") {
            header("X-User-Id", "1")
            param("q", "bench")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalElements") { value(1) }
            jsonPath("$.data.content[0].nameEn") { value("Bench Press") }
        }
    }

    // ── TC-EC-003: 부위 필터 검색 ─────────────────────────────────────────────

    @Test
    @DisplayName("TC-EC-003: muscle=CHEST 필터 — CHEST 종목만 반환")
    fun search_muscleFilter() {
        saveAndIndex("벤치프레스", muscleGroup = MuscleGroup.CHEST)
        saveAndIndex("스쿼트", muscleGroup = MuscleGroup.LEGS)
        saveAndIndex("딥스", muscleGroup = MuscleGroup.CHEST)

        mockMvc.get("/api/v1/exercises") {
            header("X-User-Id", "1")
            param("muscle", "CHEST")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalElements") { value(2) }
            jsonPath("$.data.content[0].muscleGroup") { value("CHEST") }
            jsonPath("$.data.content[1].muscleGroup") { value("CHEST") }
        }
    }

    // ── TC-EC-004: 복합 필터 검색 ─────────────────────────────────────────────

    @Test
    @DisplayName("TC-EC-004: muscle=CHEST + equipment=BARBELL 복합 필터")
    fun search_combinedFilter() {
        saveAndIndex("벤치프레스", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL)
        saveAndIndex("케이블 플라이", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.CABLE)
        saveAndIndex("스쿼트", muscleGroup = MuscleGroup.LEGS, equipment = Equipment.BARBELL)

        mockMvc.get("/api/v1/exercises") {
            header("X-User-Id", "1")
            param("muscle", "CHEST")
            param("equipment", "BARBELL")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalElements") { value(1) }
            jsonPath("$.data.content[0].name") { value("벤치프레스") }
        }
    }

    // ── TC-EC-005: 검색 결과 없음 ─────────────────────────────────────────────

    @Test
    @DisplayName("TC-EC-005: 존재하지 않는 키워드 — 빈 배열 반환")
    fun search_noResults() {
        saveAndIndex("벤치프레스")

        mockMvc.get("/api/v1/exercises") {
            header("X-User-Id", "1")
            param("q", "zzzxxx123")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content") { isEmpty() }
            jsonPath("$.data.totalElements") { value(0) }
        }
    }

    // ── TC-EC-006: 페이징 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("TC-EC-006: page=0&size=10, page=1&size=10 — 중복 없이 페이징")
    fun search_paging() {
        repeat(25) { i ->
            saveAndIndex(
                name = "종목$i",
                nameEn = "Exercise$i",
                muscleGroup = MuscleGroup.CHEST,
            )
        }

        mockMvc.get("/api/v1/exercises") {
            header("X-User-Id", "1")
            param("page", "0")
            param("size", "10")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content.length()") { value(10) }
            jsonPath("$.data.page") { value(0) }
            jsonPath("$.data.totalElements") { value(25) }
        }

        mockMvc.get("/api/v1/exercises") {
            header("X-User-Id", "1")
            param("page", "1")
            param("size", "10")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content.length()") { value(10) }
            jsonPath("$.data.page") { value(1) }
        }
    }

    // ── TC-EC-007: 유효하지 않은 필터 값 → 400 ─────────────────────────────────

    @Test
    @DisplayName("TC-EC-007: muscle=INVALID_VALUE → 400 VALIDATION_FAILED")
    fun search_invalidMuscleValue() {
        mockMvc.get("/api/v1/exercises") {
            header("X-User-Id", "1")
            param("muscle", "INVALID_VALUE")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.error.code") { value("VALIDATION_FAILED") }
        }
    }

    // ── TC-EC-008: 종목 상세 조회 — 성공 ─────────────────────────────────────

    @Test
    @DisplayName("TC-EC-008: 종목 상세 조회 — description, videoUrl 포함")
    fun getById_success() {
        val exercise =
            saveAndIndex(
                name = "벤치프레스",
                nameEn = "Bench Press",
                muscleGroup = MuscleGroup.CHEST,
                equipment = Equipment.BARBELL,
                description = "가슴 운동의 기본",
                videoUrl = "https://cdn.gymplan.io/videos/bench-press.mp4",
            )

        mockMvc.get("/api/v1/exercises/${exercise.id}") {
            header("X-User-Id", "1")
        }.andExpect {
            status { isOk() }
            jsonPath("$.success") { value(true) }
            jsonPath("$.data.exerciseId") { value(exercise.id) }
            jsonPath("$.data.name") { value("벤치프레스") }
            jsonPath("$.data.description") { value("가슴 운동의 기본") }
            jsonPath("$.data.videoUrl") { value("https://cdn.gymplan.io/videos/bench-press.mp4") }
            jsonPath("$.data.isCustom") { value(false) }
        }
    }

    // ── TC-EC-009: 종목 상세 조회 — 존재하지 않는 종목 ────────────────────────

    @Test
    @DisplayName("TC-EC-009: 존재하지 않는 종목 → 404 EXERCISE_NOT_FOUND")
    fun getById_notFound() {
        mockMvc.get("/api/v1/exercises/99999") {
            header("X-User-Id", "1")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("EXERCISE_NOT_FOUND") }
            // ErrorCode.EXERCISE_NOT_FOUND.defaultMessage = "운동 종목을 찾을 수 없습니다."
            jsonPath("$.error.message") { value("운동 종목을 찾을 수 없습니다.") }
        }
    }

    // ── TC-EC-010: 커스텀 종목 생성 ───────────────────────────────────────────

    @Test
    @DisplayName("TC-EC-010: 커스텀 종목 생성 — MySQL 저장 + ES 색인 확인")
    fun create_success() {
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

        mockMvc.post("/api/v1/exercises") {
            header("X-User-Id", "42")
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isCreated() }
            jsonPath("$.success") { value(true) }
            jsonPath("$.data.name") { value("하프 스쿼트") }
            jsonPath("$.data.isCustom") { value(true) }
            jsonPath("$.data.createdBy") { value(42) }
        }

        // MySQL 저장 확인
        val saved = exerciseRepository.findAll().first { it.name == "하프 스쿼트" }
        assert(saved.isCustom)
        assert(saved.createdBy == 42L)

        // Elasticsearch 색인 확인 — SyncTaskExecutor 로 인해 동기 실행됨
        elasticsearchOperations.indexOps(ExerciseDocument::class.java).refresh()
        mockMvc.get("/api/v1/exercises") {
            header("X-User-Id", "42")
            param("q", "하프 스쿼트") // 전체 단어 쿼리 (standard analyzer 호환)
        }.andExpect {
            status { isOk() }
            // contentAsString 은 ISO-8859-1 로 읽을 수 있으므로 jsonPath 로 검증
            jsonPath("$.data.totalElements") { value(1) }
            jsonPath("$.data.content[0].name") { value("하프 스쿼트") }
        }
    }

    // ── TC-EC-011: 커스텀 종목 생성 — 필수 필드 누락 ─────────────────────────

    @Test
    @DisplayName("TC-EC-011: name 누락 → 400 VALIDATION_FAILED")
    fun create_missingName() {
        val body =
            """
            {
              "muscleGroup": "LEGS",
              "equipment": "BARBELL",
              "difficulty": "BEGINNER"
            }
            """.trimIndent()

        mockMvc.post("/api/v1/exercises") {
            header("X-User-Id", "42")
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("VALIDATION_FAILED") }
            jsonPath("$.error.details.name") { isString() }
        }
    }

    // ── TC-EC-014: size=101 초과 → 400 ────────────────────────────────────────

    @Test
    @DisplayName("TC-EC-014: size=101 (@Max(50) 초과) → 400 VALIDATION_FAILED")
    fun search_sizeTooLarge() {
        mockMvc.get("/api/v1/exercises") {
            header("X-User-Id", "1")
            param("size", "101")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.error.code") { value("VALIDATION_FAILED") }
        }
    }
}
