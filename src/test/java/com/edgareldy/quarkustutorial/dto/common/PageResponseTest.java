package com.edgareldy.quarkustutorial.dto.common;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PageResponse page count computation.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
class PageResponseTest {

    @Test
    void _01_ShouldUseCeilingDivision_WhenComputingTotalPages() {
        assertEquals(3, PageResponse.of(List.of(1, 2, 3), 0, 3, 7).totalPages());
        assertEquals(2, PageResponse.of(List.of(1, 2, 3), 0, 3, 6).totalPages());
        assertEquals(1, PageResponse.of(List.of(1), 0, 10, 1).totalPages());
    }

    @Test
    void _02_ShouldYieldZeroPages_WhenSizeIsZero() {
        assertEquals(0, PageResponse.of(List.of(), 0, 0, 5).totalPages());
    }

    @Test
    void _03_ShouldYieldZeroPages_WhenContentIsEmpty() {
        PageResponse<String> page = PageResponse.of(List.of(), 0, 10, 0);
        assertEquals(0, page.totalPages());
        assertTrue(page.content().isEmpty());
        assertEquals(0, page.totalElements());
    }

    @Test
    void _04_ShouldKeepPassedValues_WhenBuilt() {
        PageResponse<Integer> page = PageResponse.of(List.of(4, 5), 2, 2, 6);
        assertEquals(2, page.page());
        assertEquals(2, page.size());
        assertEquals(6, page.totalElements());
        assertEquals(List.of(4, 5), page.content());
    }
}
