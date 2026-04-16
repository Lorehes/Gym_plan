#!/usr/bin/env bash
# ============================================================
# E2E 테스트용 RSA 키 쌍 생성 → .env.e2e
#
# 사용: ./scripts/gen-e2e-keys.sh
#
# 출력: 프로젝트 루트 .env.e2e (gitignore 처리됨)
# 경고: 생성된 키는 테스트 전용. 운영 환경에 절대 사용 금지.
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$PROJECT_ROOT/.env.e2e"
TMPDIR_KEYS="$(mktemp -d)"

cleanup() { rm -rf "$TMPDIR_KEYS"; }
trap cleanup EXIT

echo "[gen-e2e-keys] RSA 2048 키 생성 중..."

# RSA 키 생성 (PKCS#8 DER 형식으로 추출)
openssl genrsa -out "$TMPDIR_KEYS/private.pem" 2048 2>/dev/null
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt \
    -in "$TMPDIR_KEYS/private.pem" \
    -out "$TMPDIR_KEYS/private_pkcs8.pem"
openssl rsa -pubout -in "$TMPDIR_KEYS/private.pem" \
    -out "$TMPDIR_KEYS/public.pem" 2>/dev/null

# PEM → 단일 행 (헤더/푸터 포함, 개행 제거)
# JwtProvider.pemToDer() 가 공백을 전부 제거하므로 단일 행 PEM 이 올바르게 파싱됨
PRIVATE_KEY_SINGLE=$(awk '/-----BEGIN PRIVATE KEY-----/{p=1} p{printf $0} /-----END PRIVATE KEY-----/{p=0}' "$TMPDIR_KEYS/private_pkcs8.pem" | tr -d '\n')
PUBLIC_KEY_SINGLE=$(awk '/-----BEGIN PUBLIC KEY-----/{p=1} p{printf $0} /-----END PUBLIC KEY-----/{p=0}' "$TMPDIR_KEYS/public.pem" | tr -d '\n')

cat > "$ENV_FILE" << EOF
# ============================================================
# E2E 테스트 전용 환경변수
# 생성: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
# 경고: 테스트 전용 RSA 키 — 운영 환경 절대 사용 금지
# ============================================================

# MySQL
MYSQL_ROOT_PASSWORD=e2eroot
MYSQL_DATABASE=gymplan_user
MYSQL_USER=gymplan
MYSQL_PASSWORD=e2epw

# Redis
REDIS_PASSWORD=e2eredis

# JWT RSA 테스트 키 (단일 행 PEM, 개행 없음)
JWT_PRIVATE_KEY=${PRIVATE_KEY_SINGLE}
JWT_PUBLIC_KEY=${PUBLIC_KEY_SINGLE}
EOF

echo "[gen-e2e-keys] 완료: $ENV_FILE"
echo "[gen-e2e-keys] 경고: 이 파일은 git 에 커밋하지 마세요 (.gitignore 확인)"
