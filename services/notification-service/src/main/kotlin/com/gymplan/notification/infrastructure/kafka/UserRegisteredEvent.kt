package com.gymplan.notification.infrastructure.kafka

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserRegisteredEvent(
    val eventType: String = "USER_REGISTERED",
    val userId: String,
    val email: String,
    val nickname: String,
    val occurredAt: String,
)
