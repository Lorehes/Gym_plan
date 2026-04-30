package com.gymplan.notification.application.service

import com.gymplan.notification.application.dto.UpdateNotificationSettingsRequest
import com.gymplan.notification.domain.model.NotificationSettings
import com.gymplan.notification.infrastructure.redis.NotificationSettingsRedisRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class NotificationSettingsServiceTest {
    @Mock
    private lateinit var settingsRepository: NotificationSettingsRedisRepository

    @InjectMocks
    private lateinit var settingsService: NotificationSettingsService

    // ─────── TC-05 관련: 설정 비활성화 시 응답 ───────

    @Test
    fun `TC-05 workoutCompleteAlert false 설정 조회`() {
        // Given
        val userId = 1L
        `when`(settingsRepository.findByUserId(userId)).thenReturn(
            NotificationSettings(userId = userId, workoutCompleteAlert = false),
        )

        // When
        val result = settingsService.getSettings(userId)

        // Then
        assertThat(result.workoutCompleteAlert).isFalse()
        assertThat(result.restTimerEnabled).isTrue()
        assertThat(result.pushEnabled).isTrue()
    }

    @Test
    fun `설정 업데이트 후 저장 검증`() {
        // Given
        val userId = 1L
        val current = NotificationSettings(userId = userId)
        `when`(settingsRepository.findByUserId(userId)).thenReturn(current)

        val request =
            UpdateNotificationSettingsRequest(
                restTimerEnabled = true,
                workoutCompleteAlert = false,
                pushEnabled = true,
            )

        // When
        val result = settingsService.updateSettings(userId, request)

        // Then
        assertThat(result.workoutCompleteAlert).isFalse()
        verify(settingsRepository).save(
            NotificationSettings(
                userId = userId,
                restTimerEnabled = true,
                workoutCompleteAlert = false,
                pushEnabled = true,
            ),
        )
    }

    @Test
    fun `Redis에 설정 없으면 기본값(all true) 반환`() {
        // Given
        val userId = 99L
        `when`(settingsRepository.findByUserId(userId)).thenReturn(
            NotificationSettings(userId = userId),
        )

        // When
        val result = settingsService.getSettings(userId)

        // Then
        assertThat(result.restTimerEnabled).isTrue()
        assertThat(result.workoutCompleteAlert).isTrue()
        assertThat(result.pushEnabled).isTrue()
    }
}
