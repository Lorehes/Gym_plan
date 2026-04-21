package com.gymplan.analytics.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Phase 2 E2E — analytics-service 통합 테스트 기반 클래스.
 *
 * Testcontainers로 Elasticsearch 8 + Kafka를 기동하고
 * DynamicPropertySource로 spring 프로퍼티를 주입합니다.
 *
 * 참조: docs/specs/analytics-service.md §비기능 요구사항
 */
public abstract class AbstractAnalyticsIntegrationTest {

    static final ElasticsearchContainer elasticsearch =
            new ElasticsearchContainer(
                    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.13.4")
            )
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node");

    static final ConfluentKafkaContainer kafka =
            new ConfluentKafkaContainer(
                    DockerImageName.parse("confluentinc/cp-kafka:7.4.0")
            );

    static {
        elasticsearch.start();
        kafka.start();
        createEsIndexTemplate();
    }

    /**
     * gymplan-sessions-* 및 gymplan-sets-* 에 적용될 인덱스 템플릿을 생성합니다.
     *
     * createIndex = false 로 인해 ES가 동적 매핑을 사용하면 muscleGroups 가
     * text 로 매핑되어 terms 집계가 실패합니다. 이를 방지하기 위해 Spring 컨텍스트
     * 시작 전에 인덱스 템플릿으로 keyword 매핑을 미리 설정합니다.
     */
    private static void createEsIndexTemplate() {
        String templateBody = """
                {
                  "index_patterns": ["gymplan-sessions-*", "gymplan-sets-*"],
                  "template": {
                    "mappings": {
                      "properties": {
                        "sessionId":    {"type": "keyword"},
                        "userId":       {"type": "keyword"},
                        "planId":       {"type": "keyword"},
                        "planName":     {"type": "text"},
                        "exerciseId":   {"type": "keyword"},
                        "exerciseName": {"type": "text"},
                        "muscleGroup":  {"type": "keyword"},
                        "muscleGroups": {"type": "keyword"},
                        "startedAt":    {"type": "date"},
                        "completedAt":  {"type": "date"},
                        "occurredAt":   {"type": "date"},
                        "durationSec":  {"type": "long"},
                        "totalVolume":  {"type": "double"},
                        "totalSets":    {"type": "integer"},
                        "volume":       {"type": "double"},
                        "weightKg":     {"type": "double"},
                        "estimated1RM": {"type": "double"},
                        "reps":         {"type": "integer"},
                        "setNo":        {"type": "integer"},
                        "isSuccess":    {"type": "boolean"},
                        "isReliable":   {"type": "boolean"}
                      }
                    }
                  }
                }
                """;
        try {
            URI url = URI.create("http://" + elasticsearch.getHttpHostAddress()
                    + "/_index_template/gymplan-monthly-template");
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(url)
                    .PUT(HttpRequest.BodyPublishers.ofString(templateBody))
                    .header("Content-Type", "application/json")
                    .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("ES index template 생성 실패: " + response.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("ES index template 초기화 중 오류", e);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", elasticsearch::getHttpHostAddress);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}
