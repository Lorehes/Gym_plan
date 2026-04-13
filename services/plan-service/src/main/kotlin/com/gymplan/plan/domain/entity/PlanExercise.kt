package com.gymplan.plan.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * plan_exercises 테이블 JPA 엔티티.
 *
 * 스키마 출처: docs/database/mysql-schema.md
 * 명세 출처:   docs/specs/plan-service.md §5
 *
 * exerciseId: exercise-catalog 서비스의 종목 ID. DB FK 없음 (서비스 간 경계).
 * exerciseName / muscleGroup: 비정규화 저장 — 클라이언트가 exercise-catalog에서 선택 후 전달.
 *                              plan-service는 exercise-catalog를 HTTP 호출하지 않는다.
 */
@Entity
@Table(name = "plan_exercises")
class PlanExercise(
    plan: WorkoutPlan,
    exerciseId: Long,
    exerciseName: String,
    muscleGroup: String,
    orderIndex: Int,
    targetSets: Int = 3,
    targetReps: Int = 10,
    targetWeight: BigDecimal? = null,
    restSeconds: Int = 90,
    notes: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    var plan: WorkoutPlan = plan
        protected set

    @Column(name = "exercise_id", nullable = false)
    var exerciseId: Long = exerciseId
        protected set

    @Column(name = "exercise_name", nullable = false, length = 100)
    var exerciseName: String = exerciseName
        protected set

    @Column(name = "muscle_group", nullable = false, length = 50)
    var muscleGroup: String = muscleGroup
        protected set

    @Column(name = "order_index", nullable = false)
    var orderIndex: Int = orderIndex
        protected set

    @Column(name = "target_sets", nullable = false)
    var targetSets: Int = targetSets
        protected set

    @Column(name = "target_reps", nullable = false)
    var targetReps: Int = targetReps
        protected set

    @Column(name = "target_weight", precision = 5, scale = 2)
    var targetWeight: BigDecimal? = targetWeight
        protected set

    @Column(name = "rest_seconds", nullable = false)
    var restSeconds: Int = restSeconds
        protected set

    @Column(name = "notes", length = 255)
    var notes: String? = notes
        protected set

    fun update(
        exerciseName: String?,
        muscleGroup: String?,
        targetSets: Int?,
        targetReps: Int?,
        targetWeight: BigDecimal?,
        restSeconds: Int?,
        notes: String?,
    ) {
        exerciseName?.let { this.exerciseName = it }
        muscleGroup?.let { this.muscleGroup = it }
        targetSets?.let { this.targetSets = it }
        targetReps?.let { this.targetReps = it }
        this.targetWeight = targetWeight   // null 전달 시 목표 중량 초기화 허용
        restSeconds?.let { this.restSeconds = it }
        this.notes = notes                 // null 전달 시 노트 초기화 허용
    }

    fun updateOrderIndex(newIndex: Int) {
        this.orderIndex = newIndex
    }
}
