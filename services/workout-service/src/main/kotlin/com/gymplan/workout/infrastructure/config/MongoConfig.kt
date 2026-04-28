package com.gymplan.workout.infrastructure.config

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.connection.ConnectionPoolSettings
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import java.util.concurrent.TimeUnit

@Configuration
class MongoConfig(
    @Value("\${spring.data.mongodb.uri}") private val mongoUri: String,
) {
    @Bean
    fun mongoClientSettings(): MongoClientSettings =
        MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(mongoUri))
            .applicationName("workout-service")
            .applyToConnectionPoolSettings { builder: ConnectionPoolSettings.Builder ->
                builder
                    .maxSize(100)
                    .minSize(10)
                    .maxWaitTime(3_000, TimeUnit.MILLISECONDS)
                    .maxConnectionIdleTime(10, TimeUnit.MINUTES)
                    .maxConnectionLifeTime(30, TimeUnit.MINUTES)
            }
            .build()

    @Bean
    fun mongoDatabaseFactory(settings: MongoClientSettings): MongoDatabaseFactory {
        val connectionString = ConnectionString(mongoUri)
        val dbName = connectionString.database ?: "gymplan_workout"
        return SimpleMongoClientDatabaseFactory(
            com.mongodb.client.MongoClients.create(settings),
            dbName,
        )
    }

    @Bean
    fun mongoTemplate(factory: MongoDatabaseFactory): MongoTemplate = MongoTemplate(factory)
}
