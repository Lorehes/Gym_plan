package com.gymplan.plan.infrastructure.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import javax.sql.DataSource

/**
 * DataSource 설정 — LazyConnectionDataSourceProxy + HikariCP.
 *
 * ## 성능 문제 및 해결
 *
 * [문제] `TodayPlanService.getTodayPlan()`은 `@Transactional(readOnly = true)` 로 선언되어 있다.
 * Spring의 기본 동작은 트랜잭션 시작 시점(메서드 진입)에 HikariCP에서 JDBC 커넥션을 획득한다.
 *
 * Redis 캐시 HIT 경로에서는 SQL이 전혀 실행되지 않지만,
 * 이미 획득된 커넥션이 메서드 종료까지 점유된다.
 *
 * [영향] 체육관 오픈 시간 동시 100명 접속, 모두 캐시 HIT 시나리오:
 *   - HikariCP 기본 풀 10개 → 동시 10개 요청만 처리 가능
 *   - 11번째 요청은 connectionTimeout(30s) 대기 → P95 > 200ms 위반 가능
 *
 * [해결] `LazyConnectionDataSourceProxy`: 실제 SQL이 실행될 때만 풀에서 커넥션을 가져온다.
 *   - 캐시 HIT 경로: SQL 없음 → 커넥션 0개 사용
 *   - 캐시 MISS 경로: DB 조회 시점에만 커넥션 1개 사용
 *
 * 성능 목표: docs/context/performance-goals.md — 오늘의 루틴 P95 < 200ms
 */
@Configuration
class DataSourceConfig {

    /**
     * HikariCP DataSource.
     * spring.datasource.* 기본 설정 + spring.datasource.hikari.* 풀 튜닝 적용.
     * "hikariDataSource" 이름으로 등록하여 lazyDataSource와 순환 의존성 방지.
     */
    @Bean("hikariDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    fun hikariDataSource(properties: DataSourceProperties): HikariDataSource =
        properties.initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build() as HikariDataSource

    /**
     * Primary DataSource — HikariCP를 LazyConnectionDataSourceProxy로 래핑.
     * Spring Boot의 DataSourceAutoConfiguration이 @ConditionalOnMissingBean(DataSource.class)로
     * 선언되어 있으므로 이 Bean이 등록되면 자동 설정은 백오프된다.
     */
    @Bean
    @Primary
    fun dataSource(@Qualifier("hikariDataSource") hikari: HikariDataSource): DataSource =
        LazyConnectionDataSourceProxy(hikari)
}
