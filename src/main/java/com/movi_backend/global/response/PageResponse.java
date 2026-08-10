package com.movi_backend.global.response;

import java.util.List;

/**
 * 페이징 목록 응답. 거래내역 조회 등 목록 API에서 {@code ApiResponse}의 data로 사용한다.
 *
 * <p>3명이 각자 다른 페이징 형식을 만들면 프론트가 API마다 다르게 파싱해야 하므로,
 * 목록 응답은 이 타입으로 통일한다.
 *
 * <p>Spring Data {@code Page}를 변환하는 팩토리는 JPA 의존성 추가 후 넣는다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static <T> PageResponse<T> of(
            final List<T> content,
            final int page,
            final int size,
            final long totalElements
    ) {
        final int totalPages = calculateTotalPages(size, totalElements);
        final boolean hasNext = page + 1 < totalPages;
        return new PageResponse<>(content, page, size, totalElements, totalPages, hasNext);
    }

    private static int calculateTotalPages(final int size, final long totalElements) {
        if (size <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElements / size);
    }
}
