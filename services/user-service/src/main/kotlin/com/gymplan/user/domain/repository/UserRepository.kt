package com.gymplan.user.domain.repository

import com.gymplan.user.domain.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * users 테이블 리포지토리.
 *
 * 모든 조회는 파라미터 바인딩(JPA Parameterized Query)만 사용한다.
 * Raw SQL / 문자열 concatenation 절대 금지 (docs/context/security-guide.md).
 */
@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?

    fun existsByEmail(email: String): Boolean
}
