package com.gymplan.user.infrastructure.cache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * TokenStore.hash() 는 원본 토큰을 Redis 에 저장하지 않기 위한 결정적 SHA-256 해시.
 * Redis 인스턴스가 필요 없으므로 단위 테스트로 충분.
 */
class TokenStoreHashTest {
    @Test
    @DisplayName("같은 토큰은 같은 해시를 생성한다 (결정적)")
    fun deterministic() {
        val a = TokenStore.hash("eyJ.some.token")
        val b = TokenStore.hash("eyJ.some.token")
        assertThat(a).isEqualTo(b)
    }

    @Test
    @DisplayName("다른 토큰은 다른 해시를 생성한다")
    fun different() {
        val a = TokenStore.hash("token-a")
        val b = TokenStore.hash("token-b")
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    @DisplayName("해시 출력은 64자 hex")
    fun hexLength() {
        val h = TokenStore.hash("x")
        assertThat(h).hasSize(64).matches("[0-9a-f]+")
    }

    @Test
    @DisplayName("원본 토큰 문자열이 해시 출력에 포함되지 않는다 (원문 노출 금지)")
    fun noLeak() {
        val token = "supersecret-refresh-token-12345"
        val h = TokenStore.hash(token)
        assertThat(h).doesNotContain("supersecret")
    }
}
