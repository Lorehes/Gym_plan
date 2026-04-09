package com.gymplan.user.integration

import java.security.KeyPairGenerator
import java.util.Base64

/**
 * 통합 테스트 용 RSA 2048 키 페어를 런타임에 생성한다.
 *
 * 프로덕션에서는 Vault 를 통해 외부에서 주입되므로, 테스트 키를 저장소에 커밋하지 않기 위한 런타임 생성 패턴.
 * 생성된 PEM 은 [com.gymplan.common.security.JwtProvider] 의 `parsePublicKey`/`parsePrivateKey`
 * 와 호환되는 X.509 SubjectPublicKeyInfo / PKCS#8 포맷이다.
 */
object TestRsaKeys {
    data class Keys(
        val publicKeyPem: String,
        val privateKeyPem: String,
    )

    fun generate(): Keys {
        val keyPair =
            KeyPairGenerator.getInstance("RSA").apply {
                initialize(2048)
            }.generateKeyPair()

        val publicKeyPem =
            pem(
                type = "PUBLIC KEY",
                der = keyPair.public.encoded,
            )
        val privateKeyPem =
            pem(
                type = "PRIVATE KEY",
                der = keyPair.private.encoded,
            )
        return Keys(publicKeyPem, privateKeyPem)
    }

    private fun pem(
        type: String,
        der: ByteArray,
    ): String {
        val base64 = Base64.getEncoder().encodeToString(der)
        val body = base64.chunked(64).joinToString("\n")
        return "-----BEGIN $type-----\n$body\n-----END $type-----"
    }
}
