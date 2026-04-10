package com.gymplan.exercise.presentation.security

/**
 * 인증된 사용자의 userId 를 주입받기 위한 메서드 파라미터 어노테이션.
 *
 * Gateway 가 주입한 X-User-Id 헤더를 [CurrentUserIdArgumentResolver] 가
 * Long 값으로 변환해 주입한다.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentUserId
