package com.gymplan.exercise.domain.entity

import com.gymplan.exercise.domain.vo.Difficulty
import com.gymplan.exercise.domain.vo.Equipment
import com.gymplan.exercise.domain.vo.MuscleGroup
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.Instant

/**
 * exercises 테이블 JPA 엔티티.
 *
 * 스키마 출처: docs/database/mysql-schema.md
 *
 * ```sql
 * CREATE TABLE exercises (
 *   id           BIGINT PRIMARY KEY AUTO_INCREMENT,
 *   name         VARCHAR(100) NOT NULL,
 *   name_en      VARCHAR(100),
 *   muscle_group ENUM('CHEST','BACK','SHOULDERS','ARMS','LEGS','CORE','CARDIO'),
 *   equipment    ENUM('BARBELL','DUMBBELL','MACHINE','CABLE','BODYWEIGHT','BAND'),
 *   difficulty   ENUM('BEGINNER','INTERMEDIATE','ADVANCED'),
 *   description  TEXT,
 *   video_url    VARCHAR(500),
 *   is_custom    BOOLEAN DEFAULT FALSE,
 *   created_by   BIGINT,
 *   created_at   DATETIME DEFAULT NOW(),
 *   FULLTEXT INDEX ft_name (name, name_en)
 * );
 * ```
 */
@Entity
@Table(name = "exercises")
class Exercise(
    name: String,
    muscleGroup: MuscleGroup,
    equipment: Equipment,
    difficulty: Difficulty,
    nameEn: String? = null,
    description: String? = null,
    videoUrl: String? = null,
    isCustom: Boolean = false,
    createdBy: Long? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
        protected set

    @Column(name = "name", nullable = false, length = 100)
    var name: String = name
        protected set

    @Column(name = "name_en", length = 100)
    var nameEn: String? = nameEn
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "muscle_group", nullable = false)
    var muscleGroup: MuscleGroup = muscleGroup
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "equipment", nullable = false)
    var equipment: Equipment = equipment
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false)
    var difficulty: Difficulty = difficulty
        protected set

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = description
        protected set

    @Column(name = "video_url", length = 500)
    var videoUrl: String? = videoUrl
        protected set

    @Column(name = "is_custom", nullable = false)
    var isCustom: Boolean = isCustom
        protected set

    @Column(name = "created_by")
    var createdBy: Long? = createdBy
        protected set

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    @PrePersist
    fun onCreate() {
        createdAt = Instant.now()
    }

    override fun toString(): String = "Exercise(id=$id, name=$name, muscleGroup=$muscleGroup, equipment=$equipment)"
}
