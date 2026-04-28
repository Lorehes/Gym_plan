#!/usr/bin/env bash
# deploy-stage.sh
# Stage 1: gymplan-stage 네임스페이스에 전체 서비스 배포
#
# 실행 환경 (GoCD Agent 필수 도구):
#   kubectl, kustomize, argocd CLI, git, gsutil
#
# 주입 환경변수 (Vault Agent Injector):
#   ARGOCD_AUTH_TOKEN  — ArgoCD API 인증 토큰
#   KUBECONFIG         — K8s cluster 접근 설정
#
# GoCD 파이프라인 환경변수:
#   IMAGE_REGISTRY, ARGOCD_SERVER, STAGE_NAMESPACE

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
OVERLAY_DIR="infra/k8s/overlays/stage"
MANIFESTS_REPO="https://github.com/Lorehes/Gym_plan.git"
ARGOCD_APP="gymplan-stage"

echo "================================================================"
echo "Stage 배포 시작"
echo "IMAGE_TAG     : ${IMAGE_TAG}"
echo "NAMESPACE     : ${STAGE_NAMESPACE}"
echo "ARGOCD_SERVER : ${ARGOCD_SERVER}"
echo "================================================================"

# ───── 1. K8s 매니페스트 이미지 태그 업데이트 (kustomize) ─────
echo "[1/4] 이미지 태그 업데이트..."

cd "${OVERLAY_DIR}"

for SERVICE in "${SERVICES[@]}"; do
  kustomize edit set image \
    "${IMAGE_REGISTRY}/${SERVICE}=${IMAGE_REGISTRY}/${SERVICE}:${IMAGE_TAG}"
  echo "  ✓ ${SERVICE}:${IMAGE_TAG}"
done

cd -

# ───── 2. 변경된 매니페스트 Git 커밋 & 푸시 (GitOps) ─────
echo "[2/4] 매니페스트 Git 커밋..."

git config user.email "gocd@gymplan.internal"
git config user.name  "GoCD Deploy Bot"
git add "${OVERLAY_DIR}/kustomization.yaml"
git commit -m "chore(deploy): stage image tag → ${IMAGE_TAG} [skip ci]"
git push origin develop

# ───── 3. ArgoCD Sync → gymplan-stage ─────
echo "[3/4] ArgoCD Sync 트리거..."

argocd login "${ARGOCD_SERVER}" \
  --auth-token "${ARGOCD_AUTH_TOKEN}" \
  --grpc-web \
  --insecure

argocd app sync "${ARGOCD_APP}" \
  --force \
  --timeout 300 \
  --grpc-web

# ───── 4. 롤아웃 완료 대기 (전체 서비스) ─────
echo "[4/4] 롤아웃 완료 대기..."

for SERVICE in "${SERVICES[@]}"; do
  echo "  대기 중: ${SERVICE}..."
  kubectl rollout status deployment/"${SERVICE}" \
    -n "${STAGE_NAMESPACE}" \
    --timeout=180s
  echo "  ✓ ${SERVICE} Ready"
done

# ───── 결과 기록 ─────
cat > deploy-stage-result.txt << EOF
deploy_type=stage
image_tag=${IMAGE_TAG}
namespace=${STAGE_NAMESPACE}
timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
services=${SERVICES[*]}
status=success
EOF

echo "================================================================"
echo "Stage 배포 완료: ${STAGE_NAMESPACE}"
echo "================================================================"
