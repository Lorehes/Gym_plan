package com.gymplan.plan.domain.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

/**
 * workout_plans 테이블 JPA 엔티티.
 *
 * 스키마 출처: docs/database/mysql-schema.md
 * 명세 출처:   docs/specs/plan-service.md
 *
 * dayOfWeek: 0=월, 1=화, 2=수, 3=목, 4=금, 5=토, 6=일, null=무요일
 * 삭제는 isActive=false 로 soft delete 처리.
 */
@Entity
@Table(name = "workout_plans")
class WorkoutPlan(
    userId: Long,
    name: String,
    description: String? = null,
    dayOfWeek: Int? = null,
    isTemplate: Boolean = false,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
        protected set

    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "name", nullable = false, length = 100)
    var name: String = name
        protected set

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = description
        protected set

    @Column(name = "day_of_week")
    var dayOfWeek: Int? = dayOfWeek
        protected set

    @Column(name = "is_template", nullable = false)
    var isTemplate: Boolean = isTemplate
        protected set

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
        protected set

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set

    @OneToMany(mappedBy = "plan", cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    var exercises: MutableList<PlanExercise> = mutableListOf()
        protected set

    @PrePersist
    fun onCreate() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }

    fun update(name: String?, description: String?, dayOfWeek: Int?) {
        name?.let { this.name = it }
        this.description = description   // null 전달 시 description 초기화 허용
        dayOfWeek?.let { this.dayOfWeek = it }   // null → 기존 dayOfWeek 유지 (PATCH 의미론)
    }

    fun softDelete() {
        this.isActive = false
    }

    fun addExercise(exercise: PlanExercise) {
        exercises.add(exercise)
    }
}
