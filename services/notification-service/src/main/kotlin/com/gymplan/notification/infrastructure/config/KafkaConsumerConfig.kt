package com.gymplan.notification.infrastructure.config

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.util.backoff.FixedBackOff

/**
 * Kafka Consumer 설정.
 *
 * - 수동 ACK (MANUAL): 처리 성공 후 offset 커밋
 * - 재시도: 1회 후 DLQ 이동 ({topic}.dlq)
 * - 역직렬화: JsonDeserializer (타입 헤더 없이 페이로드 직렬화)
 */
@Configuration
class KafkaConsumerConfig(
    @Value("\${spring.kafka.bootstrap-servers}") private val bootstrapServers: String,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
) {
    @Bean
    fun consumerFactory(): ConsumerFactory<String, Any> {
        val deserializer =
            JsonDeserializer<Any>().apply {
                addTrustedPackages("com.gymplan.notification.infrastructure.kafka")
                setUseTypeHeaders(false)
            }

        val config =
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "gymplan-notification-service",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JsonDeserializer::class.java,
            )

        return DefaultKafkaConsumerFactory(config, StringDeserializer(), deserializer)
    }

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate)
        val errorHandler = DefaultErrorHandler(recoverer, FixedBackOff(0L, 1L))

        return ConcurrentKafkaListenerContainerFactory<String, Any>().apply {
            consumerFactory = consumerFactory()
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
            setCommonErrorHandler(errorHandler)
        }
    }
}
