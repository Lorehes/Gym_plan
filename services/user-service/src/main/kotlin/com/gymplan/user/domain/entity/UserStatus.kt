package com.gymplan.user.domain.entity

/**
 * 사용자 계정 상태.
 *
 * docs/database/mysql-schema.md 의 users.status ENUM 과 1:1 매칭.
 *
 * - ACTIVE:   정상 로그인 가능
 * - INACTIVE: 휴면 계정 — 로그인 차단 (AUTH_ACCOUNT_DISABLED)
 * - BANNED:   정책 위반 — 로그인 차단 (AUTH_ACCOUNT_DISABLED)
 */
enum class UserStatus {
    ACTIVE,
    INACTIVE,
    BANNED,
    ;

    fun canLogin(): Boolean = this == ACTIVE
}
