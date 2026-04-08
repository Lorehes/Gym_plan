# 성능 목표

> performance-engineer 스킬은 이 문서의 목표를 기준으로 최적화합니다.

---

## SLA 목표

| API | 목표 (P95) | 측정 방법 |
|-----|-----------|----------|
| 오늘의 루틴 조회 (Redis 캐시 히트) | **< 200ms** | Grafana / Prometheus |
| 세트 기록 추가 | **< 300ms** | Grafana / Prometheus |
| 운동 종목 검색 (Elasticsearch) | **< 500ms** | Grafana / Prometheus |
| 운동 세션 완료 | **< 500ms** | Grafana / Prometheus |
| 모바일 첫 화면 로딩 | **< 1초** | React Native Perf Monitor |
| 서비스 가용성 (월간) | **> 99.9%** | Uptime 모니터링 |

---

## 서비스별 최적화 전략

### plan-service (오늘의 루틴 조회 ⭐ 최우선)
```
Redis 캐시 우선 읽기 (Cache-Aside 패턴)
  → 캐시 히트: DB 쿼리 없음 (목표 달성)
  → 캐시 미스: DB 조회 후 Redis 저장 TTL 10분

DB 쿼리 최적화:
  → (user_id, day_of_week) 복합 인덱스
  → plan_exercises JOIN 단일 쿼리 (N+1 금지)
  → @EntityGraph 또는 fetch join 사용
```

### exercise-catalog (검색)
```
Elasticsearch 검색 우선
  → 자동완성: match_phrase_prefix
  → 전문 검색: multi_match (name, name_en)
  → MySQL은 fallback용으로만

인덱스 설정:
  → analyzer: nori (한국어 형태소)
  → 동의어 처리: 벤치 = 벤치프레스
```

### workout-service (세트 기록)
```
MongoDB 단일 문서 업데이트:
  → $push로 배열 append (전체 문서 교체 금지)
  → 진행 중 세션은 (userId, completedAt=null) 인덱스로 빠른 조회

Kafka 비동기 발행:
  → 세트 기록 API 응답 먼저, Kafka 발행은 비동기
  → 응답 시간에 Kafka 발행 포함 금지
```

### user-service (인증)
```
JWT 검증: Gateway에서만 수행
Redis 세션 조회: O(1) 단일 GET
BCrypt cost factor: 12 (보안/성능 균형)
```

---

## 모바일 성능 (React Native)

```
첫 화면 로딩 < 1초:
  → AsyncStorage에 오늘의 루틴 캐시
  → 앱 실행 시 캐시 즉시 표시 → 백그라운드에서 API 갱신

세트 체크인 UX:
  → 버튼 터치 → 즉시 로컬 상태 업데이트 (Optimistic Update)
  → API 호출은 백그라운드 (실패 시 롤백)

오프라인:
  → 세트 기록을 로컬 큐에 저장
  → 네트워크 복구 시 자동 동기화 (FIFO)
```

---

## 알람 기준 (Grafana)

| 조건 | 액션 |
|------|------|
| P95 응답 > 500ms (5분 지속) | Slack 알람 |
| 에러율 > 1% (1분 지속) | Slack 알람 |
| Pod 재시작 > 3회 (10분 내) | Slack 알람 |
| Kafka Consumer Lag > 1000 | Slack 알람 |
| Redis 메모리 > 80% | Slack 알람 |
