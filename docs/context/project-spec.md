# GymPlan 전체 기술 명세 요약

> 이 문서는 전체 프로젝트 명세의 요약본입니다.
> 상세 내용은 각 문서를 참조하세요. → [CLAUDE.md 문서 목록](../../CLAUDE.md)

---

## 프로젝트 한 줄 요약

체육관 이용자가 **웹으로 루틴을 계획**하고, **모바일로 실시간 실행·기록**하는 운동 관리 앱.

---

## 마이크로서비스 요약

| 서비스 | 포트 | 핵심 역할 | DB |
|--------|------|-----------|----|
| api-gateway | 8080 | JWT 검증, 라우팅, Rate Limit | - |
| user-service | 8081 | 인증, 회원가입, JWT RS256 | MySQL + Redis |
| plan-service | 8082 | 루틴 계획 CRUD, 오늘의 루틴 | MySQL + Redis |
| exercise-catalog | 8083 | 운동 종목 관리 및 검색 | MySQL + ES |
| workout-service | 8084 | 세션 실행, 세트 기록, Kafka 발행 | MongoDB |
| analytics-service | 8085 | Kafka 소비, 통계 집계 | Elasticsearch |
| notification-service | 8086 | Kafka 소비, 휴식 타이머 | Redis |

---

## 핵심 데이터 흐름

```
[사용자] → 루틴 계획 (plan-service + MySQL)
         → 체육관 입장 → 오늘의 루틴 조회 (Redis 캐시, P95 < 200ms)
         → 세트 기록 (workout-service + MongoDB)
         → 운동 완료 → Kafka 이벤트 발행
                     → analytics-service: 통계 집계 (Elasticsearch)
                     → notification-service: 알림 (Redis pub-sub)
```

---

## 상세 문서 링크

- [시스템 아키텍처](../architecture/system-architecture.md)
- [서비스 구성](../architecture/services.md)
- [Kafka 이벤트](../architecture/kafka-events.md)
- [MySQL 스키마](../database/mysql-schema.md)
- [MongoDB 스키마](../database/mongodb-schema.md)
- [Redis 키 설계](../database/redis-keys.md)
- [API 공통 규칙](../api/common.md)
- [Phase 계획](../planning/phase-plan.md)
- [스킬 사용 가이드](skill-guide.md)
- [보안 가이드](security-guide.md)
- [성능 목표](performance-goals.md)
