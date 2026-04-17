package com.gymplan.notification.infrastructure.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import jakarta.annotation.PostConstruct
import java.io.FileInputStream

/**
 * Firebase Admin SDK 초기화.
 * FCM 서비스 계정 JSON 경로는 Vault에서 주입 (환경변수 하드코딩 금지).
 */
@Configuration
class FcmConfig(
    @Value("\${fcm.service-account-path}") private val serviceAccountPath: String,
) {
    private val log = LoggerFactory.getLogger(FcmConfig::class.java)

    @PostConstruct
    fun initialize() {
        if (FirebaseApp.getApps().isNotEmpty()) return

        try {
            val credentials = GoogleCredentials.fromStream(FileInputStream(serviceAccountPath))
            val options =
                FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build()
            FirebaseApp.initializeApp(options)
            log.info("Firebase Admin SDK 초기화 완료")
        } catch (e: Exception) {
            log.error("Firebase Admin SDK 초기화 실패: {}", e.message)
            throw e
        }
    }
}
