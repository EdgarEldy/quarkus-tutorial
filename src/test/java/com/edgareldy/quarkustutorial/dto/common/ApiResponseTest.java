package com.edgareldy.quarkustutorial.dto.common;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the success-only ApiResponse envelope.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
class ApiResponseTest {

    @Test
    void _01_ShouldBuildEnvelopeWithAllFields_WhenCreatedWithSuccess() {
        Instant before = Instant.now();
        ApiResponse<String> response = ApiResponse.success("payload", "done");
        Instant after = Instant.now();

        assertTrue(response.success());
        assertEquals("done", response.message());
        assertEquals("payload", response.data());
        assertNotNull(response.timestamp());
        assertFalse(response.timestamp().isBefore(before));
        assertFalse(response.timestamp().isAfter(after));
    }

    @Test
    void _02_ShouldAcceptNullData_WhenCreatedWithSuccess() {
        ApiResponse<Object> response = ApiResponse.success(null, "empty");
        assertTrue(response.success());
        assertNull(response.data());
    }
}
