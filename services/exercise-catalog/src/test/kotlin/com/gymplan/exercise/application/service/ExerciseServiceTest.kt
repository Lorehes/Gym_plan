package com.gymplan.exercise.application.service

import com.gymplan.common.exception.ErrorCode
import com.gymplan.common.exception.NotFoundException
import com.gymplan.exercise.application.dto.CreateExerciseRequest
import com.gymplan.exercise.domain.entity.Exercise
import com.gymplan.exercise.domain.repository.ExerciseRepository
import com.gymplan.exercise.domain.vo.Difficulty
import com.gymplan.exercise.domain.vo.Equipment
import com.gymplan.exercise.domain.vo.MuscleGroup
import com.gymplan.exercise.infrastructure.search.ExerciseIndexer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class ExerciseServiceTest {
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var exerciseIndexer: ExerciseIndexer
    private lateinit var exerciseService: ExerciseService

    @BeforeEach
    fun setUp() {
        exerciseRepository = mock()
        exerciseIndexer = mock()
        exerciseService = ExerciseService(exerciseRepository, exerciseIndexer)
    }

    // ─────────────── TC-EC-008: 종목 상세 조회 성공 ───────────────

    @Test
    @DisplayName("TC-EC-008: 종목 상세 조회 성공")
    fun getById_success() {
        val exercise = buildExercise(10L, "벤치프레스", "Bench Press", MuscleGroup.CHEST, Equipment.BARBELL)
        whenever(exerciseRepository.findById(10L)).thenReturn(Optional.of(exercise))

        val response = exerciseService.getById(10L)

        assertThat(response.exerciseId).isEqualTo(10L)
        assertThat(response.name).isEqualTo("벤치프레스")
        assertThat(response.nameEn).isEqualTo("Bench Press")
        assertThat(response.muscleGroup).isEqualTo(MuscleGroup.CHEST)
        assertThat(response.equipment).isEqualTo(Equipment.BARBELL)
        assertThat(response.difficulty).isEqualTo(Difficulty.INTERMEDIATE)
        assertThat(response.description).isNotNull()
    }

    // ─────────────── TC-EC-009: 존재하지 않는 종목 ───────────────

    @Test
    @DisplayName("TC-EC-009: 존재하지 않는 종목 조회 시 EXERCISE_NOT_FOUND")
    fun getById_notFound() {
        whenever(exerciseRepository.findById(99999L)).thenReturn(Optional.empty())

        assertThatThrownBy { exerciseService.getById(99999L) }
            .isInstanceOf(NotFoundException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.EXERCISE_NOT_FOUND)
    }

    // ─────────────── TC-EC-010: 커스텀 종목 생성 성공 ───────────────

    @Test
    @DisplayName("TC-EC-010: 커스텀 종목 생성 성공 — isCustom=true, createdBy=userId")
    fun create_success() {
        val request =
            CreateExerciseRequest(
                name = "하프 스쿼트",
                nameEn = "Half Squat",
                muscleGroup = MuscleGroup.LEGS,
                equipment = Equipment.BARBELL,
                difficulty = Difficulty.BEGINNER,
                description = "일반 스쿼트의 절반 깊이",
            )

        whenever(exerciseRepository.save(any<Exercise>())).thenAnswer { invocation ->
            val entity = invocation.getArgument<Exercise>(0)
            val field = entity.javaClass.getDeclaredField("id")
            field.isAccessible = true
            field.set(entity, 301L)
            entity
        }

        val response = exerciseService.create(request, userId = 42L)

        assertThat(response.exerciseId).isEqualTo(301L)
        assertThat(response.name).isEqualTo("하프 스쿼트")
        assertThat(response.isCustom).isTrue()
        assertThat(response.createdBy).isEqualTo(42L)
        verify(exerciseIndexer).index(any())
    }

    // ─────────────── 헬퍼 ───────────────

    private fun buildExercise(
        id: Long,
        name: String,
        nameEn: String?,
        muscleGroup: MuscleGroup,
        equipment: Equipment,
    ): Exercise =
        Exercise(
            name = name,
            nameEn = nameEn,
            muscleGroup = muscleGroup,
            equipment = equipment,
            difficulty = Difficulty.INTERMEDIATE,
            description = "테스트 설명",
            videoUrl = "https://cdn.gymplan.io/videos/test.mp4",
        ).apply {
            val field = javaClass.getDeclaredField("id")
            field.isAccessible = true
            field.set(this, id)
        }
}
