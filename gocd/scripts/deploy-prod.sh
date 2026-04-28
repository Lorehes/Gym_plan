#!/usr/bin/env bash
# deploy-prod.sh
# Stage 4: gymplan-prod 네임스페이스 프로덕션 배포 (ArgoCD GitOps)
#
# 실행 환경 (GoCD Agent 필수 도구):
#   kubectl, kustomize, argocd CLI, git, curl
#
# 주입 환경변수 (Vault Agent Injector):
#   ARGOCD_AUTH_TOKEN  — ArgoCD API 인증 토큰
#   KUBECONFIG         — K8s cluster 접근 설정
#   SLACK_WEBHOOK_URL  — 배포 알림 Slack Webhook
#
# GoCD 파이프라인 환경변수:
#   IMAGE_REGISTRY, ARGOCD_SERVER, PROD_NAMESPACE
#
# 설계 원칙:
#   - Stage → Prod "Promote": Stage와 동일한 IMAGE_TAG를 Prod overlays에 반영
#   - Blue-Green 또는 Canary 전환은 Argo Rollouts 에서 관리 (별도 설정)
#   - 배포 실패 시 ArgoCD rollback 자동 실행

set -euo pipefail

# ───── 공통 변수 ─────
IMAGE_TAG=$(grep IMAGE_TAG ci-artifacts/env.properties | cut -d= -f2)
SERVICES=(
  api-gateway
  user-service
  plan-service
  exercise-catalog
  workout-service
  analytics-service
  notification-service
)
OVERLAY_DIR="infra/k8s/overlays/prod"
ARGOCD_APP="gymplan-prod"
ROLLOUT_TIMEOUT=300  # 초

echo "================================================================"
echo "Production 배포 시작"
echo "IMAGE_TAG     : ${IMAGE_TAG}"
echo "NAMESPACE     : ${PROD_NAMESPACE}"
echo "ARGOCD_SERVER : ${ARGOCD_SERVER}"
echo "승인자        : ${GO_TRIGGER_USER:-manual}"
echo "================================================================"

# ───── Slack 알림 함수 ─────
notify_slack() {
  local STATUS=$1
  local MESSAGE=$2
  local COLOR

  case "${STATUS}" in
    start)   COLOR="#36a64f" ;;
    success) COLOR="#36a64f" ;;
    failure) COLOR="#cc0000" ;;
    *)       COLOR="#cccccc" ;;
  esac

  curl -sf -X POST "${SLACK_WEBHOOK_URL}" \
    -H "Content-Type: application/json" \
    -d "{
      \"attachments\": [{
        \"color\": \"${COLOR}\",
        \"title\": \"GymPlan 프로덕션 배포 — ${STATUS}\",
        \"text\": \"${MESSAGE}\",
        \"fields\": [
          {\"title\": \"IMAGE_TAG\", \"value\": \"${IMAGE_TAG}\", \"short\": true},
          {\"title\": \"승인자\",    \"value\": \"${GO_TRIGGER_USER:-manual}\", \"short\": true}
        ]
      }]
    }" || echo "Slack 알림 실패 (무시)"
}

# ───── 실패 시 롤백 핸들러 ─────
on_failure() {
  echo "❌ 배포 실패 — ArgoCD 롤백 시작..."
  notify_slack "failure" "배포 실패. ArgoCD 롤백을 시작합니다.\nIMAGE_TAG: ${IMAGE_TAG}"

  argocd app rollback "${ARGOCD_APP}" --grpc-web || true
  exit 1
}
trap on_failure ERR

notify_slack "start" "프로덕션 배포를 시작합니다."

# ───── 1. Prod overlays 이미지 태그 업데이트 (Stage 태그 Promote) ─────
echo "[1/5] Prod 이미지 태그 업데이트..."

cd "${OVERLAY_DIR}"

for SERVICE in "${SERVICES[@]}"; do
  kustomize edit set image \
    "${IMAGE_REGISTRY}/${SERVICE}=${IMAGE_REGISTRY}/${SERVICE}:${IMAGE_TAG}"
  echo "  ✓ ${SERVICE}:${IMAGE_TAG}"
done

cd -

# ───── 2. 변경된 매니페스트 Git 커밋 & 푸시 (GitOps) ─────
echo "[2/5] 매니페스트 Git 커밋..."

git config user.email "gocd@gymplan.internal"
git config user.name  "GoCD Deploy Bot"
git add "${OVERLAY_DIR}/kustomization.yaml"
git commit -m "chore(deploy): prod image tag → ${IMAGE_TAG} [skip ci]

승인자: ${GO_TRIGGER_USER:-manual}
파이프라인: ${GO_PIPELINE_NAME}/${GO_PIPELINE_COUNTER}"
git push origin main

# ───── 3. ArgoCD Sync → gymplan-prod ─────
echo "[3/5] ArgoCD Sync 트리거..."

argocd login "${ARGOCD_SERVER}" \
  --auth-token "${ARGOCD_AUTH_TOKEN}" \
  --grpc-web \
  --insecure

argocd app sync "${ARGOCD_APP}" \
  --force \
  --timeout "${ROLLOUT_TIMEOUT}" \
  --grpc-web

# ───── 4. 롤아웃 완료 대기 (전체 서비스) ─────
echo "[4/5] 롤아웃 완료 대기..."

for SERVICE in "${SERVICES[@]}"; do
  echo "  대기 중: ${SERVICE}..."
  kubectl rollout status deployment/"${SERVICE}" \
    -n "${PROD_NAMESPACE}" \
    --timeout="${ROLLOUT_TIMEOUT}s"
  echo "  ✓ ${SERVICE} Ready"
done

# ───── 5. 스모크 테스트 (핵심 엔드포인트 200 확인) ─────
echo "[5/5] 스모크 테스트..."

PROD_GATEWAY_URL="https://api.gymplan.app"
SMOKE_ENDPOINTS=(
  "/actuator/health"
  "/api/exercises?q=squat"
)

for ENDPOINT in "${SMOKE_ENDPOINTS[@]}"; do
  HTTP_STATUS=$(curl -sf -o /dev/null -w "%{http_code}" \
    "${PROD_GATEWAY_URL}${ENDPOINT}" || echo "000")

  if [[ "${HTTP_STATUS}" == "200" || "${HTTP_STATUS}" == "401" ]]; then
    echo "  ✓ ${ENDPOINT} → ${HTTP_STATUS}"
  else
    echo "  ❌ ${ENDPOINT} → ${HTTP_STATUS}"
    exit 1
  fi
done

# ───── 결과 기록 ─────
cat > deploy-prod-result.txt << EOF
deploy_type=prod
image_tag=${IMAGE_TAG}
namespace=${PROD_NAMESPACE}
timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
approved_by=${GO_TRIGGER_USER:-manual}
pipeline=${GO_PIPELINE_NAME}/${GO_PIPELINE_COUNTER}
services=${SERVICES[*]}
status=success
EOF

notify_slack "success" "프로덕션 배포 완료. 스모크 테스트 통과."

echo "================================================================"
echo "Production 배포 완료: ${PROD_NAMESPACE}"
echo "================================================================"
