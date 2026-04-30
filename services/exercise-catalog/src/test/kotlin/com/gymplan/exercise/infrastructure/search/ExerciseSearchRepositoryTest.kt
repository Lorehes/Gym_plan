package com.gymplan.exercise.infrastructure.search

import com.gymplan.exercise.domain.vo.Equipment
import com.gymplan.exercise.domain.vo.MuscleGroup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.SearchHit
import org.springframework.data.elasticsearch.core.SearchHits

class ExerciseSearchRepositoryTest {
    private lateinit var elasticsearchOperations: ElasticsearchOperations
    private lateinit var searchRepository: ExerciseSearchRepository

    @BeforeEach
    fun setUp() {
        elasticsearchOperations = mock()
        searchRepository = ExerciseSearchRepository(elasticsearchOperations)
    }

    @Test
    @DisplayName("TC-EC-001: 키워드 검색 — 결과 매핑 및 페이징 정보 확인")
    fun search_withKeyword_returnsPagedResults() {
        val doc =
            ExerciseDocument(
                exerciseId = 10,
                name = "벤치프레스",
                nameEn = "Bench Press",
                muscleGroup = "CHEST",
                equipment = "BARBELL",
                difficulty = "INTERMEDIATE",
            )
        stubSearchHits(listOf(doc), totalHits = 1)

        val result =
            searchRepository.search(
                query = "벤치",
                muscle = null,
                equipment = null,
                page = 0,
                size = 20,
            )

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].exerciseId).isEqualTo(10L)
        assertThat(result.content[0].name).isEqualTo("벤치프레스")
        assertThat(result.content[0].muscleGroup).isEqualTo(MuscleGroup.CHEST)
        assertThat(result.totalElements).isEqualTo(1L)
        assertThat(result.page).isEqualTo(0)
        assertThat(result.size).isEqualTo(20)
    }

    @Test
    @DisplayName("TC-EC-003: 부위 필터 검색 — muscle 파라미터 전달")
    fun search_withMuscleFilter_returnsFilteredResults() {
        val doc =
            ExerciseDocument(
                exerciseId = 20,
                name = "인클라인 벤치프레스",
                nameEn = "Incline Bench Press",
                muscleGroup = "CHEST",
                equipment = "BARBELL",
                difficulty = "INTERMEDIATE",
            )
        stubSearchHits(listOf(doc), totalHits = 1)

        val result =
            searchRepository.search(
                query = null,
                muscle = MuscleGroup.CHEST,
                equipment = null,
                page = 0,
                size = 20,
            )

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].muscleGroup).isEqualTo(MuscleGroup.CHEST)
    }

    @Test
    @DisplayName("TC-EC-004: 복합 필터 검색 — muscle + equipment")
    fun search_withCompositeFilter() {
        stubSearchHits(emptyList(), totalHits = 0)

        val result =
            searchRepository.search(
                query = null,
                muscle = MuscleGroup.CHEST,
                equipment = Equipment.DUMBBELL,
                page = 0,
                size = 20,
            )

        assertThat(result.content).isEmpty()
        assertThat(result.totalElements).isEqualTo(0L)
    }

    @Test
    @DisplayName("TC-EC-005: 검색 결과 없음")
    fun search_noResults() {
        stubSearchHits(emptyList(), totalHits = 0)

        val result =
            searchRepository.search(
                query = "zzzxxx123",
                muscle = null,
                equipment = null,
                page = 0,
                size = 20,
            )

        assertThat(result.content).isEmpty()
        assertThat(result.totalElements).isEqualTo(0L)
        assertThat(result.last).isTrue()
    }

    @Test
    @DisplayName("TC-EC-006: 페이징 — totalPages, last 계산 검증")
    fun search_pagination() {
        val docs =
            (1L..10L).map { id ->
                ExerciseDocument(
                    exerciseId = id,
                    name = "종목$id",
                    nameEn = "Exercise$id",
                    muscleGroup = "CHEST",
                    equipment = "BARBELL",
                    difficulty = "BEGINNER",
                )
            }
        stubSearchHits(docs, totalHits = 25)

        val result =
            searchRepository.search(
                query = null,
                muscle = null,
                equipment = null,
                page = 0,
                size = 10,
            )

        assertThat(result.content).hasSize(10)
        assertThat(result.totalElements).isEqualTo(25L)
        assertThat(result.totalPages).isEqualTo(3)
        assertThat(result.last).isFalse()
    }

    @Test
    @DisplayName("모든 파라미터 null 시 전체 결과 반환 (match_all)")
    fun search_allNull_returnsAll() {
        val doc =
            ExerciseDocument(
                exerciseId = 1,
                name = "스쿼트",
                nameEn = "Squat",
                muscleGroup = "LEGS",
                equipment = "BARBELL",
                difficulty = "BEGINNER",
            )
        stubSearchHits(listOf(doc), totalHits = 1)

        val result =
            searchRepository.search(
                query = null,
                muscle = null,
                equipment = null,
                page = 0,
                size = 20,
            )

        assertThat(result.content).hasSize(1)
    }

    @Suppress("UNCHECKED_CAST")
    private fun stubSearchHits(
        docs: List<ExerciseDocument>,
        totalHits: Long,
    ) {
        val searchHits: SearchHits<ExerciseDocument> = mock()
        val hits =
            docs.map { doc ->
                val hit: SearchHit<ExerciseDocument> = mock()
                whenever(hit.content).thenReturn(doc)
                hit
            }
        whenever(searchHits.searchHits).thenReturn(hits)
        whenever(searchHits.totalHits).thenReturn(totalHits)
        whenever(
            elasticsearchOperations.search(
                any<NativeQuery>(),
                any<Class<ExerciseDocument>>(),
            ),
        ).thenReturn(searchHits)
    }
}
