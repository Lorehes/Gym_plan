package com.gymplan.analytics.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * PersonalRecordService 단위 테스트.
 *
 * 새 구현은 Read-then-Write 대신 ES Painless Script 원자 upsert를 사용.
 *   - Java 레이어에서 1RM 비교를 하지 않음 → ES 스크립트가 담당
 *   - 단위 테스트는 Java 가드 조건(isSuccess, isReliable)과
 *     esOps.update() 호출 여부만 검증
 *   - "신규 > 기존" 조건의 실제 동작은 통합 테스트(ES Testcontainers)에서 검증
 *
 * TC-006, TC-007: 스크립트 내부 비교 동작은 ES 레벨 → 통합 테스트 대상
 * TC-008: isSuccess=false 가드 — update() 미호출
 * TC-009: isReliable=false 가드 — update() 미호출
 */
@ExtendWith(MockitoExtension.class)
class PersonalRecordServiceTest {

    @Mock
    private ElasticsearchOperations esOps;

    private PersonalRecordService service;

    @BeforeEach
    void setUp() {
        service = new PersonalRecordService(esOps);
    }

    @Test
    @DisplayName("TC-006/기초: isSuccess && isReliable 조건 충족 시 esOps.update() 호출")
    void update_called_when_conditions_met() {
        service.checkAndUpdate("1", "10", "벤치프레스",
                80.0, 8, 101.3, true, true, Instant.now());

        ArgumentCaptor<UpdateQuery> captor = ArgumentCaptor.forClass(UpdateQuery.class);
        verify(esOps).update(captor.capture(), any(IndexCoordinates.class));

        UpdateQuery query = captor.getValue();
        assertThat(query.getId()).isEqualTo("1-10");
        assertThat(query.getRetryOnConflict()).isEqualTo(3);
    }

    @Test
    @DisplayName("TC-006: 신규 1RM이 기존보다 크면 update() 호출 (스크립트가 ES 레벨에서 갱신)")
    void update_called_for_new_high_record() {
        service.checkAndUpdate("1", "10", "벤치프레스",
                80.0, 8, 101.3, true, true, Instant.now());

        verify(esOps).update(any(UpdateQuery.class), any(IndexCoordinates.class));
    }

    @Test
    @DisplayName("TC-007: 신규 1RM이 기존보다 낮아도 update() 호출 (ctx.op=none은 ES 스크립트 담당)")
    void update_called_even_for_lower_record() {
        // Painless 스크립트가 ES 레벨에서 ctx.op='none'으로 처리하므로
        // Java 레이어는 무조건 update()를 호출함
        service.checkAndUpdate("1", "10", "벤치프레스",
                70.0, 5, 81.7, true, true, Instant.now());

        verify(esOps).update(any(UpdateQuery.class), any(IndexCoordinates.class));
    }

    @Test
    @DisplayName("TC-008: isSuccess=false 이면 update() 호출 안 함")
    void noUpdate_when_setFailed() {
        service.checkAndUpdate("1", "10", "벤치프레스",
                100.0, 3, 110.0, false, true, Instant.now());

        verify(esOps, never()).update(any(UpdateQuery.class), any(IndexCoordinates.class));
    }

    @Test
    @DisplayName("TC-009: isReliable=false 이면 update() 호출 안 함")
    void noUpdate_when_notReliable() {
        service.checkAndUpdate("1", "10", "벤치프레스",
                30.0, 40, EpleyCalculator.calculate(30.0, 40), true, false, Instant.now());

        verify(esOps, never()).update(any(UpdateQuery.class), any(IndexCoordinates.class));
    }

    @Test
    @DisplayName("기존 PR 없을 때도 update() 호출 (upsert doc으로 최초 생성)")
    void update_called_for_first_record() {
        service.checkAndUpdate("2", "10", "스쿼트",
                100.0, 5, 116.7, true, true, Instant.now());

        ArgumentCaptor<UpdateQuery> captor = ArgumentCaptor.forClass(UpdateQuery.class);
        verify(esOps).update(captor.capture(), any(IndexCoordinates.class));

        assertThat(captor.getValue().getId()).isEqualTo("2-10");
    }
}
