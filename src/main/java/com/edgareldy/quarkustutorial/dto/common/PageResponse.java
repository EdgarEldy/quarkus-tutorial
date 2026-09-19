package com.edgareldy.quarkustutorial.dto.common;

import java.util.List;

/**
 * Generic page of results, used as the payload of {@code ApiResponse<PageResponse<T>>} for list endpoints.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 *
 * @param <T> element type
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /**
     * Builds a page and computes the total number of pages from the total element count.
     *
     * @param content       the elements of this page
     * @param page          zero-based page index
     * @param size          requested page size
     * @param totalElements total number of elements across all pages
     * @param <T>           element type
     * @return the page
     */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }
}
