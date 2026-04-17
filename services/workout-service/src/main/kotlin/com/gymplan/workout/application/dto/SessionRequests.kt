package com.gymplan.workout.application.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** POST /api/v1/sessions */
data class StartSessionRequest(
    val planId: Long? = null,
    val planName: String? = null,
)

/** POST /api/v1/sessions/{sessionId}/sets */
data class LogSetRequest(
    @field:NotBlank val exerciseId: String,
    @field:NotBlank val exerciseName: String,
    @field:NotBlank
    @field:Pattern(regexp = "^(CHEST|BACK|SHOULDERS|ARMS|LEGS|CORE|CARDIO)$")
    val muscleGroup: String,
    @field:Min(1) val setNo: Int,
    @field:Min(1) val reps: Int,
    @field:DecimalMin(value = "0.1") val weightKg: Double,
    val isSuccess: Boolean = true,
)

/** PUT /api/v1/sessions/{sessionId}/sets/{setNo}/{exerciseId} */
data class UpdateSetRequest(
    @field:Min(1) val reps: Int? = null,
    @field:DecimalMin(value = "0.1") val weightKg: Double? = null,
    val isSuccess: Boolean? = null,
)

/** POST /api/v1/sessions/{sessionId}/complete */
data class CompleteSessionRequest(
    @field:Size(max = 500) val notes: String? = null,
)
