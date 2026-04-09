package com.gymplan.user.presentation.security

/**
 * 인증된 사용자의 userId 를 주입받기 위한 메서드 파라미터 어노테이션.
 *
 * 사용 예:
 * ```
 * @GetMapping("/me")
 * fun getMe(@CurrentUserId userId: Long): ApiResponse<UserProfileResponse> { ... }
 * ```
 *
 * 동작 원리: [CurrentUserIdArgumentResolver] 가 X-User-Id 헤더를 읽어
 * Long 값으로 변환해 주입한다. 헤더가 없으면 401 을 던진다.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentUserId
