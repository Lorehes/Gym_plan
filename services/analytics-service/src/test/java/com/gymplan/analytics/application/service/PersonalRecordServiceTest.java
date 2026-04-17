package com.gymplan.analytics.application.service;

import com.gymplan.analytics.domain.document.PersonalRecordDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TC-006: PR 갱신 — 신기록 달성
 * TC-007: PR 갱신 — 기존 기록 이하
 * TC-008: 실패 세트는 PR 계산 제외
 * TC-009: isReliable=false 세트는 PR 갱신 제외
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
    @DisplayName("TC-006: 신규 1RM이 기존보다 크면 PR 갱신")
    void update_when_newRecordIsHigher() {
        String userId = "1";
        String exerciseId = "10";
        String docId = userId + "-" + exerciseId;

        PersonalRecordDocument existing = PersonalRecordDocument.builder()
                .id(docId)
                .userId(userId)
                .exerciseId(exerciseId)
                .estimated1RM(95.0)
                .build();

        when(esOps.get(eq(docId), eq(PersonalRecordDocument.class), any(IndexCoordinates.class)))
                .thenReturn(existing);

        service.checkAndUpdate(userId, exerciseId, "벤치프레스",
                80.0, 8, 101.3, true, true, Instant.now());

        ArgumentCaptor<IndexQuery> captor = ArgumentCaptor.forClass(IndexQuery.class);
        verify(esOps).index(captor.capture(), any(IndexCoordinates.class));

        PersonalRecordDocument saved = (PersonalRecordDocument) captor.getValue().getObject();
        assertThat(saved.getEstimated1RM()).isEqualTo(101.3);
    }

    @Test
    @DisplayName("TC-007: 신규 1RM이 기존보다 낮으면 갱신 안 함")
    void noUpdate_when_newRecordIsLower() {
        String userId = "1";
        String exerciseId = "10";
        String docId = userId + "-" + exerciseId;

        PersonalRecordDocument existing = PersonalRecordDocument.builder()
                .id(docId)
                .userId(userId)
                .exerciseId(exerciseId)
                .estimated1RM(101.3)
                .build();

        when(esOps.get(eq(docId), eq(PersonalRecordDocument.class), any(IndexCoordinates.class)))
                .thenReturn(existing);

        service.checkAndUpdate(userId, exerciseId, "벤치프레스",
                70.0, 5, 81.7, true, true, Instant.now());

        verify(esOps, never()).index(any(IndexQuery.class), any(IndexCoordinates.class));
    }

    @Test
    @DisplayName("TC-008: isSuccess=false 이면 PR 갱신 안 함")
    void noUpdate_when_setFailed() {
        service.checkAndUpdate("1", "10", "벤치프레스",
                100.0, 3, 110.0, false, true, Instant.now());

        verify(esOps, never()).get(any(), any(), any());
        verify(esOps, never()).index(any(IndexQuery.class), any(IndexCoordinates.class));
    }

    @Test
    @DisplayName("TC-009: isReliable=false 이면 PR 갱신 안 함")
    void noUpdate_when_notReliable() {
        service.checkAndUpdate("1", "10", "벤치프레스",
                30.0, 40, EpleyCalculator.calculate(30.0, 40), true, false, Instant.now());

        verify(esOps, never()).get(any(), any(), any());
        verify(esOps, never()).index(any(IndexQuery.class), any(IndexCoordinates.class));
    }

    @Test
    @DisplayName("기존 PR 없으면 최초 기록으로 저장")
    void create_when_noPreviousRecord() {
        String userId = "2";
        String exerciseId = "10";
        String docId = userId + "-" + exerciseId;

        when(esOps.get(eq(docId), eq(PersonalRecordDocument.class), any(IndexCoordinates.class)))
                .thenReturn(null);

        service.checkAndUpdate(userId, exerciseId, "스쿼트",
                100.0, 5, 116.7, true, true, Instant.now());

        verify(esOps).index(any(IndexQuery.class), any(IndexCoordinates.class));
    }
}
