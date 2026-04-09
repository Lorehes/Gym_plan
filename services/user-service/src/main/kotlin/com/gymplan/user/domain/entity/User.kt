package com.gymplan.user.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

/**
 * users 테이블 JPA 엔티티.
 *
 * 스키마 출처: docs/database/mysql-schema.md
 *
 * ```sql
 * CREATE TABLE users (
 *   id          BIGINT PRIMARY KEY AUTO_INCREMENT,
 *   email       VARCHAR(255) UNIQUE NOT NULL,
 *   password    VARCHAR(255) NOT NULL,       -- BCrypt hash
 *   nickname    VARCHAR(50)  NOT NULL,
 *   profile_img VARCHAR(500),
 *   status      ENUM('ACTIVE','INACTIVE','BANNED') DEFAULT 'ACTIVE',
 *   created_at  DATETIME DEFAULT NOW(),
 *   updated_at  DATETIME DEFAULT NOW() ON UPDATE NOW()
 * );
 * ```
 *
 * 보안 규칙 (docs/context/security-guide.md):
 * - password 필드는 BCrypt 해시만 저장 (평문 금지)
 * - 이메일은 소문자로 정규화 저장 (중복 검사 일관성)
 * - password 는 toString 등에 노출되지 않도록 주의
 */
@Entity
@Table(name = "users")
class User(
    email: String,
    password: String,
    nickname: String,
    profileImg: String? = null,
    status: UserStatus = UserStatus.ACTIVE,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
        protected set

    @Column(name = "email", nullable = false, unique = true, length = 255)
    var email: String = email.lowercase()
        protected set

    @Column(name = "password", nullable = false, length = 255)
    var password: String = password
        protected set

    @Column(name = "nickname", nullable = false, length = 50)
    var nickname: String = nickname
        protected set

    @Column(name = "profile_img", length = 500)
    var profileImg: String? = profileImg
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: UserStatus = status
        protected set

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set

    @PrePersist
    fun onCreate() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }

    /**
     * 프로필 업데이트. null 인 필드는 변경하지 않음 (부분 업데이트).
     */
    fun updateProfile(
        nickname: String?,
        profileImg: String?,
    ) {
        nickname?.let { this.nickname = it }
        profileImg?.let { this.profileImg = it }
    }

    /** 비밀번호 교체 — 항상 BCrypt 해시만 받아야 한다. */
    fun changePassword(hashedPassword: String) {
        this.password = hashedPassword
    }

    override fun toString(): String = "User(id=$id, email=${email.mask()}, nickname=$nickname, status=$status)"

    private fun String.mask(): String {
        val atIdx = indexOf('@')
        if (atIdx <= 1) return "***"
        return "${first()}***${substring(atIdx)}"
    }
}
