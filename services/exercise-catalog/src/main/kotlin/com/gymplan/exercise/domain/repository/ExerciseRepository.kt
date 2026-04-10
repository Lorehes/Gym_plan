package com.gymplan.exercise.domain.repository

import com.gymplan.exercise.domain.entity.Exercise
import org.springframework.data.jpa.repository.JpaRepository

interface ExerciseRepository : JpaRepository<Exercise, Long>
