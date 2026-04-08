# 프로젝트 컨텍스트

> 이 문서는 GymPlan 프로젝트의 배경, 의사결정 근거, 전체 흐름을 설명합니다.
> 새로운 스킬/에이전트가 프로젝트를 처음 접할 때 읽는 문서입니다.

---

## 왜 이 프로젝트인가

기존 피트니스 앱들의 문제:
- UI가 복잡해서 체육관에서 한 손으로 조작하기 불편
- 계획을 세우는 화면과 실행하는 화면이 분리되지 않음
- 오프라인 환경에서 동작하지 않음
- 로딩이 느려서 운동 흐름이 끊김

GymPlan의 차별점:
- **2-track 클라이언트**: 계획은 웹(편한 환경), 실행은 모바일(체육관 최적화)
- **1초 이내 로딩**: Redis 캐시로 체육관에서 즉시 시작
- **오프라인 우선**: 와이파이 불안정해도 세트 기록 가능
- **한 손 조작**: 운동 중 폰을 들고 탭 한 번으로 세트 완료

---

## 기술 의사결정 근거

### 왜 마이크로서비스인가?
- 서비스별 독립 배포 (운동 기록 서비스 업데이트가 인증 서비스에 영향 없음)
- 부하가 집중되는 서비스(workout, plan) 독립 스케일링
- 학습 목적: 실제 프로덕션 수준의 MSA 경험

### 왜 MongoDB를 workout에 쓰는가?
- 운동 세트는 종목마다 다름 (유연한 스키마 필요)
- 세션 전체를 단일 문서로 조회 (JOIN 없음 → 빠름)
- 시계열성 데이터에 적합

### 왜 Elasticsearch를 쓰는가?
- 운동 종목 검색: "벤치"로 "벤치프레스" 검색 (한국어 형태소 분석)
- analytics: 집계 쿼리가 RDB보다 빠름

### 왜 Kafka를 쓰는가?
- workout 완료 → analytics 집계, 알림 발송을 비동기 처리
- API 응답 속도와 사이드 이펙트 분리

### 클라이언트 기술 선택
- **React Native (Expo)**: 모바일 앱, 빠른 개발
- **React + Vite**: 웹앱, 익숙한 스택으로 빠른 개발
- 두 클라이언트가 동일한 REST API를 공유

---

## 운동 데이터 모델 이해

### 루틴 계층 구조
```
사용자 (User)
  └── 루틴 (WorkoutPlan) — "가슴/삼두 루틴", 월요일 배정
        └── 루틴 운동 항목 (PlanExercise) — 운동 순서, 목표 세트/무게
              └── 운동 종목 (Exercise) — 벤치프레스 (카탈로그)
```

### 실행 계층 구조
```
운동 세션 (WorkoutSession) — 2026-04-08 09:00 시작
  └── 세션 운동 (exercises 배열) — 벤치프레스
        └── 세트 기록 (sets 배열) — 1세트: 70kg x 10회 ✅
```

### 비정규화 원칙
- 세션 기록에는 `exerciseName`, `planName` 직접 저장
- 이유: 나중에 루틴/종목이 수정/삭제되어도 과거 기록 유지
- 단점: 종목명 변경 시 과거 기록에 반영 안 됨 (의도적 설계)

---

## 개발 환경 세팅 순서

```bash
# 1. 저장소 클론
git clone https://github.com/{owner}/gymplan.git
cd gymplan

# 2. 인프라 실행
cd infra/docker-compose
docker compose -f docker-compose.local.yml up -d

# 3. 서비스 실행 (개별)
cd services/user-service
./gradlew bootRun --args='--spring.profiles.active=local'

# 4. API 테스트
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"Test1234!","nickname":"테스터"}'
```

---

## 브랜치 & PR 전략

```
main ←── develop ←── feature/*
                ←── fix/*

feature/* : 기능 개발 (Worktree 필수)
fix/*     : 버그 수정 (Worktree 권장)

PR 검토 순서:
  feature-lead (PR 생성)
  → tech-lead (기술 검토)
  → project-manager (최종 승인 & develop 머지)

main 머지: project-manager가 develop → main PR 생성 및 승인
```
