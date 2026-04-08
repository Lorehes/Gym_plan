package com.gymplan.common.dto

/**
 * 페이지네이션 응답의 표준 형태.
 *
 * docs/api/common.md 의 페이징 응답 규약과 1:1 매칭:
 * { content, page, size, totalElements, totalPages, last }
 *
 * Spring Data Page 객체에서 변환하는 from() 헬퍼 제공.
 */
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val last: Boolean,
) {
    companion object {
        /**
         * Spring Data 의 Page 객체에서 변환.
         *
         * 사용 예:
         *   val page: Page<UserEntity> = userRepository.findAll(pageable)
         *   PageResponse.from(page) { it.toDto() }
         */
        fun <S, T> from(
            page: org.springframework.data.domain.Page<S>,
            mapper: (S) -> T,
        ): PageResponse<T> =
            PageResponse(
                content = page.content.map(mapper),
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                last = page.isLast,
            )

        /** 변환 없이 그대로 사용 */
        fun <T> from(page: org.springframework.data.domain.Page<T>): PageResponse<T> =
            from(page) { it }
    }
}
