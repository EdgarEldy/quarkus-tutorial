package com.edgareldy.quarkustutorial.dto.common;

import java.time.Instant;

/**
 * Generic envelope wrapping the payload of every successful (2xx) response.
 * It is success-only: errors are RFC 9457 Problem Details, never this type.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 *
 * @param <T> payload type
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp
) {

    /**
     * Builds a success envelope stamped with the current instant.
     *
     * @param data    the payload
     * @param message a human readable message
     * @param <T>     payload type
     * @return the success envelope
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }
}
