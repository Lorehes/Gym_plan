package com.gymplan.exercise.application.service

import com.gymplan.common.dto.PageResponse
import com.gymplan.exercise.application.dto.ExerciseSummaryResponse
import com.gymplan.exercise.domain.vo.Equipment
import com.gymplan.exercise.domain.vo.MuscleGroup
import com.gymplan.exercise.infrastructure.search.ExerciseSearchRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

/**
 * Elasticsearch 기반 종목 검색 서비스.
 *
 * 검색 전략 (docs/architecture/services.md):
 *   - 한국어: nori analyzer, match_phrase_prefix
 *   - 영어: standard analyzer, multi_match
 *   - 필터: muscle_group, equipment (term query)
 */
@Service
class ExerciseSearchService(
    private val exerciseSearchRepository: ExerciseSearchRepository,
) {
    @Cacheable(
        cacheNames = ["exerciseSearch"],
        key = "#query + ':' + #muscle + ':' + #equipment + ':' + #page + ':' + #size",
    )
    fun search(
        query: String?,
        muscle: MuscleGroup?,
        equipment: Equipment?,
        page: Int,
        size: Int,
    ): PageResponse<ExerciseSummaryResponse> =
        exerciseSearchRepository.search(
            query = query,
            muscle = muscle,
            equipment = equipment,
            page = page,
            size = size,
        )
}
