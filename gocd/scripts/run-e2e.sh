#!/usr/bin/env bash
# run-e2e.sh
# Stage 2: Stage 환경 대상 E2E 테스트 실행
#
# 실행 환경 (GoCD Agent 필수 도구):
#   JDK 17, gradle wrapper
#
# 테스트 대상:
#   e2e/ScenarioATest — 루틴 생성 → 오늘의 루틴 조회 흐름
#   e2e/ScenarioBTest — 운동 종목 검색 흐름
#
# GoCD 파이프라인 환경변수:
#   STAGE_GATEWAY_URL — Stage API Gateway URL

set -euo pipefail

IMAGE_TAG=$(grep IMAGE_TAG ci-artifacts/env.properties | cut -d= -f2)

echo "================================================================"
echo "E2E 테스트 시작"
echo "IMAGE_TAG   : ${IMAGE_TAG}"
echo "GATEWAY_URL : ${STAGE_GATEWAY_URL}"
echo "================================================================"

# ───── 1. Stage 환경 헬스체크 (최대 2분 대기) ─────
echo "[1/3] Stage 환경 헬스체크..."

MAX_RETRY=24
RETRY_INTERVAL=5
RETRY=0

until curl -sf "${STAGE_GATEWAY_URL}/actuator/health" > /dev/null 2>&1; do
  RETRY=$((RETRY + 1))
  if [ "${RETRY}" -ge "${MAX_RETRY}" ]; then
    echo "❌ Stage 환경 헬스체크 실패 (${MAX_RETRY}회 재시도 초과)"
    exit 1
  fi
  echo "  대기 중... (${RETRY}/${MAX_RETRY})"
  sleep "${RETRY_INTERVAL}"
done

echo "  ✓ Stage 환경 정상"

# ───── 2. E2E 테스트 실행 ─────
echo "[2/3] E2E 테스트 실행 중..."

# E2E 테스트에 Stage URL 주입
export E2E_GATEWAY_URL="${STAGE_GATEWAY_URL}"
export E2E_IMAGE_TAG="${IMAGE_TAG}"

./gradlew \
  --no-daemon \
  --continue \
  :e2e:test \
  -PE2E_GATEWAY_URL="${STAGE_GATEWAY_URL}" \
  2>&1 | tee e2e-output.log

E2E_EXIT_CODE=${PIPESTATUS[0]}

# ───── 3. 결과 요약 출력 ─────
echo "[3/3] 테스트 결과 요약..."

if [ -d "e2e/build/test-results/test" ]; then
  TOTAL=$(grep -r 'tests=' e2e/build/test-results/test/*.xml \
    | grep -o 'tests="[0-9]*"' | grep -o '[0-9]*' | awk '{s+=$1}END{print s}')
  FAILURES=$(grep -r 'failures=' e2e/build/test-results/test/*.xml \
    | grep -o 'failures="[0-9]*"' | grep -o '[0-9]*' | awk '{s+=$1}END{print s}')
  ERRORS=$(grep -r 'errors=' e2e/build/test-results/test/*.xml \
    | grep -o 'errors="[0-9]*"' | grep -o '[0-9]*' | awk '{s+=$1}END{print s}')

  echo "  전체  : ${TOTAL:-0}"
  echo "  실패  : ${FAILURES:-0}"
  echo "  오류  : ${ERRORS:-0}"
fi

if [ "${E2E_EXIT_CODE}" -ne 0 ]; then
  echo "❌ E2E 테스트 실패 — 프로덕션 배포 차단"
  exit "${E2E_EXIT_CODE}"
fi

echo "================================================================"
echo "E2E 테스트 전체 통과"
echo "================================================================"
