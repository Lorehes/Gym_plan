package com.gymplan.exercise.infrastructure.search

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import com.gymplan.common.dto.PageResponse
import com.gymplan.exercise.application.dto.ExerciseSummaryResponse
import com.gymplan.exercise.domain.vo.Difficulty
import com.gymplan.exercise.domain.vo.Equipment
import com.gymplan.exercise.domain.vo.MuscleGroup
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.SearchHits
import org.springframework.stereotype.Repository

/**
 * Elasticsearch 기반 종목 검색 리포지토리.
 *
 * 검색 전략 (docs/architecture/services.md):
 *   - 한국어: nori analyzer 를 통한 name 필드 match 검색
 *   - 영어:   standard analyzer 를 통한 nameEn 필드 match 검색
 *   - multi_match 로 name, nameEn 동시 검색
 *   - 필터: muscleGroup, equipment (term query)
 */
@Repository
class ExerciseSearchRepository(
    private val elasticsearchOperations: ElasticsearchOperations,
) {
    fun search(
        query: String?,
        muscle: MuscleGroup?,
        equipment: Equipment?,
        page: Int,
        size: Int,
    ): PageResponse<ExerciseSummaryResponse> {
        val nativeQuery =
            NativeQuery.builder()
                .withQuery(buildQuery(query, muscle, equipment))
                .withPageable(org.springframework.data.domain.PageRequest.of(page, size))
                .build()

        val searchHits: SearchHits<ExerciseDocument> =
            elasticsearchOperations.search(nativeQuery, ExerciseDocument::class.java)

        val content =
            searchHits.searchHits.map { hit ->
                val doc = hit.content
                ExerciseSummaryResponse(
                    exerciseId = doc.exerciseId,
                    name = doc.name,
                    nameEn = doc.nameEn,
                    muscleGroup = MuscleGroup.valueOf(doc.muscleGroup),
                    equipment = Equipment.valueOf(doc.equipment),
                    difficulty = Difficulty.valueOf(doc.difficulty),
                )
            }

        val totalElements = searchHits.totalHits
        val totalPages = if (size > 0) ((totalElements + size - 1) / size).toInt() else 0

        return PageResponse(
            content = content,
            page = page,
            size = size,
            totalElements = totalElements,
            totalPages = totalPages,
            last = (page + 1) >= totalPages,
        )
    }

    private fun buildQuery(
        query: String?,
        muscle: MuscleGroup?,
        equipment: Equipment?,
    ): Query {
        val boolBuilder = BoolQuery.Builder()

        if (!query.isNullOrBlank()) {
            boolBuilder.must { m ->
                m.multiMatch { mm ->
                    mm.query(query)
                        .fields("name", "nameEn")
                }
            }
        }

        muscle?.let { mg ->
            boolBuilder.filter { f ->
                f.term { t ->
                    t.field("muscleGroup").value(mg.name)
                }
            }
        }

        equipment?.let { eq ->
            boolBuilder.filter { f ->
                f.term { t ->
                    t.field("equipment").value(eq.name)
                }
            }
        }

        return Query.Builder().bool(boolBuilder.build()).build()
    }
}
