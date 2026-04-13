package com.gymplan.exercise.infrastructure.search

import com.gymplan.exercise.domain.entity.Exercise
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import org.springframework.data.elasticsearch.annotations.Setting

/**
 * Elasticsearch 인덱스 문서.
 *
 * 매핑 (docs/specs/exercise-catalog.md):
 *   - name:        text (nori analyzer) → 한글 형태소 검색
 *   - nameEn:      text (standard analyzer) → 영문 검색
 *   - muscleGroup: keyword → term 필터
 *   - equipment:   keyword → term 필터
 *   - difficulty:  keyword → term 필터
 */
@Document(indexName = "exercises")
@Setting(settingPath = "/elasticsearch/settings.json")
data class ExerciseDocument(
    @Id
    val exerciseId: Long,
    @Field(type = FieldType.Text, analyzer = "nori_analyzer")
    val name: String,
    @Field(type = FieldType.Text, analyzer = "standard")
    val nameEn: String?,
    @Field(type = FieldType.Keyword)
    val muscleGroup: String,
    @Field(type = FieldType.Keyword)
    val equipment: String,
    @Field(type = FieldType.Keyword)
    val difficulty: String,
) {
    companion object {
        fun from(entity: Exercise): ExerciseDocument =
            ExerciseDocument(
                exerciseId = entity.id!!,
                name = entity.name,
                nameEn = entity.nameEn,
                muscleGroup = entity.muscleGroup.name,
                equipment = entity.equipment.name,
                difficulty = entity.difficulty.name,
            )
    }
}
